package com.cacheci.apkk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cacheci.apkk.i18n.Strings
import com.cacheci.apkk.model.ApkInfo
import com.cacheci.apkk.model.AppIconDrawable
import com.cacheci.apkk.model.AppLanguage
import com.cacheci.apkk.model.AppSettings
import com.cacheci.apkk.model.AppColorTheme
import com.cacheci.apkk.parser.ApkParser
import com.cacheci.apkk.platform.AdbInstaller
import com.cacheci.apkk.platform.DesktopPlatform
import com.cacheci.apkk.platform.SettingsRepository
import com.cacheci.apkk.ui.component.ButtonColors
import com.cacheci.apkk.ui.component.Card
import com.cacheci.apkk.ui.component.CardColors
import com.cacheci.apkk.ui.component.EnumSelector
import com.cacheci.apkk.ui.component.Icon
import com.cacheci.apkk.ui.component.Text
import com.cacheci.apkk.ui.component.TextButton
import com.cacheci.apkk.ui.component.TextButtonDefaults
import com.cacheci.apkk.ui.theme.AppTheme
import com.cacheci.apkk.ui.theme.AppTheme.DefaultThemeValues
import com.cacheci.apkk.ui.theme.ColorSchemeMode
import com.cacheci.apkk.ui.theme.ThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToSvgPainter

@Composable
fun ApkViewerApp(
    platform: DesktopPlatform,
    externallyOpenedPath: String?,
    chooseFile: () -> String?,
) {
    var settings by remember { mutableStateOf(SettingsRepository.load()) }

    AppTheme(
        controller = ThemeController(
            colorSchemeMode = when (settings.theme) {
                AppColorTheme.SYSTEM -> ColorSchemeMode.System
                AppColorTheme.LIGHT -> ColorSchemeMode.Light
                AppColorTheme.DARK -> ColorSchemeMode.Dark
            }
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AppTheme.colorScheme.background)
        ) {
            App(platform, externallyOpenedPath, chooseFile, settings) {
                settings = it
                SettingsRepository.save(it)
            }
        }
    }
}

