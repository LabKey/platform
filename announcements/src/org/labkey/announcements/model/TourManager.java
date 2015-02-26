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
import org.labkey.api.data.Entity;
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

    public TourManager()
    {
    }

    public static TourModel getTour(@Nullable Container c, String entityId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("EntityId"), entityId);
        if (c != null)
        {
            filter.addCondition(FieldKey.fromParts("Container"), c);
        }
        Selector selector = new TableSelector(_comm.getTableInfoTours(), filter, null);
        TourModel[] tours = selector.getArray(TourModel.class);

        if (tours.length < 1)
            return null;

        return tours[0];
    }

    public static TourModel getTour(@Nullable Container c, int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        if (null != c)
        {
            filter.addCondition(FieldKey.fromParts("Container"), c);
        }
        Selector selector = new TableSelector(_comm.getTableInfoTours(), filter, null);
        TourModel tour = selector.getObject(TourModel.class);

        if (null == tour)
            return null;

        return tour;
    }

    public static List<TourModel> getApplicableTours(@Nullable Container c)
    {
        SimpleFilter filter = new SimpleFilter();

        if( null != c)
        {
            filter.addCondition(FieldKey.fromParts("Container"), c);
        }

        return new TableSelector(_comm.getTableInfoTours(), filter, null).getArrayList(TourModel.class);
    }

    public static String getTourJson(@Nullable Container c, int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        if (c != null)
        {
            filter.addCondition(FieldKey.fromParts("Container"), c);
        }
        Selector selector = new TableSelector(_comm.getTableInfoTours(), filter, null);
        TourModel tour = selector.getObject(TourModel.class);

        if (null == tour)
            return null;

        return tour.getJson();
    }

    public static String getTourMode(@Nullable Container c, int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        if (c != null)
        {
            filter.addCondition(FieldKey.fromParts("Container"), c);
        }
        Selector selector = new TableSelector(_comm.getTableInfoTours(), filter, null);
        TourModel tour = selector.getObject(TourModel.class);

        if (null == tour)
            return null;

        return tour.getMode().toString();
    }

    public static String getTourEntity(@Nullable Container c, int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        if (c != null)
        {
            filter.addCondition(FieldKey.fromParts("Container"), c);
        }
        Selector selector = new TableSelector(_comm.getTableInfoTours(), filter, null);
        TourModel tour = selector.getObject(TourModel.class);

        if (null == tour)
            return null;

        return tour.getEntityId();
    }


    public static TourModel insertTour(Container c, User user, TourModel insert)
    {
        insert.beforeInsert(user, c.getId());
        TourModel result = Table.insert(user, _comm.getTableInfoTours(), insert);

        return result;
    }

    public static TourModel updateTour(User user, TourModel update)
    {
        update.beforeUpdate(user);
        TourModel result = Table.update(user, _comm.getTableInfoTours(), update, update.getRowId());

        return result;
    }

    public static TourModel updateTour(User user, TourModel update, Entity cur)
    {
        update.beforeUpdate(user, cur);
        TourModel result = Table.update(user, _comm.getTableInfoTours(), update, update.getRowId());

        return result;
    }

    private static void deleteTour(TourModel tour)
    {
        Table.delete(_comm.getTableInfoTours(), tour.getRowId());
    }

    public static void purgeContainer(Container c)
    {
        ContainerUtil.purgeTable(_comm.getTableInfoTours(), c, null);
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
