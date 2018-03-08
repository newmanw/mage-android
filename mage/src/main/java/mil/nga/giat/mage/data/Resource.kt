package mil.nga.giat.mage.data

const val UNKNOWN_STATUS_CODE: Int = -1

interface Resource<out T> {

    enum class Status {
        Loading,
        Success,
        Error
    }

    val value: T?

    val status: Status

    val statusCode: Int get() = UNKNOWN_STATUS_CODE

    val statusMessage: String?
}
