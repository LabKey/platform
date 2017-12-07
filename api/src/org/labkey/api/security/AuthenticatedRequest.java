/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

package org.labkey.api.security;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.FilteredEnumeration;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SessionHelper;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * User: matthewb
 * Date: Feb 5, 2009
 * Time: 8:51:07 AM
 */
public class AuthenticatedRequest extends HttpServletRequestWrapper implements AutoCloseable
{
    private static final Logger _log = Logger.getLogger(AuthenticatedRequest.class);

    private final User _user;
    private boolean _loggedIn;
    private HttpSession _session = null;

    public static AuthenticatedRequest create(@NotNull HttpServletRequest request, @NotNull User user)
    {
        if (AuthenticatedRequest.class.desiredAssertionStatus() || _log.isDebugEnabled())
            return new AuthenticatedRequestDebug(request, user);
        return new AuthenticatedRequest(request, user);
    }

    private AuthenticatedRequest(@NotNull HttpServletRequest request, @NotNull User user)
    {
        super(request instanceof AuthenticatedRequest ? (HttpServletRequest)((AuthenticatedRequest)request).getRequest() : request);
        _user = user;
        _loggedIn = !_user.isGuest();
    }

    @Override
    public void close()
    {
    }

    // Filter out the internal parameters (e.g., X-LABKEY-CSRF), #30532
    @Override
    public Enumeration<String> getParameterNames()
    {
        return new FilteredEnumeration<>(super.getParameterNames(), paramName -> !PageFlowUtil.isInternalParameter(paramName));
    }

    // Filter out the internal parameters (e.g., X-LABKEY-CSRF), #30532
    @Override
    public Map<String, String[]> getParameterMap()
    {
        return super.getParameterMap().entrySet().stream()
            .filter(e -> !PageFlowUtil.isInternalParameter(e.getKey()))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    public Principal getUserPrincipal()
    {
        return _user;
    }


    /*
     * for login.post the session belongs to guest, but after login
     * we no longer want to use a guest session.  setAuthenticatedUsers() uses
     * this as a side channel to help make this work property
     *
     * CONSIDER implement copySessionAttributes parameter, currently the session is always
     * invalidated on login anyway
     */
    public void convertToLoggedInSession()
    {
        removeAttribute(GuestSessionMarker.class.getName());
        // don't treat like guest on next getSession call
        _loggedIn = true;
    }


    @Override
    public HttpSession getSession()
    {
        return this.getSession(true);
    }


    /**
     * We always return a wrapped session of some sort, makes it easier to set breakpionts
     * or add add logging.
     *
     * @param create
     * @return
     */
    @Override
    public HttpSession getSession(boolean create)
    {
        if (null != _session && isValid(_session))
            return _session;

        if (!create)
        {
            HttpSession servletContainerSession = super.getSession(false);
            if (null == servletContainerSession)
                return null;
        }

        HttpSession s;
        if (!isGuest())
        {
            s = makeRealSession();
            return s;
        }
        else if (isRobot())
        {
            s = robotSession();
            _session = s;
            return s;
        }
        else
        {
            s = guestSession();
        }

        if (_log.isDebugEnabled() && !(s instanceof SessionWrapper))
            s = new SessionWrapper(s);

        _session = s;
        return _session;
    }


    public void clearSession(boolean invalidate, Set<String> attributesToPreserve)
    {
        SessionHelper.clearSession((HttpServletRequest) getRequest(), invalidate, attributesToPreserve);
        _session = null;
    }


    public static boolean isValid(HttpSession s)
    {
        try
        {
            if (s instanceof SessionWrapper && !((SessionWrapper) s).isValid())
                return false;
            // getCreationTime() throws IllegalStateException if called on an invalidated session
            s.getCreationTime();
            return true;
        }
        catch (IllegalStateException x)
        {
            return false;
        }
    }


    private HttpSession makeRealSession()
    {
        try
        {
            HttpSession s = AuthenticatedRequest.super.getSession(true);
            if (null == s)
            {
                ServletRequest inner = null;
                try { inner = getInnermostRequest(); } catch (Exception x){}
                if (null == inner)
                    inner = getRequest();
                throw new NullPointerException("Request.getSession(true) returned null: " + inner.getClass().getName());
            }
            if (s.isNew())
            {
                _log.debug("Created HttpSession: " + s.getId() + " " + _user.getEmail());
            }
            return s;
        }
        catch (Exception x)
        {
            ExceptionUtil.logExceptionToMothership(AuthenticatedRequest.this, x);
            return null;
        }
    }


    private boolean isGuest()
    {
        return _user.isGuest() && !_loggedIn;
    }

    private boolean isRobot()
    {
        return PageFlowUtil.isRobotUserAgent(getHeader("User-Agent"));
    }


    // Methods below use reflection to pull Tomcat-specific implementation bits out of the request.  This can be helpful
    // for low-level, temporary debugging, but it's not portable across servlet containers or versions.

    public HttpServletRequest getInnermostRequest() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, NoSuchFieldException
    {
        Object innerRequest = invokeMethod(this, HttpServletRequestWrapper.class, "_getHttpServletRequest");
        return (HttpServletRequest)getFieldValue(innerRequest, "request");
    }

    public Map getAttributeMap() throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        return (Map)getFieldValue(getInnermostRequest(), "attributes");
    }

