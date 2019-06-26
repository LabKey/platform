/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.util;


// place to centralize some common usages

import javax.servlet.http.HttpServletResponse;
import java.time.Duration;

public class ResponseHelper
{
    public static void setNoCache(HttpServletResponse response)
    {
        response.setHeader("Expires", "Sun, 01 Jan 2000 00:00:00 GMT");
        // facebook uses  private, no-cache, no-store, must-revalidate
        // google uses    no-cache, no-store, max-age=0, must-revalidate
        // amazon uses    no-cache, no-store
        response.setHeader("Cache-Control", "private, no-cache, no-store, max-age=0, must-revalidate");
        response.setHeader("Pragma", "no-cache");
    }

    // NOTE: this is actually weaker than setNoCache(), but does allow browser caching, use setNoCache() unless you have a reason
    // NOTE: see Bug 5610 & 6179 for one such reason
    public static void setPrivate(HttpServletResponse response)
    {
        response.setHeader("Expires", "");
        response.setHeader("Cache-Control", "private");
        response.setHeader("Pragma", "private");
        response.addHeader("Vary", "Cookie");
    }

    public static void setPrivate(HttpServletResponse response, Duration duration)
    {
        response.setDateHeader("Expires", HeartBeat.currentTimeMillis() + duration.toMillis());
        response.setHeader("Cache-Control", "private");
        response.setHeader("Pragma", "private");
        response.addHeader("Vary", "Cookie");
    }

    public static void setPrivate(HttpServletResponse response, int days)
    {
        response.setDateHeader("Expires", HeartBeat.currentTimeMillis() + Duration.ofDays(days).toMillis());
        response.setHeader("Cache-Control", "private");
        response.setHeader("Pragma", "private");
        response.addHeader("Vary", "Cookie");
    }

    // NOTE: this is actually weaker than NoCache, but does allow browser caching
    public static void setPublic(HttpServletResponse response)
    {
        response.setHeader("Expires", "");
        response.setHeader("Cache-Control", "public");
        response.setHeader("Pragma", "");
    }

    public static void setPublicStatic(HttpServletResponse response, int days)
    {
        response.setDateHeader("Expires", HeartBeat.currentTimeMillis() + Duration.ofDays(days).toMillis());
        response.setHeader("Cache-Control", "public");
        response.setHeader("Pragma", "");
    }
}
