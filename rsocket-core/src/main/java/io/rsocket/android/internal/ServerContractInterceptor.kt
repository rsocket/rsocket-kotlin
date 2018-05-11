package io.rsocket.android.internal

import io.reactivex.Flowable
import io.rsocket.android.DuplexConnection
import io.rsocket.android.Frame
import io.rsocket.android.FrameType.RESUME
import io.rsocket.android.FrameType.SETUP
import io.rsocket.android.exceptions.InvalidSetupException
import io.rsocket.android.exceptions.RSocketException
import io.rsocket.android.exceptions.RejectedResumeException
import io.rsocket.android.exceptions.RejectedSetupException
import io.rsocket.android.frame.SetupFrameFlyweight
import io.rsocket.android.plugins.DuplexConnectionInterceptor
import io.rsocket.android.plugins.DuplexConnectionInterceptor.Type
import io.rsocket.android.util.DuplexConnectionProxy

internal class ServerContractInterceptor(
        private val errorConsumer: (Throwable) -> Unit,
        private val protocolVersion: Int,
        private val leaseEnabled: Boolean,
        private val resumeEnabled: Boolean) : DuplexConnectionInterceptor {

    constructor(errorConsumer: (Throwable) -> Unit) :
            this(errorConsumer,
                    SetupFrameFlyweight.CURRENT_VERSION,
                    false,
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