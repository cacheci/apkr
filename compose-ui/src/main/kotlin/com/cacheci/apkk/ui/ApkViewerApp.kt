package com.cacheci.apkk.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cacheci.apkk.i18n.Strings
import com.cacheci.apkk.model.ApkInfo
import com.cacheci.apkk.model.AppIconDrawable
import com.cacheci.apkk.model.AppLanguage
import com.cacheci.apkk.model.AppSettings
import com.cacheci.apkk.model.AppTheme
import com.cacheci.apkk.parser.ApkParser
import com.cacheci.apkk.platform.AdbInstaller
import com.cacheci.apkk.platform.DesktopPlatform
import com.cacheci.apkk.platform.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

@Composable
fun ApkViewerApp(
    platform: DesktopPlatform,
    externallyOpenedPath: String?,
    chooseFile: () -> String?,
) {
    var settings by remember { mutableStateOf(SettingsRepository.load()) }
    val systemDark = isSystemInDarkTheme()
    val dark = settings.theme == AppTheme.DARK || settings.theme == AppTheme.SYSTEM && systemDark

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            ViewerContent(platform, externallyOpenedPath, chooseFile, settings) {
                settings = it
                SettingsRepository.save(it)
            }
        }
    }
}

@Composable
private fun ViewerContent(
    platform: DesktopPlatform,
    externallyOpenedPath: String?,
    chooseFile: () -> String?,
    settings: AppSettings,
    updateSettings: (AppSettings) -> Unit,
) {
    val strings = remember(settings.language) { Strings(settings.language) }
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<ApkInfo?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun load(path: String) {
        parsing = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ApkParser.parse(path, if (settings.language == AppLanguage.ZH_CN) "zh-CN" else "en-US")
                }
            }.onSuccess { info = it }
                .onFailure { dialog = strings["parseFailed"] to (it.message ?: it.toString()) }
            parsing = false
        }
    }

    LaunchedEffect(externallyOpenedPath) {
        externallyOpenedPath?.takeIf(String::isNotBlank)?.let(::load)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(strings["title"], style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(strings["subtitle"], color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
                enabled = info != null && !installing,
                onClick = {
                    val apk = info ?: return@OutlinedButton
                    installing = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { AdbInstaller.install(apk.path, settings.adbPath, platform) }
                        }.onSuccess { dialog = strings["installSuccess"] to it }
                            .onFailure { dialog = strings["installFailed"] to (it.message ?: it.toString()) }
                        installing = false
                    }
                },
            ) { Text(if (installing) strings["installing"] else strings["install"]) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { settingsVisible = !settingsVisible }) {
                Text(if (settingsVisible) strings["back"] else strings["settings"])
            }
        }

        if (settingsVisible) {
            SettingsPanel(settings, updateSettings, strings)
        } else {
            DropCard(info, parsing, strings) { chooseFile()?.let(::load) }
            info?.let { InfoGrid(it, strings) }
        }
    }

    dialog?.let { (title, message) ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
            title = { Text(title) },
            text = { Text(message) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DropCard(info: ApkInfo?, parsing: Boolean, strings: Strings, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        parsing -> strings["parsing"]
                        info != null -> info.fileName
                        else -> strings["dropHint"]
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (info != null) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        info.techFeatures.forEach { feature ->
                            Text(
                                feature.name,
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            ApkIcon(info)
        }
    }
}

@Composable
private fun ApkIcon(info: ApkInfo?) {
    val density = LocalDensity.current
    val drawable = info?.appIconDrawable
    val fallbackPainter = remember(info?.appIconBytes, info?.appIconMimeType, density) {
        info?.appIconBytes?.let { bytes ->
            runCatching {
                ByteArrayInputStream(bytes).use { input ->
                    if (info.appIconMimeType == "image/svg+xml") loadSvgPainter(input, density)
                    else BitmapPainter(loadImageBitmap(input))
                }
            }.getOrNull()
        }
    }
    Box(
        Modifier.size(66.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            drawable != null -> DrawableIcon(drawable, Modifier.fillMaxSize())
            fallbackPainter != null -> Image(fallbackPainter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Inside)
            else -> Icon(Icons.Default.Android, null, Modifier.size(42.dp), tint = Color(0xff76b5d2))
        }
    }
}

@Composable
private fun DrawableIcon(
    drawable: AppIconDrawable,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Inside,
) {
    when (drawable) {
        is AppIconDrawable.Encoded -> {
            val density = LocalDensity.current
            val painter = remember(drawable, density) {
                runCatching {
                    ByteArrayInputStream(drawable.bytes).use { input ->
                        if (drawable.mimeType == "image/svg+xml") loadSvgPainter(input, density)
                        else BitmapPainter(loadImageBitmap(input))
                    }
                }.getOrNull()
            }
            if (painter != null) Image(painter, null, modifier, contentScale = contentScale)
        }

        is AppIconDrawable.Solid -> Box(modifier.background(Color(drawable.argb.toInt())))

        is AppIconDrawable.Adaptive -> Box(modifier.clip(RoundedCornerShape(percent = 22))) {
            drawable.background?.let {
                DrawableIcon(it, Modifier.fillMaxSize(), ContentScale.FillBounds)
            }
            drawable.foreground?.let {
                DrawableIcon(it, Modifier.fillMaxSize(), ContentScale.FillBounds)
            }
        }

        is AppIconDrawable.Layers -> Box(modifier) {
            drawable.items.forEach { DrawableIcon(it, Modifier.fillMaxSize(), contentScale) }
        }

        is AppIconDrawable.Inset -> BoxWithConstraints(modifier) {
            val left = maxWidth * (drawable.left / ANDROID_ICON_VIEWPORT)
            val top = maxHeight * (drawable.top / ANDROID_ICON_VIEWPORT)
            val right = maxWidth * (drawable.right / ANDROID_ICON_VIEWPORT)
            val bottom = maxHeight * (drawable.bottom / ANDROID_ICON_VIEWPORT)
            DrawableIcon(
                drawable.drawable,
                Modifier.fillMaxSize().padding(start = left, top = top, end = right, bottom = bottom),
                contentScale,
            )
        }
    }
}

private const val ANDROID_ICON_VIEWPORT = 108f

@Composable
private fun InfoGrid(info: ApkInfo, strings: Strings) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InfoCard(strings["summary"]) { Summary(info, strings) }
            InfoCard(strings["components"]) {
                Text(
                    listOf(
                        section("Activities", info.activities, strings), section("Services", info.services, strings),
                        section("Receivers", info.receivers, strings), section("Providers", info.providers, strings),
                    ).joinToString("\n\n"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InfoCard(strings["permissions"]) {
                Text(info.permissions.joinToString("\n").ifEmpty { strings["noPermission"] }, style = MaterialTheme.typography.bodySmall)
            }
            InfoCard(strings["files"]) {
                Text(info.nativeLibs.joinToString("\n").ifEmpty { strings["noNative"] }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Summary(info: ApkInfo, strings: Strings) {
    val rows = listOf(
        strings["fileName"] to info.fileName, strings["fileSize"] to info.size,
        strings["package"] to info.packageName, strings["appName"] to (info.resolvedAppLabel.ifEmpty { info.appLabel }),
        strings["versionName"] to info.versionName, strings["versionCode"] to info.versionCode,
        strings["minSdk"] to sdkLabel(info.minSdk), strings["targetSdk"] to sdkLabel(info.targetSdk),
        strings["compileSdk"] to sdkLabel(info.compileSdk),
        strings["languages"] to info.supportedLanguages.joinToString(", ").ifEmpty { strings["default"] },
        "Debuggable" to info.debuggable.ifEmpty { strings["debugDefault"] },
        strings["fileCount"] to info.fileCount.toString(),
        "ABI" to info.abis.joinToString(", ").ifEmpty { strings["noAbi"] },
        "Signatures" to info.signatures.joinToString(", ").ifEmpty { strings["noSignature"] },
    )
    rows.forEach { (name, value) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(name, Modifier.width(116.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(value.ifEmpty { strings["undeclared"] }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPanel(settings: AppSettings, update: (AppSettings) -> Unit, strings: Strings) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            EnumSelector(strings["language"], settings.language, AppLanguage.entries) {
                update(settings.copy(language = it))
            }
            EnumSelector(strings["theme"], settings.theme, AppTheme.entries) {
                update(settings.copy(theme = it))
            }
            OutlinedTextField(
                value = settings.adbPath,
                onValueChange = { update(settings.copy(adbPath = it)) },
                label = { Text(strings["adbPath"]) },
                placeholder = { Text(strings["adbPathHint"]) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun <T : Enum<T>> EnumSelector(label: String, value: T, values: List<T>, update: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${value.displayName()}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { option ->
                DropdownMenuItem(text = { Text(option.displayName()) }, onClick = {
                    update(option)
                    expanded = false
                })
            }
        }
    }
}

private fun Enum<*>.displayName(): String = when (this) {
    AppLanguage.ZH_CN -> "简体中文"
    AppLanguage.EN_US -> "English"
    AppTheme.LIGHT -> "Light / 浅色"
    AppTheme.DARK -> "Dark / 深色"
    AppTheme.SYSTEM -> "System / 跟随系统"
    else -> name
}

private fun section(title: String, values: List<String>, strings: Strings): String =
    "[$title]\n${values.joinToString("\n").ifEmpty { strings["noComponent"] }}"

private fun sdkLabel(value: String): String {
    val name = androidVersions[value] ?: return value
    return "$value ($name)"
}

private val androidVersions = mapOf(
    "1" to "Android 1.0", "2" to "Android 1.1", "3" to "Android 1.5 Cupcake",
    "4" to "Android 1.6 Donut", "5" to "Android 2.0 Eclair", "6" to "Android 2.0.1 Eclair",
    "7" to "Android 2.1 Eclair", "8" to "Android 2.2 Froyo", "9" to "Android 2.3 Gingerbread",
    "10" to "Android 2.3.3 Gingerbread", "11" to "Android 3.0 Honeycomb", "12" to "Android 3.1 Honeycomb",
    "13" to "Android 3.2 Honeycomb", "14" to "Android 4.0 Ice Cream Sandwich",
    "15" to "Android 4.0.3 Ice Cream Sandwich", "16" to "Android 4.1 Jelly Bean",
    "17" to "Android 4.2 Jelly Bean", "18" to "Android 4.3 Jelly Bean", "19" to "Android 4.4 KitKat",
    "20" to "Android 4.4W KitKat Wear", "21" to "Android 5.0 Lollipop", "22" to "Android 5.1 Lollipop",
    "23" to "Android 6.0 Marshmallow", "24" to "Android 7.0 Nougat", "25" to "Android 7.1 Nougat",
    "26" to "Android 8.0 Oreo", "27" to "Android 8.1 Oreo", "28" to "Android 9.0 Pie",
    "29" to "Android 10 Q", "30" to "Android 11 R", "31" to "Android 12 S", "32" to "Android 12L",
    "33" to "Android 13 Tiramisu", "34" to "Android 14 UpsideDownCake",
    "35" to "Android 15 VanillaIceCream", "36" to "Android 16 Baklava", "37" to "Android 17 CinnamonBun",
)
