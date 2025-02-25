/*
 * Copyright 2015-2025 the original author or authors.
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
package io.rsocket.kotlin.transport.netty.quic

import io.netty.buffer.*
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.incubator.codec.quic.*

internal val QuicStreamChannel.debugName: String
    get() {
        val name = if (isLocalCreated) {
            "LOCAL"
        } else {
            "REMOTE"
        }
        return "STREAM-$name"
    }

// TODO: move it somehow to tests
// debug logger which can help to debug QUIC issues :)
internal class DebugLoggingHandler(private val name: String) : ChannelDuplexHandler() {
    private var read = 0L
    private var write = 0L

    private fun log(message: String) {
        println("[$name] $message")
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
//        log(format(ctx, "ACTIVE"))
        ctx.fireChannelActive()
    }


    override fun channelInactive(ctx: ChannelHandlerContext) {
        log(format(ctx, "INACTIVE", read, write))
        ctx.fireChannelInactive()
    }


    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        log(format(ctx, "EXCEPTION", cause))
        ctx.fireExceptionCaught(cause)
    }


//    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any?) {
//        log(format(ctx, "USER_EVENT", evt))
//        ctx.fireUserEventTriggered(evt)
//    }


//    override fun close(ctx: ChannelHandlerContext, promise: ChannelPromise?) {
//        log(format(ctx, "CLOSE"))
//        ctx.close(promise)
//    }


//    override fun channelReadComplete(ctx: ChannelHandlerContext) {
//        log(format(ctx, "READ COMPLETE"))
//        ctx.fireChannelReadComplete()
//    }


    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
        if (msg is ByteBuf) {
            read += msg.readableBytes()
        }

//        log(format(ctx, "READ", msg))

        ctx.fireChannelRead(msg)
    }


    override fun write(ctx: ChannelHandlerContext, msg: Any?, promise: ChannelPromise?) {
        if (msg is ByteBuf) {
            write += msg.readableBytes()
        }
//        log(format(ctx, "WRITE", msg))

        ctx.write(msg, promise)
    }


//    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
//
//        log(format(ctx, "WRITABILITY CHANGED ${ctx.channel().isWritable}"))
//
//        ctx.fireChannelWritabilityChanged()
//    }


    override fun flush(ctx: ChannelHandlerContext) {
//        log(format(ctx, "FLUSH"))
        ctx.flush()
    }

    /**
     * Formats an event and returns the formatted message.
     *
     * @param eventName the name of the event
     */
    protected fun format(ctx: ChannelHandlerContext, eventName: String): String {
        val chStr = ctx.channel().toString()
        return StringBuilder(chStr.length + 1 + eventName.length)
            .append(chStr)
            .append(' ')
            .append(eventName)
            .toString()
    }

    /**
     * Formats an event and returns the formatted message.
     *
     * @param eventName the name of the event
     * @param arg       the argument of the event
     */
    protected fun format(ctx: ChannelHandlerContext, eventName: String, arg: Any?): String {
        if (arg is ByteBuf) {
            return formatByteBuf(ctx, eventName, arg)
        } else if (arg is ByteBufHolder) {
            return formatByteBufHolder(ctx, eventName, arg)
        } else {
            return formatSimple(ctx, eventName, arg)
        }
    }

    /**
     * Formats an event and returns the formatted message.  This method is currently only used for formatting
     * [ChannelOutboundHandler.connect].
     *
     * @param eventName the name of the event
     * @param firstArg  the first argument of the event
     * @param secondArg the second argument of the event
     */
    protected fun format(ctx: ChannelHandlerContext, eventName: String, firstArg: Any?, secondArg: Any?): String {
        if (secondArg == null) {
            return formatSimple(ctx, eventName, firstArg)
        }

        val chStr = ctx.channel().toString()
        val arg1Str = firstArg.toString()
        val arg2Str = secondArg.toString()
        val buf = StringBuilder(
            chStr.length + 1 + eventName.length + 2 + arg1Str.length + 2 + arg2Str.length
        )
        buf.append(chStr).append(' ').append(eventName).append(": ").append(arg1Str).append(", ").append(arg2Str)
        return buf.toString()
    }

    /**
     * Generates the default log message of the specified event whose argument is a [ByteBuf].
     */
    private fun formatByteBuf(ctx: ChannelHandlerContext, eventName: String, msg: ByteBuf): String {
        val chStr = ctx.channel().toString()
        val length = msg.readableBytes()
        if (length == 0) {
            val buf = StringBuilder(chStr.length + 1 + eventName.length + 4)
            buf.append(chStr).append(' ').append(eventName).append(": 0B")
            return buf.toString()
        } else {
            val outputLength = chStr.length + 1 + eventName.length + 2 + 10 + 1
//            if (byteBufFormat == ByteBufFormat.HEX_DUMP) {
//                val rows = length / 16 + (if (length % 15 == 0) 0 else 1) + 4
//                val hexDumpLength = 2 + rows * 80
//                outputLength += hexDumpLength
//            }
            val buf = StringBuilder(outputLength)
            buf.append(chStr).append(' ').append(eventName).append(": ").append(length).append('B')
//            if (byteBufFormat == ByteBufFormat.HEX_DUMP) {
//                buf.append(StringUtil.NEWLINE)
//                ByteBufUtil.appendPrettyHexDump(buf, msg)
//            }

            return buf.toString()
        }
    }

    /**
     * Generates the default log message of the specified event whose argument is a [ByteBufHolder].
     */
    private fun formatByteBufHolder(ctx: ChannelHandlerContext, eventName: String, msg: ByteBufHolder): String {
        val chStr = ctx.channel().toString()
        val msgStr = msg.toString()
        val content = msg.content()
        val length = content.readableBytes()
        if (length == 0) {
            val buf = StringBuilder(chStr.length + 1 + eventName.length + 2 + msgStr.length + 4)
            buf.append(chStr).append(' ').append(eventName).append(", ").append(msgStr).append(", 0B")
            return buf.toString()
        } else {
            val outputLength = chStr.length + 1 + eventName.length + 2 + msgStr.length + 2 + 10 + 1
//            if (byteBufFormat == ByteBufFormat.HEX_DUMP) {
//                val rows = length / 16 + (if (length % 15 == 0) 0 else 1) + 4
//                val hexDumpLength = 2 + rows * 80
//                outputLength += hexDumpLength
//            }
            val buf = StringBuilder(outputLength)
            buf.append(chStr).append(' ').append(eventName).append(": ")
                .append(msgStr).append(", ").append(length).append('B')
//            if (byteBufFormat == ByteBufFormat.HEX_DUMP) {
//                buf.append(StringUtil.NEWLINE)
//                ByteBufUtil.appendPrettyHexDump(buf, content)
//            }

            return buf.toString()
        }
    }

    /**
     * Generates the default log message of the specified event whose argument is an arbitrary object.
     */
    private fun formatSimple(ctx: ChannelHandlerContext, eventName: String, msg: Any?): String {
        val chStr = ctx.channel().toString()
        val msgStr = msg.toString()
        val buf = StringBuilder(chStr.length + 1 + eventName.length + 2 + msgStr.length)
        return buf.append(chStr).append(' ').append(eventName).append(": ").append(msgStr).toString()
    }
}
