/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rsocket.android

import io.rsocket.android.frame.FrameHeaderFlyweight.FLAGS_M

import io.netty.buffer.*
import io.netty.util.IllegalReferenceCountException
import io.netty.util.Recycler
import io.netty.util.Recycler.Handle
import io.netty.util.ResourceLeakDetector
import io.rsocket.android.frame.ErrorFrameFlyweight
import io.rsocket.android.frame.FrameHeaderFlyweight
import io.rsocket.android.frame.KeepaliveFrameFlyweight
import io.rsocket.android.frame.LeaseFrameFlyweight
import io.rsocket.android.frame.RequestFrameFlyweight
import io.rsocket.android.frame.RequestNFrameFlyweight
import io.rsocket.android.frame.SetupFrameFlyweight
import io.rsocket.android.frame.VersionFlyweight
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory

/**
 * Represents a Frame sent over a [DuplexConnection].
 *
 *
 * This provides encoding, decoding and field accessors.
 */
class Frame private constructor(private val handle: Handle<Frame>) : ByteBufHolder {
    private var content: ByteBuf? = null

    /** Clear and recycle this instance.  */
    private fun recycle() {
        content = null
        handle.recycle(this)
    }

    /** Return the content which is held by this [Frame].  */
    override fun content(): ByteBuf {
        if (content!!.refCnt() <= 0) {
            throw IllegalReferenceCountException(content!!.refCnt())
        }
        return content as ByteBuf
    }

    /** Creates a deep copy of this [Frame].  */
    override fun copy(): Frame {
        return replace(content!!.copy())
    }

    /**
     * Duplicates this [Frame]. Be aware that this will not automatically call [ ][.retain].
     */
    override fun duplicate(): Frame {
        return replace(content!!.duplicate())
    }

    /**
     * Duplicates this [Frame]. This method returns a retained duplicate unlike [ ][.duplicate].
     *
     * @see ByteBuf.retainedDuplicate
     */
    override fun retainedDuplicate(): Frame {
        return replace(content!!.retainedDuplicate())
    }

    /** Returns a new [Frame] which contains the specified `content`.  */
    override fun replace(content: ByteBuf): Frame {
        return from(content)
    }

    /**
     * Returns the reference count of this object. If `0`, it means this object has been
     * deallocated.
     */
    override fun refCnt(): Int {
        return content!!.refCnt()
    }

    /** Increases the reference count by `1`.  */
    override fun retain(): Frame {
        content!!.retain()
        return this
    }

    /** Increases the reference count by the specified `increment`.  */
    override fun retain(increment: Int): Frame {
        content!!.retain(increment)
        return this
    }

    /**
     * Records the current access location of this object for debugging purposes. If this object is
     * determined to be leaked, the information recorded by this operation will be provided to you via
     * [ResourceLeakDetector]. This method is a shortcut to [touch(null)][.touch].
     */
    override fun touch(): Frame {
        content!!.touch()
        return this
    }

    /**
     * Records the current access location of this object with an additional arbitrary information for
     * debugging purposes. If this object is determined to be leaked, the information recorded by this
     * operation will be provided to you via [ResourceLeakDetector].
     */
    override fun touch(hint: Any?): Frame {
        content!!.touch(hint)
        return this
    }

    /**
     * Decreases the reference count by `1` and deallocates this object if the reference count
     * reaches at `0`.
     *
     * @return `true` if and only if the reference count became `0` and this object has
     * been deallocated
     */
    override fun release(): Boolean {
        if (content!!.release()) {
            recycle()
            return true
        }
        return false
    }

    /**
     * Decreases the reference count by the specified `decrement` and deallocates this object if
     * the reference count reaches at `0`.
     *
     * @return `true` if and only if the reference count became `0` and this object has
     * been deallocated
     */
    override fun release(decrement: Int): Boolean {
        if (content!!.release(decrement)) {
            recycle()
            return true
        }
        return false
    }

