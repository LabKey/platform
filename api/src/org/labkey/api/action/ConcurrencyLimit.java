/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.action;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Caps the number of requests executing an action at the same time, server-wide, for actions expensive enough that a
 * few simultaneous callers can exhaust heap or the request thread pool.
 * <p>
 * {@link ConcurrencyLimiter} enforces the limit from {@link SpringActionController#handleRequest}, after the permission
 * check and around the whole action including rendering. A request that can't get a permit within
 * {@link #timeoutSeconds()} is rejected with a 429 and never executes the action.
 * <p>
 * Not inherited: a subclass of an annotated action is unlimited unless it declares its own {@code @ConcurrencyLimit}.
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConcurrencyLimit
{
    int DEFAULT_TIMEOUT_SECONDS = 2;
    int DEFAULT_RETRY_AFTER_SECONDS = 30;
    String DEFAULT_MESSAGE = "";

    /** Maximum number of requests allowed to execute this action concurrently, across the whole server. Must be positive. */
    int value();

    /**
     * How long an incoming request waits for a permit before it's rejected with a 429. Keep it short - a parked thread
     * still consumes a connector thread, so a long wait just moves the exhaustion problem.
     */
    long timeoutSeconds() default DEFAULT_TIMEOUT_SECONDS;

    /** Value sent in the {@code Retry-After} response header when a request is rejected. */
    int retryAfterSeconds() default DEFAULT_RETRY_AFTER_SECONDS;

    /** Message sent to the client when a request is rejected. Defaults to a generic message when not specified. */
    String message() default DEFAULT_MESSAGE;
}
