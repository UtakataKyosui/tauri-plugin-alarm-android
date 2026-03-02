use serde::{Deserialize, Serialize};

/// アラームをセットするリクエスト
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SetAlarmRequest {
    /// アラームの一意な識別子（同じ ID でセットすると上書き）
    pub id: i32,
    /// 通知タイトル
    pub title: String,
    /// 通知本文（省略可）
    pub message: Option<String>,
    /// 発火時刻（Unix タイムスタンプ、ミリ秒）
    pub trigger_at_ms: i64,
    /// アラームタイプ: "RTC_WAKEUP"（デフォルト）| "RTC" | "ELAPSED_REALTIME_WAKEUP" | "ELAPSED_REALTIME"
    pub alarm_type: Option<String>,
    /// 正確なアラームを使用する（Android 12+ では SCHEDULE_EXACT_ALARM 権限が必要）。デフォルト: true
    pub exact: Option<bool>,
    /// Doze モード中でも発火させる（setExactAndAllowWhileIdle）。デフォルト: true
    pub allow_while_idle: Option<bool>,
    /// 繰り返し間隔（ミリ秒）。省略時は一度だけ発火
    pub repeat_interval_ms: Option<i64>,
    /// アラーム音声ファイルのパス（アプリ assets 内）。省略時はデフォルトアラーム音
    pub sound_uri: Option<String>,
    /// スヌーズを有効にするか（デフォルト: false）
    pub snooze_enabled: Option<bool>,
    /// スヌーズ時間（ミリ秒）。デフォルト 5分 = 300_000
    pub snooze_duration_ms: Option<u64>,
    /// 通知上のスヌーズボタンのラベル（デフォルト: "スヌーズ"）
    pub snooze_label: Option<String>,
    /// 繰り返す曜日リスト（0=日, 1=月, ..., 6=土）。省略時は繰り返しなし
    pub repeat_days_of_week: Option<Vec<u8>>,
}

/// スケジュール済みアラームの情報
#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AlarmInfo {
    pub id: i32,
    pub title: String,
    pub message: Option<String>,
    pub trigger_at_ms: i64,
    pub alarm_type: String,
    pub exact: bool,
    pub repeat_interval_ms: Option<i64>,
    /// アラーム音声ファイルのパス（アプリ assets 内）。省略時はデフォルトアラーム音
    pub sound_uri: Option<String>,
    /// スヌーズを有効にするか
    pub snooze_enabled: Option<bool>,
    /// スヌーズ時間（ミリ秒）
    pub snooze_duration_ms: Option<u64>,
    /// スヌーズボタンのラベル
    pub snooze_label: Option<String>,
    /// 繰り返す曜日リスト（0=日, 1=月, ..., 6=土）。省略時は繰り返しなし
    pub repeat_days_of_week: Option<Vec<u8>>,
}

/// アラームをキャンセルするリクエスト
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CancelAlarmRequest {
    pub id: i32,
}

/// アラーム一覧レスポンス
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ListAlarmsResponse {
    pub alarms: Vec<AlarmInfo>,
}

/// 正確なアラーム権限チェックのレスポンス
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CheckPermissionResponse {
    pub can_schedule_exact_alarms: bool,
}

#[cfg(test)]
mod tests {
    use super::*;

    // ------------------------------------------------------------------
    // SetAlarmRequest
    // ------------------------------------------------------------------

    #[test]
    fn set_alarm_request_serializes_to_camel_case() {
        let req = SetAlarmRequest {
            id: 1,
            title: "Morning".to_string(),
            message: Some("Wake up!".to_string()),
            trigger_at_ms: 1_700_000_000_000,
            alarm_type: Some("RTC_WAKEUP".to_string()),
            exact: Some(true),
            allow_while_idle: Some(true),
            repeat_interval_ms: None,
            sound_uri: None,
            snooze_enabled: None,
            snooze_duration_ms: None,
            snooze_label: None,
            repeat_days_of_week: None,
        };
        let json = serde_json::to_string(&req).unwrap();

        assert!(
            json.contains("\"triggerAtMs\""),
            "triggerAtMs が含まれない: {json}"
        );
        assert!(
            json.contains("\"alarmType\""),
            "alarmType が含まれない: {json}"
        );
        assert!(
            !json.contains("trigger_at_ms"),
            "snake_case が残っている: {json}"
        );
    }

