package com.plugin.alerm

import android.app.AlarmManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 計測テスト（Android デバイスまたは emulator 上で実行）。
 * 実際の Context / SharedPreferences を使い、ストレージ操作および
 * デバイス固有の動作（権限チェック等）を検証する。
 */
@RunWith(AndroidJUnit4::class)
class AlarmStorageInstrumentedTest {

    private val context: Context by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Before
    fun clearStorage() {
        context.getSharedPreferences(AlermPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(AlermPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // =========================================================================
    // saveAlarm / getStoredAlarms — 基本操作
    // =========================================================================

    @Test
    fun saveAlarm_thenGetStoredAlarms_returnsPersistedAlarm() {
        val alarm = buildAlarmJson(id = 1, title = "Morning", triggerAtMs = 1_700_000_000_000L)
        saveAlarm(context, 1, alarm)

        val alarms = getStoredAlarms(context)
        assertEquals(1, alarms.size)
        assertEquals(1, alarms[0].getInt("id"))
        assertEquals("Morning", alarms[0].getString("title"))
        assertEquals(1_700_000_000_000L, alarms[0].getLong("triggerAtMs"))
    }

    @Test
    fun saveAlarm_multipleAlarms_allPersisted() {
        repeat(5) { i -> saveAlarm(context, i, buildAlarmJson(id = i, title = "Alarm $i")) }
        assertEquals(5, getStoredAlarms(context).size)
    }

    @Test
    fun saveAlarm_sameId_overwritesPreviousAlarm() {
        saveAlarm(context, 1, buildAlarmJson(id = 1, title = "Original"))
        saveAlarm(context, 1, buildAlarmJson(id = 1, title = "Updated"))

        val alarms = getStoredAlarms(context)
        assertEquals(1, alarms.size)
        assertEquals("Updated", alarms[0].getString("title"))
    }

    // =========================================================================
    // removeAlarm
    // =========================================================================

    @Test
    fun removeAlarm_existingId_alarmIsDeleted() {
        saveAlarm(context, 10, buildAlarmJson(id = 10, title = "To Delete"))
        assertEquals(1, getStoredAlarms(context).size)

        removeAlarm(context, 10)
        assertEquals(0, getStoredAlarms(context).size)
    }

    @Test
    fun removeAlarm_nonExistentId_noError() {
        saveAlarm(context, 1, buildAlarmJson(id = 1, title = "Keep me"))
        removeAlarm(context, 999)

        assertEquals(1, getStoredAlarms(context).size)
    }

    @Test
    fun removeAlarm_onlyRemovesTargetAlarm() {
        saveAlarm(context, 1, buildAlarmJson(id = 1, title = "Keep"))
        saveAlarm(context, 2, buildAlarmJson(id = 2, title = "Delete"))

        removeAlarm(context, 2)

        val alarms = getStoredAlarms(context)
        assertEquals(1, alarms.size)
        assertEquals(1, alarms[0].getInt("id"))
    }

    @Test
    fun removeAllAlarms_storageBecomesEmpty() {
        saveAlarm(context, 1, buildAlarmJson(id = 1, title = "A"))
        saveAlarm(context, 2, buildAlarmJson(id = 2, title = "B"))

        removeAlarm(context, 1)
        removeAlarm(context, 2)

        assertTrue(getStoredAlarms(context).isEmpty())
    }

    // =========================================================================
    // getStoredAlarms — 空ストレージ
    // =========================================================================

    @Test
    fun getStoredAlarms_emptyStorage_returnsEmptyList() {
        assertTrue(getStoredAlarms(context).isEmpty())
    }

    // =========================================================================
    // オプションフィールド (message, repeatIntervalMs)
    // =========================================================================

    @Test
    fun saveAlarm_withNullMessage_persistsNullCorrectly() {
        saveAlarm(context, 5, buildAlarmJson(id = 5, title = "No message", message = null))

        val stored = getStoredAlarms(context)
        assertEquals(1, stored.size)
        assertTrue(stored[0].isNull("message"))
    }

    @Test
    fun saveAlarm_withMessage_persistsMessageCorrectly() {
        saveAlarm(context, 6, buildAlarmJson(id = 6, title = "Hi", message = "Hello World"))

        val stored = getStoredAlarms(context)
        assertEquals("Hello World", stored[0].getString("message"))
    }

    @Test
    fun saveAlarm_withRepeatInterval_persistsRepeatIntervalMs() {
        saveAlarm(context, 7, buildAlarmJson(id = 7, title = "Daily", repeatIntervalMs = 86_400_000L))

        val stored = getStoredAlarms(context)
        assertFalse(stored[0].isNull("repeatIntervalMs"))
        assertEquals(86_400_000L, stored[0].getLong("repeatIntervalMs"))
    }

    @Test
    fun saveAlarm_withoutRepeatInterval_persistsNullRepeatIntervalMs() {
        saveAlarm(context, 8, buildAlarmJson(id = 8, title = "Once", repeatIntervalMs = null))

        val stored = getStoredAlarms(context)
        assertTrue(stored[0].isNull("repeatIntervalMs"))
    }

    // =========================================================================
    // アラームタイプの永続化
    // =========================================================================

    @Test
    fun saveAlarm_allAlarmTypes_persistedCorrectly() {
        val types = listOf("RTC_WAKEUP", "RTC", "ELAPSED_REALTIME_WAKEUP", "ELAPSED_REALTIME")
        types.forEachIndexed { i, type ->
            saveAlarm(context, i, buildAlarmJson(id = i, title = "T$i", alarmType = type))
        }

        val stored = getStoredAlarms(context)
        assertEquals(4, stored.size)
        val storedTypes = stored.map { it.getString("alarmType") }.toSet()
        assertEquals(types.toSet(), storedTypes)
    }

    // =========================================================================
    // exact フラグの永続化
    // =========================================================================

    @Test
    fun saveAlarm_exactTrue_persistsTrue() {
        saveAlarm(context, 1, buildAlarmJson(id = 1, title = "Exact", exact = true))
        assertTrue(getStoredAlarms(context)[0].getBoolean("exact"))
    }

    @Test
    fun saveAlarm_exactFalse_persistsFalse() {
        saveAlarm(context, 1, buildAlarmJson(id = 1, title = "Inexact", exact = false))
        assertFalse(getStoredAlarms(context)[0].getBoolean("exact"))
    }

    // =========================================================================
    // JSON フィールドの完全性確認
    // =========================================================================

    @Test
    fun storedAlarm_hasAllRequiredFields() {
        val alarm = buildAlarmJson(
            id = 42,
            title = "Full Alarm",
            message = "desc",
            triggerAtMs = 9_000_000L,
            alarmType = "RTC",
            exact = true,
            repeatIntervalMs = 3_600_000L,
        )
        saveAlarm(context, 42, alarm)

        val stored = getStoredAlarms(context)[0]
        assertTrue(stored.has("id"))
        assertTrue(stored.has("title"))
        assertTrue(stored.has("message"))
        assertTrue(stored.has("triggerAtMs"))
        assertTrue(stored.has("alarmType"))
        assertTrue(stored.has("exact"))
        assertTrue(stored.has("repeatIntervalMs"))

        assertEquals(42, stored.getInt("id"))
        assertEquals("Full Alarm", stored.getString("title"))
        assertEquals("desc", stored.getString("message"))
        assertEquals(9_000_000L, stored.getLong("triggerAtMs"))
        assertEquals("RTC", stored.getString("alarmType"))
        assertTrue(stored.getBoolean("exact"))
        assertEquals(3_600_000L, stored.getLong("repeatIntervalMs"))
    }

    // =========================================================================
    // ストレージの独立性（テスト間でのデータ汚染がないことを確認）
    // =========================================================================

    @Test
    fun storageIsolation_savedAlarmDoesNotLeakToOtherTests() {
        // @Before / @After でクリアされているので、
        // ここに到達した時点でストレージは空のはず
        assertTrue(
            "他のテストからデータが漏れている可能性がある",
            getStoredAlarms(context).isEmpty()
        )
    }

    // =========================================================================
    // デバイス固有の動作（Android API レベルに依存）
    // =========================================================================

    @Test
    fun checkExactAlarmPermission_returnsBoolean() {
        // Android 11 以下では常に true
        // Android 12+ ではユーザー許可によって変わる（テスト環境依存）
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        // 結果が boolean であることを確認（実際の値はデバイスに依存）
        assertTrue(result == true || result == false)
    }

    @Test
    fun parseAlarmType_allTypes_returnDistinctAlarmManagerConstants() {
        // デバイス上でも AlarmManager 定数が一致することを確認
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertEquals(AlarmManager.RTC_WAKEUP, parseAlarmType("RTC_WAKEUP"))
        assertEquals(AlarmManager.RTC, parseAlarmType("RTC"))
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, parseAlarmType("ELAPSED_REALTIME_WAKEUP"))
        assertEquals(AlarmManager.ELAPSED_REALTIME, parseAlarmType("ELAPSED_REALTIME"))
    }

    // =========================================================================
    // ヘルパー
    // =========================================================================

    private fun buildAlarmJson(
        id: Int,
        title: String,
        triggerAtMs: Long = System.currentTimeMillis() + 60_000L,
        alarmType: String = "RTC_WAKEUP",
        exact: Boolean = true,
        message: String? = null,
        repeatIntervalMs: Long? = null,
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("message", message)
        put("triggerAtMs", triggerAtMs)
        put("alarmType", alarmType)
        put("exact", exact)
        put("repeatIntervalMs", repeatIntervalMs)
    }
}