    // Uses reflection to access public or private fields by name.
    private Object getFieldValue(Object o, String fieldName) throws NoSuchFieldException, IllegalAccessException
    {
        Field field = o.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(o);
    }

    // Uses reflection to invoke public or private methods by name.
    private Object invokeMethod(Object o, Class clazz, String methodName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        Method method = clazz.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(o);
    }



    private static class SessionWrapper implements HttpSession
    {
        HttpSession _real = null;
        final long _creationTime = HeartBeat.currentTimeMillis();
        long _accessedTime = _creationTime;
        boolean _valid = true;
        final String id = GUID.makeHash();
        final Hashtable<String,Object> _attributes = new Hashtable<>();

        SessionWrapper(HttpSession s)
        {
            _real = s;
        }

        @Override
        public String getId()
        {
            return null==_real ? id : _real.getId();
        }

        @Override
        public long getCreationTime()
        {
            return null==_real ? _creationTime : _real.getCreationTime();
        }

        @Override
        public long getLastAccessedTime()
        {
            return null==_real ? _accessedTime : _real.getLastAccessedTime();
        }

        void access()
        {
            _accessedTime = HeartBeat.currentTimeMillis();
        }

        boolean isValid()
        {
            return _valid;
        }

        @Override
        public ServletContext getServletContext()
        {
            return null==_real ? null : _real.getServletContext();
        }

        @Override
        public void setMaxInactiveInterval(int i)
        {
            if (null != _real)
                _real.setMaxInactiveInterval(i);
        }

        @Override
        public int getMaxInactiveInterval()
        {
            return null == _real ? 60*60 :_real.getMaxInactiveInterval();
        }

        @Override
        public HttpSessionContext getSessionContext()
        {
            return null==_real ? null : _real.getSessionContext();
        }

        @Override
        public void putValue(String s, Object o)
        {
            setAttribute(s,o);
        }

        @Override
        public void setAttribute(String s, Object o)
        {
            if (_log.isDebugEnabled())
                _log.debug("Session.setAttribute(" + s + ", " + String.valueOf(o) + ")");
            if (null==_real)
                _attributes.put(s, o);
            else
                _real.setAttribute(s, o);
        }

        @Override
        public Object getAttribute(String s)
        {
            return null==_real ? _attributes.get(s) : _real.getAttribute(s);
        }

        @Override
        public Object getValue(String s)
        {
            return null==_real ? _attributes.get(s) : _real.getValue(s);
        }

