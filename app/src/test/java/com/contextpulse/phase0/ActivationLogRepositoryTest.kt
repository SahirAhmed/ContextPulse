package com.contextpulse.phase0

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.contextpulse.phase0.model.ProfileActivationLog
import com.contextpulse.phase0.model.TriggerSource
import com.contextpulse.phase0.repository.ActivationLogRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActivationLogRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: ActivationLogRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // Clear preferences before each test
        context.getSharedPreferences("contextpulse_activation_logs_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        repository = ActivationLogRepository(context)
    }

    @Test
    fun `test default logs are initialized with transition origin and reasoning`() {
        val logs = repository.activationLogs.value
        assertTrue("Default logs should be initialized", logs.isNotEmpty())

        val firstLog = logs.first()
        assertEquals("Living Room", firstLog.toProfileName)
        assertEquals("Home Office", firstLog.fromProfileName)
        assertTrue("Contains signal reasoning", firstLog.reason.contains("signal"))
        assertEquals(TriggerSource.BEACON_SIGNAL, firstLog.triggerSource)
    }

    @Test
    fun `test logging manual switch logs user reasoning and origin`() {
        val log = repository.logActivation(
            toProfileName = "Home Office",
            fromProfileName = "Living Room",
            reason = "The profile was manually changed by user in Profiles screen",
            triggerSource = TriggerSource.MANUAL
        )

        val updatedLogs = repository.activationLogs.value
        assertEquals("Home Office", updatedLogs.first().toProfileName)
        assertEquals("Living Room", updatedLogs.first().fromProfileName)
        assertEquals("The profile was manually changed by user in Profiles screen", updatedLogs.first().reason)
        assertEquals(TriggerSource.MANUAL, updatedLogs.first().triggerSource)
        assertNotNull(log.getFormattedTime())
    }

    @Test
    fun `test logging beacon signal strength switch`() {
        repository.logActivation(
            toProfileName = "Kitchen",
            fromProfileName = "Living Room",
            reason = "The signal of Kitchen Beacon became stronger (-48 dBm vs -72 dBm)",
            triggerSource = TriggerSource.BEACON_SIGNAL
        )

        val latest = repository.activationLogs.value.first()
        assertEquals("Kitchen", latest.toProfileName)
        assertEquals("Living Room", latest.fromProfileName)
        assertTrue(latest.reason.contains("became stronger"))
        assertEquals(TriggerSource.BEACON_SIGNAL, latest.triggerSource)
    }
}
