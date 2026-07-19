package com.nianri.app.data.transfer

import com.nianri.app.domain.model.ImportantDay
import java.time.Instant

data class TransferDocument(
    val exportedAt: Instant,
    val days: List<ImportantDay>,
)

sealed class TransferFormatException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause) {
    class NotNianriConfiguration :
        TransferFormatException("Not a Nianri configuration")

    class Corrupt(cause: Throwable? = null) :
        TransferFormatException("Corrupt configuration", cause)

    class UnsupportedVersion(val version: Int) :
        TransferFormatException("Unsupported version: $version")

    class InvalidDay(message: String, cause: Throwable? = null) :
        TransferFormatException(message, cause)
}
