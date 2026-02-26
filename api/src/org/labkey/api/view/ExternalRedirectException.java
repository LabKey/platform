package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.URLHelper;

/**
 * Can redirect externally, but only if it's an absolute url and the host is on the admin-configured allow list.
 * External redirects are rarely needed; see RedirectException.
 */
public class ExternalRedirectException extends RedirectException
{
    public ExternalRedirectException(@NotNull URLHelper url)
    {
        boolean redirectExternally = !url.isLocalUri(HttpView.getRootContext()) && url.isAllowableHost();
        super(redirectExternally ? url.getURIString() : url.getLocalURIString());
    }
}
