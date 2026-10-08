package com.cacheci.apkk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.zIndex
import com.cacheci.apkk.model.ApkInfo
import com.cacheci.apkk.model.AppIconDrawable
import com.cacheci.apkk.model.AppLanguage
import com.cacheci.apkk.model.AppSettings
import com.cacheci.apkk.model.AppColorTheme
import com.cacheci.apkk.parser.ApkParser
import com.cacheci.apkk.platform.AdbDevice
import com.cacheci.apkk.platform.AdbInstaller
import com.cacheci.apkk.platform.DesktopPlatform
import com.cacheci.apkk.platform.SettingsRepository
import com.cacheci.apkk.ui.component.ButtonDefaults
import com.cacheci.apkk.ui.component.Card
import com.cacheci.apkk.ui.component.EnumSelector
import com.cacheci.apkk.ui.component.SelectableText
import com.cacheci.apkk.ui.component.SimpleTextField
import com.cacheci.apkk.ui.component.Text
import com.cacheci.apkk.ui.component.TextButton
import com.cacheci.apkk.ui.component.TextButtonDefaults
import com.cacheci.apkk.ui.component.VisualBox
import com.cacheci.apkk.ui.theme.AppTheme
import com.cacheci.apkk.ui.theme.AppTheme.DefaultThemeValues
import com.cacheci.apkk.ui.theme.ColorSchemeMode
import com.cacheci.apkk.ui.theme.ThemeController
import com.cacheci.apkk.ui.resources.Res
import com.cacheci.apkk.ui.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.datatransfer.DataFlavor
import java.io.File
import kotlin.text.ifEmpty
import kotlin.to

