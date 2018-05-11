package io.rsocket.android

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import io.rsocket.android.test.util.LocalDuplexConnection
import io.rsocket.android.util.PayloadImpl
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.reactivestreams.Publisher
import java.util.concurrent.TimeUnit

class ResponderStreamWindowTest {

    @get:Rule
    val rule = WindowRSocketRule()

    @Test(timeout = 3_000)
    fun responderStreamInbound() {
        rule.receiver.onNext(Frame.Request.from(1,
                FrameType.REQUEST_STREAM,
                PayloadImpl("test"),
                Int.MAX_VALUE))
        assertThat("responderConnection stream is not limited",
                rule.responseDemand,
                Matchers.equalTo(WindowRSocketRule.streamWindow.toLong()))
    }

    @Test(timeout = 3_000)
    fun responderChannelInbound() {
        rule.receiver.onNext(Frame.Request.from(1,
                FrameType.REQUEST_CHANNEL,
                PayloadImpl("test"),
                Int.MAX_VALUE))
        assertThat("responderConnection channel is not limited",
                rule.responseDemand,
                Matchers.equalTo(WindowRSocketRule.streamWindow.toLong()))
    }

    @Test(timeout = 3_000)
    fun responderChannelOutbound() {
        Completable.timer(100,TimeUnit.MILLISECONDS).andThen {
            rule.receiver.onNext(Frame.Request.from(1,
                    FrameType.REQUEST_CHANNEL,
                    PayloadImpl("test"),
                    2))
        }.delay(100, TimeUnit.MILLISECONDS)
                .subscribe()

        val responderDemand = rule.sender
                .filter { it.type == FrameType.REQUEST_N }
                .firstOrError()
                .blockingGet()

        assertThat("responderConnection channel request is not limited",
                Frame.RequestN.requestN(responderDemand),
                Matchers.equalTo(WindowRSocketRule.streamWindow))
    }

    class WindowRSocketRule : ExternalResource() {
        lateinit var sender: PublishProcessor<Frame>
        lateinit var receiver: PublishProcessor<Frame>
        lateinit var conn: LocalDuplexConnection
        internal lateinit var responder: RSocketResponder
        val errors: MutableList<Throwable> = ArrayList()
        var responseDemand: Long? = null

        override fun apply(base: Statement, description: Description?): Statement {
            return object : Statement() {
                @Throws(Throwable::class)
                override fun evaluate() {
                    sender = PublishProcessor.create<Frame>()
                    receiver = PublishProcessor.create<Frame>()
                    conn = LocalDuplexConnection("conn", sender, receiver)

                    responder = RSocketResponder(
                            conn,
                            object : AbstractRSocket() {
                                override fun requestStream(payload: Payload): Flowable<Payload> =
                                        Flowable.just(1, 2, 3)
                                                .map { PayloadImpl(it.toString()) as Payload }
                                                .doOnRequest { responseDemand = it }

                                override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> {
                                    Flowable.fromPublisher(payloads).subscribe()
                                    return Flowable.just(1, 2, 3)
                                            .map { PayloadImpl(it.toString()) as Payload }
                                            .doOnRequest { responseDemand = it }
                                }
                            },
                            { throwable ->
                                errors.add(throwable)
                                Unit
                            },
                            streamWindow)

                    base.evaluate()
                }
            }
        }

        companion object {
            val streamWindow = 20
        }
    }
}