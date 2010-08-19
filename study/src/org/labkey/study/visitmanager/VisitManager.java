package org.labkey.study.visitmanager;

import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.study.Visit;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.StudySchema;
import org.labkey.study.CohortFilter;
import org.labkey.study.model.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

/**
 * Copyright (c) 2008-2010 LabKey Corporation
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
    protected StudyImpl _study;
    private TreeMap<Double, VisitImpl> _sequenceMap;

    protected VisitManager(StudyImpl study)
    {
        _study = study;
    }

    public void updateParticipantVisits(User user, Collection<DataSetDefinition> changedDatasets)
    {
        updateParticipants(user, changedDatasets);
        updateParticipantVisitTable(user);
        updateVisitTable(user);

        try
        {
            CohortManager.getInstance().updateParticipantCohorts(user, _study);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        StudyManager.getInstance().clearVisitCache(_study);
        StudyManager.getInstance().reindex(_study.getContainer());
    }


    public String getLabel()
    {
        return "Visit";
    }


    public static String getParticipantSequenceKeyExpr(DbSchema schema, String ptidColumnName, String sequenceNumColumnName)
    {
        SqlDialect dialect = schema.getSqlDialect();
        String strType = dialect.sqlTypeNameFromSqlTypeInt(Types.VARCHAR);

        StringBuilder participantSequenceKey = new StringBuilder("(");
        participantSequenceKey.append(dialect.concatenate(ptidColumnName, "'|'", "CAST(" + sequenceNumColumnName + " AS " + strType + ")"));
        participantSequenceKey.append(")");

        return participantSequenceKey.toString();
    }


    public String getPluralLabel()
    {
        return "Visits";
    }

    protected abstract void updateParticipantVisitTable(User user);
    protected abstract void updateVisitTable(User user);
    public abstract Map<VisitMapKey, Integer> getVisitSummary(CohortFilter cohortFilter, QCStateSet qcStates) throws SQLException;

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
     */
    public TreeMap<Double, VisitImpl> getVisitSequenceMap()
    {
        VisitImpl[] visits = _study.getVisits(Visit.Order.DISPLAY);
        TreeMap<Double, VisitImpl> visitMap = new TreeMap<Double, VisitImpl>();
        for (VisitImpl v : visits)
            visitMap.put(v.getSequenceNumMin(),v);
        return visitMap;
    }

    public VisitImpl findVisitBySequence(double seq)
    {
        if (_sequenceMap == null)
            _sequenceMap = getVisitSequenceMap();

        if (_sequenceMap.containsKey(seq))
            return _sequenceMap.get(seq);
        SortedMap<Double, VisitImpl> m = _sequenceMap.headMap(seq);
        if (m.isEmpty())
            return null;
        double seqMin = m.lastKey();
        VisitImpl v = _sequenceMap.get(seqMin);
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
    public boolean isVisitOverlapping(VisitImpl visit) throws SQLException
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

    /** Update the Participants table to match the entries in StudyData. */
    protected void updateParticipants(User user, Collection<DataSetDefinition> changedDatasets)
    {
        try
        {
            String c = _study.getContainer().getId();

            DbSchema schema = StudySchema.getInstance().getSchema();
            TableInfo tableStudyData = StudySchema.getInstance().getTableInfoStudyData();
            TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
            TableInfo tableSpecimen = StudySchema.getInstance().getTableInfoSpecimen();

            if (!changedDatasets.isEmpty())
            {
                SQLFragment datasetParticipantsSQL = new SQLFragment("INSERT INTO " + tableParticipant + " (container, participantid)\n" +
                        "SELECT DISTINCT ?, participantid\n" +
                        "FROM " + tableStudyData + "\n" +
                        "WHERE container = ? AND participantid NOT IN (select participantid from " + tableParticipant + " where container = ?) AND datasetid IN (");
                datasetParticipantsSQL.add(c);
                datasetParticipantsSQL.add(c);
                datasetParticipantsSQL.add(c);
                String separator = "";
                for (DataSetDefinition changedDataset : changedDatasets)
                {
                    datasetParticipantsSQL.append(separator);
                    separator = ", ";
                    datasetParticipantsSQL.append(changedDataset.getDataSetId());
                }
                datasetParticipantsSQL.append(")");

                Table.execute(schema, datasetParticipantsSQL);
            }
            Table.execute(schema,
                    "DELETE FROM " + tableParticipant + " WHERE participantid IN (SELECT p.participantid FROM " +
                            tableParticipant + " p LEFT OUTER JOIN " + tableStudyData + " sd ON p.container = sd.container " +
                            "AND p.participantid = sd.participantid LEFT OUTER JOIN " + tableSpecimen + " s ON " +
                            "p.container = s.container AND s.ptid = p.participantid WHERE sd.participantid IS NULL AND " +
                            "s.ptid IS NULL AND p.container = ?) AND container = ?", new Object[] {c, c});

            updateStartDates(user);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    protected void updateStartDates(User user) throws SQLException
    {
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
        //See if there are any demographic datasets that contain a start date
        DbSchema schema = StudyManager.getSchema();

        for (DataSetDefinition dataset : _study.getDataSets())
        {
            if (dataset.isDemographicData())
            {
                TableInfo tInfo = dataset.getTableInfo(user, false, true);
                //TODO: Use Property URI & Make sure this is set properly
                ColumnInfo col = tInfo.getColumn("StartDate");
                if (null != col)
                {
                    String subselect = "(SELECT MIN(" + col.getSelectName() + ") FROM " + tInfo + " WHERE " + tInfo + "." +
                       StudyService.get().getSubjectColumnName(dataset.getContainer()) + " = " + tableParticipant + ".ParticipantId" +
                            " AND " + tableParticipant + ".Container = ?)";
                    String sql = "UPDATE " + tableParticipant + " SET StartDate = " + subselect + " WHERE (" +
                            tableParticipant + ".StartDate IS NULL OR NOT " + tableParticipant + ".StartDate = " + subselect +
                            ") AND Container = ?";
                    Table.execute(StudyManager.getSchema(), sql, new Object[] { dataset.getContainer().getId(), dataset.getContainer().getId(), dataset.getContainer().getId() });
                    break;
                }

            }
        }
        //No demographic data, so just set to study start date.
        String sqlUpdateStartDates = "UPDATE " + tableParticipant + " SET StartDate = ? WHERE Container = ? AND StartDate IS NULL";
        Parameter startDateParam = new Parameter(_study.getStartDate(), Types.TIMESTAMP);
        Table.execute(schema, sqlUpdateStartDates, new Object[] {startDateParam, _study.getContainer()});
    }
}