@Composable
fun ApkViewerApp(
    platform: DesktopPlatform,
    externallyOpenedPath: String?,
    chooseFile: (title: String) -> String?,
) {
    var settings by remember { mutableStateOf(SettingsRepository.load()) }

    CompositionLocalProvider(
        LocalAppLocale provides when (settings.language) {
            AppLanguage.ZH_CN -> "zh-CN"
            AppLanguage.EN_US -> "en-US"
        }
    ) {
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
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun App(
    platform: DesktopPlatform,
    externallyOpenedPath: String?,
    chooseFile: (title: String) -> String?,
    settings: AppSettings,
    updateSettings: (AppSettings) -> Unit,
) {
    val parseFailedText = stringResource(Res.string.parse_failed)
    val installSuccessText = stringResource(Res.string.install_success)
    val installFailedText = stringResource(Res.string.install_failed)
    val noAvailableDeviceText = stringResource(Res.string.no_available_device)
    val chooseApkText = stringResource(Res.string.choose_apk)
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<ApkInfo?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var loadingDevices by remember { mutableStateOf(false) }
    var availableDevices by remember { mutableStateOf<List<AdbDevice>>(emptyList()) }
    var selectedDeviceIndex by remember { mutableStateOf(0) }
    var deviceSelectionVisible by remember { mutableStateOf(false) }
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
                .onFailure { dialog = parseFailedText to (it.message ?: it.toString()) }
            parsing = false
        }
    }

    fun loadDevices() {
        loadingDevices = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { AdbInstaller.listDevices(settings.adbPath, platform) }
                    .filter { it.state == "device" }
            }.onSuccess { devices ->
                if (devices.isEmpty()) {
                    deviceSelectionVisible = false
                    dialog = installFailedText to noAvailableDeviceText
                } else {
                    availableDevices = devices
                    selectedDeviceIndex = 0
                    deviceSelectionVisible = true
                }
            }.onFailure {
                deviceSelectionVisible = false
                dialog = installFailedText to (it.message ?: it.toString())
            }
            loadingDevices = false
        }
    }

    fun install(device: AdbDevice) {
        val apk = info ?: return
        deviceSelectionVisible = false
        installing = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    AdbInstaller.install(apk.path, settings.adbPath, platform, device.serial)
                }
            }.onSuccess { dialog = installSuccessText to it }
                .onFailure { dialog = installFailedText to (it.message ?: it.toString()) }
            installing = false
        }
    }

    val dropTarget = remember(::load) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val files = event.awtTransferable
                    .getTransferData(DataFlavor.javaFileListFlavor) as? List<*> ?: return false
                val file = files.filterIsInstance<File>()
                    .firstOrNull { isSupportedFile(it.absolutePath) } ?: return false
                load(file.absolutePath)
                return true
            }
        }
    }

    LaunchedEffect(externallyOpenedPath) {
        externallyOpenedPath?.takeIf(String::isNotBlank)?.let(::load)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .dragAndDropTarget(
                shouldStartDragAndDrop = {
                    it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                },
                target = dropTarget,
            )
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(stringResource(Res.string.title), style = AppTheme.textStyles.h1, fontWeight = FontWeight.Medium)
            }
            TextButton(
                text = when {
                    installing -> stringResource(Res.string.installing)
                    else -> stringResource(Res.string.install)
                },
                enabled = info != null && !installing && !loadingDevices,
                onClick = ::loadDevices,
            )
            TextButton(
                text = if (settingsVisible) stringResource(Res.string.back) else stringResource(Res.string.settings),
                onClick = { settingsVisible = !settingsVisible }
            )
        }

        Card {
            Column {
                DropGrid(
                    info = info,
                    parsing = parsing,
                    onClick = { chooseFile(chooseApkText)?.let(::load) },
                )

                when {
                    settingsVisible -> SettingsPanel(settings, updateSettings)
                    info != null -> InfoGrid(info!!)
                }
            }
        }
    }

    dialog?.let { (title, message) ->
        DialogWindow(
            onCloseRequest = { dialog = null },
            title = title,
            state = rememberDialogState(
                width = 200.dp,
                height = 160.dp,
                position = WindowPosition(Alignment.Center),
            ),
            resizable = false,
        ) {
            Column (
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(message)
                TextButton(text = stringResource(Res.string.confirm), onClick = { dialog = null })
            }
        }
    }

    if (deviceSelectionVisible && availableDevices.isNotEmpty()) {
        val dialogWindowSizeState = rememberDialogState(
            width = 440.dp,
            height = 260.dp,
            position = WindowPosition(Alignment.Center),
        )

        LaunchedEffect(dialogWindowSizeState.size) {
            val size = dialogWindowSizeState.size

            val width = maxOf(size.width, 440.dp)
            val height = maxOf(size.height, 260.dp)

            if (size.width != width || size.height != height) {
                dialogWindowSizeState.size = DpSize(width, height)
            }
        }

        com.cacheci.apkk.ui.component.DialogWindow(
            onCloseRequest = {
                deviceSelectionVisible = false
            },
            title = stringResource(Res.string.select_device),
            state = dialogWindowSizeState,
            minSize = DpSize(220.dp,140.dp),
            resizable = true,
        ) {
            Box (modifier = Modifier.fillMaxSize().background(AppTheme.colorScheme.firstContainer)) {
                Column(
                    modifier = Modifier
                        .padding(DefaultThemeValues.cardInsidePadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VisualBox {
                        Column(
                            modifier = Modifier
                                .background(
                                    color = AppTheme.colorScheme.secondContainer,
                                ),
                        ) {
                            availableDevices.forEach {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            onClick = { install(it) }
                                        )
                                        .padding(DefaultThemeValues.cardInsidePadding),
                                ) {
                                    if (!it.model.isNullOrEmpty()) {
                                        Text(it.model!!)
                                        Text(
                                            it.serial,
                                            color = AppTheme.colorScheme.summary,
                                            style = AppTheme.textStyles.summary
                                        )
                                    } else {
                                        Text(it.serial)
                                    }
                                }
                            }
                        }
                    }
                    Row (
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            text = stringResource(Res.string.cancel),
                            onClick = { deviceSelectionVisible = false },
                        )
                        TextButton(
                            text = stringResource(Res.string.refresh_devices),
                            enabled = !loadingDevices,
                            onClick = ::loadDevices,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun DropGrid(
    info: ApkInfo?,
    parsing: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember {
        MutableInteractionSource()
    }
    val isPressed by interactionSource.collectIsPressedAsState()

    VisualBox(
        modifier = Modifier.fillMaxWidth(),
        visualFeedback = isPressed,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = onClick,
                    interactionSource = interactionSource,
                )
                .padding(DefaultThemeValues.cardInsidePadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ApkIcon(info)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row {
                    Text(
                        modifier = Modifier.padding(start = 6.dp),
                        text = when {
                            parsing -> stringResource(Res.string.parsing)
                            info != null -> info.resolvedAppLabel.ifEmpty { info.fileName }
                            else -> stringResource(Res.string.drop_hint)
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    info?.let {
                        Text(
                            modifier = Modifier.padding(start = 12.dp),
                            text = it.packageName,
                            color = AppTheme.colorScheme.summary
                        )
                    }
                }
                if (info != null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        info.techFeatures.forEach { feature ->
                            TextButton (
                                text = feature.name,
                                textStyle = AppTheme.textStyles.main,
                                colors = TextButtonDefaults.textButtonColors(
                                    button = ButtonDefaults.buttonColors(
                                        background = AppTheme.colorScheme.secondContainer,
                                        border = AppTheme.colorScheme.secondBorder,
                                    ),
                                ),
                                onClick = {}
                            )
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
    val defaultIconPainter = painterResource(Res.drawable.icon_droid)
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
            else -> Image(defaultIconPainter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Inside)
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
private fun InfoGrid(info: ApkInfo) {
    var currentCard by remember { mutableStateOf(0) }

    VisualBox {
        Column (modifier = Modifier.heightIn(min = 240.dp)) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(
                        AppTheme.colorScheme.secondContainer,
                    )
                    .padding(horizontal = 8.dp)
                    .offset(y = 2.dp)
                    .zIndex(1f),
                verticalArrangement = Arrangement.Bottom,
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                AppInfoCard.entries.forEachIndexed { index, card ->
                    val selected = currentCard == index
                    val interactionSource = remember {
                        MutableInteractionSource()
                    }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    VisualBox (
                        modifier = Modifier
                            .clickable(
                                onClick = {
                                    currentCard = index
                                },
                                interactionSource = interactionSource
                            ).background(
                                if (selected) AppTheme.colorScheme.primary else Color.Transparent,
                            ),
                        effectiveWidth = PaddingValues(
                            top = 2.dp, start = 2.dp, end = 2.dp,
                            bottom = if (selected) 0.dp else 2.dp
                        ),
                        visualFeedback = isPressed
                    ) {
                        Text(
                            text = card.localizedTitle(),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
            AnimatedContent(
                modifier = Modifier.fillMaxWidth(),
                targetState = currentCard,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it }) togetherWith
                                (slideOutHorizontally { -it })
                    } else {
                        (slideInHorizontally { -it }) togetherWith
                                (slideOutHorizontally { it })
                    }
                },
                label = "MainPageTransition",
            ) { card ->
                VisualBox (visualFeedback = true) {
                    when (AppInfoCard.entries[card]) {
                        AppInfoCard.BASIC ->
                            Column(
                                Modifier.fillMaxWidth().padding(
                                    horizontal = DefaultThemeValues.cardInsidePadding,
                                    vertical = 8.dp,
                                )
                            ) {
                                val rows = listOf(
                                    stringResource(Res.string.file_name) to info.fileName,
                                    stringResource(Res.string.file_size) to info.size,
                                    stringResource(Res.string.package_name) to info.packageName,
                                    stringResource(Res.string.app_name) to info.resolvedAppLabel.ifEmpty { info.appLabel },
                                    stringResource(Res.string.version_name) to info.versionName,
                                    stringResource(Res.string.version_code) to info.versionCode,
                                    stringResource(Res.string.min_sdk) to sdkLabel(info.minSdk),
                                    stringResource(Res.string.target_sdk) to sdkLabel(info.targetSdk),
                                    stringResource(Res.string.compile_sdk) to sdkLabel(info.compileSdk),
                                    stringResource(Res.string.languages) to info.supportedLanguages.joinToString(
                                        ", "
                                    ).ifEmpty { stringResource(Res.string.default_value) },
                                    stringResource(Res.string.debuggable) to info.debuggable.ifEmpty { stringResource(Res.string.debug_default) },
                                    stringResource(Res.string.file_count) to info.fileCount.toString(),
                                    stringResource(Res.string.abi) to info.abis.joinToString(", ")
                                        .ifEmpty { stringResource(Res.string.no_abi) },
                                    stringResource(Res.string.signatures) to info.signatures.joinToString(
                                        ", "
                                    ).ifEmpty { stringResource(Res.string.no_signature) },
                                )
                                rows.forEach { (name, value) ->
                                    DetailRow(
                                        title = name,
                                        detail = value,
                                    )
                                }
                            }

                        AppInfoCard.SIGNATURE ->
                            if (info.signatureDetails.isEmpty()) {
                                Text(
                                    stringResource(Res.string.no_signature),
                                    modifier = Modifier.padding(
                                        horizontal = DefaultThemeValues.cardInsidePadding,
                                    ),
                                )
                            } else {
                                var currentSig by remember(info) { mutableStateOf(0) }

                                Column(modifier = Modifier.heightIn(min = 240.dp).padding(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .background(color = AppTheme.colorScheme.secondContainer)
                                            .padding(horizontal = 12.dp)
                                            .offset(y = 2.dp)
                                            .zIndex(1f),
                                    ) {
                                        info.signatureDetails.forEachIndexed { index, signature ->
                                            val selected = currentSig == index
                                            val interactionSource = remember {
                                                MutableInteractionSource()
                                            }
                                            val isPressed by interactionSource.collectIsPressedAsState()

                                            VisualBox (
                                                visualFeedback = isPressed,
                                                effectiveWidth = PaddingValues(
                                                    start = 2.dp, end = 2.dp, top = 2.dp,
                                                    bottom = if (selected) 0.dp else 2.dp
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .height(IntrinsicSize.Min)
                                                        .clickable(
                                                            onClick = { currentSig = index },
                                                            interactionSource = interactionSource,
                                                        ),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                        text = signature.scheme,
                                                        color = AppTheme.colorScheme.element,
                                                        modifier = Modifier
                                                            .padding(8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    VisualBox (visualFeedback = true) {
                                        AnimatedContent(
                                            modifier = Modifier
                                                .heightIn(min = 30.dp)
                                                .fillMaxWidth(),
                                            targetState = currentSig,
                                            transitionSpec = {
                                                if (targetState > initialState) {
                                                    (slideInHorizontally { it }) togetherWith
                                                            (slideOutHorizontally { -it })
                                                } else {
                                                    (slideInHorizontally { -it }) togetherWith
                                                            (slideOutHorizontally { it })
                                                }
                                            },
                                        ) {
                                            val currentSig = info.signatureDetails[currentSig]
                                            Column (
                                                modifier = Modifier.padding(
                                                    horizontal = DefaultThemeValues.cardInsidePadding,
                                                )
                                            ) {
                                                DetailRow(
                                                    stringResource(Res.string.signature_verification),
                                                    if (currentSig.verificationSuccessful == true) {
                                                        stringResource(Res.string.signature_verification_success)
                                                    } else {
                                                        stringResource(Res.string.signature_verification_failed)
                                                    },
                                                    detailColor = if (currentSig.verificationSuccessful == true) AppTheme.colorScheme.success else AppTheme.colorScheme.error
                                                )

                                                currentSig.signatureAlgorithmId?.let {
                                                    var detail = "0x${it.toString(16)}"
                                                    currentSig.algorithm?.let {
                                                        detail += " (${currentSig.algorithm})"
                                                    }
                                                    DetailRow(
                                                        title = stringResource(Res.string.signature_algorithm),
                                                        detail = detail
                                                    )
                                                }

                                                currentSig.publicKeyFormat?.let {
                                                    DetailRow(
                                                        title = stringResource(Res.string.signature_public_key),
                                                        detail = "$it ${currentSig.publicKeyAlgorithm} (${currentSig.publicKeyAlgorithmOid})"
                                                    )
                                                }

                                                currentSig.issuer?.let {
                                                    DetailRow(
                                                        stringResource(Res.string.signature_issuer),
                                                        it
                                                    )
                                                }

                                                currentSig.subject?.let {
                                                    DetailRow(
                                                        stringResource(Res.string.signature_subject),
                                                        it
                                                    )
                                                }

                                                DetailRow(
                                                    stringResource(Res.string.signature_valid_duration),
                                                    "${currentSig.validFrom} -> ${currentSig.validUntil}",
                                                    detailColor = if (currentSig.certificateValidNow == true) AppTheme.colorScheme.success else AppTheme.colorScheme.error
                                                )

                                                if (currentSig.certificateSha256.isNotEmpty()) {
                                                    DetailRow(
                                                        "SHA-256",
                                                        currentSig.certificateSha256.joinToString(", ")
                                                    )
                                                }
                                                if (currentSig.certificateSha1.isNotEmpty()) {
                                                    DetailRow(
                                                        "SHA-1",
                                                        currentSig.certificateSha1.joinToString(", ")
                                                    )
                                                }

                                                if (currentSig.errors.isNotEmpty()) {
                                                    DetailRow(
                                                        stringResource(Res.string.signature_error),
                                                        currentSig.errors.joinToString(", "),
                                                        titleColor = AppTheme.colorScheme.error
                                                    )
                                                }

                                                if (currentSig.warnings.isNotEmpty()) {
                                                    DetailRow(
                                                        stringResource(Res.string.signature_warning),
                                                        currentSig.warnings.joinToString(", "),
                                                        titleColor = AppTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }


                        AppInfoCard.PERMISSION ->
                            Column(
                                Modifier.fillMaxWidth().padding(
                                    horizontal = DefaultThemeValues.cardInsidePadding,
                                    vertical = 8.dp,
                                )
                            ) {
                                SelectableText(
                                    info.permissions.joinToString("\n")
                                        .ifEmpty { stringResource(Res.string.no_permission) },
                                    style = AppTheme.textStyles.ref,
                                    fontFamily = AppTheme.monospaceFontFamily,
                                )
                            }

                        AppInfoCard.LIB -> {
                            val libArch = mutableListOf<Pair<List<String>, String>>()

                            val arm64Lib =
                                info.nativeLibs.filter { it.startsWith("lib/arm64-v8a/") }
                                    .map { it.substring(14) }
                            val armeabiLib =
                                info.nativeLibs.filter { it.startsWith("lib/armeabi-v7a/") }
                                    .map { it.substring(16) }
                            val amd64Lib =
                                info.nativeLibs.filter { it.startsWith("lib/x86_64/") }
                                    .map { it.substring(11) }
                            val x86Lib = info.nativeLibs.filter { it.startsWith("lib/x86/") }
                                .map { it.substring(8) }

                            if (arm64Lib.isNotEmpty()) libArch += arm64Lib to "arm64-v8a"
                            if (armeabiLib.isNotEmpty()) libArch += armeabiLib to "armeabi-v7a"
                            if (amd64Lib.isNotEmpty()) libArch += amd64Lib to "x86_64"
                            if (x86Lib.isNotEmpty()) libArch += x86Lib to "x86"

                            if (libArch.any { it.first.isNotEmpty() }) {
                                    Column(modifier = Modifier.heightIn(min = 240.dp).padding(4.dp)) {
                                        var currentArch by remember(info) { mutableStateOf(0) }

                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .height(IntrinsicSize.Min)
                                                .background(
                                                    color = AppTheme.colorScheme.secondContainer,
                                                )
                                                .padding(horizontal = 12.dp)
                                                .offset(y = 2.dp)
                                                .zIndex(1f),
                                        ) {
                                            libArch.forEachIndexed { index, arch ->
                                                val selected = currentArch == index
                                                val interactionSource = remember {
                                                    MutableInteractionSource()
                                                }
                                                val isPressed by interactionSource.collectIsPressedAsState()

                                                VisualBox (
                                                    visualFeedback = isPressed,
                                                    effectiveWidth = PaddingValues(
                                                        start = 2.dp, end = 2.dp, top = 2.dp,
                                                        bottom = if (selected) 0.dp else 2.dp
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .height(IntrinsicSize.Min)
                                                            .clickable(
                                                                onClick = {
                                                                    currentArch = libArch.indexOf(arch)
                                                                },
                                                                interactionSource = interactionSource,
                                                            ),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Text(
                                                            text = arch.second,
                                                            color = AppTheme.colorScheme.element,
                                                            modifier = Modifier
                                                                .padding(8.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        VisualBox (visualFeedback = true) {
                                            AnimatedContent(
                                                modifier = Modifier
                                                    .heightIn(min = 30.dp)
                                                    .fillMaxWidth(),
                                                targetState = currentArch,
                                                transitionSpec = {
                                                    if (targetState > initialState) {
                                                        (slideInHorizontally { it }) togetherWith
                                                                (slideOutHorizontally { -it })
                                                    } else {
                                                        (slideInHorizontally { -it }) togetherWith
                                                                (slideOutHorizontally { it })
                                                    }
                                                },
                                            ) { index ->
                                                SelectableText(
                                                    text = libArch[index].first.joinToString("\n"),
                                                    modifier = Modifier.padding(
                                                        horizontal = DefaultThemeValues.cardInsidePadding,
                                                    ),
                                                    style = AppTheme.textStyles.ref,
                                                    fontFamily = AppTheme.monospaceFontFamily,
                                                )
                                            }
                                        }
                                    }

                            } else {
                                Text(
                                    text = stringResource(Res.string.no_native),
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    style = AppTheme.textStyles.ref
                                )
                            }
                        }

                        AppInfoCard.ACTIVITY ->
                            Column(
                                Modifier.fillMaxWidth().padding(
                                    horizontal = DefaultThemeValues.cardInsidePadding,
                                    vertical = 8.dp,
                                )
                            ) {
                                if (info.activities.isEmpty()) {
                                    Text(stringResource(Res.string.no_component))
                                }
                                info.activities.forEach {
                                    SelectableText(
                                        text = it,
                                        color = AppTheme.colorScheme.element,
                                        style = AppTheme.textStyles.summary,
                                        fontFamily = AppTheme.monospaceFontFamily,
                                    )
                                }
                            }

                        AppInfoCard.SERVICE ->
                            Column(
                                Modifier.fillMaxWidth().padding(
                                    horizontal = DefaultThemeValues.cardInsidePadding,
                                    vertical = 8.dp,
                                )
                            ) {
                                if (info.services.isEmpty()) {
                                    Text(stringResource(Res.string.no_component))
                                }
                                info.services.forEach {
                                    SelectableText(
                                        text = it,
                                        color = AppTheme.colorScheme.element,
                                        style = AppTheme.textStyles.summary,
                                        fontFamily = AppTheme.monospaceFontFamily,
                                    )
                                }
                            }

                        AppInfoCard.PROVIDER ->
                            Column(
                                Modifier.fillMaxWidth().fillMaxSize().padding(
                                    horizontal = DefaultThemeValues.cardInsidePadding,
                                    vertical = 8.dp,
                                )
                            ) {
                                if (info.providers.isEmpty()) {
                                    Text(stringResource(Res.string.no_component))
                                }
                                info.providers.forEach {
                                    SelectableText(
                                        text = it,
                                        color = AppTheme.colorScheme.element,
                                        style = AppTheme.textStyles.summary,
                                        fontFamily = AppTheme.monospaceFontFamily,
                                    )
                                }
                            }

                        AppInfoCard.RECEIVER ->
                            Column(
                                Modifier.fillMaxWidth().padding(
                                    horizontal = DefaultThemeValues.cardInsidePadding,
                                    vertical = 8.dp,
                                )
                            ) {
                                if (info.receivers.isEmpty()) {
                                    Text(stringResource(Res.string.no_component))
                                }
                                info.receivers.forEach {
                                    SelectableText(
                                        text = it,
                                        color = AppTheme.colorScheme.element,
                                        style = AppTheme.textStyles.summary,
                                        fontFamily = AppTheme.monospaceFontFamily,
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
private fun SettingsPanel(settings: AppSettings, update: (AppSettings) -> Unit) {
    val languages = listOf(
        stringResource(Res.string.language_zh_cn),
        stringResource(Res.string.language_en_us),
    )
    val themes = listOf(
        stringResource(Res.string.theme_light),
        stringResource(Res.string.theme_dark),
        stringResource(Res.string.theme_system),
    )
    VisualBox(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            EnumSelector(
                label = stringResource(Res.string.language),
                selected = settings.language.ordinal,
                values = languages,
                onValueChange= { index ->
                    update(settings.copy(language = AppLanguage.entries[index]))
                }
            )
            EnumSelector(
                stringResource(Res.string.theme),
                settings.theme.ordinal,
                themes,
            ) { index ->
                update(settings.copy(theme = AppColorTheme.entries[index]))
            }
            SimpleTextField(
                value = settings.adbPath,
                onValueChange = { update(settings.copy(adbPath = it)) },
                label = stringResource(Res.string.adb_path),
                placeholder = stringResource(Res.string.adb_path_hint),
                inBoxAlignment = Alignment.Center,
                singleLine = true,
            )
        }
    }
}

@Composable
private fun DetailRow(
    title: String,
    detail: String,
    titleColor: Color? = null,
    detailColor: Color? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            title,
            Modifier.width(116.dp),
            color = titleColor ?: AppTheme.colorScheme.element,
            style = AppTheme.textStyles.summary
        )
        SelectableText(
            detail.ifEmpty { stringResource(Res.string.undeclared) },
            Modifier.weight(1f),
            color = detailColor ?: Color.Unspecified,
            style = AppTheme.textStyles.summary,
            fontFamily = AppTheme.monospaceFontFamily,
        )
    }
}

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
    SIGNATURE,
    PERMISSION,
    LIB,
    ACTIVITY,
    SERVICE,
    PROVIDER,
    RECEIVER,
}

private val AppInfoCard.displayName: StringResource
    get() = when (this) {
        AppInfoCard.BASIC -> Res.string.tab_basic
        AppInfoCard.SIGNATURE -> Res.string.tab_signature
        AppInfoCard.PERMISSION -> Res.string.tab_permission
        AppInfoCard.LIB -> Res.string.tab_lib
        AppInfoCard.ACTIVITY -> Res.string.tab_activity
        AppInfoCard.SERVICE -> Res.string.tab_service
        AppInfoCard.PROVIDER -> Res.string.tab_provider
        AppInfoCard.RECEIVER -> Res.string.tab_receiver
    }

@Composable
internal fun AppInfoCard.localizedTitle(): String = stringResource(displayName)
