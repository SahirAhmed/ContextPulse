package com.contextpulse.phase0

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.contextpulse.phase0.model.*
import com.contextpulse.phase0.repository.RoomProfileRepository
import com.contextpulse.phase0.system.BaselineStateManager
import com.contextpulse.phase0.system.ContextEngine
import com.contextpulse.phase0.system.SimulatedSystemStateProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BaselineStateEngineTest {

    private lateinit var context: Context
    private lateinit var repository: RoomProfileRepository
    private lateinit var baselineManager: BaselineStateManager
    private lateinit var simulatedProvider: SimulatedSystemStateProvider
    private lateinit var contextEngine: ContextEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()

        // Clear all persistent test preferences
        context.getSharedPreferences("contextpulse_room_profiles_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("contextpulse_baseline_state_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        repository = RoomProfileRepository(context)
        baselineManager = BaselineStateManager(context)
        // Initial simulated phone state: 75% brightness, Manual, All notifications
        simulatedProvider = SimulatedSystemStateProvider(
            initialBrightness = 75,
            initialBrightnessMode = BrightnessMode.MANUAL,
            initialDnd = DndState.ALL
        )
        contextEngine = ContextEngine(repository, baselineManager, simulatedProvider)
    }

    @Test
    fun `test initial state has no active room and no baseline`() {
        assertNull("Initial active room should be null", repository.activeRoomId.value)
        assertFalse("Initial state should have no baseline", baselineManager.hasBaseline())
        assertEquals(TransitionState.IDLE, baselineManager.transitionState.value)
        assertEquals(75, simulatedProvider.simulatedBrightness.value)
        assertEquals(DndState.ALL, simulatedProvider.simulatedDnd.value)
    }

    @Test
    fun `test 0 to 1 activation captures baseline and applies room profile`() {
        val bedroom = repository.rooms.value.find { it.name == "Bedroom" }!!
        // Bedroom defaults: brightness 25%, DND Priority Only
        assertEquals(25, bedroom.profile.brightnessLevel)
        assertTrue(bedroom.profile.brightnessEnabled)
        assertTrue(bedroom.profile.dndEnabled)

        val success = contextEngine.activateRoom(bedroom.id)
        assertTrue("Activation should succeed", success)

        // Verify active room
        assertEquals(bedroom.id, repository.activeRoomId.value)
        assertEquals("Bedroom", repository.activeRoom.value?.name)

        // Verify baseline was captured with the original 75% and ALL
        assertTrue("Baseline must exist", baselineManager.hasBaseline())
        val baseline = baselineManager.baselineSnapshot.value!!
        assertEquals(75, baseline.brightnessLevel.value)
        assertEquals(BrightnessMode.MANUAL, baseline.brightnessMode.value)
        assertEquals(DndState.ALL, baseline.dndState.value)
        assertEquals(bedroom.id, baseline.activeRoomIdAtCapture)

        // Verify simulated hardware output reflects Bedroom profile (25% and Priority Only)
        assertEquals(25, simulatedProvider.simulatedBrightness.value)
        assertEquals(DndState.PRIORITY, simulatedProvider.simulatedDnd.value)

        // Verify transition state is ACTIVE
        assertEquals(TransitionState.ACTIVE, baselineManager.transitionState.value)
    }

    @Test
    fun `test 1 to 2 room switch DOES NOT overwrite original baseline`() {
        val bedroom = repository.rooms.value.find { it.name == "Bedroom" }!!
        val office = repository.rooms.value.find { it.name == "Office" }!!
        // Office defaults: brightness 80%, DND Priority Only

        // 1. Activate Bedroom (0 -> 1)
        contextEngine.activateRoom(bedroom.id)
        val initialBaseline = baselineManager.baselineSnapshot.value!!
        assertEquals(75, initialBaseline.brightnessLevel.value)
        assertEquals(bedroom.id, initialBaseline.activeRoomIdAtCapture)
        assertEquals(25, simulatedProvider.simulatedBrightness.value)

        // 2. Switch from Bedroom to Office (1 -> 2)
        val switchSuccess = contextEngine.activateRoom(office.id)
        assertTrue("Switching to Office should succeed", switchSuccess)

        // Verify active room is now Office (and only Office)
        assertEquals(office.id, repository.activeRoomId.value)
        assertEquals("Office", repository.activeRoom.value?.name)

        // Verify simulated hardware output updated to Office settings (80%)
        assertEquals(80, simulatedProvider.simulatedBrightness.value)

        // CRITICAL INVARIANT: The baseline MUST still be the original baseline (75%), NOT Bedroom's 25%!
        val currentBaseline = baselineManager.baselineSnapshot.value!!
        assertEquals(
            "Original baseline brightness must be preserved across room switch",
            75,
            currentBaseline.brightnessLevel.value
        )
        assertEquals(
            "Original baseline ID must match initial capture",
            initialBaseline.id,
            currentBaseline.id
        )
        assertEquals(
            "Original capture room must still be Bedroom",
            bedroom.id,
            currentBaseline.activeRoomIdAtCapture
        )
    }

    @Test
    fun `test 1 to 0 deactivation restores original baseline and clears snapshot`() {
        val bedroom = repository.rooms.value.find { it.name == "Bedroom" }!!
        val office = repository.rooms.value.find { it.name == "Office" }!!

        // Activate Bedroom -> Switch to Office
        contextEngine.activateRoom(bedroom.id)
        contextEngine.activateRoom(office.id)
        assertEquals(80, simulatedProvider.simulatedBrightness.value)
        assertTrue(baselineManager.hasBaseline())

        // 1 -> 0 Deactivate
        val deactivated = contextEngine.deactivateRoom()
        assertTrue("Deactivation should succeed", deactivated)

        // Active room must be null
        assertNull("Active room should be null after deactivation", repository.activeRoomId.value)
        assertNull(repository.activeRoom.value)

        // Simulated hardware must be restored to original baseline (75% and ALL)
        assertEquals(75, simulatedProvider.simulatedBrightness.value)
        assertEquals(DndState.ALL, simulatedProvider.simulatedDnd.value)

        // Baseline snapshot must be cleared from storage and memory
        assertFalse("Baseline snapshot should be cleared after restoration", baselineManager.hasBaseline())
        assertNull(baselineManager.baselineSnapshot.value)
        assertEquals(TransitionState.IDLE, baselineManager.transitionState.value)
    }

    @Test
    fun `test toggle room deactivates active room and activates inactive room`() {
        val livingRoom = repository.rooms.value.find { it.name == "Living Room" }!!

        // Toggle on
        contextEngine.toggleRoom(livingRoom.id)
        assertEquals(livingRoom.id, repository.activeRoomId.value)
        assertTrue(baselineManager.hasBaseline())

        // Toggle off
        contextEngine.toggleRoom(livingRoom.id)
        assertNull(repository.activeRoomId.value)
        assertFalse(baselineManager.hasBaseline())
        assertEquals(75, simulatedProvider.simulatedBrightness.value)
    }

    @Test
    fun `test single active room enforcement across multiple transitions`() {
        val rooms = repository.rooms.value
        val r1 = rooms[0]
        val r2 = rooms[1]
        val r3 = rooms[2]

        contextEngine.activateRoom(r1.id)
        assertEquals(r1.id, repository.activeRoomId.value)

        contextEngine.activateRoom(r2.id)
        assertEquals(r2.id, repository.activeRoomId.value)

        contextEngine.activateRoom(r3.id)
        assertEquals(r3.id, repository.activeRoomId.value)

        // At all times, exactly one room is active
        val allActiveRooms = repository.rooms.value.filter { it.id == repository.activeRoomId.value }
        assertEquals(1, allActiveRooms.size)
        assertEquals(r3.id, allActiveRooms[0].id)
    }

    @Test
    fun `test baseline persistence across manager reinstantiation`() {
        val bedroom = repository.rooms.value.find { it.name == "Bedroom" }!!
        contextEngine.activateRoom(bedroom.id)

        assertTrue(baselineManager.hasBaseline())
        val savedId = baselineManager.baselineSnapshot.value!!.id

        // Re-create BaselineStateManager instance reading from the same SharedPreferences
        val newManager = BaselineStateManager(context)
        assertTrue("New manager instance must load persisted baseline", newManager.hasBaseline())
        assertEquals(savedId, newManager.baselineSnapshot.value!!.id)
        assertEquals(TransitionState.ACTIVE, newManager.transitionState.value)
    }

    @Test
    fun `test recovery from interrupted restoration`() {
        // Prepare state simulating crash during RESTORING_BASELINE
        val snapshot = SystemSnapshot.createSimulated(
            brightness = 60,
            brightnessMode = BrightnessMode.MANUAL,
            dnd = DndState.ALL
        )
        context.getSharedPreferences("contextpulse_baseline_state_v1", Context.MODE_PRIVATE)
            .edit()
            .putString("key_snapshot_json", snapshot.toJsonObject().toString())
            .putString("key_transition_state", TransitionState.RESTORING_BASELINE.name)
            .commit()

        val recoveryManager = BaselineStateManager(context)
        assertEquals(TransitionState.RESTORING_BASELINE, recoveryManager.transitionState.value)

        // Run recovery
        val report = recoveryManager.checkAndReconcileRecovery(simulatedProvider)
        assertTrue("Should require recovery", report.requiredRecovery)
        assertEquals(TransitionState.RESTORING_BASELINE, report.previousState)

        // Provider should now have restored snapshot settings (60%)
        assertEquals(60, simulatedProvider.simulatedBrightness.value)
        // Baseline snapshot should now be cleared and state IDLE
        assertFalse(recoveryManager.hasBaseline())
        assertEquals(TransitionState.IDLE, recoveryManager.transitionState.value)
    }

    @Test
    fun `test recovery from interrupted baseline capture`() {
        // Prepare state simulating crash during CAPTURING_BASELINE
        context.getSharedPreferences("contextpulse_baseline_state_v1", Context.MODE_PRIVATE)
            .edit()
            .putString("key_transition_state", TransitionState.CAPTURING_BASELINE.name)
            .commit()

        val recoveryManager = BaselineStateManager(context)
        val report = recoveryManager.checkAndReconcileRecovery(simulatedProvider)

        assertTrue(report.requiredRecovery)
        assertEquals(TransitionState.IDLE, recoveryManager.transitionState.value)
        assertFalse(recoveryManager.hasBaseline())
    }

    @Test
    fun `test profile independence when changing profile of non-active room`() {
        val rooms = repository.rooms.value
        val bedroom = rooms.find { it.name == "Bedroom" }!!
        val mosque = rooms.find { it.name == "Mosque" }!!

        // Activate Bedroom
        contextEngine.activateRoom(bedroom.id)
        assertEquals(bedroom.id, repository.activeRoomId.value)
        val originalMosqueProfile = mosque.profile

        // Edit Mosque profile while Bedroom is active
        val newMosqueProfile = originalMosqueProfile.copy(brightnessLevel = 10, dndMode = "Total Silence")
        repository.updateRoomProfile(mosque.id, newMosqueProfile)

        // Verify Bedroom active profile and simulated state are completely unaffected
        val activeBedroom = repository.activeRoom.value!!
        assertEquals(25, activeBedroom.profile.brightnessLevel)
        assertEquals(25, simulatedProvider.simulatedBrightness.value)

        // Verify Mosque was updated independently
        val updatedMosque = repository.rooms.value.find { it.id == mosque.id }!!
        assertEquals(10, updatedMosque.profile.brightnessLevel)
    }
}
