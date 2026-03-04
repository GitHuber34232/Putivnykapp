package ua.kyiv.putivnyk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchInstrumentedTest {

    @Test
    fun appContext_packageName_isExpected() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(appContext.packageName.contains("putivnyk"))
    }
}
