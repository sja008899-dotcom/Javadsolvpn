package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreloadedConfigs
import com.example.vpn.diagnostic.DiagnosticStepState
import com.example.vpn.diagnostic.SafeModeDiagnosticManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SafeModeDiagnosticTest {

    @Test
    fun safeModeDiagnostic_executesStepsAndLogsOutput() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testServer = PreloadedConfigs.DEFAULT_SERVERS.first()

        val report = SafeModeDiagnosticManager.runDiagnosticRoutine(
            context = context,
            vpnServiceInstance = null,
            targetServerForProbe = testServer
        )

        assertNotNull(report)
        // 6 distinct milestones
        assertEquals(6, report.steps.size)

        // Step 0: Environment & Binary Verification
        val envStep = report.steps[0]
        assertEquals("محیط اجرایی و داده‌های هسته", envStep.title)
        assertEquals(DiagnosticStepState.PASSED, envStep.state)

        // Step 2: JSON synthesis
        val jsonStep = report.steps[2]
        assertEquals("سنتز پیکربندی عیب‌یابی امن", jsonStep.title)
        assertEquals(DiagnosticStepState.PASSED, jsonStep.state)

        // Logs must be populated with detailed timestamps and tags
        assertTrue("Diagnostic logs must not be empty", report.logs.isNotEmpty())
        assertTrue(report.logs.any { it.tag == "DIAG" })
        assertTrue(report.logs.any { it.tag == "CORE" })
        assertTrue(report.logs.any { it.tag == "JSON" })
    }
}
