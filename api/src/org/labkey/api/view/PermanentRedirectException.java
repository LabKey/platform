package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.URLHelper;

import jakarta.servlet.http.HttpServletResponse;

/** Use when we want search engines, browsers, etc to assume that the redirecting URL is defunct and the target URL should be used going forward */
public class PermanentRedirectException extends RedirectException
{
    public PermanentRedirectException(@NotNull URLHelper url)
    {
        super(url);
    }

    @Override
    public int getHttpStatusCode()
    {
        return HttpServletResponse.SC_MOVED_PERMANENTLY;
    }
}