    /**
     * Return [ByteBuffer] that is a [ByteBuffer.slice] for the frame metadata
     *
     *
     * If no metadata is present, the ByteBuffer will have 0 capacity.
     *
     * @return ByteBuffer containing the content
     */
    val metadata: ByteBuffer
        get() {
            val metadata = FrameHeaderFlyweight.sliceFrameMetadata(content!!)
            if (metadata == null) {
                return NULL_BYTEBUFFER
            } else if (metadata.readableBytes() > 0) {
                val buffer = ByteBuffer.allocateDirect(metadata.readableBytes())
                metadata.readBytes(buffer)
                buffer.flip()
                return buffer
            } else {
                return NULL_BYTEBUFFER
            }
        }

    /**
     * Return [ByteBuffer] that is a [ByteBuffer.slice] for the frame data
     *
     *
     * If no data is present, the ByteBuffer will have 0 capacity.
     *
     * @return ByteBuffer containing the data
     */
    val data: ByteBuffer
        get() {
            val data = FrameHeaderFlyweight.sliceFrameData(content!!)
            if (data.readableBytes() > 0) {
                val buffer = ByteBuffer.allocateDirect(data.readableBytes())
                data.readBytes(buffer)
                buffer.flip()
                return buffer
            } else {
                return NULL_BYTEBUFFER
            }
        }

    /**
     * Return frame stream identifier
     *
     * @return frame stream identifier
     */
    val streamId: Int
        get() = FrameHeaderFlyweight.streamId(content!!)

    /**
     * Return frame [FrameType]
     *
     * @return frame type
     */
    val type: FrameType
        get() = FrameHeaderFlyweight.frameType(content!!)

    /**
     * Return the flags field for the frame
     *
     * @return frame flags field value
     */
    fun flags(): Int {
        return FrameHeaderFlyweight.flags(content!!)
    }

    fun hasMetadata(): Boolean {
        return isFlagSet(this.flags(), FLAGS_M)
    }

    val dataUtf8: String
        get() = StandardCharsets.UTF_8.decode(data).toString()

    /* TODO:
   *
   * fromRequest(type, id, payload)
   * fromKeepalive(ByteBuf content)
   *
   */

    // SETUP specific getters
    object Setup {

        fun from(
                flags: Int,
                keepaliveInterval: Int,
                maxLifetime: Int,
                metadataMimeType: String,
                dataMimeType: String,
                payload: Payload): Frame {
            val metadata = if (payload.hasMetadata())
                Unpooled.wrappedBuffer(payload.metadata)
            else
                Unpooled.EMPTY_BUFFER
            val data = if (payload.data != null)
                Unpooled.wrappedBuffer(payload.data)
            else
                Unpooled.EMPTY_BUFFER

            val frame = RECYCLER.get()
            frame.content = ByteBufAllocator.DEFAULT.buffer(
                    SetupFrameFlyweight.computeFrameLength(
                            flags,
                            metadataMimeType,
                            dataMimeType,
                            metadata.readableBytes(),
                            data.readableBytes()))
            frame.content!!.writerIndex(
                    SetupFrameFlyweight.encode(
                            frame.content!!,
                            flags,
                            keepaliveInterval,
                            maxLifetime,
                            metadataMimeType,
                            dataMimeType,
                            metadata,
                            data))
            return frame
        }

        fun getFlags(frame: Frame): Int {
            ensureFrameType(FrameType.SETUP, frame)
            val flags = FrameHeaderFlyweight.flags(frame.content!!)

            return flags and SetupFrameFlyweight.VALID_FLAGS
        }

        fun version(frame: Frame): Int {
            ensureFrameType(FrameType.SETUP, frame)
            return SetupFrameFlyweight.version(frame.content!!)
        }

        fun keepaliveInterval(frame: Frame): Int {
            ensureFrameType(FrameType.SETUP, frame)
            return SetupFrameFlyweight.keepaliveInterval(frame.content!!)
        }

        fun maxLifetime(frame: Frame): Int {
            ensureFrameType(FrameType.SETUP, frame)
            return SetupFrameFlyweight.maxLifetime(frame.content!!)
        }

        fun metadataMimeType(frame: Frame): String {
            ensureFrameType(FrameType.SETUP, frame)
            return SetupFrameFlyweight.metadataMimeType(frame.content!!)
        }

