mod apk;
mod arsc;
mod axml;
mod commands;
mod utils;

use std::sync::Mutex;

use tauri::{Emitter, Manager, RunEvent};

pub struct OpenedFiles(pub Mutex<Vec<String>>);

pub fn run() {
    tauri::Builder::default()
        .manage(OpenedFiles(Mutex::new(Vec::new())))
        .plugin(tauri_plugin_dialog::init())
        .invoke_handler(tauri::generate_handler![
            commands::parse_apk,
            commands::install_apk,
            commands::take_opened_files
        ])
        .build(tauri::generate_context!())
        .expect("error while building tauri application")
        .run(|app_handle, event| {
            if let RunEvent::Opened { urls } = event {
                let paths: Vec<String> = urls
                    .iter()
                    .filter_map(|url| url.to_file_path().ok())
                    .map(|path| path.to_string_lossy().into_owned())
                    .filter(|path| path.to_lowercase().ends_with(".apk"))
                    .collect();

                if paths.is_empty() {
                    return;
                }

                if let Some(opened_files) = app_handle.try_state::<OpenedFiles>() {
                    if let Ok(mut pending) = opened_files.0.lock() {
                        pending.extend(paths.clone());
                    }
                }

                let _ = app_handle.emit("apk-opened", paths);
                if let Some(window) = app_handle.get_webview_window("main") {
                    let _ = window.set_focus();
                }
            }
        });
}
