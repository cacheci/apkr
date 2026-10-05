package com.cacheci.apkk.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposableOpenTarget
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.DialogModalityType
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.cacheci.apkk.ui.component.WindowDecoration.Companion.Undecorated
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Dialog
import java.awt.Dialog.ModalityType
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.awt.event.WindowListener
import java.util.Locale
import javax.swing.JDialog
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
@ComposableOpenTarget(-1)
fun DialogWindow(
    onCloseRequest: () -> Unit,
    state: DialogState = rememberDialogState(),
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    undecorated: Boolean = false,
    transparent: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    minSize: DpSize = DpSize(0.dp, 0.dp),
    onPreviewKeyEvent: ((KeyEvent) -> Boolean) = { false },
    onKeyEvent: ((KeyEvent) -> Boolean) = { false },
    content: @Composable DialogWindowScope.() -> Unit
) {
    DialogWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        visible = visible,
        title = title,
        icon = icon,
        decoration = windowDecorationFromFlag(undecorated),
        transparent = transparent,
        resizable = resizable,
        enabled = enabled,
        focusable = focusable,
        alwaysOnTop = alwaysOnTop,
        minSize = minSize,
        modalityType = DialogModalityType.DocumentModal,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        content = content,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun windowDecorationFromFlag(undecorated: Boolean): WindowDecoration =
    if (undecorated) Undecorated() else WindowDecoration.SystemDefault

@ExperimentalComposeUiApi
@Composable
@ComposableOpenTarget(-1)
fun DialogWindow(
    onCloseRequest: () -> Unit,
    state: DialogState = rememberDialogState(),
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    decoration: WindowDecoration = WindowDecoration.SystemDefault,
    transparent: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    minSize: DpSize = DpSize(0.dp, 0.dp),
    modalityType: DialogModalityType,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean) = { false },
    onKeyEvent: ((KeyEvent) -> Boolean) = { false },
    content: @Composable DialogWindowScope.() -> Unit
) {
    SwingDialog(
        onCloseRequest = onCloseRequest,
        state = state,
        visible = visible,
        title = title,
        icon = icon,
        decoration = decoration,
        transparent = transparent,
        resizable = resizable,
        enabled = enabled,
        focusable = focusable,
        alwaysOnTop = alwaysOnTop,
        minSize = minSize,
        modalityType = modalityType.toAwtModalityType(),
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        init = { },
        content = content,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun DialogModalityType.toAwtModalityType(): ModalityType = when (this) {
    DialogModalityType.Modeless -> ModalityType.MODELESS
    DialogModalityType.DocumentModal -> ModalityType.DOCUMENT_MODAL
    DialogModalityType.ApplicationModal -> ModalityType.APPLICATION_MODAL
    else -> error("Unknown dialog modality type: $this")
}

@ExperimentalComposeUiApi
@Composable
@ComposableOpenTarget(-1)
fun SwingDialog(
    onCloseRequest: () -> Unit,
    state: DialogState = rememberDialogState(),
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    decoration: WindowDecoration = WindowDecoration.SystemDefault,
    transparent: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    minSize: DpSize = DpSize(0.dp, 0.dp),
    onPreviewKeyEvent: ((KeyEvent) -> Boolean) = { false },
    onKeyEvent: ((KeyEvent) -> Boolean) = { false },
    modalityType: ModalityType = ModalityType.DOCUMENT_MODAL,
    init: (ComposeDialog) -> Unit,
    content: @Composable DialogWindowScope.() -> Unit
) {
    val owner = LocalAwtWindow.current

    val currentState by rememberUpdatedState(state)
    val currentTitle by rememberUpdatedState(title)
    val currentIcon by rememberUpdatedState(icon)
    val currentDecoration by rememberUpdatedState(decoration)
    val currentTransparent by rememberUpdatedState(transparent)
    val currentResizable by rememberUpdatedState(resizable)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentFocusable by rememberUpdatedState(focusable)
    val currentAlwaysOnTop by rememberUpdatedState(alwaysOnTop)
    val currentModalityType by rememberUpdatedState(modalityType)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)

    val updater = remember(::ComponentUpdater)

    val density = LocalDensity.current
    val currentMinSize by rememberUpdatedState(minSize)

    // the state applied to the dialog. exist to avoid races between DialogState changes and the state stored inside the native dialog
    val appliedState = remember {
        object {
            var size: DpSize? = null
            var position: WindowPosition? = null
        }
    }

    val listeners = remember {
        object {
            var windowListenerRef = windowListenerRef()
            var componentListenerRef = componentListenerRef()

            fun removeFromAndClear(window: ComposeDialog) {
                windowListenerRef.unregisterFromAndClear(window)
                componentListenerRef.unregisterFromAndClear(window)
            }
        }
    }

    val coroutineContext = rememberCoroutineScope().coroutineContext

    androidx.compose.ui.awt.SwingDialog(
        visible = visible,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        create = {
            val graphicsConfiguration = WindowLocationTracker.lastActiveGraphicsConfiguration
            val dialog = if (owner != null) {
                ComposeDialog(
                    owner = owner,
                    modalityType = currentModalityType,
                    graphicsConfiguration = graphicsConfiguration,
                    coroutineContext = coroutineContext
                )
            } else {
                ComposeDialog(
                    graphicsConfiguration = graphicsConfiguration,
                    coroutineContext = coroutineContext
                )
            }
            dialog.apply {
                // close state is controlled by DialogState.isOpen
                defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
                listeners.windowListenerRef.registerWithAndSet(
                    this,
                    object : WindowAdapter() {
                        override fun windowClosing(e: WindowEvent?) {
                            currentOnCloseRequest()
                        }
                    }
                )
                listeners.componentListenerRef.registerWithAndSet(
                    this,
                    object : ComponentAdapter() {
                        override fun componentResized(e: ComponentEvent) {
                            currentState.size = DpSize(width.dp, height.dp)
                            appliedState.size = currentState.size
                        }

                        override fun componentMoved(e: ComponentEvent) {
                            currentState.position = WindowPosition(x.dp, y.dp)
                            appliedState.position = currentState.position
                        }
                    }
                )
                WindowLocationTracker.onWindowCreated(this)

                init(dialog)
            }
        },
        dispose = {
            WindowLocationTracker.onWindowDisposed(it)
            // We need to remove them because AWT can still call them after dispose()
            listeners.removeFromAndClear(it)
            it.dispose()
        },
        update = { dialog ->
            updater.update {
                set(currentTitle, dialog::setTitle)
                set(currentIcon, dialog::setIcon)
                set(currentDecoration is UndecoratedWindowDecoration, dialog::setUndecoratedSafely)
                set(currentTransparent, dialog::isTransparent::set)
                set(currentResizable, dialog::setResizable)
                set(currentEnabled, dialog::setEnabled)
                set(currentFocusable, dialog::setFocusableWindowState)
                set(currentAlwaysOnTop, dialog::setAlwaysOnTop)
                set(currentModalityType, dialog::setModalityType)
                set(currentDecoration.resizerThickness, dialog::undecoratedResizerThickness::set)
            }

            dialog.minimumSize = with(density) {
                Dimension(
                    currentMinSize.width.roundToPx(),
                    currentMinSize.height.roundToPx()
                )
            }

            if (state.size != appliedState.size) {
                dialog.setSizeSafely(state.size, WindowPlacement.Floating)
                appliedState.size = state.size
            }
            if (state.position != appliedState.position) {
                dialog.setPositionSafely(
                    state.position,
                    WindowPlacement.Floating,
                    platformDefaultPosition = { WindowLocationTracker.getCascadeLocationFor(dialog) }
                )
                appliedState.position = state.position
            }
        },
        content = content
    )
}

internal class ComponentUpdater {
    private var updatedValues = mutableListOf<Any?>()

    fun update(body: UpdateScope.() -> Unit) {
        UpdateScope().body()
    }

    inner class UpdateScope {
        private var index = 0

        /**
         * Compare [value] with the old one and if it is changed - store a new value and call
         * [update]
         */
        fun <T> set(value: T, update: (T) -> Unit) {
            if (index < updatedValues.size) {
                if (updatedValues[index] != value) {
                    update(value)
                    updatedValues[index] = value
                }
            } else {
                check(index == updatedValues.size)
                update(value)
                updatedValues.add(value)
            }

            index++
        }
    }
}

internal class ListenerOnWindowRef<T>(
    private val register: Window.(T) -> Unit,
    private val unregister: Window.(T) -> Unit
) {
    private var value: T? = null

    fun registerWithAndSet(window: Window, listener: T) {
        window.register(listener)
        value = listener
    }

    fun unregisterFromAndClear(window: Window) {
        value?.let {
            window.unregister(it)
            value = null
        }
    }
}

internal fun windowListenerRef() = ListenerOnWindowRef<WindowListener>(
    register = Window::addWindowListener,
    unregister = Window::removeWindowListener
)

internal fun componentListenerRef() = ListenerOnWindowRef<ComponentListener>(
    register = Component::addComponentListener,
    unregister = Component::removeComponentListener
)

internal object WindowLocationTracker {
    private val cascadeOffset = Point(48, 48)

    private var windowsOrderedByLastFocused = mutableSetOf<Window>()

    private val focusListener = object : WindowFocusListener {
        override fun windowGainedFocus(e: WindowEvent) {
            // put window on the top of the set
            windowsOrderedByLastFocused.remove(e.window)
            windowsOrderedByLastFocused.add(e.window)
        }

        override fun windowLostFocus(e: WindowEvent) = Unit
    }

    fun onWindowCreated(window: Window) {
        window.addWindowFocusListener(focusListener)
    }

    fun onWindowDisposed(window: Window) {
        window.removeWindowFocusListener(focusListener)
        windowsOrderedByLastFocused.remove(window)
    }

    val lastActiveGraphicsConfiguration: GraphicsConfiguration? get() =
        windowsOrderedByLastFocused.lastOrNull()?.graphicsConfiguration

    fun getCascadeLocationFor(window: Window): Point {
        return getCascadeLocationFor(
            graphicsDevice = window.graphicsConfiguration.device,
            windowSize = window.size
        )
    }

    fun getCascadeLocationFor(
        graphicsDevice: GraphicsDevice,
        windowSize: Dimension
    ): Point {
        val lastFocusedWindow = windowsOrderedByLastFocused.lastOrNull {
            it.graphicsConfiguration.device == graphicsDevice
        }

        val graphicsConfiguration = graphicsDevice.defaultConfiguration
        val screenBounds = graphicsConfiguration.bounds
        val screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration)
        val screenLeftTop = screenBounds.topLeft + Point(screenInsets.left, screenInsets.top)
        val screenBottomRight = screenBounds.bottomRight - Point(screenInsets.right, screenInsets.bottom)

        val lastLocation = lastFocusedWindow?.location ?: screenLeftTop
        var location = lastLocation + cascadeOffset
        val bottomRight = location + windowSize.bottomRight
        if (bottomRight.x > screenBottomRight.x || bottomRight.y > screenBottomRight.y) {
            location = screenLeftTop + cascadeOffset
        }
        return location
    }
}

