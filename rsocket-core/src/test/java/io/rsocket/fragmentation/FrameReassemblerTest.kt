/*
 * Copyright 2016 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.rsocket.fragmentation

import io.rsocket.Frame
import io.rsocket.FrameType
import io.rsocket.util.PayloadImpl
import org.junit.Ignore
import java.nio.ByteBuffer
import java.util.concurrent.ThreadLocalRandom
import org.junit.Test

/**  */
class FrameReassemblerTest {

    @Ignore("Same as original project - does not test anything")
    @Test
    fun testAppend() {
        val data = createRandomBytes(16)
        val metadata = createRandomBytes(16)

        val from = Frame.Request.from(
                1024, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)
        val frameFragmenter = FrameFragmenter(2)
        val reassembler = FrameReassembler(from)
        frameFragmenter.fragment(from).subscribe({ reassembler.append(it) })
    }

    private fun createRandomBytes(size: Int): ByteBuffer {
        val bytes = ByteArray(size)
        ThreadLocalRandom.current().nextBytes(bytes)
        return ByteBuffer.wrap(bytes)
    }
    /*
      ByteBuffer data = createRandomBytes(16);
      ByteBuffer metadata = createRandomBytes(16);

      Frame from = Frame.Request.from(1024, FrameType.REQUEST_RESPONSE, new PayloadImpl(data, metadata), 1);

      FrameFragmenter frameFragmenter = new FrameFragmenter(2);

      FrameReassembler reassembler = new FrameReassembler(2);

      frameFragmenter
          .fragment(from)
          .log()
          .doOnNext(reassembler::append)
          .blockLast();

      Frame reassemble = reassembler.reassemble();

      Assert.assertEquals(reassemble.getStreamId(), from.getStreamId());
      Assert.assertEquals(reassemble.getType(), from.getType());

      ByteBuffer reassembleData = reassemble.getData();
      ByteBuffer reassembleMetadata = reassemble.getMetadata();

      Assert.assertTrue(reassembleData.hasRemaining());
      Assert.assertTrue(reassembleMetadata.hasRemaining());

      while (reassembleData.hasRemaining()) {
          Assert.assertEquals(reassembleData.get(), data.get());
      }

      while (reassembleMetadata.hasRemaining()) {
          Assert.assertEquals(reassembleMetadata.get(), metadata.get());
      }
  }

  @Test
  public void testReassmembleAndClear() {
      ByteBuffer data = createRandomBytes(16);
      ByteBuffer metadata = createRandomBytes(16);

      Frame request = Frame.Request.from(1024, FrameType.REQUEST_RESPONSE, new PayloadImpl(data, metadata), 1);

      FrameFragmenter frameFragmenter = new FrameFragmenter(2);

      FrameReassembler reassembler = new FrameReassembler(2);

      Iterable<ByteBuf> fragments = frameFragmenter
          .fragment(request)
          .log()
          .map(frame -> frame.content().copy())
          .toIterable();

      fragments
          .forEach(f -> ByteBufUtil.prettyHexDump(f));


      for (int i = 0; i < 5; i++) {
          for (ByteBuf frame : fragments) {
              reassembler
                  .append(Frame.from(frame));
          }

          Frame reassemble = reassembler.reassemble();

          Assert.assertEquals(reassemble.getStreamId(), request.getStreamId());
          Assert.assertEquals(reassemble.getType(), reassemble.getType());

          ByteBuffer reassembleData = reassemble.getData();
          ByteBuffer reassembleMetadata = reassemble.getMetadata();

          Assert.assertTrue(reassembleData.hasRemaining());
          Assert.assertTrue(reassembleMetadata.hasRemaining());

          while (reassembleData.hasRemaining()) {
              Assert.assertEquals(reassembleData.get(), data.get());
          }

          while (reassembleMetadata.hasRemaining()) {
              Assert.assertEquals(reassembleMetadata.get(), metadata.get());
          }

      }
  }

  @Test
  public void substring() {
      String s = "1234567890";
      String substring = s.substring(0, 5);
      System.out.println(substring);
      String substring1 = s.substring(5, 10);
      System.out.println(substring1);
  }

  */
}
