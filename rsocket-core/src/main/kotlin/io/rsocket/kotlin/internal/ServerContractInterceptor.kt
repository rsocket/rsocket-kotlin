/*
 * Copyright 2015-2018 the original author or authors.
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

package io.rsocket.kotlin.internal

import io.reactivex.Flowable
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType.RESUME
import io.rsocket.kotlin.FrameType.SETUP
import io.rsocket.kotlin.exceptions.InvalidSetupException
import io.rsocket.kotlin.exceptions.RSocketException
import io.rsocket.kotlin.exceptions.RejectedResumeException
import io.rsocket.kotlin.exceptions.RejectedSetupException
import io.rsocket.kotlin.interceptors.DuplexConnectionInterceptor
import io.rsocket.kotlin.interceptors.DuplexConnectionInterceptor.Type
import io.rsocket.kotlin.internal.frame.SetupFrameFlyweight
import io.rsocket.kotlin.util.DuplexConnectionProxy

internal class ServerContractInterceptor(
        private val errorConsumer: (Throwable) -> Unit,
        private val protocolVersion: Int,
        private val leaseEnabled: Boolean,
        private val resumeEnabled: Boolean) : DuplexConnectionInterceptor {

    constructor(errorConsumer: (Throwable) -> Unit, leaseEnabled: Boolean) :
            this(errorConsumer,
                    SetupFrameFlyweight.CURRENT_VERSION,
                    leaseEnabled,
                    false)

    override fun invoke(type: Type, source: DuplexConnection): DuplexConnection =
            if (type == Type.SETUP)
                SetupContract(source,
                        errorConsumer,
                        protocolVersion,
                        leaseEnabled,
                        resumeEnabled)
            else source
}

internal class SetupContract(source: DuplexConnection,
                             private val errorConsumer: (Throwable) -> Unit,
                             private val protocolVersion: Int,
                             private val leaseEnabled: Boolean,
                             private val resumeEnabled: Boolean)
    : DuplexConnectionProxy(source) {

    override fun receive(): Flowable<Frame> {
        return source.receive()
                .filter { f ->
                    val accept =
                            try {
                                when (f.type) {
                                    SETUP -> checkSetupFrame(source, f)
                                    RESUME -> checkResumeFrame(source)
                                    else -> unknownFrame(f)
                                }
                            } catch (e: Throwable) {
                                errorConsumer(e)
                                false
                            }

                    if (!accept) {
                        f.release()
                    }
                    accept
                }
    }

    private fun checkSetupFrame(conn: DuplexConnection,
                                setupFrame: Frame): Boolean {
        val version = Frame.Setup.version(setupFrame)
        val leaseEnabled = Frame.Setup.leaseEnabled(setupFrame)
        val resumeEnabled = Frame.Setup.resumeEnabled(setupFrame)

        return checkSetupVersion(version, conn)
                && checkSetupLease(leaseEnabled, conn)
                && checkSetupResume(resumeEnabled, conn)
    }

    private fun checkResumeFrame(conn: DuplexConnection) =
            check(!resumeEnabled,
                    { RejectedResumeException("Resumption is not supported") },
                    conn)

    private fun unknownFrame(f: Frame): Boolean {
        errorConsumer(IllegalArgumentException(
                "Unknown setup frame: $f"))
        return false
    }

    private fun checkSetupVersion(version: Int, conn: DuplexConnection) =
            check(version != protocolVersion,
                    {
                        InvalidSetupException(
                                "Unsupported protocol: version $version, " +
                                        "expected: $protocolVersion")
                    },
                    conn
            )

    private fun checkSetupLease(leaseEnabled: Boolean,
                                conn: DuplexConnection) =
            check(leaseEnabled && !this.leaseEnabled,
                    { RejectedSetupException("Lease is not supported") },
                    conn)

    private fun checkSetupResume(resumeEnabled: Boolean,
                                 conn: DuplexConnection) =
            check(resumeEnabled && !this.resumeEnabled,
                    { RejectedSetupException("Resumption is not supported") },
                    conn)

    private inline fun check(errorPred: Boolean,
                             error: () -> RSocketException,
                             conn: DuplexConnection): Boolean =
            if (errorPred) {
                terminate(conn, error())
                false
            } else {
                true
            }

    private fun terminate(conn: DuplexConnection, error: RSocketException) {
        conn.sendOne(Frame.Error.from(0, error))
                .andThen(conn.close())
                .subscribe({}, errorConsumer)
    }
}