/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: adam
 * Date: Aug 23, 2007
 * Time: 2:08:17 PM
 */
public class TokenAuthentication
{
    private final static Map<String, User> _tokenMap = new ConcurrentHashMap<>();

    // Generate a random token associated with this user/session.  We store the token in a map
    // associated with the user.  Later, we will validate the token by checking the map and
    // retrieving the user if it's valid.  We also create a TokenHolder and stick it in the session
    // so our token expires at the same time the LabKey session expires.
    public static String createToken(HttpServletRequest request, User user)
    {
        String token = GUID.makeHash();
        _tokenMap.put(token, user);
        TokenHolder holder = new TokenHolder(token);
        request.getSession().setAttribute("token", holder);

        return token;
    }

    public static User getUserForToken(String token)
    {
        return _tokenMap.get(token);
    }

    public static void invalidateToken(String token)
    {
        _tokenMap.remove(token);
    }

    private static class TokenHolder implements HttpSessionBindingListener
    {
        String _token;

        private TokenHolder(String token)
        {
            _token = token;
        }

        public void valueBound(HttpSessionBindingEvent httpSessionBindingEvent)
        {
            // Do nothing
        }

        public void valueUnbound(HttpSessionBindingEvent httpSessionBindingEvent)
        {
            invalidateToken(_token);
        }
    }
}
