import { invoke } from "@tauri-apps/api/core";
import { message, open } from "@tauri-apps/plugin-dialog";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
import defaultApkIcon from "../src-tauri/icons/icon_droid.svg";
import "./styles.css";

type ApkInfo = {
  path: string;
  file_name: string;
  size: string;
  package_name: string;
  version_name: string;
  version_code: string;
  min_sdk: string;
  target_sdk: string;
  compile_sdk: string;
  app_label: string;
  resolved_app_label: string;
  app_icon: string;
  resolved_app_icon: string;
  app_icon_data_url: string;
  supported_languages: string[];
  debuggable: string;
  permissions: string[];
  activities: string[];
  services: string[];
  receivers: string[];
  providers: string[];
  native_libs: string[];
  abis: string[];
  signatures: string[];
  file_count: number;
  tech_features: TechFeature[];
};

type TechFeature = {
  name: string;
  icon: string;
};

type Language = "zh-CN" | "en-US";
type Theme = "light" | "dark" | "system";
type Settings = {
  language: Language;
  theme: Theme;
  adbPath: string;
};

const translations: Record<Language, Record<string, string>> = {
  "zh-CN": {
    title: "APK 基础信息查看器",
    subtitle: "选择一个 APK (S)，快速查看包名、版本、SDK、权限、组件、ABI 与签名文件。",
    language: "语言",
    theme: "主题",
    themeLight: "浅色",
    themeDark: "深色",
    themeSystem: "跟随系统",
    settings: "设置",
    back: "返回",
    dropHint: "拖拽 APK 到这里或点击选择文件",
    summary: "基础信息",
    permissions: "权限",
    components: "组件",
    files: "原生库",
    emptyData: "暂无数据",
    parsing: "解析中",
    parseFailed: "解析失败",
    noFeatures: "未检测到技术特性",
    undeclared: "未声明",
    defaultValue: "默认",
    noPermission: "未声明权限",
    noNative: "未发现 native so",
    noSignature: "未发现 META-INF 签名文件",
    noComponent: "无",
    noAbi: "无 native so",
    debugDefault: "false/未声明",
    install: "安装",
    installing: "安装中",
    installSuccess: "安装完成",
    installFailed: "安装失败",
    noApkSelected: "请先选择 APK",
    adbPath: "ADB 位置",
    adbPathPlaceholder: "留空则自动查找",
  },
  "en-US": {
    title: "APK Info Viewer",
    subtitle: "Choose an APK to inspect package, version, SDK, permissions, components, ABI, and signatures.",
    language: "Language",
    theme: "Theme",
    themeLight: "Light",
    themeDark: "Dark",
    themeSystem: "System",
    settings: "Settings",
    back: "Back",
    dropHint: "Drop APK here or click to choose",
    summary: "Summary",
    permissions: "Permissions",
    components: "Components",
    files: "Files",
    emptyData: "No data",
    parsing: "Parsing",
    parseFailed: "Parse failed",
    noFeatures: "No tech features detected",
    undeclared: "Undeclared",
    defaultValue: "Default",
    noPermission: "No permissions declared",
    noNative: "No native so found",
    noSignature: "No META-INF signature files found",
    noComponent: "None",
    noAbi: "No native so",
    debugDefault: "false/undeclared",
    install: "Install",
    installing: "Installing",
    installSuccess: "Installed",
    installFailed: "Install failed",
    noApkSelected: "Choose an APK first",
    adbPath: "ADB path",
    adbPathPlaceholder: "Leave empty to auto-detect",
  },
};

