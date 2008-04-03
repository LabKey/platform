package org.labkey.api.security;

import org.labkey.api.util.GUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: Aug 23, 2007
 * Time: 2:08:17 PM
 */
public class TokenAuthentication
{
    private static Map<String, User> _tokenMap = new HashMap<String, User>();

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
