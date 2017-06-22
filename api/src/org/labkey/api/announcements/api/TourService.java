/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.api.announcements.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.ActionURL;

import java.util.List;

/**
 * Created by Marty on 1/19/2015.
 */
public interface TourService
{
    @Nullable
    static TourService get()
    {
        return ServiceRegistry.get(TourService.class);
    }

    static void setInstance(TourService impl)
    {
        ServiceRegistry.get().registerService(TourService.class, impl);
    }

    ActionURL getManageListsURL(Container container);
    List<Tour> getApplicableTours(@Nullable Container container);
}
