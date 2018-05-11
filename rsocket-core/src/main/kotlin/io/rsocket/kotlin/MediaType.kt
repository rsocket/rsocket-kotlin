package io.rsocket.kotlin

interface MediaType {

    fun dataMimeType(): String

    fun metadataMimeType(): String
}