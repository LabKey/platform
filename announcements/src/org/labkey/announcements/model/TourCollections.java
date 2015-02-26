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
        if (c != null)
        {
            filter.addCondition(FieldKey.fromParts("Container"), c);
        }
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
