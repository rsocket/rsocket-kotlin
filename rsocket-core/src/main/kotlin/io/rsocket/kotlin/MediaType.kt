package io.rsocket.kotlin

interface MediaType {

    /**
     * @return MIME type of payload metadata
     */
    fun metadataMimeType(): String

    /**
     * @return MIME type of payload data
     */
    fun dataMimeType(): String
}