/*
 * Copyright (c) 2012-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.LockManager;
import org.labkey.api.security.AuthenticatedRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.concurrent.Callable;

/**
 * User: matthewb
 * Date: 2012-01-24
 * Time: 3:03 PM
 */
public class SessionHelper
{
    private static final LockManager<HttpSession> LOCK_MANAGER = new LockManager<>();

    public static Object getSessionLock(HttpSession s)
    {
        // We're intentionally using LockManager to do the striping for us, but just plain synchronization around the
        // object that's returned instead of using the java.util.concurrent.Lock behavior
        return LOCK_MANAGER.getLock(s);
    }


    public static void setAttribute(HttpServletRequest req, String key, Object value, boolean createSession)
    {
        HttpSession s = req.getSession(createSession);
        if (null == s)
            return;
        s.setAttribute(key,value);
    }


    /** If value is not found in session it is created and added */
    public static <Q> Q getAttribute(@NotNull HttpServletRequest req, @NotNull String key, @Nullable Callable<Q> initializeValue)
    {
        HttpSession s = req.getSession(true);
        if (null == s)
            return null;
        synchronized (getSessionLock(s))
        {
            Q value = (Q)s.getAttribute(key);
            if (null == value && initializeValue != null)
            {
                try
                {
                    value = initializeValue.call();
                    s.setAttribute(key, value);
                }
                catch (RuntimeException x)
                {
                    throw x;
                }
                catch (Exception x)
                {
                    throw new RuntimeException(x);
                }
            }
            return value;
        }
    }


    /** Does not modify the session */
    public static Object getAttribute(@NotNull HttpServletRequest req, @NotNull String key, @Nullable Object defaultValue)
    {
        HttpSession s = req.getSession(true);
        if (null == s)
            return defaultValue;
        Object value = s.getAttribute(key);
//        if (value instanceof Reference)
//            value = ((Reference)value).get();
        return null == value ? defaultValue : value;
    }


    public static void invalidateSession(@NotNull HttpServletRequest request)
    {
        if (request instanceof AuthenticatedRequest)
        {
            ((AuthenticatedRequest)request).invalidateSession();
        }
        else
        {
            HttpSession s = request.getSession(false);
            if (null != s)
                s.invalidate();
        }
    }
}
