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
package org.labkey.api.view;

/**
 * The server is refusing to handle this request right now because too many similar requests are already in flight.
 * Rendered as an HTTP 429 with a {@code Retry-After} header telling the client how long to wait.
 *
 * @see org.labkey.api.action.ConcurrencyLimit
 */
public class TooManyRequestsException extends HttpStatusException
{
    public static final int SC_TOO_MANY_REQUESTS = 429;

    private final int _retryAfterSeconds;

    public TooManyRequestsException(String message, int retryAfterSeconds)
    {
        super(message, null, SC_TOO_MANY_REQUESTS);
        _retryAfterSeconds = retryAfterSeconds;
    }

    /** @return the number of seconds to advertise in the {@code Retry-After} response header */
    public int getRetryAfterSeconds()
    {
        return _retryAfterSeconds;
    }
}
