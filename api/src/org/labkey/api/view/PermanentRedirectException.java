package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.URLHelper;

import javax.servlet.http.HttpServletResponse;

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
