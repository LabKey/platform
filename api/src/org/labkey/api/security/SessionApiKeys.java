/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.labkey.api.util.GUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: adam
 * Date: Aug 23, 2007
 * Time: 2:08:17 PM
 */
public class SessionApiKeys
{
    private final static Map<String, HttpSession> _apiKeyMap = new ConcurrentHashMap<>();
    private final static Object LOCK = new Object();
    private final static String SESSION_KEY = "apikeys";

    // Generate a random api key associated with this session. We store the api key in a map associated with the session.
    // Later, we will validate the api key by checking the map and retrieving the session if it's valid. We also create an
    // ApiKeyHolder and stick it in the session so our api keys all expire at the same time the LabKey session expires.
    public static String createApiKey(HttpServletRequest request)
    {
        String apiKey = "session:" + GUID.makeHash();
        HttpSession session = request.getSession(true);
        _apiKeyMap.put(apiKey, session);

        synchronized (LOCK)
        {
            @SuppressWarnings("unchecked")
            ApiKeyHolder holder = (ApiKeyHolder)session.getAttribute(SESSION_KEY);

            if (null == holder)
            {
                List<String> apiKeys = new LinkedList<>();
                holder = new ApiKeyHolder(apiKeys);
                session.setAttribute(SESSION_KEY, holder);
            }

            holder.getApiKeys().add(apiKey);
        }

        return apiKey;
    }

    public static HttpSession getSession(String apiKey)
    {
        return _apiKeyMap.get(apiKey);
    }

    private static void invalidateApiKeys(List<String> apiKeys)
    {
        synchronized (LOCK)
        {
            apiKeys.forEach(_apiKeyMap::remove);
        }
    }

    private static class ApiKeyHolder implements HttpSessionBindingListener
    {
        private final List<String> _apiKeys;

        private ApiKeyHolder(List<String> apiKeys)
        {
            _apiKeys = apiKeys;
        }

        private List<String> getApiKeys()
        {
            return _apiKeys;
        }

        public void valueBound(HttpSessionBindingEvent httpSessionBindingEvent)
        {
            // Do nothing
        }

        public void valueUnbound(HttpSessionBindingEvent httpSessionBindingEvent)
        {
            invalidateApiKeys(_apiKeys);
        }
    }
}
