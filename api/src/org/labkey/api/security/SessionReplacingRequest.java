package org.labkey.api.security;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;

/**
 * Created by adam on 4/3/2016.
 */
public class SessionReplacingRequest extends HttpServletRequestWrapper
{
    private final HttpSession _session;

    public SessionReplacingRequest(HttpServletRequest request, HttpSession session)
    {
        super(request);
        _session = session;
    }

    @Override
    public HttpSession getSession(boolean create)
    {
        return _session;
    }

    @Override
    public HttpSession getSession()
    {
        return _session;
    }
}
