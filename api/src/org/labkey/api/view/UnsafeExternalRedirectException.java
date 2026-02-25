package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.URLHelper;

/**
 * Very rarely needed, throwing this exception redirects to an external site WITHOUT checking its host name against the
 * admin-configured allow list. Use this only in cases where you are certain the URL is safe for external redirection,
 * for example, a trusted user has configured a URL that is used to redirect. (Obviously, a URL coming from a returnUrl
 * or other parameter is NOT safe and should use one of the other redirect exceptions.) If using this class, add a
 * comment explaining why bypassing the allow list is safe.
 */
public class UnsafeExternalRedirectException extends RedirectException
{
    public UnsafeExternalRedirectException(@NotNull URLHelper url)
    {
        boolean redirectExternally = !url.isLocalUri(HttpView.getRootContext());
        super(redirectExternally ? url.getURIString() : url.getLocalURIString());
    }
}
