/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.api.analytics;

import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

/**
 * Adds analytics tracking code to HTML pages, such as Google Analytics
 */
public interface AnalyticsService
{
    static AnalyticsService get()
    {
        return ServiceRegistry.get(AnalyticsService.class);
    }

    static void set(AnalyticsService impl)
    {
        ServiceRegistry.get().registerService(AnalyticsService.class, impl);
    }

    static String getTrackingScript()
    {
        AnalyticsService svc = get();
        if (svc == null)
        {
            return "";
        }
        return svc.getTrackingScript(HttpView.getRootContext());
    }

    String getTrackingScript(ViewContext viewContext);
//    String getSanitizedUrl(ViewContext viewContext);
}