internal fun Window.setIcon(painter: Painter?) {
    setIconImage(painter?.toAwtImage(density, layoutDirectionFor(this), iconSize))
}

private val iconSize = Size(192f, 192f)

internal fun layoutDirectionFor(component: Component): LayoutDirection {
    val orientation = component.componentOrientation
    return if (orientation != ComponentOrientation.UNKNOWN) {
        orientation.layoutDirection
    } else {
        // To preserve backwards compatibility we fall back to the locale
        component.locale.layoutDirection
    }
}

internal val Locale.layoutDirection: LayoutDirection
    get() = ComponentOrientation.getOrientation(this).layoutDirection

internal val ComponentOrientation.layoutDirection: LayoutDirection
    get() = when {
        isLeftToRight -> LayoutDirection.Ltr
        isHorizontal -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }

private val GraphicsConfiguration.density: Density get() = Density(
    defaultTransform.scaleX.toFloat(),
    fontScale = 1f
)

internal val Component.density: Density get() = graphicsConfiguration.density

internal val Dimension.bottomRight get() = Point(width, height)
internal operator fun Point.plus(other: Point) = Point(x + other.x, y + other.y)
internal operator fun Point.minus(other: Point) = Point(x - other.x, y - other.y)

internal val Rectangle.topLeft get() = Point(x, y)
internal val Rectangle.bottomRight get() = Point(x + width, y + height)


