/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

import org.labkey.api.view.ViewServlet;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/*
* User: adam
* Date: Jan 3, 2011
* Time: 3:36:13 PM
*/
public class UniqueID
{
    private static final AtomicLong SERVER_SESSION_COUNTER = new AtomicLong();

    // Initialize a unique counter to use within this request
    public static void initializeRequestScopedUID(HttpServletRequest request)
    {
        request.setAttribute(ViewServlet.REQUEST_UID_COUNTER, new AtomicInteger());
    }

    /*
        Provides a unique integer within the context of a single request. This is handy for generating identifiers that
        must be unique in the scope of a single request, including HTML class names and element ids, javascript function
        and variable names, etc. Uniqueness is guaranteed ONLY when the same request is provided; as a result:

        - Do not cache these UIDs or the content that uses them. Avoid putting them into formal caches, static variables,
          static collections, etc.
        - Do not serialize these UIDs or the content that uses them. Avoid putting them into the database, writing them
          to the file system, etc.

        If you need cacheable UIDs, consider using the getServerSessionScopedUID() method below.
    */
    public static int getRequestScopedUID(HttpServletRequest request)
    {
        AtomicInteger counter = (AtomicInteger)request.getAttribute(ViewServlet.REQUEST_UID_COUNTER);

        if (null == counter)
            throw new IllegalStateException("Unique request counter was not initialized");

        return counter.incrementAndGet();
    }

    /*
        Provides a unique long within the context of an entire server session. This is handy for generating identifiers
        (like the above) that need to be cached between requests in a server-tied cache. Uniqueness is guaranteed ONLY
        when used in the context of a single server session; as a result:

        - Do not serialize these UIDs or the content that uses them. Avoid putting them into the database, writing them
          to the file system, and caching them between servers or persisting them in user sessions.
     */
    public static long getServerSessionScopedUID()
    {
        return SERVER_SESSION_COUNTER.incrementAndGet();
    }
}
