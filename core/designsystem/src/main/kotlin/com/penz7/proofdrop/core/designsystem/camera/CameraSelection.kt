package com.penz7.proofdrop.core.designsystem.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Prefers the back camera but falls back to the front one. Rugged handhelds, some tablets
 * and emulators only expose a single camera, and binding a missing lens fails every capture.
 */
suspend fun CameraController.useBestAvailableCamera(context: Context) {
    suspendCancellableCoroutine { cont ->
        initializationFuture.addListener({ cont.resume(Unit) }, ContextCompat.getMainExecutor(context))
    }
    cameraSelector = when {
        hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
        hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
        else -> return
    }
}