@OptIn(ExperimentalComposeUiApi::class)
@Immutable
internal class UndecoratedWindowDecoration(val resizerThickness: Dp): WindowDecoration {
    override fun equals(other: Any?): Boolean {
        if (other !is UndecoratedWindowDecoration) return false
        return other.resizerThickness == resizerThickness
    }

    override fun hashCode(): Int {
        return resizerThickness.hashCode()
    }
}

@ExperimentalComposeUiApi
sealed interface WindowDecoration {

    /**
     * Specifies that the default system decoration should be used.
     */
    data object SystemDefault : WindowDecoration

    companion object {
        /**
         * Specifies that the window should be undecorated.
         *
         * If it is resizable, the given thickness will be used for the edge resizers.
         */
        fun Undecorated(
            resizerThickness: Dp = WindowDecorationDefaults.ResizerThickness
        ) : WindowDecoration {
            return UndecoratedWindowDecoration(resizerThickness)
        }
    }
}

@ExperimentalComposeUiApi
object WindowDecorationDefaults {
    /**
     * The default thickness of the resizers in an undecorated window.
     */
    val ResizerThickness: Dp = 8.dp
}

internal fun Dialog.setUndecoratedSafely(value: Boolean) {
    if (this.isUndecorated != value) {
        this.isUndecorated = value
    }
}

