package mil.nga.giat.mage.ui.geopackage.media

import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.glide.rememberGlidePainter
import mil.nga.giat.mage.ui.theme.MageTheme
import mil.nga.giat.mage.ui.theme.topAppBarBackground
import java.io.File

data class GeoPackageMediaKey(
   val geopackageName: String,
   val mediaTable: String,
   val mediaId: Long
)

@Composable
fun GeoPackageMediaScreen(
   key: GeoPackageMediaKey,
   onOpen: (GeoPackageMedia) -> Unit,
   onClose: () -> Unit,
   viewModel: GeoPackageMediaViewModel = hiltViewModel()
) {
   val mediaState by viewModel.geoPackageMedia.observeAsState()
   val media = mediaState ?: return

   LaunchedEffect(key) {
      viewModel.setKey(key)
   }

   MageTheme {
      Scaffold(
         topBar = {
            TopBar { onClose() }
         },
         content = { paddingValues ->
            Column(Modifier.padding(paddingValues)) {
               Media(media) { onOpen(media) }
            }
         }
      )
   }
}

@Composable
private fun TopBar(
   onClose: () -> Unit
) {
   TopAppBar(
      backgroundColor = MaterialTheme.colors.topAppBarBackground,
      contentColor = Color.White,
      title = { Text("GeoPackage Media") },
      navigationIcon = {
         IconButton(onClick = { onClose.invoke() }) {
            Icon(Icons.Default.ArrowBack, "Cancel Edit")
         }
      }
   )
}

@Composable
private fun Media(
   media: GeoPackageMedia,
   onOpen: (() -> Unit)? = null
) {
   when(media.type) {
      GeoPackageMediaType.IMAGE -> {
         MediaImage(path = media.path)
      }
      GeoPackageMediaType.VIDEO -> {
         MediaVideo(path = media.path)
      }
      GeoPackageMediaType.AUDIO -> {
         MediaVideo(path = media.path)
      }
      else -> {
         MediaOther {
            onOpen?.invoke()
         }
      }
   }
}

@Composable
private fun MediaImage(
   path: String
) {
   Box(
      Modifier
         .fillMaxWidth()
         .background(Color(0x19000000))
   ) {
      Image(
         painter = rememberGlidePainter(
            File(path),
            fadeIn = true
         ),
         contentDescription = "GeoPackage Image",
         Modifier.fillMaxSize()
      )
   }
}

@Composable
private fun MediaVideo(
   path: String
) {
   val mediaPlayer: MediaPlayer = MediaPlayer.create(LocalContext.current, Uri.fromFile(File(path)))
   val mediaController = MediaController(LocalContext.current)
   mediaController.setMediaPlayer(object : MediaController.MediaPlayerControl {
      override fun canPause() = true
      override fun canSeekBackward() = true
      override fun canSeekForward() = true
      override fun start() = mediaPlayer.start()
      override fun pause() = mediaPlayer.pause()
      override fun getDuration() = mediaPlayer.duration
      override fun getCurrentPosition() = mediaPlayer.currentPosition
      override fun seekTo(position: Int) = mediaPlayer.seekTo(position)
      override fun isPlaying() = mediaPlayer.isPlaying
      override fun getAudioSessionId() = mediaPlayer.audioSessionId
      override fun getBufferPercentage(): Int {
         return mediaPlayer.currentPosition * 100 / mediaPlayer.duration
      }
   })

   DisposableEffect(
      AndroidView(
         modifier = Modifier
            .fillMaxWidth(),
         factory = { context ->
            val videoView = VideoView(context).apply {
               layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }

            mediaController.setAnchorView(videoView)
            mediaController.setMediaPlayer(videoView)

            videoView.setMediaController(mediaController)
            videoView.setVideoURI(Uri.fromFile(File(path)))
            videoView.setOnPreparedListener {
               videoView.start()
               mediaController.show()
            }

            videoView
         }
      )
   ) {
      onDispose {
         mediaController.hide()
         mediaPlayer.stop()
         mediaPlayer.release()
      }
   }
}

@Composable
private fun MediaOther(
   onOpen: (() -> Unit)? = null
) {
   Column(
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
         .fillMaxSize()
         .padding(16.dp)
   ) {

      CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
         Icon(
            imageVector = Icons.Default.VisibilityOff,
            contentDescription = "Media Icon",
            tint = MaterialTheme.colors.primary.copy(LocalContentAlpha.current),
            modifier = Modifier
               .height(164.dp)
               .width(164.dp)
               .padding(bottom = 16.dp)
         )
      }

      CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
         Text(
            text = "Preview Not Available",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(bottom = 8.dp),
         )

         Text(
            text = "You may be able to view this content in another app",
            style = MaterialTheme.typography.subtitle2,
            modifier = Modifier.padding(bottom = 32.dp),
         )
      }

      OutlinedButton(
         modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
         onClick = {
            onOpen?.invoke()
         }
      ) {
         Text(text = "OPEN WITH")
      }
   }
}