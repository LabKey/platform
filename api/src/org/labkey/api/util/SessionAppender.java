/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.ConcurrentReferenceHashMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * User: matthewb
 * Date: Aug 18, 2009
 * Time: 2:18:21 PM
 */
@Plugin(name = "SessionAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class SessionAppender extends AbstractAppender
{
    @PluginFactory
    public static SessionAppender createAppender(@PluginAttribute("name") String name,
                                                 @PluginElement("Layout") Layout<? extends Serializable> layout,
                                                 @PluginElement("Filter") final Filter filter)
    {
        return new SessionAppender(name, filter, layout, false, null);
    }

    protected SessionAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties)
    {
        super(name, filter, layout, ignoreExceptions, properties);
    }

    static class AppenderInfo implements Serializable
    {
        AppenderInfo(String key, boolean on)
        {
            this.key = key;
            this.on = on;
        }

        final String key;
        boolean on;
        final Map<LogEvent, String> eventIdMap = new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK)
        {
            @Override
            public String put(LogEvent key, String value)
            {
                // Safeguard against runaway size. ConcurrentReferenceHashMap does not have removeEldestEntry so have
                // to randomly remove an entry
                if (size() > 1000)
                    remove(0);
                return super.put(key, value);
            }
        };
        int eventId=0;
    }

    private static final ThreadLocal<AppenderInfo> localInfo = new ThreadLocal<>();

    // AppenderInfos are thread-local variables initialized with the user session. This Map allows background threads to share an
    // active session's appenderInfo to output logs to that session's SessionAppender. When the session is ended, the
    // thread-local appenderInfo will be released and this map, which uses weak references, will allow gc to remove and
    // reclaim the appenderInfo entry.
    private static final Map<String, AppenderInfo> appenderInfos = new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    // Makes appenderInfo available outside this thread
    private static void registerAppenderInfo(AppenderInfo info)
    {
        appenderInfos.put(info.key, info);
    }

    // If accessing appenderInfo using this function ensure operations on the appenderInfo are thread safe as multiple
    // threads could be accessing it.
    public static AppenderInfo getAppenderInfoByKey(String key)
    {
        return appenderInfos.get(key);
    }

    public static String getAppendingInfoKey(HttpServletRequest request)
    {
        return _getLoggingForSession(request).key;
    }

    @Override
    public void append(LogEvent event)
    {
        AppenderInfo info = localInfo.get();
        if (null == info || !info.on)
            return;
        synchronized (info.eventIdMap)
        {
            info.eventIdMap.put(event, String.valueOf(++info.eventId));
        }
    }


    public static Map<LogEvent, String> getLoggingEvents(HttpServletRequest request)
    {
        AppenderInfo info = _getLoggingForSession(request);
        if (null == info)
            return Collections.emptyMap();
        synchronized (info.eventIdMap)
        {
            return info.eventIdMap;
        }
    }

    public static void setLoggingForSession(HttpServletRequest request, boolean on)
    {
        AppenderInfo info = _getLoggingForSession(request);
        if (null != info)
            info.on = on;
    }

    public static boolean isLogging(HttpServletRequest request)
    {
        AppenderInfo info = _getLoggingForSession(request);
        return null != info && info.on;
    }

    private static AppenderInfo _getLoggingForSession(@NotNull HttpServletRequest request)
    {
        HttpSession session = request.getSession(true);
        if (null == session)
            return null;
        synchronized (SessionHelper.getSessionLock(session))
        {
            AppenderInfo info = (AppenderInfo)session.getAttribute("SessionAppender#info");
            if (null == info)
            {
                info = new AppenderInfo(session.getId(), false);
                session.setAttribute("SessionAppender#info",info);
            }
            registerAppenderInfo(info);
            return info;
        }
    }

    public static void removeAppenderInfo()
    {
        localInfo.remove();
    }

    // set up logging for this thread, based on an already existing AppenderInfo
    public static void initThread(AppenderInfo info)
    {
        localInfo.set(info);
    }
    
    // set up logging for this thread, based on session settings
    public static void initThread(HttpServletRequest request)
    {
        localInfo.set(_getLoggingForSession(request));
    }
}
