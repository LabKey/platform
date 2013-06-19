/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.settings.AppProps;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: Matthew
 * Date: Feb 20, 2010
 * Time: 3:22:42 PM
 *
 * requires PollingFilter to be installed in web.xml
 *
 *
 * Lifetime issue is a little tricky.  I think this is a reasonable rule.  As long as the owning object exists
 * the PollKey won't be deleted (except by explicit done()).  Once the object is no longer owned, it will
 * go away if it has not been access for > N minutes.
 */
public class PollingUtil
{
    private static final String ATTR_NAME = "PollingUtil.map";
    private static final Map<String,PollKey> globalMap = newSynchronizedMap();


    public static PollKey createKey(@Nullable HttpSession session)
    {
        return createKey(session, null, null);
    }


    public static PollKey createKey(@Nullable HttpSession session, @Nullable String key,Object scope)
    {
        Map<String,PollKey> m = getMap(session);
        return new PollKey(m, key, scope);
    }

    
    public static class PollKey
    {
        private final WeakReference<Object> ref;
        private final String key;
        private final Map map;
        private boolean done = false;
        private boolean clearOnRead;
        private long lastAccessed;
        private Object json = null;


        private PollKey(Map<String,PollKey> m, String key, Object ref)
        {
            this.ref = new WeakReference<>(ref);
            this.key = null==key ? GUID.makeHash() : key;
            this.map = m;
            accessed();
            m.put(this.key, this);
        }


        public void done()
        {
            done = true;
        }


        private boolean isHeld()
        {
            return !done && null != ref.get();
        }

        public synchronized void setJson(Object o)
        {
            setJson(o, false);
        }

        public synchronized void setJson(Object o, boolean clearOnRead)
        {
            accessed();
            json = o;
            this.clearOnRead = clearOnRead;
            notifyAll();
        }

        public synchronized Object getJson()
        {
            accessed();
            Object ret = json;
            if (null != ret && clearOnRead)
            {
                json = null;
                if (!isHeld())
                    map.remove(key);
            }
            return ret;
        }

        public synchronized Object getJson(long ms)
        {
            accessed();
            if (null == json)
            {
                try
                {
                    wait(Math.min(ms,1000));
                }
                catch (InterruptedException x) { /* */ }
            }
            return json;
        }

        void accessed()
        {
            lastAccessed = HeartBeat.currentTimeMillis();
        }

        public String getUrl()
        {
            return AppProps.getInstance().getContextPath() + Path.parse(key).encode("/","") + ".poll";
        }


        public void close()
        {
            map.remove(this.key);
        }
    }


    private static class PollingMap extends LinkedHashMap<String,PollKey>
    {
        PollingMap()
        {
            super(10, 0.5F, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PollKey> eldest)
        {
            if (size()<=1)
                return false;
            PollKey key = eldest.getValue();
            // if key is still 'owned' bump to front of queue
            if (key.isHeld())
            {
                key.map.get(key.key);
                return false;
            }
            long age = (HeartBeat.currentTimeMillis() - key.lastAccessed)/(60*1000);
            return age > 30;
        }
    }
    

    private static Map<String, PollKey> newSynchronizedMap()
    {
        return Collections.synchronizedMap(new PollingMap());
    }


    private static Map<String, PollKey> getMap(HttpSession session)
    {
        if (null == session)
            return globalMap;
        
        synchronized (session)
        {
            Map<String,PollKey> m = (Map<String,PollKey>)session.getAttribute(ATTR_NAME);
            if (null == m)
            {
                m = newSynchronizedMap();
                session.setAttribute(ATTR_NAME, m);
            }
            return m;
        }
    }


    /**
     * NOTE: caller should be prepared for SC_NOT_FOUND, it will often mean the process in question has simply completed
     */
    public static class PollingFilter implements javax.servlet.Filter
    {
        public void init(FilterConfig filterConfig) throws ServletException
        {
        }

        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
        {
            String servletPath = ((HttpServletRequest)request).getServletPath();    // decoded already, leading '/'
            if (servletPath.endsWith(".poll"))
                servletPath = servletPath.substring(0,servletPath.length()-".poll".length());
            String key = StringUtils.stripStart(servletPath,"/");

            HttpSession s = ((HttpServletRequest)request).getSession();
            Map<String,PollKey> m = getMap(s);
            PollKey pk = null==m ? null : m.get(key);
                                                         
            if (null == pk)
            {
                m = getMap(null);
                pk = m.get(key);
            }

            if (null == pk)
            {
                ((HttpServletResponse)response).setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            response.setContentType("application/json;charset=UTF-8");
            ((HttpServletResponse)response).setHeader("Expires", "Sun, 01 Jan 2000 00:00:00 GMT");
            PrintWriter out = response.getWriter();

            Object json = pk.getJson();
            if (json == null)
            {
                out.write("{}");
            }
            else if (json instanceof String)
            {
                out.write((String)json);
                out.write((String)json);
            }
            else if (json instanceof JSONObject)
            {
                out.write(json.toString());
            }
            else
            {
                out.write(( new JSONObject(json)).toString());
            }
            out.flush();
        }

        public void destroy()
        {
        }
    }
}