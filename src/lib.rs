use tauri::{
    plugin::{Builder, TauriPlugin},
    Manager, Runtime,
};

pub use models::*;

#[cfg(desktop)]
mod desktop;
#[cfg(mobile)]
mod mobile;

mod commands;
mod error;
mod models;

pub use error::{Error, Result};

#[cfg(desktop)]
use desktop::Alarm;
#[cfg(mobile)]
use mobile::Alarm;

/// Extensions to [`tauri::App`], [`tauri::AppHandle`] and [`tauri::Window`] to access the alarm APIs.
pub trait AlarmExt<R: Runtime> {
    fn alarm(&self) -> &Alarm<R>;
}

impl<R: Runtime, T: Manager<R>> crate::AlarmExt<R> for T {
    fn alarm(&self) -> &Alarm<R> {
        self.state::<Alarm<R>>().inner()
    }
}

/// プラグインを初期化する
pub fn init<R: Runtime>() -> TauriPlugin<R> {
    Builder::new("alarm")
        .invoke_handler(tauri::generate_handler![
            commands::set_alarm,
            commands::cancel_alarm,
            commands::list_alarms,
            commands::check_exact_alarm_permission,
            commands::open_exact_alarm_settings,
        ])
        .setup(|app, api| {
            #[cfg(mobile)]
            let alarm = mobile::init(app, api)?;
            #[cfg(desktop)]
            let alarm = desktop::init(app, api)?;
            app.manage(alarm);
            Ok(())
        })
        .build()
}
