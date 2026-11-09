/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.security;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HtmlString;

// Wiki lookups needed by WikiTermsOfUseProvider, implemented by the wiki service
public interface TermsOfUseWikiSource
{
    static @Nullable TermsOfUseWikiSource get()
    {
        return ServiceRegistry.get().getService(TermsOfUseWikiSource.class);
    }

    // Quick check for a terms-of-use wiki in the provided container. Helps optimize the every-request terms check.
    boolean hasTermsOfUseWiki(Container c);

    @Nullable HtmlString getHtml(Container c, String name);
}
