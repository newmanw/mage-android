package mil.nga.giat.mage.data

@Suppress("AddVarianceModifier")
data class Resource<T>(
    val content: T? = null,
    val status: Status = Status.Success,
    val statusCode: Int = status.ordinal,
    val statusMessage: String? = status.toString()) {

    fun requireContent(): T {
        return content!!
    }

    enum class Status {
        Loading,
        Success,
        Error
    }

    companion object {

        @JvmStatic
        fun <T> loading(): Resource<T> {
            return Resource(null, Resource.Status.Loading)
        }

        @JvmStatic
        fun <T> loading(content: T?): Resource<T> {
            return Resource(content, Resource.Status.Loading)
        }

        @JvmStatic
        fun <T> success(): Resource<T> {
            return Resource(null, Resource.Status.Success)
        }

        @JvmStatic
        fun <T> success(content: T?): Resource<T> {
            return Resource(content, Resource.Status.Success)
        }
    }
}