        fun dataMimeType(frame: Frame): String {
            ensureFrameType(FrameType.SETUP, frame)
            return SetupFrameFlyweight.dataMimeType(frame.content!!)
        }
    }

    object Error {
        private val errorLogger = LoggerFactory.getLogger(Error::class.java)

        fun from(streamId: Int, throwable: Throwable, dataBuffer: ByteBuf): Frame {
            if (errorLogger.isDebugEnabled) {
                errorLogger.debug("an error occurred, creating error frame", throwable)
            }

            val code = ErrorFrameFlyweight.errorCodeFromException(throwable)
            val frame = RECYCLER.get()
            frame.content = ByteBufAllocator.DEFAULT.buffer(
                    ErrorFrameFlyweight.computeFrameLength(dataBuffer.readableBytes()))
            frame.content!!.writerIndex(
                    ErrorFrameFlyweight.encode(frame.content!!, streamId, code, dataBuffer))
            return frame
        }

        fun from(streamId: Int, throwable: Throwable): Frame {
            val data = throwable.message ?: ""
            val bytes = data.toByteArray(StandardCharsets.UTF_8)

            return from(streamId, throwable, Unpooled.wrappedBuffer(bytes))
        }

        fun errorCode(frame: Frame): Int {
            ensureFrameType(FrameType.ERROR, frame)
            return ErrorFrameFlyweight.errorCode(frame.content!!)
        }

        fun message(frame: Frame): String {
            ensureFrameType(FrameType.ERROR, frame)
            return ErrorFrameFlyweight.message(frame.content!!)
        }
    }

    object Lease {

        fun from(ttl: Int, numberOfRequests: Int, metadata: ByteBuf): Frame {
            val frame = RECYCLER.get()
            frame.content = ByteBufAllocator.DEFAULT.buffer(
                    LeaseFrameFlyweight.computeFrameLength(metadata.readableBytes()))
            frame.content!!.writerIndex(
                    LeaseFrameFlyweight.encode(frame.content!!, ttl, numberOfRequests, metadata))
            return frame
        }

        fun ttl(frame: Frame): Int {
            ensureFrameType(FrameType.LEASE, frame)
            return LeaseFrameFlyweight.ttl(frame.content!!)
        }

        fun numberOfRequests(frame: Frame): Int {
            ensureFrameType(FrameType.LEASE, frame)
            return LeaseFrameFlyweight.numRequests(frame.content!!)
        }
    }

    object RequestN {

        fun from(streamId: Int, requestN: Long): Frame {
            val v = if (requestN > Integer.MAX_VALUE) Integer.MAX_VALUE else requestN.toInt()
            return from(streamId, v)
        }

        fun from(streamId: Int, requestN: Int): Frame {
            if (requestN < 1) {
                throw IllegalStateException("request n must be greater than 0")
            }

            val frame = RECYCLER.get()
            frame.content = ByteBufAllocator.DEFAULT.buffer(RequestNFrameFlyweight.computeFrameLength())
            frame.content!!.writerIndex(RequestNFrameFlyweight.encode(frame.content!!, streamId, requestN))
            return frame
        }

        fun requestN(frame: Frame): Int {
            ensureFrameType(FrameType.REQUEST_N, frame)
            return RequestNFrameFlyweight.requestN(frame.content!!)
        }
    }

    object Request {

        fun from(streamId: Int, type: FrameType, payload: Payload, initialRequestN: Long): Frame {
            val v = if (initialRequestN > Integer.MAX_VALUE) Integer.MAX_VALUE else initialRequestN.toInt()
            return from(streamId, type, payload, v)
        }

