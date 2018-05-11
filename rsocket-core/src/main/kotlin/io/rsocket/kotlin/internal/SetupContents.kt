package io.rsocket.kotlin.internal

import io.rsocket.kotlin.*
import io.rsocket.kotlin.internal.frame.FrameHeaderFlyweight
import java.nio.ByteBuffer

internal class SetupContents(
        private val metadataMimeType: String,
        private val dataMimeType: String,
        override val data: ByteBuffer,
        override val metadata: ByteBuffer,
        private val keepAliveInterval: Int,
        private val keepAliveLifetime: Int,
        private val flags: Int) : Setup, KeepAlive {

    override fun keepAliveInterval(): Duration =
            Duration.ofMillis(keepAliveInterval)

    override fun keepAliveMaxLifeTime(): Duration =
            Duration.ofMillis(keepAliveLifetime)

    override fun metadataMimeType(): String = metadataMimeType

    override fun dataMimeType(): String = dataMimeType

    override fun hasMetadata(): Boolean = Frame.isFlagSet(flags, FrameHeaderFlyweight.FLAGS_M)

    companion object {

        internal fun create(setupFrame: Frame): SetupContents {
            Frame.ensureFrameType(FrameType.SETUP, setupFrame)
            return try {
                SetupContents(
                        Frame.Setup.metadataMimeType(setupFrame),
                        Frame.Setup.dataMimeType(setupFrame),
                        setupFrame.data,
                        setupFrame.metadata,
                        Frame.Setup.keepaliveInterval(setupFrame),
                        Frame.Setup.maxLifetime(setupFrame),
                        Frame.Setup.getFlags(setupFrame))
            } finally {
                setupFrame.release()
            }
        }
    }
}
