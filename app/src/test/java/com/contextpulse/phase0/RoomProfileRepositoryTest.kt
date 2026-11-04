package com.contextpulse.phase0

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.contextpulse.phase0.model.RoomItem
import com.contextpulse.phase0.model.RoomProfile
import com.contextpulse.phase0.repository.RoomProfileRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomProfileRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: RoomProfileRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // Clear preferences before each test
        context.getSharedPreferences("contextpulse_room_profiles_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        repository = RoomProfileRepository(context)
    }

    @Test
    fun `test initial default rooms are created with independent profiles`() {
        val rooms = repository.rooms.value
        assertTrue("Should contain initial rooms", rooms.isNotEmpty())
        val names = rooms.map { it.name }
        assertTrue("Contains Bedroom", names.contains("Bedroom"))
        assertTrue("Contains Living Room", names.contains("Living Room"))
        assertTrue("Contains Office", names.contains("Office"))
        assertTrue("Contains Mosque", names.contains("Mosque"))

        // Verify independent profiles
        val bedroom = rooms.find { it.name == "Bedroom" }!!
        val mosque = rooms.find { it.name == "Mosque" }!!

        assertNotEquals(bedroom.id, mosque.id)
        // Bedroom defaults to 25% brightness, Mosque defaults to 40%
        assertNotEquals(bedroom.profile.brightnessLevel, mosque.profile.brightnessLevel)
    }

    @Test
    fun `test creating room with independent profile`() {
        val studyProfile = RoomProfile(
            dndEnabled = true,
            dndMode = "Priority Only",
            brightnessEnabled = true,
            brightnessLevel = 75,
            appRestrictionsEnabled = true,
            restrictedApps = listOf("Instagram", "Games"),
            notificationBehaviorEnabled = true,
            notificationMode = "Mute All"
        )

        val newRoom = repository.createRoom(name = "Study Hall", profile = studyProfile)

        val retrieved = repository.rooms.value.find { it.id == newRoom.id }
        assertNotNull("New room should exist", retrieved)
        assertEquals("Study Hall", retrieved!!.name)
        assertEquals(75, retrieved.profile.brightnessLevel)
        assertTrue(retrieved.profile.dndEnabled)
        assertEquals(listOf("Instagram", "Games"), retrieved.profile.restrictedApps)
    }

    @Test
    fun `test editing one room profile does not affect other rooms`() {
        val rooms = repository.rooms.value
        val roomA = rooms[0]
        val roomB = rooms[1]

        val initialProfileB = roomB.profile

        // Modify roomA's profile
        val updatedProfileA = roomA.profile.copy(
            brightnessEnabled = true,
            brightnessLevel = 95,
            dndEnabled = true,
            dndMode = "Total Silence",
            restrictedApps = listOf("TikTok")
        )
        repository.updateRoomProfile(roomA.id, updatedProfileA)

        // Verify roomA was updated
        val retrievedA = repository.rooms.value.find { it.id == roomA.id }!!
        assertEquals(95, retrievedA.profile.brightnessLevel)
        assertEquals("Total Silence", retrievedA.profile.dndMode)

        // Verify roomB was completely untouched
        val retrievedB = repository.rooms.value.find { it.id == roomB.id }!!
        assertEquals(initialProfileB.brightnessLevel, retrievedB.profile.brightnessLevel)
        assertEquals(initialProfileB.dndMode, retrievedB.profile.dndMode)
        assertEquals(initialProfileB.dndEnabled, retrievedB.profile.dndEnabled)
    }

    @Test
    fun `test manual room activation and switching updates active profile`() {
        val rooms = repository.rooms.value
        val bedroom = rooms.find { it.name == "Bedroom" }!!
        val office = rooms.find { it.name == "Office" }!!

        assertNull("Initially no room is active", repository.activeRoomId.value)
        assertNull(repository.activeRoom.value)

        // Activate Bedroom
        repository.setActiveRoom(bedroom.id)
        assertEquals(bedroom.id, repository.activeRoomId.value)
        assertEquals("Bedroom", repository.activeRoom.value?.name)
        assertEquals(bedroom.profile.brightnessLevel, repository.activeRoom.value?.profile?.brightnessLevel)

        // Switch to Office
        repository.setActiveRoom(office.id)
        assertEquals(office.id, repository.activeRoomId.value)
        assertEquals("Office", repository.activeRoom.value?.name)
        assertEquals(office.profile.brightnessLevel, repository.activeRoom.value?.profile?.brightnessLevel)

        // Deactivate
        repository.setActiveRoom(null)
        assertNull(repository.activeRoomId.value)
        assertNull(repository.activeRoom.value)
    }

    @Test
    fun `test room and profile persistence survives application restart`() {
        // Create a custom room in current instance
        val customProfile = RoomProfile(
            dndEnabled = true,
            dndMode = "Alarms Only",
            brightnessEnabled = true,
            brightnessLevel = 15,
            appRestrictionsEnabled = true,
            restrictedApps = listOf("YouTube", "Twitch"),
            autoRestoreOnExit = true,
            customNotes = "Quiet zone"
        )
        val created = repository.createRoom(name = "Meditation Corner", profile = customProfile)

        // Set as active room
        repository.setActiveRoom(created.id)
        assertEquals(created.id, repository.activeRoomId.value)

        // Simulate app restart by instantiating a new repository instance from the same context
        val newRepositoryInstance = RoomProfileRepository(context)

        // Verify rooms survived restart
        val restoredRooms = newRepositoryInstance.rooms.value
        val restoredRoom = restoredRooms.find { it.id == created.id }
        assertNotNull("Saved room must survive restart", restoredRoom)
        assertEquals("Meditation Corner", restoredRoom!!.name)
        assertEquals(15, restoredRoom.profile.brightnessLevel)
        assertEquals("Alarms Only", restoredRoom.profile.dndMode)
        assertEquals(listOf("YouTube", "Twitch"), restoredRoom.profile.restrictedApps)
        assertEquals("Quiet zone", restoredRoom.profile.customNotes)

        // Verify active room selection survived restart
        assertEquals(created.id, newRepositoryInstance.activeRoomId.value)
        assertEquals("Meditation Corner", newRepositoryInstance.activeRoom.value?.name)
    }

    @Test
    fun `test editing room name and deleting room`() {
        val room = repository.createRoom("Temporary Room")
        repository.updateRoomName(room.id, "Renamed Room")

        val renamed = repository.rooms.value.find { it.id == room.id }
        assertNotNull(renamed)
        assertEquals("Renamed Room", renamed!!.name)

        // Delete room
        repository.deleteRoom(room.id)
        val deleted = repository.rooms.value.find { it.id == room.id }
        assertNull("Room should be removed", deleted)
    }

    @Test
    fun `test beacon trigger configurations can be added, updated, and persisted`() {
        val room = repository.createRoom("Meeting Space")

        // 1. Add a MAC address beacon
        val macTrigger = com.contextpulse.phase0.model.BeaconTriggerConfig(
            type = com.contextpulse.phase0.model.BeaconIdentifierType.MAC_ADDRESS,
            macAddress = "AA:BB:CC:DD:EE:FF",
            label = "Desk BLE Beacon"
        )
        repository.addTriggerToRoom(room.id, macTrigger)

        // 2. Add an iBeacon trigger
        val iBeaconTrigger = com.contextpulse.phase0.model.BeaconTriggerConfig(
            type = com.contextpulse.phase0.model.BeaconIdentifierType.IBEACON,
            uuid = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0",
            major = 10,
            minor = 20,
            label = "Ceiling iBeacon"
        )
        repository.addTriggerToRoom(room.id, iBeaconTrigger)

        // Verify triggers in current repository
        val updatedRoom = repository.rooms.value.find { it.id == room.id }!!
        assertEquals(2, updatedRoom.triggerConfigs.size)
        assertEquals("AA:BB:CC:DD:EE:FF", updatedRoom.triggerConfigs[0].macAddress)
        assertEquals(com.contextpulse.phase0.model.BeaconIdentifierType.MAC_ADDRESS, updatedRoom.triggerConfigs[0].type)
        assertEquals("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0", updatedRoom.triggerConfigs[1].uuid)
        assertEquals(10, updatedRoom.triggerConfigs[1].major)
        assertEquals(20, updatedRoom.triggerConfigs[1].minor)

        // Simulate app restart
        val newRepo = RoomProfileRepository(context)
        val reloadedRoom = newRepo.rooms.value.find { it.id == room.id }!!
        assertEquals(2, reloadedRoom.triggerConfigs.size)
        assertEquals("Desk BLE Beacon", reloadedRoom.triggerConfigs[0].label)
        assertEquals("Ceiling iBeacon", reloadedRoom.triggerConfigs[1].label)

        // Remove one trigger
        newRepo.removeTriggerFromRoom(room.id, macTrigger.id)
        val afterRemoval = newRepo.rooms.value.find { it.id == room.id }!!
        assertEquals(1, afterRemoval.triggerConfigs.size)
        assertEquals("Ceiling iBeacon", afterRemoval.triggerConfigs[0].label)
    }
}

