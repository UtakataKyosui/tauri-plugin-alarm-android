package com.plugin.alerm

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

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarmId", 0)
        val title = intent.getStringExtra("title") ?: "Alarm"
        val message = intent.getStringExtra("message") ?: ""
        val soundUri = intent.getStringExtra("soundUri")

        // Android 8+ では通知チャンネルが必要
        // 音声は MediaPlayer で管理するため、チャンネルの通知音はサイレントにして二重鳴動を防ぐ
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AlermPlugin.CHANNEL_ID,
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

        val notification = NotificationCompat.Builder(context, AlermPlugin.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { if (contentPendingIntent != null) setContentIntent(contentPendingIntent) }
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(alarmId, notification)
    }

    /**
     * assets フォルダ内の音声ファイルを MediaPlayer で再生する。失敗時はデフォルト音にフォールバック。
     * - MediaPlayer を try 外で初期化し、catch で必ず release() する
     * - AssetFileDescriptor は use {} で確実に close する
     * - リスナーは prepare()/start() より前に設定し、短音声でも release() を取りこぼさない
     */
    private fun playAssetSound(context: Context, soundUri: String) {
        val mediaPlayer = MediaPlayer()
        try {
            context.assets.openFd(soundUri).use { afd ->
                mediaPlayer.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setOnCompletionListener { it.release() }
                    setOnErrorListener { mp, _, _ ->
                        mp.release()
                        true
                    }
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    prepare()
                    start()
                }
            }
        } catch (e: Exception) {
            mediaPlayer.release()
            // assets が見つからない場合はデフォルト音にフォールバック
            playDefaultAlarmSound(context)
        }
    }

    /**
     * システムのデフォルトアラーム音を MediaPlayer で再生する。
     * Ringtone は stop() を呼ばないと無限再生になるため、ワンショット再生できる MediaPlayer を使う。
     * uri が null（デフォルト音未設定）の場合は無音で戻る。
     */
    private fun playDefaultAlarmSound(context: Context) {
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: return

        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setOnCompletionListener { it.release() }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    true
                }
                setDataSource(context, uri)
                prepare()
                start()
            }
        } catch (e: Exception) {
            mediaPlayer.release()
        }
    }
}
