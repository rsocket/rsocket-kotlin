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
 *//*


package io.rsocket

import io.rsocket.test.util.TestDuplexConnection
import io.rsocket.test.util.TestSubscriber
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.reactivestreams.Subscriber

abstract class AbstractSocketRule<T : RSocket> : ExternalResource() {

    lateinit var connection: TestDuplexConnection
    protected lateinit var connectSub: Subscriber<Void>
    lateinit var socket: T
    lateinit var errors: ConcurrentLinkedQueue<Throwable>

    override fun apply(base: Statement, description: Description?): Statement {
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                connection = TestDuplexConnection()
                connectSub = TestSubscriber.create()!!
                errors = ConcurrentLinkedQueue()
                init()
                base.evaluate()
            }
        }
    }

    protected open fun init() {
        socket = newRSocket()
    }

    protected abstract fun newRSocket(): T
}
*/
