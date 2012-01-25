package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.concurrent.Callable;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2012-01-24
 * Time: 3:03 PM
 */
public class SessionHelper
{
    private static final Object[] _locks = new Object[23];
    static
    {
        for (int i=0 ; i<_locks.length ; i++)
            _locks[i] = new Object();
    }

    public static Object getSessionLock(HttpSession s)
    {
        return _locks[(0x7fff&s.hashCode())%_locks.length];
    }


    public static void setAttribute(HttpServletRequest req, String key, Object value, boolean createSession)
    {
        HttpSession s = req.getSession(createSession);
        if (null == s)
            return;
        s.setAttribute(key,value);
    }


    /** If value is not found in session it is created and added */
    public static Object getAttribute(@NotNull HttpServletRequest req, @NotNull String key, @Nullable Callable initializeValue)
    {
        HttpSession s = req.getSession(true);
        if (null == s)
            return null;
        synchronized (getSessionLock(s))
        {
            Object value = s.getAttribute(key);
            if (null == value)
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
    public Object getAttribute(@NotNull HttpServletRequest req, @NotNull String key, @Nullable Object defaultValue)
    {
        HttpSession s = req.getSession(true);
        if (null == s)
            return defaultValue;
        Object value = s.getAttribute(key);
//        if (value instanceof Reference)
//            value = ((Reference)value).get();
        return null == value ? defaultValue : value;
    }
}