        fun from(streamId: Int, type: FrameType, payload: Payload, initialRequestN: Int): Frame {
            if (initialRequestN < 1) {
                throw IllegalStateException("initial request n must be greater than 0")
            }
            val metadata = if (payload.hasMetadata()) Unpooled.wrappedBuffer(payload.metadata) else null
            val data = Unpooled.wrappedBuffer(payload.data)

            val frame = RECYCLER.get()
            frame.content = ByteBufAllocator.DEFAULT.buffer(
                    RequestFrameFlyweight.computeFrameLength(
                            type, metadata?.readableBytes(), data.readableBytes()))

            if (type.hasInitialRequestN()) {
                frame.content!!.writerIndex(
                        RequestFrameFlyweight.encode(
                                frame.content!!,
                                streamId,
                                if (metadata != null) FLAGS_M else 0,
                                type,
                                initialRequestN,
                                metadata,
                                data))
            } else {
                frame.content!!.writerIndex(
                        RequestFrameFlyweight.encode(
                                frame.content!!, streamId, if (metadata != null) FLAGS_M else 0, type, metadata, data))
            }

            return frame
        }

        fun from(streamId: Int, type: FrameType, flags: Int): Frame {
            val frame = RECYCLER.get()
            frame.content = ByteBufAllocator.DEFAULT.buffer(RequestFrameFlyweight.computeFrameLength(type, null, 0))
            frame.content!!.writerIndex(
                    RequestFrameFlyweight.encode(
                            frame.content!!, streamId, flags, type, Unpooled.EMPTY_BUFFER, Unpooled.EMPTY_BUFFER))
            return frame
        }

        fun from(
                streamId: Int,
                type: FrameType,
                metadata: ByteBuf,
                data: ByteBuf,
                initialRequestN: Int,
                flags: Int): Frame {
            val frame = RECYCLER.get()
            frame.content = ByteBufAllocator.DEFAULT.buffer(
                    RequestFrameFlyweight.computeFrameLength(
                            type, metadata.readableBytes(), data.readableBytes()))
            frame.content!!.writerIndex(
                    RequestFrameFlyweight.encode(
                            frame.content!!, streamId, flags, type, initialRequestN, metadata, data))
            return frame
        }

        fun initialRequestN(frame: Frame): Int {
            val type = frame.type
            val result: Int

            if (!type.isRequestType) {
                throw AssertionError("expected request type, but saw " + type.name)
            }

            when (frame.type) {
                FrameType.REQUEST_RESPONSE -> result = 1
                FrameType.FIRE_AND_FORGET -> result = 0
                else -> result = RequestFrameFlyweight.initialRequestN(frame.content!!)
            }

            return result
        }

        fun isRequestChannelComplete(frame: Frame): Boolean {
            ensureFrameType(FrameType.REQUEST_CHANNEL, frame)
            val flags = FrameHeaderFlyweight.flags(frame.content!!)

            return flags and FrameHeaderFlyweight.FLAGS_C == FrameHeaderFlyweight.FLAGS_C
        }
    }

    object PayloadFrame {

        @JvmOverloads
        fun from(streamId: Int, type: FrameType, payload: Payload, flags: Int = if (payload.hasMetadata()) FLAGS_M else 0): Frame {
            val metadata = if (payload.hasMetadata()) Unpooled.wrappedBuffer(payload.metadata) else null
            val data = Unpooled.wrappedBuffer(payload.data)
            return from(streamId, type, metadata, data, flags)
        }

        @JvmOverloads
        fun from(
                streamId: Int, type: FrameType, metadata: ByteBuf? = null, data: ByteBuf = Unpooled.EMPTY_BUFFER, flags: Int = 0): Frame {
            val frame = RECYCLER.get()
            frame.content = ByteBufAllocator.DEFAULT.buffer(
                    FrameHeaderFlyweight.computeFrameHeaderLength(
                            type, metadata?.readableBytes(), data.readableBytes()))
            frame.content!!.writerIndex(
                    FrameHeaderFlyweight.encode(frame.content!!, streamId, flags, type, metadata, data))
            return frame
        }
    }

    object Cancel {

        fun from(streamId: Int): Frame {
            val frame = RECYCLER.get()
            frame.content = ByteBufAllocator.DEFAULT.buffer(
                    FrameHeaderFlyweight.computeFrameHeaderLength(FrameType.CANCEL, null, 0))
            frame.content!!.writerIndex(
                    FrameHeaderFlyweight.encode(
                            frame.content!!, streamId, 0, FrameType.CANCEL, null, Unpooled.EMPTY_BUFFER))
            return frame
        }
    }

