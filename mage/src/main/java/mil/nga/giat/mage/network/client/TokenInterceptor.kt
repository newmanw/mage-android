package mil.nga.giat.mage.network.client

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import mil.nga.giat.mage.R
import mil.nga.giat.mage.sdk.event.IEventDispatcher
import mil.nga.giat.mage.sdk.event.ISessionEventListener
import mil.nga.giat.mage.sdk.utils.UserUtility
import okhttp3.Interceptor
import okhttp3.Response
import java.net.HttpURLConnection

class TokenInterceptor(
   private val context: Context,
   private val preferences: SharedPreferences,
   private val userAgent: String
): Interceptor, IEventDispatcher<ISessionEventListener> {
   val listeners = mutableListOf<ISessionEventListener>()

   override fun intercept(chain: Interceptor.Chain): Response {
      val builder = chain.request().newBuilder()

      // add token
      val token = preferences.getString(context.getString(R.string.tokenKey), null)
      if (token != null && token.trim { it <= ' ' }.isNotEmpty()) {
         builder.addHeader("Authorization", "Bearer $token")
      }

      builder.addHeader("User-Agent", userAgent)

      val response = chain.proceed(builder.build())

      val statusCode = response.code()
      if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
         // If token has not expired yet, expire it and send notification to listeners
         if (hasToken()) {
            UserUtility.getInstance(context).clearTokenInformation()
            for (listener in listeners) {
               listener.onTokenExpired()
            }
         }
         Log.w(LOG_NAME, "TOKEN EXPIRED")
      } else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
         Log.w(LOG_NAME, "404 Not Found.")
      }

      return response
   }

   override fun addListener(listener: ISessionEventListener): Boolean {
      return listeners.add(listener)
   }

   override fun removeListener(listener: ISessionEventListener): Boolean {
      return listeners.remove(listener)
   }

   private fun hasToken(): Boolean {
      val token = preferences.getString(context.getString(R.string.tokenKey), "")!!
      val tokenExpiration = preferences.getString(context.getString(R.string.tokenExpirationDateKey), "")!!

      return token.isNotEmpty() || tokenExpiration.isNotEmpty()
   }

   companion object {
      private val LOG_NAME = TokenInterceptor::class.java.name
   }
}