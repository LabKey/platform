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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.ViewContext;

import java.util.function.Consumer;

public interface WarningService
{
    String SESSION_WARNINGS_BANNER_KEY = "PAGE_CONFIG$SESSION_WARNINGS_BANNER_KEY";

    static WarningService get()
    {
        return ServiceRegistry.get().getService(WarningService.class);
    }

    static void setInstance(WarningService impl)
    {
        ServiceRegistry.get().registerService(WarningService.class, impl);
    }

    boolean showAllWarnings();
    void register(WarningProvider provider);
    void forEachProvider(Consumer<WarningProvider> consumer);
    Warnings getWarnings(@Nullable ViewContext context);
    HtmlString getWarningsHtml(Warnings warnings, ViewContext context);
    void rerunSchemaCheck(); // Doesn't really fit on this service, but there's no better existing service
}
