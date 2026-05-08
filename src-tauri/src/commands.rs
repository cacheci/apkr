use crate::apk::{parse_apk_file, parse_apk_file_with_locale, ApkInfo};
use crate::OpenedFiles;
use std::{
    env,
    path::{Path, PathBuf},
    process::Command,
};
use tauri::State;

#[tauri::command]
pub fn parse_apk(path: String, preferred_locale: Option<String>) -> Result<ApkInfo, String> {
    match preferred_locale {
        Some(locale) => parse_apk_file_with_locale(&path, &locale),
        None => parse_apk_file(&path),
    }
    .map_err(|err| err.to_string())
}

#[tauri::command]
pub fn install_apk(path: String, adb_path: Option<String>) -> Result<String, String> {
    if !Path::new(&path).is_file() {
        return Err("APK 文件不存在".to_owned());
    }

    let adb = resolve_adb(adb_path.as_deref())?;
    let output = Command::new(&adb)
        .arg("install")
        .arg("-r")
        .arg(&path)
        .output()
        .map_err(|err| format!("无法启动 adb：{err}"))?;

    if output.status.success() {
        let stdout = String::from_utf8_lossy(&output.stdout).trim().to_owned();
        return Ok(if stdout.is_empty() { "安装完成".to_owned() } else { stdout });
    }

    let stderr = String::from_utf8_lossy(&output.stderr).trim().to_owned();
    let stdout = String::from_utf8_lossy(&output.stdout).trim().to_owned();
    let detail = if !stderr.is_empty() { stderr } else { stdout };
    Err(if detail.is_empty() {
        format!("adb install 失败：{}", output.status)
    } else {
        detail
    })
}

#[tauri::command]
pub fn take_opened_files(opened_files: State<OpenedFiles>) -> Result<Vec<String>, String> {
    let mut pending = opened_files
        .0
        .lock()
        .map_err(|_| "无法读取打开的文件列表".to_owned())?;
    Ok(pending.drain(..).collect())
}

fn resolve_adb(configured_path: Option<&str>) -> Result<PathBuf, String> {
    let configured_path = configured_path.map(str::trim).filter(|path| !path.is_empty());
    if let Some(path) = configured_path {
        let adb = PathBuf::from(path);
        if adb.is_file() {
            return Ok(adb);
        }
        return Err(format!("配置的 adb 不存在：{path}"));
    }

    find_adb().ok_or_else(|| "未找到 adb 或 adb.exe，请在设置中配置 adb 位置".to_owned())
}

fn find_adb() -> Option<PathBuf> {
    env::var_os("PATH")
        .into_iter()
        .flat_map(|path_var| env::split_paths(&path_var).collect::<Vec<_>>())
        .chain(common_adb_dirs())
        .flat_map(|dir| adb_candidates(&dir))
        .find(|candidate| candidate.is_file())
}

fn adb_candidates(dir: &Path) -> Vec<PathBuf> {
    if cfg!(windows) {
        vec![dir.join("adb.exe"), dir.join("adb")]
    } else {
        vec![dir.join("adb"), dir.join("adb.exe")]
    }
}

fn common_adb_dirs() -> Vec<PathBuf> {
    let mut dirs = vec![
        PathBuf::from("/opt/homebrew/bin"),
        PathBuf::from("/usr/local/bin"),
        PathBuf::from("/usr/bin"),
        PathBuf::from("/bin"),
    ];

    if let Some(home) = env::var_os("HOME") {
        let home = PathBuf::from(home);
        dirs.push(home.join("Library/Android/sdk/platform-tools"));
        dirs.push(home.join("Android/Sdk/platform-tools"));
        dirs.push(home.join("AppData/Local/Android/Sdk/platform-tools"));
    }

    dirs
}
