/*
 * Copyright 2015-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin

import io.rsocket.kotlin.frame.io.*

public sealed interface RSocketMimeType {
    public sealed interface WithName : RSocketMimeType {
        public val name: String
    }

    public sealed interface WithIdentifier : RSocketMimeType {
        public val identifier: Byte
    }

    public sealed interface WellKnown : WithIdentifier, WithName

    public companion object {
        // can be accessed by constructors
        internal val wellKnownByIdentifiers: Array<WellKnown?> = arrayOfNulls(128)
        internal val wellKnownByName: MutableMap<String, WellKnown> = HashMap(128)
        private fun wellKnown(name: String, identifier: Byte): WellKnown {
            val type = WellKnownImpl(name, identifier)
            wellKnownByIdentifiers[identifier.toInt()] = type
            wellKnownByName[name] = type
            return type
        }

        public val ApplicationAvro: WellKnown = wellKnown("application/avro", 0x00)
        public val ApplicationCbor: WellKnown = wellKnown("application/cbor", 0x01)
        public val ApplicationGraphql: WellKnown = wellKnown("application/graphql", 0x02)
        public val ApplicationGzip: WellKnown = wellKnown("application/gzip", 0x03)
        public val ApplicationJavascript: WellKnown = wellKnown("application/javascript", 0x04)
        public val ApplicationJson: WellKnown = wellKnown("application/json", 0x05)
        public val ApplicationOctetStream: WellKnown = wellKnown("application/octet-stream", 0x06)
        public val ApplicationPdf: WellKnown = wellKnown("application/pdf", 0x07)
        public val ApplicationThrift: WellKnown = wellKnown("application/vnd.apache.thrift.binary", 0x08)
        public val ApplicationProtoBuf: WellKnown = wellKnown("application/vnd.google.protobuf", 0x09)
        public val ApplicationXml: WellKnown = wellKnown("application/xml", 0x0A)
        public val ApplicationZip: WellKnown = wellKnown("application/zip", 0x0B)

        public val AudioAac: WellKnown = wellKnown("audio/aac", 0x0C)
        public val AudioMp3: WellKnown = wellKnown("audio/mp3", 0x0D)
        public val AudioMp4: WellKnown = wellKnown("audio/mp4", 0x0E)
        public val AudioMpeg3: WellKnown = wellKnown("audio/mpeg3", 0x0F)
        public val AudioMpeg: WellKnown = wellKnown("audio/mpeg", 0x10)
        public val AudioOgg: WellKnown = wellKnown("audio/ogg", 0x11)
        public val AudioOpus: WellKnown = wellKnown("audio/opus", 0x12)
        public val AudioVorbis: WellKnown = wellKnown("audio/vorbis", 0x13)

        public val ImageBmp: WellKnown = wellKnown("image/bmp", 0x14)
        public val ImageGif: WellKnown = wellKnown("image/gif", 0x15)
        public val ImageHeicSequence: WellKnown = wellKnown("image/heic-sequence", 0x16)
        public val ImageHeic: WellKnown = wellKnown("image/heic", 0x17)
        public val ImageHeifSequence: WellKnown = wellKnown("image/heif-sequence", 0x18)
        public val ImageHeif: WellKnown = wellKnown("image/heif", 0x19)
        public val ImageJpeg: WellKnown = wellKnown("image/jpeg", 0x1A)
        public val ImagePng: WellKnown = wellKnown("image/png", 0x1B)
        public val ImageTiff: WellKnown = wellKnown("image/tiff", 0x1C)

        public val MultipartMixed: WellKnown = wellKnown("multipart/mixed", 0x1D)

        public val TextCss: WellKnown = wellKnown("text/css", 0x1E)
        public val TextCsv: WellKnown = wellKnown("text/csv", 0x1F)
        public val TextHtml: WellKnown = wellKnown("text/html", 0x20)
        public val TextPlain: WellKnown = wellKnown("text/plain", 0x21)
        public val TextXml: WellKnown = wellKnown("text/xml", 0x22)

        public val VideoH264: WellKnown = wellKnown("video/H264", 0x23)
        public val VideoH265: WellKnown = wellKnown("video/H265", 0x24)
        public val VideoVp8: WellKnown = wellKnown("video/VP8", 0x25)

        public val ApplicationHessian: WellKnown = wellKnown("application/x-hessian", 0x26)
        public val ApplicationJavaObject: WellKnown = wellKnown("application/x-java-object", 0x27)
        public val ApplicationCloudeventsJson: WellKnown = wellKnown("application/cloudevents+json", 0x28)
        public val ApplicationCapnProto: WellKnown = wellKnown("application/x-capnp", 0x29)
        public val ApplicationFlatBuffers: WellKnown = wellKnown("application/x-flatbuffers", 0x2A)

        public val MessageRSocketMimeType: WellKnown = wellKnown("message/x.rsocket.mime-type.v0", 0x7A)
        public val MessageRSocketAcceptMimeTypes: WellKnown = wellKnown("message/x.rsocket.accept-mime-types.v0", 0x7b)
        public val MessageRSocketAuthentication: WellKnown = wellKnown("message/x.rsocket.authentication.v0", 0x7C)
        public val MessageRSocketTracingZipkin: WellKnown = wellKnown("message/x.rsocket.tracing-zipkin.v0", 0x7D)
        public val MessageRSocketRouting: WellKnown = wellKnown("message/x.rsocket.routing.v0", 0x7E)
        public val MessageRSocketCompositeMetadata: WellKnown = wellKnown("message/x.rsocket.composite-metadata.v0", 0x7F)
    }
}

@Suppress("FunctionName")
public fun RSocketMimeType(name: String): RSocketMimeType.WithName {
    return RSocketMimeType.wellKnownByName[name] ?: run {
        name.requireAscii() // TODO: move requireAscii to internal IO
        require(name.length in 1..128) { "Mime-type text length must be in range 1..128 but was '${name.length}'" }

        WithNameImpl(name)
    }
}

@Suppress("FunctionName")
public fun RSocketMimeType(identifier: Byte): RSocketMimeType.WithIdentifier {
    // we check identifier before accessing `wellKnownByIdentifiers` as we need to validate index for array access
    require(identifier in 0..128) { "Mime-type identifier must be in range 0..128 but was '${identifier}'" }

    return RSocketMimeType.wellKnownByIdentifiers[identifier.toInt()] ?: WithIdentifierImpl(identifier)
}

// TODO: add toString, equals, hashcode
private class WithNameImpl(override val name: String) : RSocketMimeType.WithName
private class WithIdentifierImpl(override val identifier: Byte) : RSocketMimeType.WithIdentifier
private class WellKnownImpl(override val name: String, override val identifier: Byte) : RSocketMimeType.WellKnown
