package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.URLHelper;

/**
 * Very rarely needed, throwing this exception redirects to an external site WITHOUT checking its host name against the
 * admin-configured allow list. This should be used only in cases where a trusted user has configured a URL that is used
 * to redirect. Always add a comment explaining why bypassing the allow list is safe.
 */
public class UnsafeExternalRedirectException extends RedirectException
{
    public UnsafeExternalRedirectException(@NotNull URLHelper url)
    {
        boolean redirectExternally = !url.isLocalUri(HttpView.getRootContext());
        super(redirectExternally ? url.getURIString() : url.getLocalURIString());
    }
}