    object Keepalive {

        fun from(data: ByteBuf, respond: Boolean): Frame {
            val frame = RECYCLER.get()
            frame.content = ByteBufAllocator.DEFAULT.buffer(
                    KeepaliveFrameFlyweight.computeFrameLength(data.readableBytes()))

            val flags = if (respond) KeepaliveFrameFlyweight.FLAGS_KEEPALIVE_R else 0
            frame.content!!.writerIndex(KeepaliveFrameFlyweight.encode(frame.content!!, flags, data))

            return frame
        }

        fun hasRespondFlag(frame: Frame): Boolean {
            ensureFrameType(FrameType.KEEPALIVE, frame)
            val flags = FrameHeaderFlyweight.flags(frame.content!!)

            return flags and KeepaliveFrameFlyweight.FLAGS_KEEPALIVE_R == KeepaliveFrameFlyweight.FLAGS_KEEPALIVE_R
        }
    }

    override fun toString(): String {
        val type = FrameHeaderFlyweight.frameType(content!!)
        val payload = StringBuilder()
        val metadata = FrameHeaderFlyweight.sliceFrameMetadata(content!!)

        if (metadata != null) {
            if (0 < metadata.readableBytes()) {
                payload.append(
                        String.format("metadata: \"%s\" ", metadata.toString(StandardCharsets.UTF_8)))
            }
        }

        val data = FrameHeaderFlyweight.sliceFrameData(content!!)
        if (0 < data.readableBytes()) {
            payload.append(String.format("data: \"%s\" ", data.toString(StandardCharsets.UTF_8)))
        }

        val streamId = FrameHeaderFlyweight.streamId(content!!).toLong()

        var additionalFlags = ""
        when (type) {
            FrameType.LEASE -> additionalFlags = " Permits: " + Lease.numberOfRequests(this) + " TTL: " + Lease.ttl(this)
            FrameType.REQUEST_N -> additionalFlags = " RequestN: " + RequestN.requestN(this)
            FrameType.KEEPALIVE -> additionalFlags = " Respond flag: " + Keepalive.hasRespondFlag(this)
            FrameType.REQUEST_STREAM, FrameType.REQUEST_CHANNEL -> additionalFlags = " Initial Request N: " + Request.initialRequestN(this)
            FrameType.ERROR -> additionalFlags = " Error code: " + Error.errorCode(this)
            FrameType.SETUP -> {
                val version = Setup.version(this)
                additionalFlags = " Version: " +
                        VersionFlyweight.toString(version) +
                        " keep-alive interval: " +
                        Setup.keepaliveInterval(this) +
                        " max lifetime: " +
                        Setup.maxLifetime(this) +
                        " metadata mime type: " +
                        Setup.metadataMimeType(this) +
                        " data mime type: " +
                        Setup.dataMimeType(this)
            }
        }

        return "Frame => Stream ID: " +
                streamId +
                " Type: " +
                type +
                additionalFlags +
                " Payload: " +
                payload
    }

    companion object {
        val NULL_BYTEBUFFER = ByteBuffer.allocateDirect(0)

        private val RECYCLER = object : Recycler<Frame>() {
            override fun newObject(handle: Handle<Frame>): Frame {
                return Frame(handle)
            }
        }

        /**
         * Acquire a free Frame backed by given ByteBuf
         *
         * @param content to use as backing buffer
         * @return frame
         */
        fun from(content: ByteBuf): Frame {
            val frame = RECYCLER.get()
            frame.content = content

            return frame
        }

        fun isFlagSet(flags: Int, checkedFlag: Int): Boolean {
            return flags and checkedFlag == checkedFlag
        }

        fun setFlag(current: Int, toSet: Int): Int {
            return current or toSet
        }

        fun ensureFrameType(frameType: FrameType, frame: Frame) {
            val typeInFrame = frame.type

            if (typeInFrame !== frameType) {
                throw AssertionError("expected $frameType, but saw$typeInFrame")
            }
        }
    }
}
