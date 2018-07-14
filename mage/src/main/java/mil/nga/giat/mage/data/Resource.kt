package mil.nga.giat.mage.data

@Suppress("AddVarianceModifier")
interface Resource<T> {

    val content: T?
    val status: Resource.Status
    val statusCode: Int
    val statusMessage: String?

    enum class Status {
        Loading,
        Success,
        Error
    }
}

interface ListResource<T> : Resource<List<T>>

interface SetResource<T> : Resource<Set<T>>

interface MapResource<K, V> : Resource<Map<K, V>>

data class BasicResource<T>(
        override val content: T?,
        override val status: Resource.Status,
        override val statusCode: Int,
        override val statusMessage: String?) : Resource<T> {

    constructor(content: T?, status: Resource.Status) : this(content, status, status.ordinal, status.name)


    companion object {

        @JvmStatic
        fun <T> loading(): Resource<T> {
            return BasicResource(null, Resource.Status.Loading)
        }

        @JvmStatic
        fun <T> loading(content: T): Resource<T> {
            return BasicResource(content, Resource.Status.Loading)
        }

        @JvmStatic
        fun <T> success(): Resource<T> {
            return BasicResource(null, Resource.Status.Success)
        }

        @JvmStatic
        fun <T> success(content: T): Resource<T> {
            return BasicResource(content, Resource.Status.Success)
        }
    }
}
