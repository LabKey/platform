package org.labkey.core.view;

import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.ShortURLService;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Looks for incoming URLs that match a short URL registered with ShortURLService. If so, the filter will bounce the
 * browser to the target URL.
 * User: jeckels
 * Date: 1/31/14
 */
public class ShortURLFilter implements Filter
{
    @Override
    public void destroy()
    {

    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest)req;
        HttpServletResponse response = (HttpServletResponse)resp;

        // Check for a matching shortURL
        String potentialShortURL = (null==request.getServletPath()?"":request.getServletPath()) + (null==request.getPathInfo()?"":request.getPathInfo());

        // Strip off the leading slash and trailing ".url"
        if (potentialShortURL.startsWith("/"))
        {
            potentialShortURL = potentialShortURL.substring(1);
        }
        if (potentialShortURL.toLowerCase().endsWith(ShortURLRecord.URL_SUFFIX))
        {
            potentialShortURL = potentialShortURL.substring(0, potentialShortURL.length() - ShortURLRecord.URL_SUFFIX.length());
        }
        ShortURLRecord fullURL = ServiceRegistry.get(ShortURLService.class).resolveShortURL(potentialShortURL);
        if (fullURL != null)
        {
            // We found a match, do a redirect and bail out
            response.sendRedirect(fullURL.getFullURL());
        }
        else
        {
            chain.doFilter(req, resp);
        }
    }

    @Override
    public void init(FilterConfig arg0) throws ServletException
    {
    }
}
