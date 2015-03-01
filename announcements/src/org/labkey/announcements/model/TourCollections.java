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
package org.labkey.announcements.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Marty on 2/25/2015.
 */
public class TourCollections
{
    private final Map<Integer, TourModel> toursByRowId = new HashMap<>();
    private final Map<String, TourModel> toursByEntityId = new HashMap<>();
    private final List<TourModel> toursList = new ArrayList<>();
    private static final CommSchema _comm = CommSchema.getInstance();

    public TourCollections(Container c)
    {
        SimpleFilter filter = new SimpleFilter();
        if (c == null)
            return;
        else
            filter.addCondition(FieldKey.fromParts("Container"), c);

        Selector selector = new TableSelector(_comm.getTableInfoTours(), filter, null);
        TourModel[] tours = selector.getArray(TourModel.class);
        for(TourModel tour : tours)
        {
            toursByRowId.put(tour.getRowId(), tour);
            toursByEntityId.put(tour.getEntityId(), tour);
            toursList.add(tour);
        }
    }

    @Nullable
    public TourModel getTourByRowId(Integer rowId)
    {
        return toursByRowId.get(rowId);
    }

    @Nullable
    public TourModel getTourByEntityId(String entityId)
    {
        return toursByEntityId.get(entityId);
    }

    public List<TourModel> getTourList()
    {
        return toursList;
    }
}
