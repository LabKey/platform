/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.GUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: adam
 * Date: Aug 23, 2007
 */
public abstract class SessionKeyManager<T>
{
    private final Map<String, T> KEY_MAP = new ConcurrentHashMap<>();
    private final Object SESSION_LOCK = new Object();

    protected abstract @NotNull String getSessionAttributeName();
    protected abstract @Nullable String getKeyPrefix();
    protected abstract T validateContext(T context, String key);

    // Generate a random key, associate it with the provided context, and track the key in session to support invalidation.
    // All keys expire at the same time the LabKey session expires (explicit logout or session timeout).
    public String createKey(HttpServletRequest request, T context)
    {
        String prefix = getKeyPrefix();
        String key = (null != prefix ? prefix : "") + GUID.makeHash();
        HttpSession session = request.getSession(true);
        KEY_MAP.put(key, context);

        synchronized (SESSION_LOCK)
        {
            @SuppressWarnings("unchecked")
            KeyHolder holder = (KeyHolder)session.getAttribute(getSessionAttributeName());

            if (null == holder)
            {
                holder = new KeyHolder();
                session.setAttribute(getSessionAttributeName(), holder);
            }

            holder.addKey(key);
        }

        return key;
    }

    public @Nullable T getContext(String key)
    {
        T context = KEY_MAP.get(key);

        if (null != context)
            context = validateContext(context, key);

        return context;
    }

    public void invalidateKey(String key)
    {
        KEY_MAP.remove(key);
    }

    boolean isKeyInSession(HttpSession session, String key)
    {
        synchronized (SESSION_LOCK)
        {
            //noinspection unchecked
            KeyHolder holder = (KeyHolder) session.getAttribute(getSessionAttributeName());

            return (null != holder && holder.containsKey(key));
        }
    }

    private class KeyHolder implements HttpSessionBindingListener
    {
        private final Set<String> _keys = new HashSet<>();

        private void addKey(String key)
        {
            _keys.add(key);
        }

        private boolean containsKey(String key)
        {
            return _keys.contains(key);
        }

        public void valueBound(HttpSessionBindingEvent httpSessionBindingEvent)
        {
            // Do nothing
        }

        public void valueUnbound(HttpSessionBindingEvent httpSessionBindingEvent)
        {
            synchronized (SESSION_LOCK)
            {
                _keys.forEach(SessionKeyManager.this::invalidateKey);
                _keys.clear();
            }
        }
    }
}
