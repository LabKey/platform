/*
 * Copyright (c) 2015-2018 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ViewContext;

public interface ActivityService
{
    static ActivityService get()
    {
        return ServiceRegistry.get().getService(ActivityService.class);
    }

    static void setInstance(ActivityService impl)
    {
        ServiceRegistry.get().registerService(ActivityService.class, impl);
    }

    @Nullable Activity getCurrentActivity(ViewContext context);

    @Nullable JSONObject getCurrentActivityAsJson(ViewContext context);
}
