package com.example.ui.components

import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R

@Composable
fun CameraXPreviewCard(
  useBackCamera: Boolean,
  onSwitchCamera: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val previewView = remember {
    PreviewView(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
      scaleType = PreviewView.ScaleType.FILL_CENTER
    }
  }

  DisposableEffect(lifecycleOwner, useBackCamera) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val executor = ContextCompat.getMainExecutor(context)
    var boundProvider: ProcessCameraProvider? = null

    val listener = Runnable {
      try {
        val provider = cameraProviderFuture.get()
        boundProvider = provider
        val preview = Preview.Builder().build().also {
          it.surfaceProvider = previewView.surfaceProvider
        }
        val selector = if (useBackCamera) {
          CameraSelector.DEFAULT_BACK_CAMERA
        } else {
          CameraSelector.DEFAULT_FRONT_CAMERA
        }
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview)
      } catch (e: Exception) {
        Log.e("CameraXPreviewCard", "CameraX bind failed: ${e.message}", e)
      }
    }
    cameraProviderFuture.addListener(listener, executor)

    onDispose {
      boundProvider?.unbindAll()
    }
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(210.dp)
      .clip(MaterialTheme.shapes.medium)
      .background(Color.Black),
  ) {
    AndroidView(
      factory = { previewView },
      modifier = Modifier.fillMaxSize(),
    )

    // Visible Camera Active Indicator
    Surface(
      color = Color(0xCC0B1626),
      shape = RoundedCornerShape(50.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(12.dp),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary),
        )
        Icon(
          imageVector = Icons.Default.Videocam,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = if (useBackCamera) "CAMERA LIVE • REAR" else "CAMERA LIVE • FRONT",
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
        )
      }
    }

    IconButton(
      onClick = onSwitchCamera,
      colors = IconButtonDefaults.iconButtonColors(
        containerColor = Color(0xAA0B1626),
        contentColor = Color.White,
      ),
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(8.dp)
        .testTag("switch_camera_button"),
    ) {
      Icon(
        imageVector = Icons.Default.Cameraswitch,
        contentDescription = stringResource(R.string.dashboard_switch_camera),
      )
    }
  }
}
