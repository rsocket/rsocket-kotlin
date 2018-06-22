package io.rsocket.kotlin

/**
 * Configures [Payload] data and metadata MIME types
 */
class MediaTypeOptions : MediaType {
    private var dataMimeType: String = "application/binary"
    private var metadataMimeType: String = "application/binary"

    fun dataMimeType(dataMimeType: String): MediaTypeOptions {
        assertMediaType(dataMimeType)
        this.dataMimeType = dataMimeType
        return this
    }

    override fun dataMimeType(): String = dataMimeType

    fun metadataMimeType(metadataMimeType: String): MediaTypeOptions {
        assertMediaType(metadataMimeType)
        this.metadataMimeType = metadataMimeType
        return this
    }

    override fun metadataMimeType(): String = metadataMimeType

    fun copy(): MediaTypeOptions = MediaTypeOptions()
            .dataMimeType(dataMimeType)
            .metadataMimeType(metadataMimeType)

    private fun assertMediaType(mediaType: String) {
        if (mediaType.isEmpty()) {
            throw IllegalArgumentException("media type must be non-empty")
        }
    }
}