package mil.nga.giat.mage.newsfeed

import android.app.Application
import android.content.SharedPreferences
import android.database.Cursor
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import com.j256.ormlite.android.AndroidDatabaseResults
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.stmt.PreparedQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mil.nga.giat.mage.R
import mil.nga.giat.mage.form.Form
import mil.nga.giat.mage.map.marker.ObservationBitmapFactory
import mil.nga.giat.mage.newsfeed.observation.ObservationItemState
import mil.nga.giat.mage.observation.ObservationImportantState
import mil.nga.giat.mage.sdk.datastore.DaoStore
import mil.nga.giat.mage.sdk.datastore.observation.Observation
import mil.nga.giat.mage.sdk.datastore.observation.ObservationHelper
import mil.nga.giat.mage.sdk.datastore.observation.State
import mil.nga.giat.mage.sdk.datastore.user.Event
import mil.nga.giat.mage.sdk.datastore.user.EventHelper
import mil.nga.giat.mage.sdk.datastore.user.User
import mil.nga.giat.mage.sdk.datastore.user.UserHelper
import mil.nga.giat.mage.sdk.event.IObservationEventListener
import mil.nga.giat.mage.sdk.exceptions.UserException
import mil.nga.giat.mage.utils.DateFormatFactory
import org.apache.commons.lang3.StringUtils
import java.sql.SQLException
import java.text.DateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class ObservationsViewModel @Inject constructor(
   val application: Application,
   val preferences: SharedPreferences
): ViewModel() {
   var dateFormat: DateFormat = DateFormatFactory.format("yyyy-MM-dd HH:mm zz", Locale.getDefault(), application)

   private val observationDao: Dao<Observation, Long> = DaoStore.getInstance(application).observationDao;
   private val observationHelper = ObservationHelper.getInstance(application)
   private val scheduler = Executors.newScheduledThreadPool(1)
   private var queryUpdateHandle: ScheduledFuture<*>? = null
   private var requeryTime: Long = 0

   private val currentUser: User? = null
   private val event: Event = EventHelper.getInstance(application).currentEvent

   val listener = object : IObservationEventListener {
      override fun onObservationUpdated(updated: Observation) { query() }
      override fun onObservationCreated(observations: MutableCollection<Observation>?, sendUserNotifcations: Boolean?) { query() }
      override fun onObservationDeleted(observation: Observation?) { query() }
      override fun onError(error: Throwable?) {}
   }

   private val _observationPagingFlowLiveData: MutableLiveData<Flow<PagingData<ObservationItemState>>> = MutableLiveData()
   val observationPagingFlowLiveData: LiveData<Flow<PagingData<ObservationItemState>>> = _observationPagingFlowLiveData

   init {
      observationHelper.addListener(listener)
      query()
   }

   override fun onCleared() {
      super.onCleared()
      observationHelper.removeListener(listener)
   }

   private fun query() {
      // TODO what thread is this on?
      val query = buildQuery()
      val cursor = obtainCursor(query)
      _observationPagingFlowLiveData.value = Pager(PagingConfig(pageSize = 5)) {
         ObservationDataSource(cursor, query)
      }.flow.map {
         it.map { observation ->
            // TODO what thread is this on?
            toObservationItemState(observation)
         }
      }.cachedIn(viewModelScope)
   }

   @Throws(SQLException::class)
   private fun buildQuery(): PreparedQuery<Observation> {
      val filterId = getTimeFilterId()

      val qb = observationDao.queryBuilder()
      val c = Calendar.getInstance()
      val filters: MutableList<String?> = ArrayList()
      var footerText = "All observations have been returned"
      if (filterId == application.resources.getInteger(R.integer.time_filter_last_month)) {
         filters.add("Last Month")
         footerText = "End of results for Last Month filter"
         c.add(Calendar.MONTH, -1)
      } else if (filterId == application.resources.getInteger(R.integer.time_filter_last_week)) {
         filters.add("Last Week")
         footerText = "End of results for Last Week filter"
         c.add(Calendar.DAY_OF_MONTH, -7)
      } else if (filterId == application.resources.getInteger(R.integer.time_filter_last_24_hours)) {
         filters.add("Last 24 Hours")
         footerText = "End of results for Last 24 Hours filter"
         c.add(Calendar.HOUR, -24)
      } else if (filterId == application.resources.getInteger(R.integer.time_filter_today)) {
         filters.add("Since Midnight")
         footerText = "End of results for Today filter"
         c[Calendar.HOUR_OF_DAY] = 0
         c[Calendar.MINUTE] = 0
         c[Calendar.SECOND] = 0
         c[Calendar.MILLISECOND] = 0
      } else if (filterId == application.resources.getInteger(R.integer.time_filter_custom)) {
         val customFilterTimeUnit: String = getCustomTimeUnit()
         val customTimeNumber: Int = getCustomTimeNumber()
         filters.add("Last $customTimeNumber $customFilterTimeUnit")
         footerText = "End of results for custom filter"
         when (customFilterTimeUnit) {
            "Hours" -> c.add(Calendar.HOUR, -1 * customTimeNumber)
            "Days" -> c.add(Calendar.DAY_OF_MONTH, -1 * customTimeNumber)
            "Months" -> c.add(Calendar.MONTH, -1 * customTimeNumber)
            else -> c.add(Calendar.MINUTE, -1 * customTimeNumber)
         }
      } else {
         // no filter
         c.time = Date(0)
      }
      requeryTime = c.timeInMillis

      // TODO set footer in LazyColumn as Item
//      adapter.setFooterText(footerText)

      qb.where()
         .ne("state", State.ARCHIVE)
         .and()
         .ge("timestamp", c.time)
         .and()
         .eq("event_id", EventHelper.getInstance(application).currentEvent.id)

      val actionFilters: MutableList<String?> = ArrayList()
      val favorites: Boolean =
         preferences.getBoolean(application.resources.getString(R.string.activeFavoritesFilterKey), false)
      if (favorites && currentUser != null) {
         val observationFavoriteDao = DaoStore.getInstance(application).observationFavoriteDao
         val favoriteQb = observationFavoriteDao.queryBuilder()
         favoriteQb.where()
            .eq("user_id", currentUser.getRemoteId())
            .and()
            .eq("is_favorite", true)
         qb.join(favoriteQb)
         actionFilters.add("Favorites")
      }
      val important: Boolean =
         preferences.getBoolean(application.resources.getString(R.string.activeImportantFilterKey), false)
      if (important) {
         val observationImportantDao = DaoStore.getInstance(application).observationImportantDao
         val importantQb = observationImportantDao.queryBuilder()
         importantQb.where().eq("is_important", true)
         qb.join(importantQb)
         actionFilters.add("Important")
      }
      qb.orderBy("timestamp", false)
      if (actionFilters.isNotEmpty()) {
         filters.add(StringUtils.join(actionFilters, " & "))
      }

      // TODO set subtitle in fragment, listen for changes
//      (getActivity() as AppCompatActivity).supportActionBar!!
//         .setSubtitle(StringUtils.join(filters, ", "))

      return qb.prepare()
   }

   @Throws(SQLException::class)
   private fun obtainCursor(query: PreparedQuery<Observation>): Cursor {
      val iterator = observationDao.iterator(query)

      // get the raw results which can be cast under Android
      val results = iterator.rawResults as AndroidDatabaseResults
      val cursor = results.rawCursor
      if (cursor.moveToLast()) {
         val oldestTime = cursor.getLong(cursor.getColumnIndex("last_modified"))
         queryUpdateHandle?.cancel(true)

         queryUpdateHandle = scheduler.schedule(
            Runnable { updateFilter() },
            oldestTime - requeryTime,
            TimeUnit.MILLISECONDS
         )

         cursor.moveToFirst()
      }

      return cursor
   }

   private fun toObservationItemState(observation: Observation): ObservationItemState {
      val user = UserHelper.getInstance(application).read(observation.userId)

      val importantState = if (observation.important?.isImportant == true) {
         val importantUser: User? = try {
            UserHelper.getInstance(application).read(observation.important?.userId)
         } catch (ue: UserException) { null }

         ObservationImportantState(
            description = observation.important?.description,
            user = importantUser?.displayName
         )
      } else null

      var primary: String? = null
      var secondary: String? = null
      var formId: Long? = null
      if (observation.forms.isNotEmpty()) {
         val observationForm = observation.forms.first()
         val formJson = event.formMap[observationForm?.formId]
         val formDefinition = Form.fromJson(formJson)

         formId = formDefinition?.id
         primary = observationForm?.properties?.find { it.key == formDefinition?.primaryMapField }?.value as? String
         secondary = observationForm?.properties?.find { it.key == formDefinition?.secondaryMapField }?.value as? String
      }

      val icon = ObservationBitmapFactory.bitmap(
         application,
         observation.event.remoteId,
         formId,
         primary,
         secondary
      )

      return ObservationItemState(
         user?.displayName,
         dateFormat.format(observation.timestamp),
         primary,
         secondary,
         icon,
         importantState
      )
   }

   private fun updateFilter() {
      query()
   }

   private fun getTimeFilterId(): Int {
      return preferences.getInt(
         application.resources.getString(R.string.activeTimeFilterKey),
         application.resources.getInteger(R.integer.time_filter_none)
      )
   }

   private fun getCustomTimeNumber(): Int {
      return preferences.getInt(
         application.resources.getString(R.string.customObservationTimeNumberFilterKey),
         0
      )
   }

   private fun getCustomTimeUnit(): String {
      return preferences.getString(
         application.resources.getString(R.string.customObservationTimeUnitFilterKey),
         application.resources.getStringArray(R.array.timeUnitEntries).get(0)
      ) ?: ""
   }
}