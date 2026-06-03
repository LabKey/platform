/*
 * Copyright (c) 2022-2026 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.URLHelper;

import jakarta.servlet.http.HttpServletResponse;

/** Use when we want search engines, browsers, etc. to assume that the redirecting URL is defunct and the target URL should be used going forward */
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
