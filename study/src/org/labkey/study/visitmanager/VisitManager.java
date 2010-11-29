package org.labkey.study.visitmanager;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
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
        updateParticipantVisits(user, changedDatasets, null, null);
    }

    /**
     * Updates the participant, visit, and participant visit tables. Also updates automatic cohort assignments.
     * @param changedDatasets the datasets that may have one or more rows modified
     * @param potentiallyAddedParticipants optionally, the specific participants that may have been added to the study.
     * If null, all of the changedDatasets and specimens will be checked to see if they contain new participants
     * @param potentiallyDeletedParticipants optionally, the specific participants that may have been removed from the
     * study. If null, all participants will be checked to see if they are still in the study.
     */
    public void updateParticipantVisits(User user, Collection<DataSetDefinition> changedDatasets, @Nullable Set<String> potentiallyAddedParticipants, @Nullable Set<String> potentiallyDeletedParticipants)
    {
        updateParticipants(user, changedDatasets, potentiallyAddedParticipants, potentiallyDeletedParticipants);
        updateParticipantVisitTable(user);
        updateVisitTable(user);

        Integer cohortDatasetId = _study.getParticipantCohortDataSetId();
        // Only bother updating cohort assignment if we're doing automatic cohort assignment and there's an edit
        // to the dataset that specifies the cohort
        if (!_study.isManualCohortAssignment() &&
                cohortDatasetId != null &&
                changedDatasets.contains(StudyManager.getInstance().getDataSetDefinition(_study, cohortDatasetId.intValue())))
        {
            try
            {
                CohortManager.getInstance().updateParticipantCohorts(user, getStudy());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        StudyManager.getInstance().clearVisitCache(getStudy());
        StudyManager.getInstance().reindex(getStudy().getContainer());
    }


    public String getLabel()
    {
        return "Visit";
    }


    public static String getParticipantSequenceKeyExpr(DbSchema schema, String ptidColumnName, String sequenceNumColumnName)
    {
        SqlDialect dialect = schema.getSqlDialect();
        String strType = dialect.sqlTypeNameFromSqlType(Types.VARCHAR);

        //CAST(CAST(? AS NUMERIC(15, 4)) AS " + strType +

        StringBuilder participantSequenceKey = new StringBuilder("(");
        participantSequenceKey.append(dialect.concatenate(ptidColumnName, "'|'", "CAST(CAST(" + sequenceNumColumnName + " AS NUMERIC(15,4)) AS " + strType + ")"));
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
    protected abstract SQLFragment getDatasetSequenceNumsSQL(Study study);

    public Map<Integer, List<Double>> getDatasetSequenceNums()
    {
        SQLFragment sql = getDatasetSequenceNumsSQL(getStudy());
        ResultSet rs = null;
        Map<Integer, List<Double>> ret = new HashMap<Integer, List<Double>>();
        try
        {
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql);
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
        VisitImpl[] visits = getStudy().getVisits(Visit.Order.DISPLAY);
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
    protected void updateParticipants(User user, Collection<DataSetDefinition> changedDatasets, Set<String> potentiallyInsertedParticipants, Set<String> potentiallyDeletedParticipants)
    {
        try
        {
            String c = getStudy().getContainer().getId();

            DbSchema schema = StudySchema.getInstance().getSchema();
            TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();

            // Don't bother if we explicitly know that no participants were added, or that no datasets were edited
            if (!changedDatasets.isEmpty() && (potentiallyInsertedParticipants == null || !potentiallyInsertedParticipants.isEmpty()))
            {
                SQLFragment datasetParticipantsSQL = new SQLFragment("INSERT INTO " + tableParticipant + " (container, participantid)\n" +
                        "SELECT DISTINCT ?, participantid\n" +
                        "FROM (").append(studyDataPtids(changedDatasets)).append("\n" +
                        ") x WHERE participantid NOT IN (SELECT participantid FROM " + tableParticipant + " WHERE container = ?)");
                datasetParticipantsSQL.add(c);
                datasetParticipantsSQL.add(c);

                // Databases limit the size of IN clauses, so check that we won't blow the cap
                if (potentiallyInsertedParticipants != null && potentiallyInsertedParticipants.size() < 450)
                {
                    // We have an explicit list of potentially added participants, so filter to only look at them
                    datasetParticipantsSQL.append(" AND participantid IN (");
                    datasetParticipantsSQL.append(StringUtils.repeat("?", ", ", potentiallyInsertedParticipants.size()));
                    datasetParticipantsSQL.addAll(potentiallyInsertedParticipants);
                    datasetParticipantsSQL.append(")");
                }

                Table.execute(schema, datasetParticipantsSQL);
            }
            
            // If we don't know which participants might have been deleted, or we know and there are some,
            // keep the participant list up to date
            if (potentiallyDeletedParticipants == null || !potentiallyDeletedParticipants.isEmpty())
            {
                purgeParticipants(potentiallyDeletedParticipants);
            }

            updateStartDates(user);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public void purgeParticipants(Set<String> potentiallyDeletedParticipants)
    {
        try
        {
            String c = getStudy().getContainer().getId();

            DbSchema schema = StudySchema.getInstance().getSchema();
            TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
            TableInfo tableSpecimen = StudySchema.getInstance().getTableInfoSpecimen();

            SQLFragment ptids = new SQLFragment();
            SQLFragment studyDataPtids = studyDataPtids(getStudy().getDataSets());
            if (null != studyDataPtids)
            {
                ptids.append(studyDataPtids);
                ptids.append(" UNION\n");
            }
            ptids.append("SELECT ptid FROM " + tableSpecimen.getFromSQL("spec") + " WHERE spec.container=?");
            ptids.add(c);

            SQLFragment del = new SQLFragment();
            del.append("DELETE FROM " + tableParticipant + " WHERE container=? ");
            del.add(c);
            del.append(" AND participantid NOT IN (\n");
            del.append(ptids);
            del.append(")");

            // Databases limit the size of IN clauses, so check that we won't blow the cap
            if (potentiallyDeletedParticipants != null && potentiallyDeletedParticipants.size() < 450)
            {
                // We have an explicit list of potentially deleted participants, so filter to only look at them
                del.append(" AND participantid IN (");
                del.append(StringUtils.repeat("?", ", ", potentiallyDeletedParticipants.size()));
                del.addAll(potentiallyDeletedParticipants);
                del.append(")");
            }
            
            Table.execute(schema, del);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    private SQLFragment studyDataPtids(Collection<DataSetDefinition> defs)
    {
        SQLFragment f = new SQLFragment();
        String union = "";
        for (DataSetDefinition d : defs)
        {
            TableInfo sti = d.getStorageTableInfo();
            if (null == sti)
                continue;
            f.append(union);
            f.append("SELECT participantid FROM ").append(sti.toString());
            union = " UNION\n";
        }
        return f.isEmpty() ? null : f;
    }


    protected void updateStartDates(User user) throws SQLException
    {
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
        //See if there are any demographic datasets that contain a start date
        DbSchema schema = StudyManager.getSchema();

        for (DataSetDefinition dataset : getStudy().getDataSets())
        {
            if (dataset.isDemographicData())
            {
                TableInfo tInfo = dataset.getStorageTableInfo();
                if (null == tInfo) continue;
                //TODO: Use Property URI & Make sure this is set properly
                ColumnInfo col = tInfo.getColumn("StartDate");
                if (null != col)
                {
                    String subselect = "(SELECT MIN(" + col.getSelectName() + ") FROM " + tInfo +
                            " WHERE " + tInfo + ".ParticipantId = " + tableParticipant + ".ParticipantId" +
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
        Parameter.TypedValue startDateParam = new Parameter.TypedValue(getStudy().getStartDate(), Types.TIMESTAMP);
        Table.execute(schema, sqlUpdateStartDates, new Object[] {startDateParam, getStudy().getContainer()});
    }

    StudyImpl getStudy()
    {
        return _study;
    }
}
