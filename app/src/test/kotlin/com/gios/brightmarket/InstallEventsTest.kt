package com.gios.brightmarket

import android.content.pm.PackageInstaller
import com.gios.brightmarket.install.InstallEvents
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue "update all" runs on. Each case here is a way the batch used to
 * either race itself or hang.
 */
class InstallEventsTest {

    @Test
    fun `a result published after the wait is registered completes it`() = runBlocking {
        val waiter = InstallEvents.expect("com.example.one")
        InstallEvents.publish("com.example.one", PackageInstaller.STATUS_SUCCESS, "Installed")
        assertTrue(waiter.await().success)
    }

    @Test
    fun `pending user action is not an answer`() {
        val waiter = InstallEvents.expect("com.example.two")
        InstallEvents.publish(
            "com.example.two",
            PackageInstaller.STATUS_PENDING_USER_ACTION,
            "confirm",
        )
        // The dialog request arrives first, every time, on the only install path
        // this app has. Completing on it would release the queue before the
        // person has even seen the dialog -- which is the original bug.
        assertFalse(waiter.isCompleted)
        InstallEvents.publish("com.example.two", PackageInstaller.STATUS_SUCCESS, "Installed")
        assertTrue(waiter.isCompleted)
    }

    @Test
    fun `a result for another package leaves this one waiting`() {
        val waiter = InstallEvents.expect("com.example.three")
        InstallEvents.publish("com.example.other", PackageInstaller.STATUS_SUCCESS, "Installed")
        assertFalse(waiter.isCompleted)
    }

    @Test
    fun `cancel releases a wait whose session never committed`() = runBlocking {
        val waiter = InstallEvents.expect("com.example.four")
        InstallEvents.cancel("com.example.four")
        assertFalse(waiter.await().success)
    }

    @Test
    fun `a second wait for the same package does not orphan the first`() = runBlocking {
        val first = InstallEvents.expect("com.example.five")
        val second = InstallEvents.expect("com.example.five")
        // Left uncompleted, the earlier one would hold its coroutine until the
        // timeout -- five minutes of a batch doing nothing.
        assertTrue(first.isCompleted)
        assertFalse(first.await().success)
        InstallEvents.publish("com.example.five", PackageInstaller.STATUS_SUCCESS, "Installed")
        assertTrue(second.await().success)
    }

    @Test
    fun `a published result is delivered once, not to the next install`() = runBlocking {
        val first = InstallEvents.expect("com.example.six")
        InstallEvents.publish("com.example.six", PackageInstaller.STATUS_SUCCESS, "Installed")
        assertEquals(PackageInstaller.STATUS_SUCCESS, first.await().status)
        val second = InstallEvents.expect("com.example.six")
        assertFalse(second.isCompleted)
    }
}
