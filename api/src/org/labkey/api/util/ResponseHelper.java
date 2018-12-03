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