    #[test]
    fn set_alarm_request_deserializes_from_camel_case() {
        let json = r#"{
            "id": 1,
            "title": "Test",
            "triggerAtMs": 1000000,
            "alarmType": "RTC",
            "exact": false
        }"#;
        let req: SetAlarmRequest = serde_json::from_str(json).unwrap();

        assert_eq!(req.id, 1);
        assert_eq!(req.trigger_at_ms, 1_000_000);
        assert_eq!(req.alarm_type.as_deref(), Some("RTC"));
        assert_eq!(req.exact, Some(false));
        assert!(req.repeat_days_of_week.is_none());
    }

    // ------------------------------------------------------------------
    // AlarmInfo
    // ------------------------------------------------------------------

    #[test]
    fn alarm_info_round_trip() {
        let info = AlarmInfo {
            id: 42,
            title: "Alarm".to_string(),
            message: Some("Ring!".to_string()),
            trigger_at_ms: 1_700_000_000_000,
            alarm_type: "RTC_WAKEUP".to_string(),
            exact: true,
            repeat_interval_ms: None,
            sound_uri: None,
            snooze_enabled: None,
            snooze_duration_ms: None,
            snooze_label: None,
            repeat_days_of_week: None,
        };
        let json = serde_json::to_string(&info).unwrap();

        assert!(
            json.contains("\"triggerAtMs\""),
            "triggerAtMs が含まれない: {json}"
        );

        let restored: AlarmInfo = serde_json::from_str(&json).unwrap();
        assert_eq!(restored.id, 42);
        assert_eq!(restored.trigger_at_ms, 1_700_000_000_000);
    }

    #[test]
    fn alarm_info_with_sound_uri_round_trip() {
        let info = AlarmInfo {
            id: 8,
            title: "Sound Alarm".to_string(),
            message: None,
            trigger_at_ms: 1_700_000_000_000,
            alarm_type: "RTC_WAKEUP".to_string(),
            exact: true,
            repeat_interval_ms: None,
            sound_uri: Some("sounds/alarm.mp3".to_string()),
            snooze_enabled: None,
            snooze_duration_ms: None,
            snooze_label: None,
            repeat_days_of_week: None,
        };
        let json = serde_json::to_string(&info).unwrap();

        assert!(
            json.contains("\"soundUri\""),
            "soundUri が含まれない: {json}"
        );

        let restored: AlarmInfo = serde_json::from_str(&json).unwrap();
        assert_eq!(restored.sound_uri.as_deref(), Some("sounds/alarm.mp3"));
    }

    #[test]
    fn alarm_info_with_repeat_interval_round_trip() {
        let info = AlarmInfo {
            id: 3,
            title: "Daily".to_string(),
            message: None,
            trigger_at_ms: 0,
            alarm_type: "RTC".to_string(),
            exact: false,
            repeat_interval_ms: Some(86_400_000),
            sound_uri: None,
            snooze_enabled: None,
            snooze_duration_ms: None,
            snooze_label: None,
            repeat_days_of_week: None,
        };
        let json = serde_json::to_string(&info).unwrap();
        let restored: AlarmInfo = serde_json::from_str(&json).unwrap();

        assert_eq!(restored.repeat_interval_ms, Some(86_400_000));
        assert!(!restored.exact);
    }

    // ------------------------------------------------------------------
    // CancelAlarmRequest
    // ------------------------------------------------------------------

    #[test]
    fn cancel_alarm_request_serializes_correctly() {
        let req = CancelAlarmRequest { id: 99 };
        let json = serde_json::to_string(&req).unwrap();
        let restored: CancelAlarmRequest = serde_json::from_str(&json).unwrap();
        assert_eq!(restored.id, 99);
    }

    // ------------------------------------------------------------------
    // ListAlarmsResponse
    // ------------------------------------------------------------------

    #[test]
    fn list_alarms_response_serializes_alarms_array() {
        let response = ListAlarmsResponse {
            alarms: vec![
                AlarmInfo {
                    id: 1,
                    title: "A".to_string(),
                    message: None,
                    trigger_at_ms: 1000,
                    alarm_type: "RTC_WAKEUP".to_string(),
                    exact: true,
                    repeat_interval_ms: None,
                    sound_uri: None,
                    snooze_enabled: None,
                    snooze_duration_ms: None,
                    snooze_label: None,
                    repeat_days_of_week: None,
                },
                AlarmInfo {
                    id: 2,
                    title: "B".to_string(),
                    message: Some("msg".to_string()),
                    trigger_at_ms: 2000,
                    alarm_type: "RTC".to_string(),
                    exact: false,
                    repeat_interval_ms: Some(60_000),
                    sound_uri: Some("sounds/alarm.mp3".to_string()),
                    snooze_enabled: Some(true),
                    snooze_duration_ms: Some(300_000),
                    snooze_label: Some("スヌーズ".to_string()),
                    repeat_days_of_week: None,
                },
            ],
        };
        let json = serde_json::to_string(&response).unwrap();
        assert!(json.contains("\"alarms\""), "alarms キーが含まれない");

        let restored: ListAlarmsResponse = serde_json::from_str(&json).unwrap();
        assert_eq!(restored.alarms.len(), 2);
        assert_eq!(restored.alarms[0].id, 1);
        assert_eq!(restored.alarms[1].id, 2);
    }

    #[test]
    fn list_alarms_response_empty() {
        let response = ListAlarmsResponse { alarms: vec![] };
        let json = serde_json::to_string(&response).unwrap();
        let restored: ListAlarmsResponse = serde_json::from_str(&json).unwrap();
        assert!(restored.alarms.is_empty());
    }

    // ------------------------------------------------------------------
    // CheckPermissionResponse
    // ------------------------------------------------------------------

    #[test]
    fn check_permission_response_true() {
        let resp = CheckPermissionResponse {
            can_schedule_exact_alarms: true,
        };
        let json = serde_json::to_string(&resp).unwrap();
        assert!(json.contains("\"canScheduleExactAlarms\":true"));
        let restored: CheckPermissionResponse = serde_json::from_str(&json).unwrap();
        assert!(restored.can_schedule_exact_alarms);
    }

    #[test]
    fn check_permission_response_false() {
        let resp = CheckPermissionResponse {
            can_schedule_exact_alarms: false,
        };
        let json = serde_json::to_string(&resp).unwrap();
        let restored: CheckPermissionResponse = serde_json::from_str(&json).unwrap();
        assert!(!restored.can_schedule_exact_alarms);
    }

    // ------------------------------------------------------------------
    // スヌーズフィールド
    // ------------------------------------------------------------------

    #[test]
    fn set_alarm_request_with_snooze_serializes_camel_case() {
        let req = SetAlarmRequest {
            id: 1,
            title: "Snooze Test".to_string(),
            message: None,
            trigger_at_ms: 1_700_000_000_000,
            alarm_type: None,
            exact: None,
            allow_while_idle: None,
            repeat_interval_ms: None,
            sound_uri: None,
            snooze_enabled: Some(true),
            snooze_duration_ms: Some(300_000),
            snooze_label: Some("スヌーズ".to_string()),
            repeat_days_of_week: None,
        };
        let json = serde_json::to_string(&req).unwrap();

        assert!(
            json.contains("\"snoozeEnabled\""),
            "snoozeEnabled が含まれない: {json}"
        );
        assert!(
            json.contains("\"snoozeDurationMs\""),
            "snoozeDurationMs が含まれない: {json}"
        );
        assert!(
            json.contains("\"snoozeLabel\""),
            "snoozeLabel が含まれない: {json}"
        );
        assert!(
            !json.contains("snooze_enabled"),
            "snake_case が残っている: {json}"
        );
    }

    #[test]
    fn set_alarm_request_with_snooze_deserializes() {
        let json = r#"{
            "id": 1,
            "title": "Snooze",
            "triggerAtMs": 1000000,
            "snoozeEnabled": true,
            "snoozeDurationMs": 60000,
            "snoozeLabel": "後で"
        }"#;
        let req: SetAlarmRequest = serde_json::from_str(json).unwrap();

        assert_eq!(req.snooze_enabled, Some(true));
        assert_eq!(req.snooze_duration_ms, Some(60_000));
        assert_eq!(req.snooze_label.as_deref(), Some("後で"));
    }

    #[test]
    fn alarm_info_with_snooze_round_trip() {
        let info = AlarmInfo {
            id: 10,
            title: "Snooze Alarm".to_string(),
            message: None,
            trigger_at_ms: 1_700_000_000_000,
            alarm_type: "RTC_WAKEUP".to_string(),
            exact: true,
            repeat_interval_ms: None,
            sound_uri: None,
            snooze_enabled: Some(true),
            snooze_duration_ms: Some(300_000),
            snooze_label: Some("スヌーズ".to_string()),
            repeat_days_of_week: None,
        };
        let json = serde_json::to_string(&info).unwrap();

        assert!(
            json.contains("\"snoozeEnabled\""),
            "snoozeEnabled が含まれない: {json}"
        );
        assert!(
            json.contains("\"snoozeDurationMs\":300000"),
            "snoozeDurationMs の値が不正: {json}"
        );

        let restored: AlarmInfo = serde_json::from_str(&json).unwrap();
        assert_eq!(restored.snooze_enabled, Some(true));
        assert_eq!(restored.snooze_duration_ms, Some(300_000));
        assert_eq!(restored.snooze_label.as_deref(), Some("スヌーズ"));
    }

    // ------------------------------------------------------------------
    // repeatDaysOfWeek
    // ------------------------------------------------------------------

    #[test]
    fn set_alarm_request_with_repeat_days_serializes_camel_case() {
        let req = SetAlarmRequest {
            id: 10,
            title: "Weekly".to_string(),
            message: None,
            trigger_at_ms: 1_700_000_000_000,
            alarm_type: None,
            exact: None,
            allow_while_idle: None,
            repeat_interval_ms: None,
            sound_uri: None,
            snooze_enabled: None,
            snooze_duration_ms: None,
            snooze_label: None,
            repeat_days_of_week: Some(vec![1, 3, 5]),
        };
        let json = serde_json::to_string(&req).unwrap();

        assert!(
            json.contains("\"repeatDaysOfWeek\""),
            "repeatDaysOfWeek が含まれない: {json}"
        );
        assert!(
            json.contains("[1,3,5]"),
            "曜日リストの値が含まれない: {json}"
        );
        assert!(
            !json.contains("repeat_days_of_week"),
            "snake_case が残っている: {json}"
        );
    }

    #[test]
    fn set_alarm_request_with_repeat_days_deserializes() {
        let json = r#"{
            "id": 20,
            "title": "Mon-Wed-Fri",
            "triggerAtMs": 1000000,
            "repeatDaysOfWeek": [1, 3, 5]
        }"#;
        let req: SetAlarmRequest = serde_json::from_str(json).unwrap();

        assert_eq!(req.repeat_days_of_week, Some(vec![1, 3, 5]));
    }

    #[test]
    fn set_alarm_request_without_repeat_days_is_none() {
        let json = r#"{
            "id": 30,
            "title": "OneShot",
            "triggerAtMs": 1000000
        }"#;
        let req: SetAlarmRequest = serde_json::from_str(json).unwrap();

        assert!(req.repeat_days_of_week.is_none());
    }

    #[test]
    fn alarm_info_with_repeat_days_round_trip() {
        let info = AlarmInfo {
            id: 11,
            title: "Weekly Mon-Fri".to_string(),
            message: None,
            trigger_at_ms: 1_700_000_000_000,
            alarm_type: "RTC_WAKEUP".to_string(),
            exact: true,
            repeat_interval_ms: None,
            sound_uri: None,
            snooze_enabled: None,
            snooze_duration_ms: None,
            snooze_label: None,
            repeat_days_of_week: Some(vec![1, 2, 3, 4, 5]),
        };
        let json = serde_json::to_string(&info).unwrap();

        assert!(
            json.contains("\"repeatDaysOfWeek\""),
            "repeatDaysOfWeek が含まれない: {json}"
        );

        let restored: AlarmInfo = serde_json::from_str(&json).unwrap();
        assert_eq!(restored.repeat_days_of_week, Some(vec![1, 2, 3, 4, 5]));
    }

    #[test]
    fn alarm_info_without_repeat_days_round_trip() {
        let info = AlarmInfo {
            id: 12,
            title: "NoRepeat".to_string(),
            message: None,
            trigger_at_ms: 1_700_000_000_000,
            alarm_type: "RTC".to_string(),
            exact: false,
            repeat_interval_ms: None,
            sound_uri: None,
            snooze_enabled: None,
            snooze_duration_ms: None,
            snooze_label: None,
            repeat_days_of_week: None,
        };
        let json = serde_json::to_string(&info).unwrap();
        let restored: AlarmInfo = serde_json::from_str(&json).unwrap();

        assert!(restored.repeat_days_of_week.is_none());
    }
}
