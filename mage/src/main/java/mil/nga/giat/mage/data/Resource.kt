package mil.nga.giat.mage.data

interface Resource<out T> {

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
    }
}
