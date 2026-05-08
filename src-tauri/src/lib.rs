mod apk;
mod arsc;
mod axml;
mod commands;
mod utils;

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .invoke_handler(tauri::generate_handler![commands::parse_apk, commands::install_apk])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
