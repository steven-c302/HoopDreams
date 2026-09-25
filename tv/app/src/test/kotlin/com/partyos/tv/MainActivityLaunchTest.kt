package com.partyos.tv

import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityLaunchTest {
    @Test fun activityLaunchesWithoutCrashing() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertFalse(activity.isFinishing)
    }
}
