/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.apache.beehive.netui.pageflow;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.Serializable;

/**
 * Fix Beehive bug with unsynchronized access to _requestCount and _maxConcurrentRequestCount
 */

public class PageFlowControllerFIXED extends PageFlowController
{
    final LockObject _requestCountLock = new LockObject();

    boolean incrementRequestCount(HttpServletRequest request, HttpServletResponse response, ServletContext servletContext) throws IOException
    {
        synchronized(_requestCountLock)
        {
            return super.incrementRequestCount(request, response, servletContext);
        }
    }

    void decrementRequestCount(HttpServletRequest request)
    {
        synchronized(_requestCountLock)
        {
            super.decrementRequestCount(request);
        }
    }

    private static class LockObject implements Serializable {}
}