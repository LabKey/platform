package org.labkey.announcements.model;

import org.jetbrains.annotations.Nullable;
import org.labkey.announcements.query.AnnouncementSchema;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        if (c != null)
        {
            filter.addCondition(FieldKey.fromParts("Container"), c);
        }
        Selector selector = new TableSelector(_comm.getTableInfoTours(), filter, null);
        TourModel tour = selector.getObject(TourModel.class);

        if (null == tour)
            return null;

        return tour;
    }

    public static List<Map<String,String>> getApplicableTours(@Nullable Container c)
    {
        final String rowId = "RowId";
        final String title = "Title";
        final String desc = "Description";
        final String mode = "Mode";


        SQLFragment sql = new SQLFragment("SELECT " + rowId + ", " + title + ", " + desc + ", " + mode + " FROM "
                + _comm.getSchemaName() + "." + AnnouncementSchema.TOURS_TABLE_NAME);
        if (c != null)
        {
            sql.append(" WHERE Container = ?");
            sql.add(c);
        }

        final List<Map<String,String>> result = new ArrayList<>();

        new SqlSelector(_comm.getSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                if (Integer.parseInt(rs.getString(mode)) > 0)
                {
                    Map<String, String> map = new HashMap<String, String>();
                    map.put(rowId, rs.getString(rowId).toString());
                    map.put(title, rs.getString(title));
                    map.put(desc, rs.getString(desc));
                    map.put(mode, rs.getString(mode));
                    result.add(map);
                }
            }
        });

        return result;
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

    private static void deleteTour(TourModel tour)
    {
        Table.delete(_comm.getTableInfoTours(), tour.getRowId());
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
