package com.plugin.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * AlarmManager からのブロードキャストを受信し、通知を表示する。
 * ファイル名は旧スキャフォールドのまま (Example.kt) だが、クラス名は AlarmReceiver。
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        /**
         * 現在再生中の MediaPlayer を保持する。
         * SnoozeReceiver からスヌーズ押下時に [stopCurrentSound] を呼び、音尌の再生を止める。
         */
        @Volatile
        private var currentMediaPlayer: android.media.MediaPlayer? = null

        /**
         * 現在再生中のアラーム音を停止し、リソースを解放する。
         * スヌーズボタンが押された際に SnoozeReceiver から呼び出す。
         */
        fun stopCurrentSound() {
            currentMediaPlayer?.let { mp ->
                runCatching {
                    if (mp.isPlaying) mp.stop()
                    mp.release()
                }
                currentMediaPlayer = null
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarmId", 0)
        val title = intent.getStringExtra("title") ?: "Alarm"
        val message = intent.getStringExtra("message") ?: ""
        val soundUri = intent.getStringExtra("soundUri")
        val alarmTypeName = intent.getStringExtra("alarmType") ?: "RTC_WAKEUP"
        val exact = intent.getBooleanExtra("exact", true)
        val allowWhileIdle = intent.getBooleanExtra("allowWhileIdle", true)
        val snoozeEnabled = intent.getBooleanExtra("snoozeEnabled", false)
        val snoozeDurationMs = intent.getLongExtra("snoozeDurationMs", AlarmPlugin.DEFAULT_SNOOZE_DURATION_MS)
        val snoozeLabel = intent.getStringExtra("snoozeLabel") ?: AlarmPlugin.DEFAULT_SNOOZE_LABEL
        val repeatDaysOfWeek = intent.getIntArrayExtra("repeatDaysOfWeek")
        val originalTriggerAtMs = intent.getLongExtra("originalTriggerAtMs", -1L)

        // Android 8+ では通知チャンネルが必要
        // 音声は MediaPlayer で管理するため、チャンネルの通知音はサイレントにして二重鳴動を防ぐ
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AlarmPlugin.CHANNEL_ID,
                "Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled alarm notifications"
                enableVibration(true)
                setSound(null, null)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        // 音声再生
        if (soundUri != null) {
            playAssetSound(context, soundUri)
        } else {
            playDefaultAlarmSound(context)
        }

        // タップ時にアプリを起動する PendingIntent
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPendingIntent = launchIntent?.let {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            PendingIntent.getActivity(context, alarmId, it, flags)
        }

        val builder = NotificationCompat.Builder(context, AlarmPlugin.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { if (contentPendingIntent != null) setContentIntent(contentPendingIntent) }

        // スヌーズボタン追加
        if (snoozeEnabled) {
            val snoozeIntent = Intent(context, SnoozeReceiver::class.java).apply {
                putExtra("alarmId", alarmId)
                putExtra("title", title)
                putExtra("message", message)
                putExtra("alarmType", alarmTypeName)
                putExtra("snoozeDurationMs", snoozeDurationMs)
                putExtra("snoozeEnabled", true)
                putExtra("snoozeLabel", snoozeLabel)
                if (soundUri != null) putExtra("soundUri", soundUri)
            }
            // SnoozeReceiver は AlarmReceiver とは別コンポーネントのため、同じ requestCode でも衝突しない
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context, alarmId, snoozeIntent,
                buildPendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
            )
            builder.addAction(android.R.drawable.ic_menu_revert, snoozeLabel, snoozePendingIntent)
        }

        val notification = builder.build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(alarmId, notification)

        // 曜日繰り返しの場合、次回の発火時刻を計算して再登録する（空配列クラッシュ防止）
        if (repeatDaysOfWeek != null && repeatDaysOfWeek.isNotEmpty() && originalTriggerAtMs >= 0) {
            rescheduleForNextDay(
                context = context,
                alarmId = alarmId,
                title = title,
                message = message,
                soundUri = soundUri,
                repeatDaysOfWeek = repeatDaysOfWeek.toList(),
                originalTriggerAtMs = originalTriggerAtMs,
                alarmTypeName = alarmTypeName,
                exact = exact,
                allowWhileIdle = allowWhileIdle,
            )
        }
    }

    private fun rescheduleForNextDay(
        context: Context,
        alarmId: Int,
        title: String,
        message: String,
        soundUri: String?,
        repeatDaysOfWeek: List<Int>,
        originalTriggerAtMs: Long,
        alarmTypeName: String,
        exact: Boolean,
        allowWhileIdle: Boolean,
    ) {
        val nextTrigger = nextTriggerForDaysOfWeek(
            triggerAtMs = originalTriggerAtMs,
            days = repeatDaysOfWeek,
            fromMs = System.currentTimeMillis(),
        )

        val nextIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarmId", alarmId)
            putExtra("title", title)
            putExtra("message", message)
            if (soundUri != null) putExtra("soundUri", soundUri)
            putExtra("alarmType", alarmTypeName)
            putExtra("exact", exact)
            putExtra("allowWhileIdle", allowWhileIdle)
            putExtra("repeatDaysOfWeek", repeatDaysOfWeek.toIntArray())
            putExtra("originalTriggerAtMs", originalTriggerAtMs)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, alarmId, nextIntent,
            buildPendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )

        val alarmType = parseAlarmType(alarmTypeName)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        when {
            exact -> scheduleExactAlarm(alarmManager, alarmType, nextTrigger, pendingIntent, allowWhileIdle)
            else -> {
                if (allowWhileIdle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(alarmType, nextTrigger, pendingIntent)
                } else {
                    alarmManager.set(alarmType, nextTrigger, pendingIntent)
                }
            }
        }

        // SharedPreferences の triggerAtMs を次回時刻に同期的更新
        updateAlarmTriggerTime(context, alarmId, nextTrigger)
    }

    /**
     * MediaPlayer のセットアップと再生を行う共通ヘルパー。
     * - AudioAttributes / リスナーの設定を一元管理
     * - 初期化失敗（catch）・再生中エラー（onErrorListener）いずれも onErrorFallback を呼び出す
     * - リスナーは prepare()/start() より前に設定し、短音声でも release() を取りこぼさない
     *
     * @param dataSourceProvider MediaPlayer にデータソースを設定するラムダ
     * @param onErrorFallback エラー発生時のフォールバック処理（省略可）
     */
    private fun playSound(
        dataSourceProvider: (android.media.MediaPlayer) -> Unit,
        onErrorFallback: (() -> Unit)? = null,
    ) {
        val mediaPlayer = android.media.MediaPlayer()
        currentMediaPlayer = mediaPlayer
        try {
            mediaPlayer.apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setOnCompletionListener {
                    it.release()
                    if (currentMediaPlayer === it) currentMediaPlayer = null
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (currentMediaPlayer === mp) currentMediaPlayer = null
                    onErrorFallback?.invoke()
                    true
                }
                dataSourceProvider(this)
                prepare()
                start()
            }
        } catch (e: Exception) {
            mediaPlayer.release()
            if (currentMediaPlayer === mediaPlayer) currentMediaPlayer = null
            onErrorFallback?.invoke()
        }
    }

    /**
     * assets フォルダ内の音声ファイルを再生する。
     * 初期化失敗・再生中デコードエラーともにデフォルト音にフォールバック。
     */
    private fun playAssetSound(context: Context, soundUri: String) {
        playSound(
            dataSourceProvider = { mp ->
                context.assets.openFd(soundUri).use { afd ->
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
            },
            onErrorFallback = { playDefaultAlarmSound(context) },
        )
    }

    /**
     * システムのデフォルトアラーム音を再生する。
     * getActualDefaultRingtoneUri() で端末に実際に設定されている URI を取得する。
     * アラーム音が未設定（null）の場合は無音で戻る。
     */
    private fun playDefaultAlarmSound(context: Context) {
        val uri: Uri = RingtoneManager.getActualDefaultRingtoneUri(
            context, RingtoneManager.TYPE_ALARM
        ) ?: return
        playSound(dataSourceProvider = { mp -> mp.setDataSource(context, uri) })
    }
}