@OptIn(ExperimentalComposeUiApi::class)
internal val WindowDecoration.resizerThickness: Dp
    get() = when {
        this is UndecoratedWindowDecoration -> resizerThickness
        else -> WindowDecorationDefaults.ResizerThickness
    }

internal fun Window.setSizeSafely(size: DpSize, placement: WindowPlacement) {
    if (!isVisible || (placement == WindowPlacement.Floating)) {
        setSizeImpl(size)
    }
}

private fun Window.setSizeImpl(size: DpSize) {
    val availableSize by lazy {
        val screenBounds = graphicsConfiguration.bounds
        val screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration)

        IntSize(
            width = screenBounds.width - screenInsets.left - screenInsets.right,
            height = screenBounds.height - screenInsets.top - screenInsets.bottom
        )
    }

    val isWidthSpecified = size.isSpecified && size.width.isSpecified
    val isHeightSpecified = size.isSpecified && size.height.isSpecified

    val width = if (isWidthSpecified) {
        size.width.value.roundToInt().coerceAtLeast(0)
    } else {
        availableSize.width
    }

    val height = if (isHeightSpecified) {
        size.height.value.roundToInt().coerceAtLeast(0)
    } else {
        availableSize.height
    }

    var computedPreferredSize: Dimension? = null
    if (!isWidthSpecified || !isHeightSpecified) {
        preferredSize = Dimension(width, height)
        pack()  // Makes it displayable

        // We set preferred size to null, and then call getPreferredSize, which will compute the
        // actual preferred size determined by the content (see the description of setPreferredSize)
        preferredSize = null
        computedPreferredSize = preferredSize
    }

    if (!isDisplayable) {
        // Pack to allow drawing the first frame
        preferredSize = Dimension(width, height)
        pack()
    }

    setSize(
        if (isWidthSpecified) width else computedPreferredSize!!.width,
        if (isHeightSpecified) height else computedPreferredSize!!.height,
    )
}

internal fun Window.setPositionSafely(
    position: WindowPosition,
    placement: WindowPlacement,
    platformDefaultPosition: () -> Point
) {
    if (!isVisible || (placement == WindowPlacement.Floating)) {
        setPositionImpl(position, platformDefaultPosition)
    }
}

internal fun Window.setPositionImpl(
    position: WindowPosition,
    platformDefaultPosition: () -> Point
) = when (position) {
    WindowPosition.PlatformDefault -> location = platformDefaultPosition()
    is WindowPosition.Aligned -> alignToScreen(position.alignment)
    is WindowPosition.Absolute -> setLocation(
        position.x.value.roundToInt(),
        position.y.value.roundToInt()
    )
}

internal fun Window.alignToScreen(alignment: Alignment) {
    location = locationAlignedToScreen(
        windowSize = IntSize(width, height),
        alignment = alignment
    )
}

internal fun Window.locationAlignedToScreen(windowSize: IntSize, alignment: Alignment): Point {
    val screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration)
    val screenBounds = graphicsConfiguration.bounds
    val screenSize = IntSize(
        screenBounds.width - screenInsets.left - screenInsets.right,
        screenBounds.height - screenInsets.top - screenInsets.bottom
    )
    val location = alignment.align(windowSize, screenSize, LayoutDirection.Ltr)
    return Point(
        screenBounds.x + screenInsets.left + location.x,
        screenBounds.y + screenInsets.top + location.y
    )
}