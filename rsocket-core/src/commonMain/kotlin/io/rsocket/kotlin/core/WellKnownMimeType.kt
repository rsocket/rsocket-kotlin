/*
 * Copyright 2015-2020 the original author or authors.
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

package io.rsocket.kotlin.core

public enum class WellKnownMimeType(
    public override val text: String,
    public override val identifier: Byte,
) : MimeTypeWithName, MimeTypeWithId {
    ApplicationAvro("application/avro", 0x00),
    ApplicationCbor("application/cbor", 0x01),
    ApplicationGraphql("application/graphql", 0x02),
    ApplicationGzip("application/gzip", 0x03),
    ApplicationJavascript("application/javascript", 0x04),
    ApplicationJson("application/json", 0x05),
    ApplicationOctetStream("application/octet-stream", 0x06),
    ApplicationPdf("application/pdf", 0x07),
    ApplicationThrift("application/vnd.apache.thrift.binary", 0x08),
    ApplicationProtoBuf("application/vnd.google.protobuf", 0x09),
    ApplicationXml("application/xml", 0x0A),
    ApplicationZip("application/zip", 0x0B),
    AudioAac("audio/aac", 0x0C),
    AudioMp3("audio/mp3", 0x0D),
    AudioMp4("audio/mp4", 0x0E),
    AudioMpeg3("audio/mpeg3", 0x0F),
    AudioMpeg("audio/mpeg", 0x10),
    AudioOgg("audio/ogg", 0x11),
    AudioOpus("audio/opus", 0x12),
    AudioVorbis("audio/vorbis", 0x13),
    ImageBmp("image/bmp", 0x14),
    ImageGif("image/gif", 0x15),
    ImageHeicSequence("image/heic-sequence", 0x16),
    ImageHeic("image/heic", 0x17),
    ImageHeifSequence("image/heif-sequence", 0x18),
    ImageHeif("image/heif", 0x19),
    ImageJpeg("image/jpeg", 0x1A),
    ImagePng("image/png", 0x1B),
    ImageTiff("image/tiff", 0x1C),
    MultipartMixed("multipart/mixed", 0x1D),
    TextCss("text/css", 0x1E),
    TextCsv("text/csv", 0x1F),
    TextHtml("text/html", 0x20),
    TextPlain("text/plain", 0x21),
    TextXml("text/xml", 0x22),
    VideoH264("video/H264", 0x23),
    VideoH265("video/H265", 0x24),
    VideoVp8("video/VP8", 0x25),
    ApplicationHessian("application/x-hessian", 0x26),
    ApplicationJavaObject("application/x-java-object", 0x27),
    ApplicationCloudeventsJson("application/cloudevents+json", 0x28),
    ApplicationCapnProto("application/x-capnp", 0x29),
    ApplicationFlatBuffers("application/x-flatbuffers", 0x2A),

    MessageRSocketMimeType("message/x.rsocket.mime-type.v0", 0x7A),
    MessageRSocketAcceptMimeTypes("message/x.rsocket.accept-mime-types.v0", 0x7b),
    MessageRSocketAuthentication("message/x.rsocket.authentication.v0", 0x7C),
    MessageRSocketTracingZipkin("message/x.rsocket.tracing-zipkin.v0", 0x7D),
    MessageRSocketRouting("message/x.rsocket.routing.v0", 0x7E),
    MessageRSocketCompositeMetadata("message/x.rsocket.composite-metadata.v0", 0x7F);

    override fun toString(): String = text

    public companion object {
        private val byIdentifier: Array<WellKnownMimeType?> = arrayOfNulls(128)
        private val byName: MutableMap<String, WellKnownMimeType> = HashMap(128)

        init {
            values().forEach {
                byIdentifier[it.identifier.toInt()] = it
                byName[it.text] = it
            }
        }

        public operator fun invoke(identifier: Byte): WellKnownMimeType? = byIdentifier[identifier.toInt()]

        public operator fun invoke(text: String): WellKnownMimeType? = byName[text]
    }
}
