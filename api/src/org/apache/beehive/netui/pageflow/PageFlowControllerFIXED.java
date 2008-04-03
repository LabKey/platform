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