package org.labkey.study.visitmanager;

import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.StudySchema;
import org.labkey.study.model.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

/**
 * Copyright (c) 2008-2009 LabKey Corporation
* <p/>
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* <p/>
* User: brittp
* Created: Feb 29, 2008 11:23:08 AM
*/
public abstract class VisitManager
{
    protected Study _study;
    private TreeMap<Double, Visit> _sequenceMap;

    protected VisitManager(Study study)
    {
        _study = study;
    }

    public void updateParticipantVisits(User user)
    {
        boolean requiresUncache = updateParticipants(user);
        updateParticipantVisitTable(user);
        updateVisitTable(user);
        StudyManager.getInstance().clearVisitCache(_study);
        if (requiresUncache)
            StudyManager.getInstance().clearCaches(_study.getContainer(), true);
    }

    public String getLabel()
    {
        return "Visit";
    }

    public String getPluralLabel()
    {
        return "Visits";
    }

    protected abstract void updateParticipantVisitTable(User user);
    protected abstract void updateVisitTable(User user);
    public abstract Map<VisitMapKey, Integer> getVisitSummary(Cohort cohort, QCStateSet qcStates) throws SQLException;

    // Return sql for fetching all datasets and their visit sequence numbers, given a container
    protected abstract String getDatasetSequenceNumsSQL();

    public Map<Integer, List<Double>> getDatasetSequenceNums()
    {
        String sql = getDatasetSequenceNumsSQL();
        ResultSet rs = null;
        Map<Integer, List<Double>> ret = new HashMap<Integer, List<Double>>();
        try
        {
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql, new Object[] {_study.getContainer()});
            while (rs.next())
            {
                Integer datasetId  = rs.getInt(1);
                Double sequenceNum = rs.getDouble(2);
                List<Double> l = ret.get(datasetId);
                if (null == l)
                {
                    l = new ArrayList<Double>();
                    ret.put(datasetId, l);
                }
                l.add(sequenceNum);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
      return ret;
    }
    
    /**
     * Return a map mapping the minimum value of a visit to the visit.
     * In the case of a date-based study, this is actually a "Day" map, not a sequence map
     * @return
     */
    public TreeMap<Double, Visit> getVisitSequenceMap()
    {
        Visit[] visits = _study.getVisits();
        TreeMap<Double,Visit> visitMap = new TreeMap<Double, Visit>();
        for (Visit v : visits)
            visitMap.put(v.getSequenceNumMin(),v);
        return visitMap;
    }

    public Visit findVisitBySequence(double seq)
    {
        if (_sequenceMap == null)
            _sequenceMap = getVisitSequenceMap();

        if (_sequenceMap.containsKey(seq))
            return _sequenceMap.get(seq);
        SortedMap<Double,Visit> m = _sequenceMap.headMap(seq);
        if (m.isEmpty())
            return null;
        double seqMin = m.lastKey();
        Visit v = _sequenceMap.get(seqMin);
        // v will be null only if we already searched for seq and didn't find it
        if (null == v)
            return null;
        if (!(v.getSequenceNumMin() <= seq && seq <= v.getSequenceNumMax()))
            v = null;
        _sequenceMap.put(seq, v);
        return v;
    }

    /**
     * Returns true if the specified Visit overlaps other visits in the same container
     * according to sequence numbers.
     *
     * @param visit The visit to check
     * @return True if the visit overlaps existing visits in the same container
     * @throws SQLException thrown if there's a database error
     */
    public boolean isVisitOverlapping(Visit visit) throws SQLException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo visitTable = StudySchema.getInstance().getTableInfoVisit();

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS overlaps FROM ");
        sql.append(visitTable);
        sql.append(" WHERE SequenceNumMax >= ");
        sql.append(visit.getSequenceNumMin());
        sql.append(" AND SequenceNumMin <= ");
        sql.append(visit.getSequenceNumMax());
        sql.append(" AND Container = '");
        sql.append(visit.getContainer().getId());
        sql.append("'");
        sql.append(" AND RowId <> "); //exclude the specified visit itself
        sql.append(visit.getRowId()); //new visits will have a rowId of 0, which shouldn't conflict

        Integer overlaps = Table.executeSingleton(schema, sql.toString(), null, Integer.class);
        return (null != overlaps && overlaps.intValue() != 0);
    }

    /** Update the Participants table to match the entries in StudyData. Return true if changes require uncaching all datasets */
    protected boolean updateParticipants(User user)
    {
        try
        {
            String c = _study.getContainer().getId();

            DbSchema schema = StudySchema.getInstance().getSchema();
            TableInfo tableStudyData = StudySchema.getInstance().getTableInfoStudyData();
            TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
            TableInfo tableSpecimen = StudySchema.getInstance().getTableInfoSpecimen();

            Table.execute(schema,
                    "INSERT INTO " + tableParticipant + " (container, participantid)\n" +
                    "SELECT DISTINCT ?, participantid\n" +
                    "FROM " + tableStudyData + "\n"+
                    "WHERE container = ? AND participantid NOT IN (select participantid from " + tableParticipant + " where container = ?)",
                    new Object[] {c, c, c});
            Table.execute(schema,
                    "INSERT INTO " + tableParticipant + " (container, participantid)\n" +
                    "SELECT DISTINCT ?, ptid AS participantid\n" +
                    "FROM " + tableSpecimen + "\n"+
                    "WHERE container = ? AND ptid NOT IN (select participantid from " + tableParticipant + " where container = ?)",
                    new Object[] {c, c, c});
            Table.execute(schema,
                    "DELETE FROM " + tableParticipant  + "\n" +
                    "WHERE container = ? AND participantid NOT IN (select participantid from " + tableStudyData  + " where container = ?) AND " +
                             "participantid NOT IN (select ptid from " + tableSpecimen  + " where container = ?)",
                    new Object[] {c, c, c});

            return 0 != updateStartDates(user);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    protected int updateStartDates(User user) throws SQLException
    {
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
        //See if there are any demographic datasets that contain a start date
        DataSetDefinition[] datasets = _study.getDataSets();
        DbSchema schema = StudyManager.getSchema();
        int count = 0;
        for (DataSetDefinition dataset : datasets)
        {
            if (dataset.isDemographicData())
            {
                TableInfo tInfo = dataset.getTableInfo(user, false, true);
                //TODO: Use Property URI & Make sure this is set properly
                ColumnInfo col = tInfo.getColumn("StartDate");
                if (null != col)
                {
                    String subselect = "(SELECT " + col.getSelectName() + " FROM " + tInfo + " WHERE ParticipantId=" + tableParticipant + ".ParticipantId)";
                    String sql = "UPDATE " + tableParticipant + " SET StartDate= " + subselect + " WHERE " +
                            tableParticipant + ".StartDate IS NULL OR NOT " + tableParticipant + ".StartDate=" + subselect;
                    count = Table.execute(StudyManager.getSchema(), sql, null);
                    break;
                }

            }
        }
        //No demographic data, so just set to study start date.
        String sqlUpdateStartDates = "UPDATE " + tableParticipant + " SET StartDate = ? WHERE Container = ? AND StartDate IS NULL";
        Parameter startDateParam = new Parameter(_study.getStartDate(), Types.TIMESTAMP);
        return count + Table.execute(schema, sqlUpdateStartDates, new Object[] {startDateParam, _study.getContainer()});
    }

}
