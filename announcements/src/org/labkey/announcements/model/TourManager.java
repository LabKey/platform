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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;

import java.util.List;

/**
 * Created by Marty on 1/15/2015.
 */
public class TourManager
{
    private static final CommSchema _comm = CommSchema.getInstance();

    private TourManager()
    {
    }

    private static TourCollections getTourCollections(Container c)
    {
        return TourCache.getTourCollections(c);
    }

    public static TourModel getTourFromDb(Container c, int rowId)
    {
        SimpleFilter filter;
        if (null != c)
        {
            filter = SimpleFilter.createContainerFilter(c);
        }
        else
        {
            filter = new SimpleFilter();
        }
        filter.addCondition(FieldKey.fromParts("rowId"), rowId);
        Selector selector = new TableSelector(_comm.getTableInfoTours(), filter, null);

        return selector.getObject(TourModel.class);
    }

    @Nullable
    public static TourModel getTour(Container c, String entityId)
    {
        return getTourCollections(c).getTourByEntityId(entityId);
    }

    @Nullable
    public static TourModel getTour(Container c, int rowId)
    {
        return getTourCollections(c).getTourByRowId(rowId);
    }

    public static List<TourModel> getApplicableTours(Container c)
    {
        return getTourCollections(c).getTourList();
    }

    @Nullable
    public static String getTourJson(Container c, int rowId)
    {
        TourModel tour = getTourCollections(c).getTourByRowId(rowId);

        if (null == tour)
            return null;

        return tour.getJson();
    }

    @Nullable
    public static String getTourMode(Container c, int rowId)
    {
        TourModel tour = getTourCollections(c).getTourByRowId(rowId);

        if (null == tour || null == tour.getMode())
            return null;

        return tour.getMode().toString();
    }

    public static TourModel insertTour(Container c, User user, TourModel insert)
    {
        insert.beforeInsert(user, c.getId());
        TourModel result = Table.insert(user, _comm.getTableInfoTours(), insert);
        TourCache.uncache(c);

        return result;
    }

    public static TourModel updateTour(User user, TourModel update)
    {
        update.beforeUpdate(user);
        TourModel result = Table.update(user, _comm.getTableInfoTours(), update, update.getRowId());
        TourCache.uncache(ContainerManager.getForId(update.getContainerId()));

        return result;
    }

    private static void deleteTour(TourModel tour)
    {
        Table.delete(_comm.getTableInfoTours(), tour.getRowId());
        TourCache.uncache(ContainerManager.getForId(tour.getContainerId()));
    }

    public static void purgeContainer(Container c)
    {
        ContainerUtil.purgeTable(_comm.getTableInfoTours(), c, null);
        TourCache.uncache(c);
    }

    public static void deleteTour(Container c, int rowId)
    {
        TourModel tour = getTour(c, rowId);

        if (tour != null)
        {
            deleteTour(tour);
        }
    }
}
