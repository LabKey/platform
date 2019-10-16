/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.view.template;

import org.labkey.api.annotations.RemoveIn20_1;
import org.labkey.api.view.ViewContext;

public interface WarningProvider
{
    // Add warnings for conditions that will never change while the server is running (e.g., size of JVM heap or Tomcat version).
    // These are displayed to site administrators only.
    default void addStaticWarnings(Warnings warnings)
    {
    }

    // Add warnings based on the current context (folder, user, page, etc.).
    default void addDynamicWarnings(Warnings warnings, ViewContext context)
    {
        addDismissibleWarnings(warnings, context);
    }

    // All warnings are dismissible -- implementors should override addDynamicWarnings() instead
    @Deprecated
    @RemoveIn20_1
    default void addDismissibleWarnings(Warnings warnings, ViewContext context)
    {
    }
}
