package mil.nga.giat.mage.observation.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import mil.nga.giat.mage.R
import mil.nga.giat.mage.observation.attachment.AttachmentViewActivity
import mil.nga.giat.mage.observation.edit.ObservationEditActivity
import mil.nga.giat.mage.people.PeopleActivity
import mil.nga.giat.mage.sdk.datastore.user.Permission
import mil.nga.giat.mage.database.model.user.User
import mil.nga.giat.mage.data.datasource.user.UserLocalDataSource
import mil.nga.giat.mage.database.model.observation.Observation
import mil.nga.giat.mage.form.edit.dialog.FormReorderDialog
import mil.nga.giat.mage.ui.directions.NavigationType
import mil.nga.giat.mage.ui.observation.view.ObservationAction
import mil.nga.giat.mage.ui.observation.view.ObservationViewScreen
import mil.nga.giat.mage.utils.googleMapsUri
import javax.inject.Inject

@AndroidEntryPoint
class ObservationViewActivity : AppCompatActivity() {

  companion object {
    private val LOG_NAME = ObservationViewActivity::class.java.name

    const val OBSERVATION_RESULT_TYPE ="OBSERVATION_RESULT_TYPE"
    const val OBSERVATION_ID_EXTRA = "OBSERVATION_ID"
    const val INITIAL_LOCATION_EXTRA = "INITIAL_LOCATION"
    const val INITIAL_ZOOM_EXTRA = "INITIAL_ZOOM"
  }

  enum class ResultType { NAVIGATE }

  private var currentUser: User? = null
  private var hasEventUpdatePermission = false

  private var defaultMapZoom: Float? = null
  private var defaultMapCenter: LatLng? = null

  @Inject lateinit var userLocalDataSource: UserLocalDataSource

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    defaultMapZoom = intent.getFloatExtra(INITIAL_ZOOM_EXTRA, 0.0f)
    defaultMapCenter = intent.getParcelableExtra(INITIAL_LOCATION_EXTRA) ?: LatLng(0.0, 0.0)

    require(intent?.getLongExtra(OBSERVATION_ID_EXTRA, -1L) != -1L) { "OBSERVATION_ID is required to launch ObservationViewActivity" }
    val observationId = intent.getLongExtra(OBSERVATION_ID_EXTRA, -1L)

    try {
      currentUser = userLocalDataSource.readCurrentUser()
      hasEventUpdatePermission = currentUser?.role?.permissions?.permissions?.contains(
         Permission.UPDATE_EVENT) ?: false
    } catch (e: Exception) {
      Log.e(LOG_NAME, "Cannot read current user")
    }
    
    setContent { 
      ObservationViewScreen(
        observationId = observationId,
        onAction = { onAction(it) }
      )
    }
  }

  private fun onAction(action: ObservationAction) {
    when (action) {
      ObservationAction.Close -> { finish() }
      ObservationAction.ReorderForms -> { onReorderForms() }
      is ObservationAction.Favorites -> { onFavorites(action.userIds) }
      is ObservationAction.Edit -> { onEditObservation(action.observationId) }
      is ObservationAction.Attachment -> { onAttachmentClick(action.attachmentId) }
      is ObservationAction.Directions -> { onDirections(action.type, action.observation) }
    }
  }

  private fun onEditObservation(id: Long) {
    if (!userLocalDataSource.isCurrentUserPartOfCurrentEvent()) {
      AlertDialog.Builder(this)
        .setTitle(R.string.no_event_title)
        .setMessage(R.string.observation_no_event_edit_message)
        .setPositiveButton(android.R.string.ok) { _, _ -> }.show()
      return
    }

    val intent = Intent(this, ObservationEditActivity::class.java)
    intent.putExtra(ObservationEditActivity.OBSERVATION_ID, id)
    intent.putExtra(ObservationEditActivity.INITIAL_LOCATION, defaultMapCenter)
    intent.putExtra(ObservationEditActivity.INITIAL_ZOOM, defaultMapZoom)

    startActivity(intent)
  }

  private fun onFavorites(users: Collection<String>) {
    val intent = Intent(this, PeopleActivity::class.java)
    intent.putStringArrayListExtra(PeopleActivity.USER_REMOTE_IDS, ArrayList(users))
    startActivity(intent)
  }

  private fun onDirections(type: NavigationType, observation: Observation) {
    when (type) {
      NavigationType.Map -> {
        val intent = Intent(Intent.ACTION_VIEW, observation.geometry.googleMapsUri())
        startActivity(intent)
      }
      NavigationType.Bearing -> {
        val intent = Intent()
        intent.putExtra(OBSERVATION_ID_EXTRA, observation.id)
        intent.putExtra(OBSERVATION_RESULT_TYPE, ResultType.NAVIGATE)
        setResult(Activity.RESULT_OK, intent)

        finish()
      }
    }
  }

  private fun onAttachmentClick(id: Long) {
    val intent = Intent(applicationContext, AttachmentViewActivity::class.java)
    intent.putExtra(AttachmentViewActivity.ATTACHMENT_ID_EXTRA, id)
    startActivity(intent)
  }

  private fun onReorderForms() {
    val reorderDialog = FormReorderDialog.newInstance()
    reorderDialog.show(supportFragmentManager, "DIALOG_FORM_REORDER")
  }
}