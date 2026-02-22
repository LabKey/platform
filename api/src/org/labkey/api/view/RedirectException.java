/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.view;

import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.SkipMothershipLogging;
import org.labkey.api.util.URLHelper;

/**
 * When thrown in the context of an HTTP request, sends the client a *temporary* redirect in the HTTP response. Not
 * treated as a loggable error. See {@link PermanentRedirectException} if a permanent redirect is desired.
 * Note: This always redirects to the local server. If an external redirect is needed (this is rare), use
 * {@link ExternalRedirectException} or (even rarer) {@link UnsafeExternalRedirectException}.
 */
public class RedirectException extends RuntimeException implements SkipMothershipLogging
{
    private final String _url;

    // Never redirects externally
    public RedirectException(@NotNull ActionURL url)
    {
        this(url.getLocalURIString());
    }

    // Never redirects externally
    public RedirectException(@NotNull URLHelper url)
    {
        this(url.getLocalURIString());
    }

    @Deprecated // TODO: eliminate all outside callers and make this protected
    public RedirectException(String url)
    {
        _url = url;
    }

    public String getURL()
    {
        return _url;
    }

    public int getHttpStatusCode()
    {
        return HttpServletResponse.SC_MOVED_TEMPORARILY;
    }
}
