package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regression: Activity.display is unavailable on the app's minimum Android API. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], application = Application::class)
class RefreshRateCompatibilityTest {
  @Test
  fun `minimum supported Android starts and resumes with a refresh preference`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // Exercise Activity startup without needing a signed-in Firebase account.
    StartupCrashStore.record(context, "compatibility_fixture", IllegalStateException("test recovery"))
    val controller = Robolectric.buildActivity(MainActivity::class.java)
    try {
      controller.create().start().resume()
      val preference = controller.get().window.attributes.preferredRefreshRate
      assertTrue("A valid display refresh preference is recorded", preference.isFinite() && preference > 0f)
      controller.pause().resume()
    } finally {
      controller.pause().stop().destroy()
      StartupCrashStore.clearCrash(context)
    }
  }
}