const androidVersions: Record<string, string> = {
  "1": "Android 1.0",
  "2": "Android 1.1",
  "3": "Android 1.5 Cupcake",
  "4": "Android 1.6 Donut",
  "5": "Android 2.0 Eclair",
  "6": "Android 2.0.1 Eclair",
  "7": "Android 2.1 Eclair",
  "8": "Android 2.2 Froyo",
  "9": "Android 2.3 Gingerbread",
  "10": "Android 2.3.3 Gingerbread",
  "11": "Android 3.0 Honeycomb",
  "12": "Android 3.1 Honeycomb",
  "13": "Android 3.2 Honeycomb",
  "14": "Android 4.0 Ice Cream Sandwich",
  "15": "Android 4.0.3 Ice Cream Sandwich",
  "16": "Android 4.1 Jelly Bean",
  "17": "Android 4.2 Jelly Bean",
  "18": "Android 4.3 Jelly Bean",
  "19": "Android 4.4 KitKat",
  "20": "Android 4.4W KitKat Wear",
  "21": "Android 5.0 Lollipop",
  "22": "Android 5.1 Lollipop",
  "23": "Android 6.0 Marshmallow",
  "24": "Android 7.0 Nougat",
  "25": "Android 7.1 Nougat",
  "26": "Android 8.0 Oreo",
  "27": "Android 8.1 Oreo",
  "28": "Android 9 Pie",
  "29": "Android 10",
  "30": "Android 11",
  "31": "Android 12",
  "32": "Android 12L",
  "33": "Android 13",
  "34": "Android 14",
  "35": "Android 15",
  "36": "Android 16",
};

const currentPath = document.querySelector<HTMLElement>("#current-path")!;
const techFeatures = document.querySelector<HTMLElement>("#tech-features")!;
const summary = document.querySelector<HTMLElement>("#summary")!;
const permissions = document.querySelector<HTMLElement>("#permissions")!;
const components = document.querySelector<HTMLElement>("#components")!;
const files = document.querySelector<HTMLElement>("#files")!;
const dropZone = document.querySelector<HTMLElement>("#drop-zone")!;
const appIcon = document.querySelector<HTMLImageElement>("#app-icon")!;
const mainView = document.querySelector<HTMLElement>("#main-view")!;
const settingsView = document.querySelector<HTMLElement>("#settings-view")!;
const settingsToggle = document.querySelector<HTMLButtonElement>("#settings-toggle")!;
const installToggle = document.querySelector<HTMLButtonElement>("#install-toggle")!;
const languageSelect = document.querySelector<HTMLSelectElement>("#language-select")!;
const themeSelect = document.querySelector<HTMLSelectElement>("#theme-select")!;
const adbPathInput = document.querySelector<HTMLInputElement>("#adb-path-input")!;
let settings = loadSettings();
let currentInfo: ApkInfo | null = null;
let isSettingsView = false;

const t = (key: string) => translations[settings.language][key] || translations["zh-CN"][key] || key;
const empty = (value: string | undefined) => value && value.length > 0 ? value : t("undeclared");

showAppIcon(defaultApkIcon);
applySettings();
void boot();

async function boot() {
  try {
    await loadPendingOpenedApk();
  } finally {
    const currentWindow = getCurrentWindow();
    try {
      await currentWindow.show();
      await currentWindow.setFocus();
    } catch (error) {
      console.error("Failed to show main window", error);
    }
  }
}

async function loadApk(path: string) {
  installToggle.disabled = true;
  currentPath.textContent = fileNameFromPath(path);
  renderFeatures([{ name: t("parsing"), icon: "..." }]);
  showAppIcon(defaultApkIcon);

  try {
    const info = await invoke<ApkInfo>("parse_apk", { path, preferredLocale: settings.language });
    currentInfo = info;
    installToggle.disabled = false;
    renderInfo(info);
  } catch (error) {
    currentInfo = null;
    installToggle.disabled = true;
    clearFeatures();
    await showDialog(`${t("parseFailed")}：${String(error)}`, t("parseFailed"), "error");
  }
}

async function installCurrentApk() {
  if (!currentInfo) {
    await showDialog(t("noApkSelected"), t("installFailed"), "warning");
    return;
  }

  installToggle.disabled = true;
  installToggle.textContent = t("installing");

  try {
    const result = await invoke<string>("install_apk", { path: currentInfo.path, adbPath: settings.adbPath });
    const text = `${t("installSuccess")}：${result || currentInfo.file_name}`;
    await showDialog(text, t("installSuccess"), "info");
  } catch (error) {
    await showDialog(`${t("installFailed")}：${String(error)}`, t("installFailed"), "error");
  } finally {
    installToggle.disabled = false;
    installToggle.textContent = t("install");
  }
}

