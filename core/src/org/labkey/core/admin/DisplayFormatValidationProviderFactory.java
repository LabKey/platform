/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.core.admin;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.sitevalidation.SiteValidationProvider;
import org.labkey.api.admin.sitevalidation.SiteValidationProviderFactory;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;

public class DisplayFormatValidationProviderFactory implements SiteValidationProviderFactory
{
    @Override
    public String getName()
    {
        return "Display Format Validator";
    }

    @Override
    public String getDescription()
    {
        return "Report non-standard date and time display formats";
    }

    @Override
    public SiteValidationProvider getSiteValidationProvider()
    {
        return new SiteValidationProvider()
        {
            private final DisplayFormatAnalyzer _analyzer = new DisplayFormatAnalyzer();

            @Override
            public SiteValidationProviderFactory getFactory()
            {
                return DisplayFormatValidationProviderFactory.this;
            }

            @Override
            public @Nullable SiteValidationResultList runValidation(Container container, User u)
            {
                SiteValidationResultList results = new SiteValidationResultList();
                _analyzer.handle(container, u, (c, type, format, contextProvider) -> {
                    DisplayFormatAnalyzer.DisplayFormatContext context = contextProvider.get();
                    results.addWarn(
                        HtmlStringBuilder.of(context.message() + ": ")
                            .append(HtmlString.unsafe("<i>"))
                            .append(format)
                            .append(HtmlString.unsafe("</i>"))
                            .getHtmlString(),
                        context.url()
                    );
                });

                return results.nullIfEmpty();
            }
        };
    }
}
