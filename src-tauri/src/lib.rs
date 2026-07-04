mod apk;
mod arsc;
mod axml;
mod commands;
mod utils;

use std::{env, path::PathBuf, sync::Mutex};

use tauri::{AppHandle, Emitter, Manager};

#[cfg(any(target_os = "macos", target_os = "ios", target_os = "android"))]
use tauri::RunEvent;

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
            if matches!(event, tauri::RunEvent::Ready) {
                handle_opened_paths(app_handle, collect_startup_apks());
            }

            #[cfg(any(target_os = "macos", target_os = "ios", target_os = "android"))]
            if let RunEvent::Opened { urls } = event {
                let paths: Vec<String> = urls
                    .iter()
                    .filter_map(|url| url.to_file_path().ok())
                    .map(|path| path.to_string_lossy().into_owned())
                    .filter(|path| path.to_lowercase().ends_with(".apk"))
                    .collect();

                handle_opened_paths(app_handle, paths);
            }
        });
}

fn collect_startup_apks() -> Vec<String> {
    env::args_os()
        .skip(1)
        .map(PathBuf::from)
        .filter(|path| path.is_file())
        .map(|path| path.to_string_lossy().into_owned())
        .filter(|path| path.to_lowercase().ends_with(".apk"))
        .collect()
}

fn handle_opened_paths(app_handle: &AppHandle, paths: Vec<String>) {
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