        @Override
        public Enumeration<String> getAttributeNames()
        {
            return null==_real ? _attributes.keys() : _real.getAttributeNames();
        }

        @Override
        public String[] getValueNames()
        {
            return null==_real ? _attributes.keySet().toArray(new String[0]) : _real.getValueNames();
        }

        @Override
        public void removeAttribute(String s)
        {
            if (null != _real)
                _real.removeAttribute(s);
        }

        @Override
        public void removeValue(String s)
        {
            if (null != _real)
                _real.removeAttribute(s);
        }

        @Override
        public void invalidate()
        {
            _valid = false;
            _attributes.clear();
            if (null != _real)
            {
                _real.invalidate();
                _real = null;
            }
        }

        @Override
        public boolean isNew()
        {
            return null == _real || _real.isNew();
        }
    }


    /* Robots never get a real tomcat session */

    HttpSession robotSession()
    {
        return new SessionWrapper(null);
    }


    /*
     * GUEST sessions are highly suspect
     *
     * Keep new references to new guest sessions in a 'nursery'.  When the nursery is full
     * start expiring sessions that appear inactive.  Active sessions get handled by tomcat.
     */
    static final int NURSERY_SIZE = AppProps.getInstance().isDevMode() ? 100 : 1000;
    static final Map<String,GuestSessionMarker> _nursery = Collections.synchronizedMap(new LinkedHashMap<String, GuestSessionMarker>(2000, 0.5f, true)
    {
        /* returning true here means this session no longer needs to be tracked in the guest nursery
         * This may be because
         *    a) It has graduated to "active" and Tomcat can do normal session timeout
         *    b) The session is now invalid for some reason
         *    c) We've killed/expired it, so we don't need to track
         */
        @Override
        protected boolean removeEldestEntry(Entry<String, GuestSessionMarker> e)
        {
            try
            {
                String id = e.getKey();
                GuestSessionMarker marker = e.getValue();

                // if session is active, let tomcat manage it
                if (marker.isActive())
                    return true;

                // if not a real and valid session then there is no need to track it
                if (null == marker._session || !isValid(marker._session))
                    return true;

                // check for recycled session object?
                if (!StringUtils.equals(id, marker._session.getId()))
                    return true;

                // expire the session if the nursery is overflowing
                boolean remove = size() > NURSERY_SIZE;
                if (remove)
                    marker.expire();
                return remove;
            }
            catch (IllegalStateException x)
            {
                return true;    // inactive session
            }
            catch (Exception x)
            {
                ExceptionUtil.logExceptionToMothership(null, x);
                return true;
            }
        }
    });


    HttpSession guestSession()
    {
        HttpSession containerSession = makeRealSession();
        if (null == containerSession)
            return null;

        GuestSessionMarker guestSession;
        synchronized (_nursery)
        {
            guestSession = _nursery.get(containerSession.getId());
            if (null == guestSession  && containerSession.isNew())
            {
                guestSession = new GuestSessionMarker(this);
                containerSession.setAttribute(GuestSessionMarker.class.getName(), guestSession);
                _nursery.put(containerSession.getId(), guestSession);
            }
        }
        if (null != guestSession)
            guestSession.access();
        return containerSession;
    }



    // helper to avoid filling the _log
    private static Cache<String, String> logMessages = CacheManager.getCache(100, TimeUnit.MINUTES.toMillis(1), "GuestSession Messages");

    private static void _logGuestSession(String ip, String msg)
    {
        ip = StringUtils.defaultString(ip,"unknown");
        String prevMessage = logMessages.get(ip);
        if (StringUtils.equals(prevMessage, msg))
            return;
        logMessages.put(ip, msg);
        _log.warn(msg);
    }



    /*
     * This class is used to track guest sessions
     */
    private static class GuestSessionMarker implements HttpSessionBindingListener
    {
        HttpSession _session = null;
        final String _ip;
        final long[] _accessedTime = new long[5];
        final Map<String, String> _info;

