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
package org.labkey.announcements.api;

import org.labkey.announcements.ToursController;
import org.labkey.announcements.model.TourManager;
import org.labkey.announcements.model.TourModel;
import org.labkey.api.announcements.api.Tour;
import org.labkey.api.announcements.api.TourService;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Marty on 1/19/2015.
 */
public class TourServiceImpl implements TourService
{
    public ActionURL getManageListsURL(Container container)
    {
        return new ActionURL(ToursController.BeginAction.class, container);
    }

    public List<Tour> getApplicableTours(Container container)
    {
        List<Tour> tours = new ArrayList<>();
        for(TourModel model : TourManager.getApplicableTours(container))
        {
            tours.add(new TourImpl(model));
        }

        return tours;
    }
}
