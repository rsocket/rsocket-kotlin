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
package io.rsocket.android.frame;

import static io.rsocket.android.frame.FrameHeaderFlyweight.FLAGS_M;
import static io.rsocket.android.frame.Utils.INTEGER_BYTES;
import static io.rsocket.android.frame.Utils.SHORT_BYTES;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.rsocket.FrameType;
import java.nio.charset.StandardCharsets;

public class SetupFrameFlyweight {
  private SetupFrameFlyweight() {}

  public static final int FLAGS_RESUME_ENABLE = 0b00_1000_0000;
  public static final int FLAGS_WILL_HONOR_LEASE = 0b00_0100_0000;
  public static final int FLAGS_STRICT_INTERPRETATION = 0b00_0010_0000;

  public static final int VALID_FLAGS =
      FLAGS_RESUME_ENABLE | FLAGS_WILL_HONOR_LEASE | FLAGS_STRICT_INTERPRETATION | FLAGS_M;

  public static final int CURRENT_VERSION = VersionFlyweight.encode(1, 0);

  // relative to start of passed offset
  private static final int VERSION_FIELD_OFFSET = FrameHeaderFlyweight.FRAME_HEADER_LENGTH;
  private static final int KEEPALIVE_INTERVAL_FIELD_OFFSET = VERSION_FIELD_OFFSET + INTEGER_BYTES;
  private static final int MAX_LIFETIME_FIELD_OFFSET =
      KEEPALIVE_INTERVAL_FIELD_OFFSET + INTEGER_BYTES;
  private static final int VARIABLE_DATA_OFFSET = MAX_LIFETIME_FIELD_OFFSET + INTEGER_BYTES;

  public static int computeFrameLength(
      final int flags,
      final String metadataMimeType,
      final String dataMimeType,
      final int metadataLength,
      final int dataLength) {
    return computeFrameLength(flags, 0, metadataMimeType, dataMimeType, metadataLength, dataLength);
  }

  private static int computeFrameLength(
      final int flags,
      final int resumeTokenLength,
      final String metadataMimeType,
      final String dataMimeType,
      final int metadataLength,
      final int dataLength) {
    int length =
        FrameHeaderFlyweight.computeFrameHeaderLength(FrameType.SETUP, metadataLength, dataLength);

    length += INTEGER_BYTES * 3;

    if ((flags & FLAGS_RESUME_ENABLE) != 0) {
      length += SHORT_BYTES + resumeTokenLength;
    }

    length += 1 + metadataMimeType.getBytes(StandardCharsets.UTF_8).length;
    length += 1 + dataMimeType.getBytes(StandardCharsets.UTF_8).length;

    return length;
  }

  public static int encode(
      final ByteBuf byteBuf,
      int flags,
      final int keepaliveInterval,
      final int maxLifetime,
      final String metadataMimeType,
      final String dataMimeType,
      final ByteBuf metadata,
      final ByteBuf data) {
    if ((flags & FLAGS_RESUME_ENABLE) != 0) {
      throw new IllegalArgumentException("RESUME_ENABLE not supported");
    }

    return encode(
        byteBuf,
        flags,
        keepaliveInterval,
        maxLifetime,
        Unpooled.EMPTY_BUFFER,
        metadataMimeType,
        dataMimeType,
        metadata,
        data);
  }

  // Only exposed for testing, other code shouldn't create frames with resumption tokens for now
  static int encode(
      final ByteBuf byteBuf,
      int flags,
      final int keepaliveInterval,
      final int maxLifetime,
      final ByteBuf resumeToken,
      final String metadataMimeType,
      final String dataMimeType,
      final ByteBuf metadata,
      final ByteBuf data) {
    final int frameLength =
        computeFrameLength(
            flags,
            resumeToken.readableBytes(),
            metadataMimeType,
            dataMimeType,
            metadata.readableBytes(),
            data.readableBytes());

    int length =
        FrameHeaderFlyweight.encodeFrameHeader(byteBuf, frameLength, flags, FrameType.SETUP, 0);

    byteBuf.setInt(VERSION_FIELD_OFFSET, CURRENT_VERSION);
    byteBuf.setInt(KEEPALIVE_INTERVAL_FIELD_OFFSET, keepaliveInterval);
    byteBuf.setInt(MAX_LIFETIME_FIELD_OFFSET, maxLifetime);

    length += INTEGER_BYTES * 3;

    if ((flags & FLAGS_RESUME_ENABLE) != 0) {
      byteBuf.setShort(length, resumeToken.readableBytes());
      length += SHORT_BYTES;
      int resumeTokenLength = resumeToken.readableBytes();
      byteBuf.setBytes(length, resumeToken, resumeTokenLength);
      length += resumeTokenLength;
    }

    length += putMimeType(byteBuf, length, metadataMimeType);
    length += putMimeType(byteBuf, length, dataMimeType);

    length += FrameHeaderFlyweight.encodeMetadata(byteBuf, FrameType.SETUP, length, metadata);
    length += FrameHeaderFlyweight.encodeData(byteBuf, length, data);

    return length;
  }

  public static int version(final ByteBuf byteBuf) {
    return byteBuf.getInt(VERSION_FIELD_OFFSET);
  }

  public static int keepaliveInterval(final ByteBuf byteBuf) {
    return byteBuf.getInt(KEEPALIVE_INTERVAL_FIELD_OFFSET);
  }

  public static int maxLifetime(final ByteBuf byteBuf) {
    return byteBuf.getInt(MAX_LIFETIME_FIELD_OFFSET);
  }

  public static String metadataMimeType(final ByteBuf byteBuf) {
    final byte[] bytes = getMimeType(byteBuf, metadataMimetypeOffset(byteBuf));
    return new String(bytes, StandardCharsets.UTF_8);
  }

  public static String dataMimeType(final ByteBuf byteBuf) {
    int fieldOffset = metadataMimetypeOffset(byteBuf);

    fieldOffset += 1 + byteBuf.getByte(fieldOffset);

    final byte[] bytes = getMimeType(byteBuf, fieldOffset);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  public static int payloadOffset(final ByteBuf byteBuf) {
    int fieldOffset = metadataMimetypeOffset(byteBuf);

    final int metadataMimeTypeLength = byteBuf.getByte(fieldOffset);
    fieldOffset += 1 + metadataMimeTypeLength;

    final int dataMimeTypeLength = byteBuf.getByte(fieldOffset);
    fieldOffset += 1 + dataMimeTypeLength;

    return fieldOffset;
  }

  private static int metadataMimetypeOffset(final ByteBuf byteBuf) {
    return VARIABLE_DATA_OFFSET + resumeTokenTotalLength(byteBuf);
  }

  private static int resumeTokenTotalLength(final ByteBuf byteBuf) {
    if ((FrameHeaderFlyweight.flags(byteBuf) & FLAGS_RESUME_ENABLE) == 0) {
      return 0;
    } else {
      return SHORT_BYTES + byteBuf.getShort(VARIABLE_DATA_OFFSET);
    }
  }

  private static int putMimeType(
      final ByteBuf byteBuf, final int fieldOffset, final String mimeType) {
    byte[] bytes = mimeType.getBytes(StandardCharsets.UTF_8);

    byteBuf.setByte(fieldOffset, (byte) bytes.length);
    byteBuf.setBytes(fieldOffset + 1, bytes);

    return 1 + bytes.length;
  }

  private static byte[] getMimeType(final ByteBuf byteBuf, final int fieldOffset) {
    final int length = byteBuf.getByte(fieldOffset);
    final byte[] bytes = new byte[length];

    byteBuf.getBytes(fieldOffset + 1, bytes);
    return bytes;
  }
}