        GuestSessionMarker(AuthenticatedRequest r)
        {
            // do not hold onto request
            _ip = r.getRemoteAddr();
            _info = new TreeMap<>();
            Enumeration<String> e = r.getHeaderNames();
            while (e.hasMoreElements())
            {
                String name = e.nextElement();
                if (StringUtils.startsWithIgnoreCase(name, "x-"))
                    _info.put(name, r.getHeader(name));
            }
            _info.put("user-agent", r.getHeader("User-Agent"));
        }

        @Override
        public void valueBound(HttpSessionBindingEvent e)
        {
            _session = e.getSession();
        }

        @Override
        public void valueUnbound(HttpSessionBindingEvent e)
        {
            if (null != _session)
                _nursery.remove(_session.getId());
            _session = null;
        }

        void expire()
        {
            HttpSession s = _session;
            if (null == s)
                return;
            _session = null;

            try
            {
                long age = HeartBeat.currentTimeMillis() - s.getCreationTime();
                if (age < TimeUnit.HOURS.toMillis(1))
                {
                    //String q = PageFlowUtil.toQueryString(_info.entrySet());
                    StringBuilder sb = new StringBuilder();
                    for (Entry<String, String> e : _info.entrySet())
                        sb.append(e.getKey()).append("=").append(e.getValue()).append("&");
                    sb.setLength(sb.length()-1);
                    _logGuestSession(_ip, "Due to server load, guest session was forced to expire: " + _ip + "?" + sb.toString());
                }
                s.setMaxInactiveInterval(0);
            }
            catch (IllegalStateException x)
            {
                /* */
            }
        }

        synchronized void access()
        {
            long t = HeartBeat.currentTimeMillis();
            if (t != _accessedTime[_accessedTime.length-1])
                System.arraycopy(_accessedTime,1,_accessedTime,0,_accessedTime.length-1);
            _accessedTime[_accessedTime.length-1] = t;
        }

        synchronized boolean isActive()
        {
            return _accessedTime[0] != 0;
        }
    }



    private static class AuthenticatedRequestDebug extends AuthenticatedRequest
    {
        // for debugging
        private final Thread _allocationThread;
        private boolean _closed = false;

        AuthenticatedRequestDebug(HttpServletRequest request, User user)
        {
            super(request, user);
            _allocationThread = Thread.currentThread();
        }


        @Override
        public void close()
        {
            checkClosed();
            _closed = true;
        }

        private boolean checkClosed()
        {
            if (_closed)
            {
                if (_allocationThread != Thread.currentThread())
                    throw new IllegalStateException("Using request closed by a different thread");
                else
                    throw new IllegalStateException("Requset has been closed");
            }
            return true;
        }

        @Override
        public void setAttribute(String name, Object o)
        {
            checkClosed();
            super.setAttribute(name, o);
        }

        @Override
        public HttpSession getSession(boolean create)
        {
            checkClosed();
            return super.getSession(create);
        }

        @Override
        public String getRequestURI()
        {
            checkClosed();
            return super.getRequestURI();
        }

        @Override
        public StringBuffer getRequestURL()
        {
            checkClosed();
            return super.getRequestURL();
        }

        @Override
        public Object getAttribute(String name)
        {
            checkClosed();
            return super.getAttribute(name);
        }

        @Override
        public String getParameter(String name)
        {
            checkClosed();
            return super.getParameter(name);
        }

        @Override
        public Map<String, String[]> getParameterMap()
        {
            checkClosed();
            return super.getParameterMap();
        }

        @Override
        public Enumeration<String> getParameterNames()
        {
            checkClosed();
            return super.getParameterNames();
        }

        @Override
        public String[] getParameterValues(String name)
        {
            checkClosed();
            return super.getParameterValues(name);
        }

        @Override
        public void removeAttribute(String name)
        {
            checkClosed();
            super.removeAttribute(name);
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path)
        {
            checkClosed();
            return super.getRequestDispatcher(path);
        }
    }
}
