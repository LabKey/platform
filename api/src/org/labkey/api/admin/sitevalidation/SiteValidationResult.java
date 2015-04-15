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
import org.labkey.api.view.ActionURL;

/**
 * User: tgaluhn
 * Date: 4/8/2015
 */
public class SiteValidationResult
{
    public enum Level
    {
        TITLE,
        INFO,
        WARN,
        ERROR;

        public SiteValidationResult create(String message) { return create(message, null);}
        public SiteValidationResult create(String message, ActionURL link) { return new SiteValidationResult(this, message, link);}
    }

    final Level level;
    final String message;
    final ActionURL link;

    private SiteValidationResult(@NotNull Level level, @NotNull String message, @Nullable ActionURL link)
    {
        this.level = level;
        this.message = message;
        this.link = link;
    }

    public Level getLevel()
    {
        return level;
    }

    public String getMessage()
    {
        return message;
    }

    public ActionURL getLink()
    {
        return link;
    }
}
