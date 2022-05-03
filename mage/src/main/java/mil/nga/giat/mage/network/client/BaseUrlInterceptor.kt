package mil.nga.giat.mage.network.client

import mil.nga.giat.mage.network.Server
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class BaseUrlInterceptor(
   private val server: Server
): Interceptor {

   override fun intercept(chain: Interceptor.Chain): Response {
      var request: Request = chain.request()
      val baseUrl = HttpUrl.parse(server.baseUrl)!!

      val url = request.url().newBuilder()
         .scheme(baseUrl.scheme())
         .host(baseUrl.url().toURI().host)
         .build()

      request = request.newBuilder()
         .url(url)
         .build()

      return chain.proceed(request)
   }
}