package mil.nga.giat.mage.observation.attachment

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import mil.nga.giat.mage.ui.observation.attachment.AttachmentViewScreen
import mil.nga.giat.mage.ui.observation.attachment.Shareable
import javax.inject.Inject

@AndroidEntryPoint
class AttachmentViewActivity: AppCompatActivity() {
   @Inject lateinit var preferences: SharedPreferences

   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)

      val attachmentId = intent.getLongExtra(ATTACHMENT_ID_EXTRA, -1L)
      val attachmentPath = intent.getStringExtra(ATTACHMENT_PATH_EXTRA)


      setContent {
         AttachmentViewScreen(
            attachmentId = if (attachmentId == -1L) null else attachmentId,
            attachmentPath = attachmentPath,
            onShare = { sharable -> onShareAttachment(sharable) },
            onClose = { finish() }
         )
      }
   }

   private fun onShareAttachment(shareable: Shareable) {
      val intent = when(shareable.type) {
         Shareable.Type.SHARE -> {
            Intent().apply {
               action = Intent.ACTION_SEND
               putExtra(Intent.EXTRA_STREAM, shareable.uri)
               type = shareable.contentType
            }
         }
         Shareable.Type.OPEN -> {
            Intent(Intent.ACTION_VIEW).apply {
               type = contentResolver.getType(shareable.uri)
               setDataAndType(shareable.uri,contentResolver.getType(shareable.uri))
               flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
         }
      }

      startActivity(Intent.createChooser(intent, "Share MAGE Attachment"))
   }

   companion object {
      const val ATTACHMENT_ID_EXTRA = "ATTACHMENT_ID_EXTRA"
      const val ATTACHMENT_PATH_EXTRA = "ATTACHMENT_PATH_EXTRA"
   }
}