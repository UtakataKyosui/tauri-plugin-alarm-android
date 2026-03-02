use serde::de::DeserializeOwned;
use tauri::{
    plugin::{PluginApi, PluginHandle},
    AppHandle, Runtime,
};

use crate::models::*;

#[cfg(target_os = "ios")]
tauri::ios_plugin_binding!(init_plugin_alarm);

// Kotlin/Swift プラグインクラスを初期化する
pub fn init<R: Runtime, C: DeserializeOwned>(
    _app: &AppHandle<R>,
    api: PluginApi<R, C>,
) -> crate::Result<Alarm<R>> {
    #[cfg(target_os = "android")]
    let handle = api.register_android_plugin("com.plugin.alarm", "AlarmPlugin")?;
    #[cfg(target_os = "ios")]
    let handle = api.register_ios_plugin(init_plugin_alarm)?;
    Ok(Alarm(handle))
}

/// Access to the alarm APIs.
pub struct Alarm<R: Runtime>(PluginHandle<R>);

impl<R: Runtime> Alarm<R> {
    pub fn set_alarm(&self, payload: SetAlarmRequest) -> crate::Result<AlarmInfo> {
        self.0
            .run_mobile_plugin("setAlarm", payload)
            .map_err(Into::into)
    }

    pub fn cancel_alarm(&self, payload: CancelAlarmRequest) -> crate::Result<()> {
        self.0
            .run_mobile_plugin("cancelAlarm", payload)
            .map_err(Into::into)
    }

    pub fn list_alarms(&self) -> crate::Result<ListAlarmsResponse> {
        self.0
            .run_mobile_plugin("listAlarms", ())
            .map_err(Into::into)
    }

    pub fn check_exact_alarm_permission(&self) -> crate::Result<CheckPermissionResponse> {
        self.0
            .run_mobile_plugin("checkExactAlarmPermission", ())
            .map_err(Into::into)
    }

    pub fn open_exact_alarm_settings(&self) -> crate::Result<()> {
        self.0
            .run_mobile_plugin("openExactAlarmSettings", ())
            .map_err(Into::into)
    }
}
