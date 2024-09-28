/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.admin.sitevalidation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.view.ActionURL;

public class SiteValidationResult
{
    public enum Level
    {
        INFO,
        WARN,
        ERROR;

        public SiteValidationResult create() { return create(HtmlString.EMPTY_STRING);}
        public SiteValidationResult create(HtmlString html) { return create(html, null);}
        public SiteValidationResult create(HtmlString message, @Nullable ActionURL link) { return new SiteValidationResult(this, message, link);}
    }

    private final Level level;
    private final HtmlStringBuilder builder;
    private final ActionURL link;

    private SiteValidationResult(@NotNull Level level, @NotNull HtmlString message, @Nullable ActionURL link)
    {
        this.level = level;
        this.builder = HtmlStringBuilder.of(message);
        this.link = link;
    }

    public Level getLevel()
    {
        return level;
    }

    public HtmlString getMessage()
    {
        return builder.getHtmlString();
    }

    public ActionURL getLink()
    {
        return link;
    }

    public SiteValidationResult append(String message)
    {
        builder.append(message);
        return this;
    }

    public SiteValidationResult append(Object o)
    {
        return append(o.toString());
    }
}
