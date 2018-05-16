package io.rsocket.android.util

interface MediaType {

    fun dataMimeType(): String

    fun metadataMimeType(): String
}