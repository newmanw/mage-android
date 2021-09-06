package mil.nga.giat.mage.newsfeed

import android.database.Cursor
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.j256.ormlite.android.AndroidDatabaseResults
import com.j256.ormlite.stmt.PreparedQuery
import mil.nga.giat.mage.sdk.datastore.observation.Observation

import kotlin.collections.ArrayList

class ObservationDataSource(
   private val cursor: Cursor,
   private val query: PreparedQuery<Observation>
) : PagingSource<Int, Observation>() {

   override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Observation> {
      val page = params.key ?: 0
      val observations = ArrayList<Observation>(params.loadSize)

      if (cursor.move(page * params.loadSize)) {
         do {
            val observation = query.mapRow(AndroidDatabaseResults(cursor, null, false))
            observations.add(observation)
         } while (cursor.moveToNext() && observations.size < params.loadSize)
      }

      return LoadResult.Page(
         data = observations,
         prevKey = if (page == 0) null else page - 1,
         nextKey = page.plus(1)
      )
   }

   override fun getRefreshKey(state: PagingState<Int, Observation>): Int? {
      return null
   }
}