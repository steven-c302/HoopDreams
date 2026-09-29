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
    // Not setup(): making the window visible starts the studio's infinite animations, and the paused
    // looper never goes idle. Create, start and resume still run onCreate, where the launch crash was.
    @Test fun activityLaunchesWithoutCrashing() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }
}