function renderInfo(info: ApkInfo) {
  const rows: Array<[string, string]> = [
    [settings.language === "zh-CN" ? "文件名" : "File name", info.file_name],
    [settings.language === "zh-CN" ? "文件大小" : "File size", info.size],
    [settings.language === "zh-CN" ? "包名" : "Package", info.package_name],
    [settings.language === "zh-CN" ? "应用名" : "App name", info.resolved_app_label || info.app_label],
    [settings.language === "zh-CN" ? "版本名" : "Version name", info.version_name],
    [settings.language === "zh-CN" ? "版本号" : "Version code", info.version_code],
    [settings.language === "zh-CN" ? "最低 SDK" : "Min SDK", formatSdkVersion(info.min_sdk)],
    [settings.language === "zh-CN" ? "目标 SDK" : "Target SDK", formatSdkVersion(info.target_sdk)],
    [settings.language === "zh-CN" ? "编译 SDK" : "Compile SDK", formatSdkVersion(info.compile_sdk)],
    [settings.language === "zh-CN" ? "支持语言" : "Languages", info.supported_languages.join(", ") || t("defaultValue")],
    ["Debuggable", info.debuggable || t("debugDefault")],
    [settings.language === "zh-CN" ? "文件数量" : "File count", String(info.file_count)],
    ["ABI", info.abis.join(", ") || t("noAbi")],
    [settings.language === "zh-CN" ? "签名文件" : "Signatures", info.signatures.join(", ") || t("noSignature")],
  ];

  summary.innerHTML = rows
    .map(([key, value]) => `<div><dt>${escapeHtml(key)}</dt><dd>${escapeHtml(empty(value))}</dd></div>`)
    .join("");

  permissions.textContent = info.permissions.join("\n") || t("noPermission");
  components.textContent = [
    section("Activities", info.activities),
    section("Services", info.services),
    section("Receivers", info.receivers),
    section("Providers", info.providers),
  ].join("\n\n");
  files.textContent = info.native_libs.join("\n") || t("noNative");
  renderFeatures(info.tech_features);

  if (info.app_icon_data_url) {
    showAppIcon(info.app_icon_data_url);
  } else {
    showAppIcon(defaultApkIcon);
  }
}

function showAppIcon(src: string) {
  appIcon.src = src;
  appIcon.hidden = false;
}

function renderFeatures(features: TechFeature[]) {
  if (features.length === 0) {
    clearFeatures();
    return;
  }

  techFeatures.classList.remove("message");
  techFeatures.innerHTML = features
    .map(
      (feature) => `
        <span class="tech-chip">
          ${renderFeatureIcon(feature)}
          ${escapeHtml(feature.name)}
        </span>
      `,
    )
    .join("");
}

function clearFeatures() {
  techFeatures.classList.remove("message");
  techFeatures.textContent = "";
}

function renderFeatureIcon(feature: TechFeature) {
  if (/^https?:\/\//.test(feature.icon)) {
    return `<img class="tech-icon" src="${escapeHtml(feature.icon)}" alt="" />`;
  }

  return `<span class="tech-icon">${escapeHtml(feature.icon)}</span>`;
}

async function showDialog(text: string, title: string, kind: "info" | "warning" | "error") {
  await message(text, { title, kind });
}

function section(title: string, items: string[]) {
  return `[${title}]\n${items.join("\n") || t("noComponent")}`;
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[char]!));
}

function fileNameFromPath(path: string) {
  return path.split(/[\\/]/).pop() || path;
}

function formatSdkVersion(value: string) {
  if (!value) {
    return value;
  }

  const version = androidVersions[value];
  return version ? `${value} (${version})` : value;
}

function loadSettings(): Settings {
  let saved: Partial<Settings> = {};
  try {
    saved = JSON.parse(localStorage.getItem("apk-info-settings") || "{}") as Partial<Settings>;
  } catch {
    saved = {};
  }

  return {
    language: saved.language === "en-US" ? "en-US" : "zh-CN",
    theme: saved.theme === "dark" || saved.theme === "system" ? saved.theme : "light",
    adbPath: typeof saved.adbPath === "string" ? saved.adbPath : "",
  };
}

