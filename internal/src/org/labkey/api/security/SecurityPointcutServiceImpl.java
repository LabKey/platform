package org.labkey.api.security;

import org.labkey.api.module.Module;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.CSRFException;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.ViewServlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

public class SecurityPointcutServiceImpl implements SecurityPointcutService
{
    @Override
    public boolean beforeResolveAction(HttpServletRequest req, HttpServletResponse res, Module m, String controller, String action)
    {
        return true;
    }

    @Override
    public boolean beforeProcessRequest(HttpServletRequest req, HttpServletResponse res)
    {
        if (!ViewServlet.validChars(req))
        {
            BlacklistFilter.handleBadRequest(req);
            return sendError(res, HttpServletResponse.SC_BAD_REQUEST, "Invalid characters in request.");
        }

        if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_BLOCKER))
        {
           if (BlacklistFilter.isOnBlacklist(req))
               return sendError(res, SC_GONE,"Try again later.");
        }
        return true;
    }


    @Override
    public void afterProcessRequest(HttpServletRequest req, HttpServletResponse res)
    {
        if (res.getStatus() == SC_NOT_FOUND)
            BlacklistFilter.handleNotFound(req);
        else if (res.getStatus() == SC_UNAUTHORIZED || res.getStatus() == SC_FORBIDDEN)
        {
            Object ex = req.getAttribute(ExceptionUtil.REQUEST_EXCEPTION_ATTRIBUTE);
            if (ex instanceof CSRFException)
                BlacklistFilter.handleBadRequest(req);
        }
    }


    private boolean sendError(HttpServletResponse res, int status, String message)
    {
        try
        {
            res.setStatus(status);
            res.setContentType("text/plain");
            res.getWriter().write(message);
        }
        catch (IOException x)
        {
            /* pass */
        }
        return false;
    }
}