@Composable
private fun App(
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
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(strings["title"], style = AppTheme.textStyles.h1, fontWeight = FontWeight.Medium)
            }
            TextButton(
                text = if (installing) strings["installing"] else strings["install"],
                enabled = info != null && !installing,
                onClick = {
                    val apk = info ?: return@TextButton
                    installing = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { AdbInstaller.install(apk.path, settings.adbPath, platform) }
                        }.onSuccess { dialog = strings["installSuccess"] to it }
                            .onFailure { dialog = strings["installFailed"] to (it.message ?: it.toString()) }
                        installing = false
                    }
                },
            )
            TextButton(
                text = if (settingsVisible) strings["back"] else strings["settings"],
                onClick = { settingsVisible = !settingsVisible }
            )
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
            confirmButton = { TextButton(text = "OK", onClick = { dialog = null })},
            title = { Text(title) },
            text = { Text(message) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DropCard(info: ApkInfo?, parsing: Boolean, strings: Strings, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(DefaultThemeValues.cardInsidePadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ApkIcon(info)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    modifier = Modifier.padding( start= 6.dp ),
                    text = when {
                        parsing -> strings["parsing"]
                        info != null -> info.resolvedAppLabel.ifEmpty { info.fileName }
                        else -> strings["dropHint"]
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (info != null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        info.techFeatures.forEach { feature ->
                            Card (colors = CardColors(
                                background = AppTheme.colorScheme.secondContainer,
                                border = AppTheme.colorScheme.secondBorder,
                            )
                            ) {
                                Text(
                                    feature.name,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = AppTheme.textStyles.main,
                                )
                            }
                        }
                    }
                }
            }
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
                if (info.appIconMimeType == "image/svg+xml") bytes.decodeToSvgPainter(density)
                else BitmapPainter(bytes.decodeToImageBitmap())
            }.getOrNull()
        }
    }
    Box(
        Modifier
            .size(66.dp)
            .clip(RoundedCornerShape(percent = 22))
            .background(AppTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            drawable != null -> DrawableIcon(drawable, Modifier.fillMaxSize())
            fallbackPainter != null -> Image(fallbackPainter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Inside)
            else -> Icon(Icons.Default.Android, null, Modifier.size(42.dp), tint = AppTheme.colorScheme.primary)
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
                    if (drawable.mimeType == "image/svg+xml") drawable.bytes.decodeToSvgPainter(density)
                    else BitmapPainter(drawable.bytes.decodeToImageBitmap())
                }.getOrNull()
            }
            if (painter != null) Image(painter, null, modifier, contentScale = contentScale)
        }

        is AppIconDrawable.Solid -> Box(modifier.background(Color(drawable.argb.toInt())))

        is AppIconDrawable.Adaptive -> Box(modifier) {
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
    var currentCard by remember { mutableStateOf(AppInfoCard.BASIC) }
    Column (verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        Card( modifier = Modifier.fillMaxWidth() ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppInfoCard.entries.forEach {
                    val selected = currentCard == it
                    TextButton(
                        it.displayName(),
                        onClick = {
                            currentCard = it
                        },
                        colors = TextButtonDefaults.textButtonColors(
                            text = if (selected) AppTheme.colorScheme.primaryElement else AppTheme.colorScheme.element,
                            button = ButtonColors(
                                background = if (selected) AppTheme.colorScheme.primary else AppTheme.colorScheme.secondContainer,
                                border = AppTheme.colorScheme.secondBorder
                            )
                        )
                    )
                }
            }
        }
        AnimatedContent(
            targetState = currentCard,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "MainPageTransition",
        ) { card ->
            Card {
                when (card) {
                    AppInfoCard.BASIC ->
                        Card( Modifier.weight(1f).fillMaxWidth() ) {
                            Column (
                                Modifier.padding(
                                    horizontal = DefaultThemeValues.cardInsidePadding,
                                    vertical = 8.dp,
                                )
                            ) {
                                AppInfoBasic(info, strings)
                            }
                        }

                    AppInfoCard.COMPONENT ->
                        Card( Modifier.weight(1f).fillMaxWidth() ) {
                            Column (
                                Modifier.padding(
                                    horizontal = DefaultThemeValues.cardInsidePadding,
                                    vertical = 8.dp,
                                )
                            ) {
                                Text(
                                    listOf(
                                        section("Activities", info.activities, strings),
                                        section("Services", info.services, strings),
                                        section("Receivers", info.receivers, strings),
                                        section("Providers", info.providers, strings),
                                    ).joinToString("\n\n"),
                                    style = AppTheme.textStyles.ref,
                                )
                            }
                        }

                    AppInfoCard.SIGNATURE ->
                        Card(Modifier.weight(1f).fillMaxWidth()) {
                            Column (
                                Modifier.padding(
                                    horizontal = DefaultThemeValues.cardInsidePadding,
                                    vertical = 8.dp,
                                )
                            ) {}
                        }

                    AppInfoCard.PERMISSION ->
                        Card(Modifier.weight(1f).fillMaxWidth()) {
                            Column (
                                Modifier.padding(
                                    horizontal = DefaultThemeValues.cardInsidePadding,
                                    vertical = 8.dp,
                                )
                            ) {
                                Text(
                                    info.permissions.joinToString("\n")
                                        .ifEmpty { strings["noPermission"] },
                                    style = AppTheme.textStyles.ref
                                )
                            }
                        }

                    AppInfoCard.LIB ->
                        Card(Modifier.weight(1f).fillMaxWidth()) {
                            Column (
                                Modifier.padding(
                                    horizontal = DefaultThemeValues.cardInsidePadding,
                                    vertical = 8.dp,
                                )
                            ) {
                                Text(
                                    info.nativeLibs.joinToString("\n").ifEmpty { strings["noNative"] },
                                    style = AppTheme.textStyles.ref
                                )
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun AppInfoBasic(info: ApkInfo, strings: Strings) {
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
            Text(name, Modifier.width(116.dp), color = AppTheme.colorScheme.element, style = AppTheme.textStyles.summary)
            Text(value.ifEmpty { strings["undeclared"] }, Modifier.weight(1f), style = AppTheme.textStyles.summary)
        }
    }
}

@Composable
private fun SettingsPanel(settings: AppSettings, update: (AppSettings) -> Unit, strings: Strings) {
    Card(
        Modifier.fillMaxWidth(),
    ) {
        Column {
            EnumSelector(
                label = strings["language"],
                selected = settings.language.ordinal,
                values = AppLanguage.entries.map(Enum<*>::displayName),
                onValueChange= { index ->
                    update(settings.copy(language = AppLanguage.entries[index]))
                }
            )
            EnumSelector(
                strings["theme"],
                settings.theme.ordinal,
                AppColorTheme.entries.map(Enum<*>::displayName)
            ) { index ->
                update(settings.copy(theme = AppColorTheme.entries[index]))
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

fun Enum<*>.displayName(): String = when (this) {
    AppLanguage.ZH_CN -> "简体中文"
    AppLanguage.EN_US -> "English"
    AppColorTheme.LIGHT -> "Light / 浅色"
    AppColorTheme.DARK -> "Dark / 深色"
    AppColorTheme.SYSTEM -> "System / 跟随系统"
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


enum class AppInfoCard {
    BASIC,
    COMPONENT,
    SIGNATURE,
    PERMISSION,
    LIB,
}