function saveSettings() {
  localStorage.setItem("apk-info-settings", JSON.stringify(settings));
}

function applySettings() {
  languageSelect.value = settings.language;
  themeSelect.value = settings.theme;
  adbPathInput.value = settings.adbPath;
  document.documentElement.lang = settings.language;
  document.documentElement.dataset.theme = settings.theme;
  document.querySelectorAll<HTMLElement>("[data-i18n]").forEach((element) => {
    const key = element.dataset.i18n;
    if (key) {
      element.textContent = t(key);
    }
  });
  document.querySelectorAll<HTMLElement>("[data-i18n-title]").forEach((element) => {
    const key = element.dataset.i18nTitle;
    if (key) {
      element.title = t(key);
      element.setAttribute("aria-label", t(key));
    }
  });
  document.querySelectorAll<HTMLInputElement>("[data-i18n-placeholder]").forEach((element) => {
    const key = element.dataset.i18nPlaceholder;
    if (key) {
      element.placeholder = t(key);
    }
  });

  if (!currentInfo) {
    currentPath.textContent = t("dropHint");
  } else {
    renderInfo(currentInfo);
  }
  settingsToggle.textContent = isSettingsView ? t("back") : t("settings");
  installToggle.textContent = t("install");
  installToggle.disabled = !currentInfo;
}

function showSettingsView() {
  isSettingsView = true;
  mainView.hidden = true;
  settingsView.hidden = false;
  settingsToggle.textContent = t("back");
}

function showMainView() {
  isSettingsView = false;
  settingsView.hidden = true;
  mainView.hidden = false;
  settingsToggle.textContent = t("settings");
}

languageSelect.addEventListener("change", () => {
  settings = {
    ...settings,
    language: languageSelect.value === "en-US" ? "en-US" : "zh-CN",
  };
  saveSettings();
  applySettings();
});

themeSelect.addEventListener("change", () => {
  const theme = themeSelect.value;
  settings = {
    ...settings,
    theme: theme === "dark" || theme === "system" ? theme : "light",
  };
  saveSettings();
  applySettings();
});

adbPathInput.addEventListener("input", () => {
  settings = {
    ...settings,
    adbPath: adbPathInput.value.trim(),
  };
  saveSettings();
});

settingsToggle.addEventListener("click", () => {
  if (isSettingsView) {
    showMainView();
  } else {
    showSettingsView();
  }
});

installToggle.addEventListener("click", installCurrentApk);

async function pickApk() {
  const selected = await open({ multiple: false, filters: [{ name: "Android APK", extensions: ["apk", "xml", "xxxxml"] }] });
  if (typeof selected === "string") {
    await loadApk(selected);
  }
}

dropZone.addEventListener("click", pickApk);

dropZone.addEventListener("keydown", async (event) => {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    await pickApk();
  }
});

dropZone.addEventListener("dragover", (event) => {
  event.preventDefault();
  dropZone.classList.add("dragging");
});

dropZone.addEventListener("dragleave", () => dropZone.classList.remove("dragging"));

dropZone.addEventListener("drop", async (event) => {
  event.preventDefault();
  dropZone.classList.remove("dragging");
  const file = event.dataTransfer?.files?.[0];
  const path = (file as File & { path?: string } | undefined)?.path;
  if (path) {
    await loadApk(path);
  }
});

listen<{ paths: string[] }>("tauri://drag-drop", async (event) => {
  const path = event.payload.paths?.[0];
  if (/\.(apk|xml|xxxxml)$/i.test(path)) {
    await loadApk(path);
  }
});

listen<string[]>("apk-opened", async (event) => {
  await loadFirstApkPath(event.payload);
});

async function loadPendingOpenedApk() {
  try {
    const paths = await invoke<string[]>("take_opened_files");
    await loadFirstApkPath(paths);
  } catch (error) {
    console.error("Failed to load opened APK", error);
  }
}

async function loadFirstApkPath(paths: string[]) {
  const path = paths.find((candidate) => /\.apk$/i.test(candidate));
  if (path) {
    await loadApk(path);
  }
}
