package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.study.StudySchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 1, 2011
 * Time: 2:26:02 PM
 */
public class ParticipantListManager
{
    public static TableInfo getTableInfoParticipantGroup()
    {
        return StudySchema.getInstance().getSchema().getTable("ParticipantGroup");
    }

    public static TableInfo getTableInfoParticipantGroupMap()
    {
        return StudySchema.getInstance().getSchema().getTable("ParticipantGroupMap");
    }

    public static ParticipantClassification getParticipantClassification(Container c, String name)
    {
        SimpleFilter filter = new SimpleFilter("Container", c);
        filter.addCondition("Label", name);

        ParticipantClassification[] lists = getParticipantClassifications(filter);

        if (lists.length > 0)
        {
            return lists[0];
        }
        ParticipantClassification def = new ParticipantClassification();
        def.setContainer(c.getId());
        def.setLabel(name);

        return def;
    }

    public static ParticipantClassification[] getParticipantClassifications(SimpleFilter filter)
    {
        try {
            ParticipantClassification[] lists = Table.select(StudySchema.getInstance().getTableInfoParticipantClassification(), Table.ALL_COLUMNS, filter, null, ParticipantClassification.class);

            for (ParticipantClassification pc : lists)
            {
                //pc.setParticipantIds(getParticipants(pc));
                pc.setGroups(getParticipantGroups(pc));
            }
            return lists;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public static ParticipantClassification[] getParticipantClassifications(Container c)
    {
        return getParticipantClassifications(new SimpleFilter("Container", c));
    }

    public static ParticipantClassification setParticipantClassification(User user, ParticipantClassification def)
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try {
            scope.ensureTransaction();
            ParticipantClassification ret;

            if (def.isNew())
                ret = Table.insert(user, StudySchema.getInstance().getTableInfoParticipantClassification(), def);
            else
                ret = Table.update(user, StudySchema.getInstance().getTableInfoParticipantClassification(), def, def.getRowId());

            switch (ParticipantClassification.Type.valueOf(ret.getType()))
            {
                case list:
                    updateListTypeDef(user, ret);
                    break;
                case query:
                    throw new UnsupportedOperationException("Participant classification type: query not yet supported");
                case cohort:
                    throw new UnsupportedOperationException("Participant classification type: cohort not yet supported");
            }
            scope.commitTransaction();
            return ret;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public static ParticipantGroup setParticipantGroup(User user, ParticipantGroup group)
    {
        try {
            if (group.isNew())
                return Table.insert(user, getTableInfoParticipantGroup(), group);
            else
                return Table.update(user, getTableInfoParticipantGroup(), group, group.getRowId());
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public static ParticipantGroup[] getParticipantGroups(ParticipantClassification def) throws SQLException
    {
        if (!def.isNew())
        {
            SQLFragment sql = new SQLFragment("SELECT * FROM (");
            sql.append("SELECT pg.label, pg.rowId FROM ").append(StudySchema.getInstance().getTableInfoParticipantClassification(), "pc");
            sql.append(" JOIN ").append(getTableInfoParticipantGroup(), "pg").append(" ON pc.rowId = pg.classificationId WHERE pg.classificationId = ?) jr ");
            sql.append(" JOIN ").append(getTableInfoParticipantGroupMap(), "gm").append(" ON jr.rowId = gm.groupId;");

            ParticipantGroupMap[] maps = Table.executeQuery(StudySchema.getInstance().getSchema(), sql.getSQL(), new Object[]{def.getRowId()}, ParticipantGroupMap.class);
            Map<Integer, ParticipantGroup> groupMap = new HashMap<Integer, ParticipantGroup>();

            for (ParticipantGroupMap pg : maps)
            {
                if (!groupMap.containsKey(pg.getGroupId()))
                {
                    ParticipantGroup group = new ParticipantGroup();
                    group.setClassificationId(def.getRowId());
                    group.setLabel(pg.getLabel());
                    group.setContainer(pg.getContainerId());
                    group.setRowId(pg.getRowId());

                    groupMap.put(pg.getGroupId(), group);
                }
                groupMap.get(pg.getGroupId()).addParticipantId(pg.getParticipantId());
            }
            return groupMap.values().toArray(new ParticipantGroup[groupMap.size()]);
        }
        return new ParticipantGroup[0];
    }

    public static class ParticipantGroupMap extends ParticipantGroup
    {
        private String _participantId;
        private int _groupId;

        public String getParticipantId()
        {
            return _participantId;
        }

        public void setParticipantId(String participantId)
        {
            _participantId = participantId;
        }

        public int getGroupId()
        {
            return _groupId;
        }

        public void setGroupId(int groupId)
        {
            _groupId = groupId;
        }
    }

    public static String[] getParticipants(ParticipantClassification def) throws SQLException
    {
        if (!def.isNew())
        {
            SQLFragment sql = new SQLFragment("SELECT ParticipantId FROM ").append(getTableInfoParticipantGroup(), "");

            sql.append(" WHERE GroupId = ? AND Container = ?;");

            return Table.executeArray(StudySchema.getInstance().getSchema(), sql.getSQL(), new Object[]{def.getRowId(), def.getContainerId()}, String.class);
        }
        return new String[0];
    }

    private static void updateListTypeDef(User user, ParticipantClassification def) throws SQLException
    {
        assert !def.isNew() : "The participant classification has not been created yet";

        if (!def.isNew() && def.getParticipantIds().length != 0)
        {
            // add a single entry to the participant group table that whose label is the same as the classification
            ParticipantGroup group = new ParticipantGroup();
            group.setClassificationId(def.getRowId());
            group.setLabel(def.getLabel());
            group.setContainer(def.getContainerId());

            group = setParticipantGroup(user, group);

            // add the mapping from group to participants
            SQLFragment sql = new SQLFragment("INSERT INTO ").append(getTableInfoParticipantGroupMap(), "");
            sql.append(" (GroupId, ParticipantId, Container) VALUES ");

            String delim = "";
            List<Object> params = new ArrayList<Object>();

            for (String id : def.getParticipantIds())
            {
                params.add(group.getRowId());
                params.add(id);
                params.add(group.getContainerId());

                sql.append(delim).append("(?, ?, ?)");
                delim = ",";
            }
            sql.append(";");

            Table.execute(StudySchema.getInstance().getSchema(), sql.getSQL(), params.toArray());
        }
    }

    public static void deleteParticipantClassification(User user, ParticipantClassification def)
    {
        if (def.isNew())
            throw new IllegalArgumentException("Participant classification has not been saved to the database yet");

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try {
            scope.ensureTransaction();

            // remove any participant group mappings from the junction table
            SQLFragment sqlMapping = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroupMap(), "").append(" WHERE GroupId IN ");
            sqlMapping.append("(SELECT RowId FROM ").append(getTableInfoParticipantGroup(), "pg").append(" WHERE ClassificationId = ?)");
            Table.execute(StudySchema.getInstance().getSchema(), sqlMapping.getSQL(), def.getRowId());

            // delete the participant groups
            SQLFragment sqlGroup = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroup(), "").append(" WHERE RowId IN ");
            sqlGroup.append("(SELECT RowId FROM ").append(getTableInfoParticipantGroup(), "pg").append(" WHERE ClassificationId = ?)");
            Table.execute(StudySchema.getInstance().getSchema(), sqlGroup.getSQL(), def.getRowId());

            // delete the participant list definition
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(StudySchema.getInstance().getTableInfoParticipantClassification(), "").append(" WHERE RowId = ?");
            Table.execute(StudySchema.getInstance().getSchema(), sql.getSQL(), def.getRowId());

            scope.commitTransaction();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            scope.closeConnection();
        }
    }
}
