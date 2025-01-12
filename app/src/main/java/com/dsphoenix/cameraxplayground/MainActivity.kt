@file:OptIn(ExperimentalMaterial3Api::class)

package com.dsphoenix.cameraxplayground

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.contentValuesOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsphoenix.cameraxplayground.tensor.Classification
import com.dsphoenix.cameraxplayground.tensor.LandmarkClassifier
import com.dsphoenix.cameraxplayground.tensor.LandmarkImageAnalyzer
import com.dsphoenix.cameraxplayground.tensor.TfLiteLandmarkClassifier
import com.dsphoenix.cameraxplayground.ui.theme.CameraXPlaygroundTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private var recording: Recording? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set full-screen mode
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Hide status bar
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.statusBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (!hasRequiredPermissions()) {
            ActivityCompat.requestPermissions(
                this, CAMERAX_PERMISSIONS, 0
            )
        }

        setContent {
            CameraXPlaygroundTheme {
                val scaffoldState = rememberBottomSheetScaffoldState()
                val scope = rememberCoroutineScope()

                var classifications by remember {
                    mutableStateOf(emptyList<Classification>())
                }
                val analyzer = remember {
                    LandmarkImageAnalyzer(
                        classifier = TfLiteLandmarkClassifier(
                            context = applicationContext
                        ),
                        onResults = {
                            classifications = it
                        }
                    )
                }
                val controller = remember {
                    LifecycleCameraController(applicationContext).apply {
                        setEnabledUseCases(
                            CameraController.IMAGE_CAPTURE
                                    or CameraController.VIDEO_CAPTURE
                                    or CameraController.IMAGE_ANALYSIS
                        )
                        setImageAnalysisAnalyzer(
                            ContextCompat.getMainExecutor(applicationContext),
                            analyzer
                        )
                    }
                }

                val viewModel = viewModel<MainViewModel>()
                val bitmaps by viewModel.bitmaps.collectAsState()

                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 0.dp,
                    sheetContent = {
                        PhotoBottomSheetContent(
                            bitmaps = bitmaps,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        CameraPreview(
                            controller = controller,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = {
                                controller.cameraSelector = controller.flipCamera()
                            },
                            modifier = Modifier
                                .offset(16.dp, 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch camera"
                            )
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter),
                        ) {
                            classifications.forEach {
                                Text(
                                    text = it.name,
                                    fontSize = 20.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                )
                                println(it.name + " " + it.score)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            scaffoldState.bottomSheetState.expand()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Photo,
                                        contentDescription = "Open gallery"
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        takePhoto(
                                            controller = controller,
                                            onPhotoTaken = viewModel::onTakePhoto
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Camera,
                                        contentDescription = "Take a photo"
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        recordVideo(controller)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Take a video"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return CAMERAX_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                applicationContext,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun takePhoto(
        controller: LifecycleCameraController,
        onPhotoTaken: (Bitmap) -> Unit
    ) {
        if (!hasRequiredPermissions()) {
            return
        }

        controller.takePicture(
            ContextCompat.getMainExecutor(applicationContext),
            object : OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)

                    val matrix = Matrix().apply {
                        postRotate(image.imageInfo.rotationDegrees.toFloat())
                    }

                    val rotatedBitmap = Bitmap.createBitmap(
                        image.toBitmap(),
                        0,
                        0,
                        image.width,
                        image.height,
                        matrix,
                        true
                    )

                    onPhotoTaken(rotatedBitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)

                    Log.e("Camera", "Something went wrong: ", exception)
                }
            }
        )
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO requested actually
    private fun recordVideo(controller: LifecycleCameraController) {
        if (recording != null) {
            recording?.stop()
            recording = null
            return
        }

        if (!hasRequiredPermissions()) {
            return
        }

        val outputFile = File(filesDir, "my-recording.mp4")
        recording = controller.startRecording(
            FileOutputOptions.Builder(
                outputFile
            ).build(),
            AudioConfig.create(true),
            ContextCompat.getMainExecutor(applicationContext),
        ) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    if (event.hasError()) {
                        recording?.close()
                        recording = null

                        Toast.makeText(
                            applicationContext,
                            "Video capture failure",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            applicationContext,
                            "Video recorded successfully",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    companion object {
        private val CAMERAX_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    }

    private fun LifecycleCameraController.flipCamera(): CameraSelector {
        return if (this.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }
}
