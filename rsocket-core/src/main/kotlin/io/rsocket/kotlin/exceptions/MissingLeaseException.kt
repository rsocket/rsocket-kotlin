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

package io.rsocket.kotlin.exceptions

import io.rsocket.kotlin.internal.lease.Lease

class MissingLeaseException internal constructor(lease: Lease, tag: String)
    : RejectedException(leaseMessage(lease, tag)) {

    override fun fillInStackTrace(): Throwable = this

    companion object {
        internal fun leaseMessage(lease: Lease, tag: String): String {
            val expired = lease.isExpired
            val allowedRequests = lease.allowedRequests
            val initialAllowedRequests = lease.initialAllowedRequests
            return "$tag: Missing lease. " +
                    "Expired: $expired, allowedRequests:" +
                    " $allowedRequests/$initialAllowedRequests"
        }
    }
}
