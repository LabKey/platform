/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.log4j.spi.LoggingEvent;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.LinkedList;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Aug 18, 2009
 * Time: 2:18:21 PM
 */
public class SessionAppender extends org.apache.log4j.AppenderSkeleton
{
    static class AppenderInfo
    {
        AppenderInfo(String key, boolean on)
        {
            this.key = key;
            this.on = on;
        }
        String key;
        boolean on;
        List list = Collections.synchronizedList(new LinkedList());
    }

    static ThreadLocal<AppenderInfo> localInfo = new ThreadLocal();

    protected void append(LoggingEvent event)
    {
        AppenderInfo info = localInfo.get();
        if (null == info || !info.on)
            return;
        info.list.add(event);
        if (info.list.size() > 1000)
            info.list.remove(0);
    }


    public void close()
    {
    }


    public boolean requiresLayout()
    {
        return false;
    }


    public static LoggingEvent[] getLoggingEvents(HttpServletRequest request)
    {
        AppenderInfo info = _getLoggingForSession(request);
        if (null == info)
            return new LoggingEvent[0];
        // list.toArray(list.size()) is not thread safe
        return (LoggingEvent[])info.list.toArray(new LoggingEvent[0]);
    }


    public static void setLoggingForSession(HttpServletRequest request, boolean on)
    {
        _getLoggingForSession(request).on = on;
    }


    public static boolean isLogging(HttpServletRequest request)
    {
        return _getLoggingForSession(request).on;
    }
    

    private static AppenderInfo _getLoggingForSession(HttpServletRequest request)
    {
        HttpSession session = request.getSession(true);
        AppenderInfo info;
        synchronized (session)
        {
            info = (AppenderInfo)session.getAttribute("SessionAppender#info");
            if (null == info)
            {
                info = new AppenderInfo(session.getId(), false);
                session.setAttribute("SessionAppender#info",info);
            }
        }
        return info;
    }

    
    // set up logging for this thread, based on session settings
    public static void initThread(HttpServletRequest request)
    {
        localInfo.set(_getLoggingForSession(request));
    }
}
