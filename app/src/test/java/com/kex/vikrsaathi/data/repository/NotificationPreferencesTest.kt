package com.kex.vikrsaathi.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import com.kex.vikrsaathi.domain.notify.NotifyKind
import com.kex.vikrsaathi.domain.notify.WallTime
import com.kex.vikrsaathi.notify.NotificationScheduler
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NotificationPreferencesTest {

    @Before
    fun clear() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences(NotificationPreferences.PREFS, 0).edit().clear().commit()
    }

    @Test
    fun restoreKeepsCustomTimesButLeavesNotificationsOff() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = NotificationPreferences(context)
        prefs.enabled = true
        prefs.permissionPrompted = true
        prefs.defaultTime = WallTime(8, 15)
        prefs.setEnabled(NotifyKind.DAILY_SALES, true)
        prefs.setCustomTime(NotifyKind.DAILY_SALES, WallTime(19, 0))
        val json = prefs.toJson()
        prefs.reset()
        prefs.enabled = true
        prefs.applyJson(JSONObject(json.toString()))
        assertFalse(prefs.enabled)
        assertFalse(prefs.permissionPrompted)
        assertEquals(WallTime(8, 15), prefs.defaultTime)
        assertTrue(prefs.schedule(NotifyKind.DAILY_SALES).enabled)
        assertEquals(WallTime(19, 0), prefs.schedule(NotifyKind.DAILY_SALES).customTime)
        assertNull(prefs.schedule(NotifyKind.WEEKLY_SALES).customTime)
    }

    @Test
    fun backupJsonDoesNotContainTheMasterSwitch() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = NotificationPreferences(context)
        prefs.enabled = true
        assertFalse(prefs.toJson().has("enabled"))
    }

    @Test
    fun schedulerUsesOneOfflineWorkNameAndCancelsWhenOff() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        WorkManager.initialize(
            context,
            Configuration.Builder()
                .setExecutor(Executors.newSingleThreadExecutor())
                .build()
        )
        val prefs = NotificationPreferences(context)
        prefs.enabled = true
        NotificationScheduler.reconcile(context)
        val manager = WorkManager.getInstance(context)
        val scheduled = manager.getWorkInfosForUniqueWork(NotificationScheduler.WORK_NAME).get()
        assertEquals(1, scheduled.size)
        assertEquals(NetworkType.NOT_REQUIRED, scheduled.single().constraints.requiredNetworkType)
        prefs.enabled = false
        NotificationScheduler.reconcile(context)
        val after = manager.getWorkInfosForUniqueWork(NotificationScheduler.WORK_NAME).get()
        assertTrue(after.all { it.state.isFinished })
    }
}
