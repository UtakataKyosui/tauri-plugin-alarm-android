use tauri::{command, AppHandle, Runtime};

use crate::models::*;
use crate::AlarmExt;
use crate::Result;

#[command]
pub(crate) async fn set_alarm<R: Runtime>(
    app: AppHandle<R>,
    payload: SetAlarmRequest,
) -> Result<AlarmInfo> {
    app.alarm().set_alarm(payload)
}

#[command]
pub(crate) async fn cancel_alarm<R: Runtime>(
    app: AppHandle<R>,
    payload: CancelAlarmRequest,
) -> Result<()> {
    app.alarm().cancel_alarm(payload)
}

#[command]
pub(crate) async fn list_alarms<R: Runtime>(app: AppHandle<R>) -> Result<ListAlarmsResponse> {
    app.alarm().list_alarms()
}

#[command]
pub(crate) async fn check_exact_alarm_permission<R: Runtime>(
    app: AppHandle<R>,
) -> Result<CheckPermissionResponse> {
    app.alarm().check_exact_alarm_permission()
}

#[command]
pub(crate) async fn open_exact_alarm_settings<R: Runtime>(app: AppHandle<R>) -> Result<()> {
    app.alarm().open_exact_alarm_settings()
}
