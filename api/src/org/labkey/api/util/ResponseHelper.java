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

import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.StringTokenizer;

import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;

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
        response.setHeader("Cache-Control", "private, max-age=" + duration.toSeconds());
        response.setHeader("Pragma", "private");
        response.addHeader("Vary", "Cookie");
    }

    public static void setPrivate(HttpServletResponse response, int days)
    {
        setPrivate(response, Duration.ofDays(days));
    }

    // NOTE: this is actually weaker than NoCache, but does allow browser caching
    public static void setPublic(HttpServletResponse response)
    {
        response.setHeader("Expires", "");
        response.setHeader("Cache-Control", "public");
        response.setHeader("Pragma", "");
    }

    public static void setPublicStatic(HttpServletResponse response, Duration duration)
    {
        response.setDateHeader("Expires", HeartBeat.currentTimeMillis() + duration.toMillis());
        response.setHeader("Cache-Control", "public, max-age=" + duration.toSeconds());
        response.setHeader("Pragma", "");
    }

    public static void setPublicStatic(HttpServletResponse response, int days)
    {
        setPublicStatic(response, Duration.ofDays(days));
    }

    /**
     * Check if the conditions specified in the optional If headers are
     * satisfied.
     *
     * @param context
     * @param eTag
     * @param lastModified
     * @return boolean true if the resource meets all the specified conditions,
     *         and false if any of the conditions is not satisfied, in which case
     *         request processing is stopped
     */
    public static boolean checkIfHeaders(ViewContext context,
                                         String eTag, long lastModified)
            throws IOException
    {
        return checkIfHeaders(context.getRequest(), context.getResponse(), eTag, lastModified);
    }

    /**
     * Check if the conditions specified in the optional If headers are
     * satisfied.
     *
     * @param request   The servlet request we are processing
     * @param response  The servlet response we are creating
     * @param eTag
     * @param lastModified
     * @return boolean true if the resource meets all the specified conditions,
     *         and false if any of the conditions is not satisfied, in which case
     *         request processing is stopped
     */
    public static boolean checkIfHeaders(HttpServletRequest request,
                                         HttpServletResponse response,
                                         String eTag, long lastModified)
        throws IOException
    {
        return checkIfMatch(request, response, eTag)
                && checkIfModifiedSince(request, response, eTag, lastModified)
                && checkIfNoneMatch(request, response, eTag)
                && checkIfUnmodifiedSince(request, response, lastModified);
    }

    /**
     * Check if the if-match condition is satisfied.
     *
     *
     * @param context
     * @param eTag
     * @return boolean true if the resource meets the specified condition,
     *         and false if the condition is not satisfied, in which case request
     *         processing is stopped
     */
    private static boolean checkIfMatch(HttpServletRequest request, HttpServletResponse response, String eTag) throws IOException
    {
        String headerValue = request.getHeader("If-Match");
        if (headerValue != null)
        {
            if (headerValue.indexOf('*') == -1)
            {
                StringTokenizer commaTokenizer = new StringTokenizer
                        (headerValue, ",");
                boolean conditionSatisfied = false;

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens())
                {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

                // If none of the given ETags match, 412 Precondition failed is
                // sent back
                if (!conditionSatisfied)
                {
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * Check if the if-modified-since condition is satisfied.
     *
     * @return boolean true if the resource meets the specified condition,
     *         and false if the condition is not satisfied, in which case request
     *         processing is stopped
     */
    private static boolean checkIfModifiedSince(HttpServletRequest request, HttpServletResponse response, String eTag, long lastModified)
    {
        try
        {
            long headerValue = request.getDateHeader("If-Modified-Since");
            if (headerValue != -1)
            {
                // If an If-None-Match header has been specified, if modified since
                // is ignored.
                if ((request.getHeader("If-None-Match") == null))
                {
                    if (lastModified < headerValue + 1000)
                    {
                        // The entity has not been modified since the date
                        // specified by the client. This is not an error case.
                        response.setStatus(SC_NOT_MODIFIED);
                        response.addHeader("ETag", eTag);
                        return false;
                    }
                }
            }
        }
        catch (IllegalArgumentException illegalArgument)
        {
            return true;
        }
        return true;
    }


    /**
     * Check if the if-none-match condition is satisfied.
     *
     * @return boolean true if the resource meets the specified condition,
     *         and false if the condition is not satisfied, in which case request
     *         processing is stopped
     */
    private static boolean checkIfNoneMatch(HttpServletRequest request, HttpServletResponse response, String eTag) throws IOException
    {
        String headerValue = request.getHeader("If-None-Match");
        if (headerValue != null)
        {
            boolean conditionSatisfied = false;

            if (!headerValue.equals("*"))
            {
                StringTokenizer commaTokenizer = new StringTokenizer(headerValue, ",");
                while (!conditionSatisfied && commaTokenizer.hasMoreTokens())
                {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }
            }
            else
            {
                conditionSatisfied = true;
            }

            if (conditionSatisfied)
            {
                // For GET and HEAD, we should respond with
                // 304 Not Modified.
                // For every other method, 412 Precondition Failed is sent
                // back.
                String method = request.getMethod();
                if (("GET".equals(method)) || ("HEAD".equals(method)))
                {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", eTag);
                    return false;
                }
                response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                return false;
            }
        }
        return true;
    }


    /**
     * Check if the if-unmodified-since condition is satisfied.
     *
     * @return boolean true if the resource meets the specified condition,
     *         and false if the condition is not satisfied, in which case request
     *         processing is stopped
     */
    private static boolean checkIfUnmodifiedSince(HttpServletRequest request, HttpServletResponse response, long lastModified) throws IOException
    {
        try
        {
            long headerValue = request.getDateHeader("If-Unmodified-Since");
            if (headerValue != -1)
            {
                if (lastModified >= (headerValue + 1000))   // UNDONE: why the +1000???
                {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        }
        catch (IllegalArgumentException illegalArgument)
        {
            return true;
        }
        return true;
    }

}
