package com.ashuchat.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * Reusable WebRTC SurfaceViewRenderer wrapped inside Jetpack Compose.
 * Supports hardware scaling, aspect ratio fill/fit, mirroring, and overlay z-ordering.
 */
@Composable
fun WebRtcVideoView(
    videoTrack: VideoTrack?,
    eglContext: EglBase.Context?,
    modifier: Modifier = Modifier,
    isMirror: Boolean = false,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL,
    isOverlay: Boolean = false
) {
    if (eglContext == null) return

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglContext, null)
                setScalingType(scalingType)
                setMirror(isMirror)
                setEnableHardwareScaler(true)
                if (isOverlay) {
                    setZOrderMediaOverlay(true)
                }
                if (videoTrack != null) {
                    try {
                        videoTrack.addSink(this)
                        tag = videoTrack
                    } catch (e: Exception) {
                        android.util.Log.w("WebRtcVideoView", "Failed adding sink in factory: ${e.localizedMessage}")
                    }
                }
            }
        },
        update = { view ->
            view.setMirror(isMirror)
            view.setScalingType(scalingType)

            val currentAttachedTrack = view.tag as? VideoTrack
            if (currentAttachedTrack != videoTrack) {
                if (currentAttachedTrack != null) {
                    try {
                        currentAttachedTrack.removeSink(view)
                    } catch (e: Exception) {
                        android.util.Log.w("WebRtcVideoView", "Failed removing old sink: ${e.localizedMessage}")
                    }
                }
                if (videoTrack != null) {
                    try {
                        videoTrack.addSink(view)
                    } catch (e: Exception) {
                        android.util.Log.w("WebRtcVideoView", "Failed adding new sink: ${e.localizedMessage}")
                    }
                }
                view.tag = videoTrack
            }
        },
        onRelease = { view ->
            val currentAttachedTrack = view.tag as? VideoTrack ?: videoTrack
            if (currentAttachedTrack != null) {
                try {
                    currentAttachedTrack.removeSink(view)
                } catch (e: Exception) {
                    android.util.Log.w("WebRtcVideoView", "Error removing sink on release: ${e.localizedMessage}")
                }
            }
            try {
                view.release()
            } catch (e: Exception) {
                android.util.Log.w("WebRtcVideoView", "Error releasing renderer: ${e.localizedMessage}")
            }
            view.tag = null
        }
    )
}
