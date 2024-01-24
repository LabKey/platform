//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.labkey.api.view;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Simple redirector to redirect and forward from legacy context paths to the root context */
public class RedirectorServlet extends HttpServlet
{
    private final String _legacyContextPath;

    public RedirectorServlet(String legacyContextPath)
    {
        if (!legacyContextPath.startsWith("/") || legacyContextPath.length() < 2)
        {
            throw new IllegalArgumentException("Invalid legacy context path: " + legacyContextPath);
        }
        _legacyContextPath = legacyContextPath;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {

        if ("get".equalsIgnoreCase(request.getMethod()))
        {
            // Send a redirect to let the client know there's a new preferred URL
            String originalUrl = request.getRequestURL().toString();
            String redirectUrl = originalUrl.replaceFirst(_legacyContextPath, "");

            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader("Location", redirectUrl);
        }
        else
        {
            // For non-GETs, use a forward so that we don't lose POST parameters, etc
            String originalUri = request.getRequestURI();
            String forwardUri = originalUri.replaceFirst(_legacyContextPath, "");

            getServletContext().getRequestDispatcher(forwardUri).forward(request, response);
        }
    }
}
