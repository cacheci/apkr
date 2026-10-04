package com.cacheci.apkk.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.cacheci.apkk.platform.DesktopPlatform
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.awt.datatransfer.DataFlavor
import java.io.File

object DesktopAppLauncher {
    fun launch(platform: DesktopPlatform, arguments: Array<String>) = application {
        var externallyOpenedPath by remember { mutableStateOf(arguments.firstOrNull(::isSupportedFile)) }

        Window(
            onCloseRequest = ::exitApplication,
            title = "APK Viewer",
            state = WindowState(size = DpSize(1120.dp, 760.dp)),
        ) {
            val composeWindow = window
            DisposableEffect(composeWindow) {
                composeWindow.minimumSize = java.awt.Dimension(760, 560)
                val dropTarget = installDropTarget(composeWindow) { externallyOpenedPath = it }
                val desktop = Desktop.getDesktop().takeIf { Desktop.isDesktopSupported() }
                if (platform == DesktopPlatform.MACOS && desktop?.isSupported(Desktop.Action.APP_OPEN_FILE) == true) {
                    desktop.setOpenFileHandler { event ->
                        event.files.firstOrNull { isSupportedFile(it.absolutePath) }
                            ?.let { externallyOpenedPath = it.absolutePath }
                    }
                }
                onDispose {
                    dropTarget.isActive = false
                    if (platform == DesktopPlatform.MACOS) runCatching { desktop?.setOpenFileHandler(null) }
                }
            }

            ApkViewerApp(
                platform = platform,
                externallyOpenedPath = externallyOpenedPath,
                chooseFile = { title -> chooseApk(composeWindow, title) },
            )
        }
    }

    private fun chooseApk(owner: ComposeWindow, title: String): String? {
        val dialog = FileDialog(owner, title, FileDialog.LOAD).apply {
            filenameFilter = java.io.FilenameFilter { _, name -> isSupportedFile(name) }
            isVisible = true
        }
        return dialog.file?.let { File(dialog.directory, it).absolutePath }
    }

    private fun installDropTarget(window: ComposeWindow, onFile: (String) -> Unit): DropTarget {
        return DropTarget(window, DnDConstants.ACTION_COPY, object : DropTargetAdapter() {
            override fun drop(event: DropTargetDropEvent) {
                runCatching {
                    event.acceptDrop(DnDConstants.ACTION_COPY)
                    @Suppress("UNCHECKED_CAST")
                    val files = event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                    files.firstOrNull { isSupportedFile(it.absolutePath) }?.absolutePath?.let(onFile)
                    event.dropComplete(true)
                }.onFailure { event.dropComplete(false) }
            }
        }, true)
    }

    private fun isSupportedFile(path: String): Boolean =
        path.endsWith(".apk", ignoreCase = true) || path.endsWith(".xml", ignoreCase = true) ||
            path.endsWith(".xxxxml", ignoreCase = true)
}
