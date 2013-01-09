/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

package org.labkey.study.visitmanager;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.study.CohortFilter;
import org.labkey.study.StudySchema;
import org.labkey.study.model.CohortManager;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.VisitMapKey;
import org.labkey.study.query.StudyQuerySchema;

import javax.servlet.ServletContextEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

/**
 * User: brittp
 * Created: Feb 29, 2008 11:23:08 AM
 */
public abstract class VisitManager
{
    private static final Logger LOGGER = Logger.getLogger(VisitManager.class);

    protected StudyImpl _study;
    private TreeMap<Double, VisitImpl> _sequenceMap;

    protected VisitManager(StudyImpl study)
    {
        _study = study;
    }

    /**
     * Updates the participant, visit, and participant visit tables. Also updates automatic cohort assignments.
     * @param user the current user
     * @param changedDatasets the datasets that may have one or more rows modified
     */
    public void updateParticipantVisits(User user, Collection<DataSetDefinition> changedDatasets)
    {
        updateParticipantVisits(user, changedDatasets, null, null, true);
    }

    /**
     * Updates the participant, visit, and (optionally) participant visit tables. Also updates automatic cohort assignments.
     * @param user the current user
     * @param changedDatasets the datasets that may have one or more rows modified
     * @param potentiallyAddedParticipants optionally, the specific participants that may have been added to the study.
     * If null, all of the changedDatasets and specimens will be checked to see if they contain new participants
     * @param potentiallyDeletedParticipants optionally, the specific participants that may have been removed from the
     * study. If null, all participants will be checked to see if they are still in the study.
     * @param participantVisitResyncRequired If true, will force an update of the ParticipantVisit mapping for this study
     */
    public void updateParticipantVisits(User user, Collection<DataSetDefinition> changedDatasets, @Nullable Set<String> potentiallyAddedParticipants, @Nullable Set<String> potentiallyDeletedParticipants, boolean participantVisitResyncRequired)
    {
        updateParticipants(user, changedDatasets, potentiallyAddedParticipants, potentiallyDeletedParticipants);
        if (participantVisitResyncRequired)
        {
            updateParticipantVisitTable(user);
        }
        updateVisitTable(user);

        Integer cohortDatasetId = _study.getParticipantCohortDataSetId();
        // Only bother updating cohort assignment if we're doing automatic cohort assignment and there's an edit
        // to the dataset that specifies the cohort
        if (!_study.isManualCohortAssignment() &&
                cohortDatasetId != null &&
                changedDatasets.contains(StudyManager.getInstance().getDataSetDefinition(_study, cohortDatasetId)))
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

        StudyManager.getInstance().clearParticipantVisitCaches(getStudy());
    }


    public String getLabel()
    {
        return "Visit";
    }


    public static String getParticipantSequenceNumExpr(DbSchema schema, String ptidColumnName, String sequenceNumColumnName)
    {
        SqlDialect dialect = schema.getSqlDialect();
        String strType = dialect.sqlTypeNameFromSqlType(Types.VARCHAR);

        //CAST(CAST(? AS NUMERIC(15, 4)) AS " + strType +

        StringBuilder participantSequenceNum = new StringBuilder("(");
        participantSequenceNum.append(dialect.concatenate(ptidColumnName, "'|'", "CAST(CAST(" + sequenceNumColumnName + " AS NUMERIC(15,4)) AS " + strType + ")"));
        participantSequenceNum.append(")");

        return participantSequenceNum.toString();
    }


    public String getPluralLabel()
    {
        return "Visits";
    }

    protected abstract void updateParticipantVisitTable(@Nullable User user);
    protected abstract void updateVisitTable(User user);

    // Produce appropriate SQL for getVisitSummary().  The SQL must select dataset ID, sequence number, and then the specified statistics;
    // it also needs to filter by cohort and qcstates.  Tables providing the statistics must be aliased using the provided alias.
    protected abstract SQLFragment getVisitSummarySql(CohortFilter cohortFilter, QCStateSet qcStates, String stats, String alias, boolean showAll, boolean useVisitId);

    public Map<VisitMapKey, VisitStatistics> getVisitSummary(CohortFilter cohortFilter, QCStateSet qcStates, Set<VisitStatistic> stats, boolean showAll) throws SQLException
    {
        String alias = "SD";
        StringBuilder statsSql = new StringBuilder();

        for (VisitStatistic stat : stats)
        {
            statsSql.append(", ");
            statsSql.append(stat.getSql(alias));
        }

        boolean useVisitId = true;

        SQLFragment sql = getVisitSummarySql(cohortFilter, qcStates, statsSql.toString(), alias, showAll, useVisitId);
        ResultSet rows = null;

        try
        {
            rows = Table.executeQuery(StudySchema.getInstance().getSchema(), sql, false, false);

            Map<VisitMapKey, VisitStatistics> visitSummary = new HashMap<VisitMapKey, VisitStatistics>();
            VisitMapKey key = null;
            VisitStatistics statistics = new VisitStatistics();

            while (rows.next())
            {
                int datasetId = rows.getInt(1);
                int visitRowId;

                if (useVisitId)
                {
                    visitRowId = rows.getInt(2);
                    if (rows.wasNull())
                        continue;
                }
                else
                {
                    double sequenceNum = rows.getDouble(2);
                    VisitImpl v = findVisitBySequence(sequenceNum);
                    if (null == v)
                        continue;
                    visitRowId = v.getRowId();
                }

                if (null == key || key.datasetId != datasetId || key.visitRowId != visitRowId)
                {
                    if (key != null)
                        visitSummary.put(key, statistics);
                    key = new VisitMapKey(datasetId, visitRowId);
                    statistics = new VisitStatistics();
                }

                // Accumulate all the statistics columns
                int column = 3;

                for (VisitStatistic stat : stats)
                    statistics.add(stat, rows.getInt(column++));
            }

            if (key != null)
                visitSummary.put(key, statistics);

//            assert dump(visitSummary, stats);
            return visitSummary;
        }
        finally
        {
            ResultSetUtil.close(rows);
        }
    }

    boolean dump(Map<VisitMapKey, VisitStatistics> map, Set<VisitStatistic> set)
    {
        VisitStatistic[] statsToDisplay = set.toArray(new VisitStatistic[set.size()]);
        for (Map.Entry<VisitMapKey,VisitStatistics> e : map.entrySet())
        {
            VisitMapKey key = e.getKey();
            VisitStatistics stats = e.getValue();
            System.out.println("datasetId=" + key.datasetId + " visitRowId=" + key.visitRowId + " stat=" + (null==stats?"null":stats.get(statsToDisplay[0])));
        }
        return true;
    }


    public static void cancelParticipantPurge(Container c)
    {
        synchronized (POTENTIALLY_DELETED_PARTICIPANTS)
        {
            POTENTIALLY_DELETED_PARTICIPANTS.remove(c);
        }
        synchronized (PURGE_PARTICIPANT_LOCK)
        {
            // Wait until the current purge is complete before returning
        }
    }


    @SuppressWarnings({"UnusedDeclaration"})  // Always used by enumerating values()
    public enum VisitStatistic
    {
        // First enum value is the default (always selected if none are selected)
        ParticipantCount
        {
            @Override
            String getSql(@NotNull String alias)
            {
                return "CAST(COUNT(DISTINCT " + alias + ".ParticipantId) AS INT)";
            }

            @Override
            public String getDisplayString(Study study)
            {
                return study.getSubjectNounSingular() + " Count";
            }},

        RowCount
        {
            @Override
            String getSql(@NotNull String alias)
            {
                return "CAST(COUNT(*) AS INT)";
            }

            @Override
            public String getDisplayString(Study study)
            {
                return "Row Count";
            }
        };

        abstract String getSql(@NotNull String alias);
        public abstract String getDisplayString(Study study);
    }

    public static class VisitStatistics
    {
        private final Map<VisitStatistic, Integer> _map;

        public VisitStatistics()
        {
            _map = new EnumMap<VisitStatistic, Integer>(VisitStatistic.class);

            for (VisitStatistic stat : VisitStatistic.values())
                _map.put(stat, 0);
        }

        public void add(VisitStatistic stat, int count)
        {
            _map.put(stat, _map.get(stat) + count);
        }

        public int get(VisitStatistic stat)
        {
            return _map.get(stat);
        }
    }

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
        return (null != overlaps && overlaps != 0);
    }

    /** Update the Participants table to match the entries in StudyData. */
    protected void updateParticipants(User user, Collection<DataSetDefinition> changedDatasets,
        final Set<String> potentiallyInsertedParticipants, 
        Set<String> potentiallyDeletedParticipants)
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
                scheduleParticipantPurge(potentiallyDeletedParticipants);
            }

            updateStartDates(user);


            //
            // tell the search service about potential new ptids
            //
            final SearchService ss = ServiceRegistry.get(SearchService.class);
            final String cid = _study.getContainer().getId();
            if (null != ss)
            {
                if (null != potentiallyInsertedParticipants)
                {
                    final ArrayList<String> ptids = new ArrayList<String>(potentiallyInsertedParticipants);
                    ArrayList<Pair<String,String>> list = new ArrayList<Pair<String, String>>(ptids.size());
                    for (String ptid : ptids)
                        list.add(new Pair<String,String>(cid,ptid));
                    ss.addParticipantIds(list);
                    Runnable r = new Runnable(){ public void run(){
                        StudyManager.indexParticipantView(ss.defaultTask(), _study.getContainer(), ptids);
                    }};
                    ss.defaultTask().addRunnable(r, SearchService.PRIORITY.group);
                }
                else
                {
                    Runnable r = new Runnable() { public void run()
                    {
                        ResultSet rs = null;
                        try
                        {
                            rs = Table.executeQuery(StudySchema.getInstance().getSchema(),
                                "SELECT container, participantid FROM study.participant study\n" +
                                "    WHERE study.container=? AND NOT EXISTS (SELECT participantid FROM search.participantindex search\n" +
                                "      WHERE search.container=? AND search.participantid=study.participantid)", new Object[]{cid,cid});
                            ArrayList<Pair<String,String>> list = new ArrayList<Pair<String, String>>();
                            while (rs.next())
                                list.add(new Pair<String,String>(rs.getString(1),rs.getString(2)));
                            ss.addParticipantIds(list);
                            ArrayList<String> ptids = new ArrayList<String>(list.size());
                            for (Pair<String,String> p : list)
                                ptids.add(p.second);
                            StudyManager.indexParticipantView(ss.defaultTask(), _study.getContainer(), ptids);
                        }
                        catch (SQLException x)
                        {
                            throw new RuntimeSQLException(x);
                        }
                        finally
                        {
                            ResultSetUtil.close(rs);
                        }
                    } };
                    ss.defaultTask().addRunnable(r, SearchService.PRIORITY.group);
                }
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    /**
     * Queue up cleanup of unused participants to be processed by a background thread. We can get away without
     * persisting these requests in the event that the server is shut down before the request has been processed
     * because there's a scheduled maintenance task that does a full resync on a daily basis.
     *
     * @param potentiallyDeletedParticipants null if all participants in the study should be checked to see if they're
     * still referenced, or the specific set if we can only look at some of them for perf reasons
     */
    public void scheduleParticipantPurge(@Nullable Set<String> potentiallyDeletedParticipants)
    {
        assert potentiallyDeletedParticipants == null || !potentiallyDeletedParticipants.isEmpty() : "Shouldn't be called with an empty set of participant ids";
        Container container = getStudy().getContainer();
        synchronized (POTENTIALLY_DELETED_PARTICIPANTS)
        {
            Set<String> mergedPTIDs;
            if (potentiallyDeletedParticipants == null)
            {
                // We are being asked to check all participants, so it doesn't matter if there are specific participants
                // that are queued
                mergedPTIDs = null;
            }
            else if (POTENTIALLY_DELETED_PARTICIPANTS.containsKey(container))
            {
                // We already have a set of participants queued to be potentially purged
                Set<String> existingPTIDs = POTENTIALLY_DELETED_PARTICIPANTS.get(container);
                if (existingPTIDs == null)
                {
                    // The existing request is for all participants, so respect that
                    mergedPTIDs = null;
                }
                else
                {
                    // Add the subset that are now being requested to the existing set
                    mergedPTIDs = existingPTIDs;
                    mergedPTIDs.addAll(potentiallyDeletedParticipants);
                }
            }
            else
            {
                // This is the only request for this study in the queue, so copy the set of participants
                mergedPTIDs = new HashSet<String>(potentiallyDeletedParticipants);
            }
            POTENTIALLY_DELETED_PARTICIPANTS.put(container, mergedPTIDs);

            if (TIMER == null)
            {
                // This is the first request, so start the timer
                TIMER = new Timer("Participant purge", true);
                TimerTask task = new PurgeParticipantsTask();
                TIMER.scheduleAtFixedRate(task, PURGE_PARTICIPANT_INTERVAL, PURGE_PARTICIPANT_INTERVAL);
                ShutdownListener listener = new ParticipantPurgeContextListener();
                // Add a shutdown listener to stop the worker thread if the webapp is shut down
                ContextListener.addShutdownListener(listener);
            }
        }
    }

    /** Study container -> set of participants that may no longer be referenced. Set is null if we don't know specific PTIDs. */
    private static final Map<Container, Set<String>> POTENTIALLY_DELETED_PARTICIPANTS = new HashMap<Container, Set<String>>();
    private static Timer TIMER;
    /** Number of milliseconds to wait between batches of participant purges */
    private static final long PURGE_PARTICIPANT_INTERVAL = DateUtils.MILLIS_PER_MINUTE * 5;

    private static final Object PURGE_PARTICIPANT_LOCK = new Object();

    private static SQLFragment studyDataPtids(Collection<DataSetDefinition> defs)
    {
        SQLFragment f = new SQLFragment();
        String union = "";
        for (DataSetDefinition d : defs)
        {
            TableInfo sti = d.getStorageTableInfo();
            if (null == sti)
                continue;
            f.append(union);
            f.append("SELECT DISTINCT participantid FROM ").append(sti.toString());
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
                    String subselect = schema.getSqlDialect().getDateTimeToDateCast("(SELECT MIN(" + col.getSelectName() + ") FROM " + tInfo +
                            " WHERE " + tInfo + ".ParticipantId = " + tableParticipant + ".ParticipantId" +
                            " AND " + tableParticipant + ".Container = ?)");
                    String sql = "UPDATE " + tableParticipant + " SET StartDate = " + subselect + " WHERE (" +
                            tableParticipant + ".StartDate IS NULL OR NOT " + tableParticipant + ".StartDate = " + subselect +
                            ") AND Container = ?";
                    Table.execute(StudyManager.getSchema(), sql, dataset.getContainer().getId(), dataset.getContainer().getId(), dataset.getContainer().getId());
                    break;
                }
            }
        }
        //No demographic data, so just set to study start date.
        String sqlUpdateStartDates = "UPDATE " + tableParticipant + " SET StartDate = ? WHERE Container = ? AND StartDate IS NULL";
        Parameter.TypedValue startDateParam = new Parameter.TypedValue(getStudy().getStartDate(), JdbcType.TIMESTAMP);
        Table.execute(schema, sqlUpdateStartDates, startDateParam, getStudy().getContainer());
    }

    protected static TableInfo getSpecimenTable(StudyImpl study)
    {
        // If this is an ancillary study, the specimen table may be subject to special filtering, so we need to use
        // the query table, rather than the underlying database table.  We don't do this in all cases for performance
        // reasons.
        if (study.isAncillaryStudy())
        {
            StudyQuerySchema studyQuerySchema = new StudyQuerySchema(study, null, false);
            return studyQuerySchema.getTable(StudyQuerySchema.SIMPLE_SPECIMEN_TABLE_NAME);
        }
        else
            return StudySchema.getInstance().getTableInfoSpecimen();
    }


    /** @param potentiallyDeletedParticipants null if all participants should be examined,
     * or the subset of all participants that might have been deleted and should be checked
     * @return the number of participants that were deleted */

     public static int performParticipantPurge(@NotNull StudyImpl study, @Nullable Set<String> potentiallyDeletedParticipants)
    {
        if (potentiallyDeletedParticipants != null && potentiallyDeletedParticipants.isEmpty())
        {
            return 0;
        }

        for (int retry = 1 ; retry >= 0 ; retry--)
        {
            try
            {
                DbSchema schema = StudySchema.getInstance().getSchema();
                TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
                TableInfo tableSpecimen = getSpecimenTable(study);

                SQLFragment ptids = new SQLFragment();
                SQLFragment studyDataPtids = studyDataPtids(study.getDataSets());
                if (null != studyDataPtids)
                {
                    ptids.append(studyDataPtids);
                    ptids.append(" UNION\n");
                }
                ptids.append("SELECT DISTINCT ptid FROM ");
                ptids.append(tableSpecimen, "spec");
                ptids.append(" WHERE spec.container=?");
                ptids.add(study.getContainer().getId());

                SQLFragment del = new SQLFragment();
                del.append("DELETE FROM ").append(tableParticipant.getSelectName()).append(" WHERE container=? ");
                del.add(study.getContainer().getId());
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

                return Table.execute(schema, del);
            }
            catch (SQLException x)
            {
                if (retry != 0 && ("42P01".equals(x.getSQLState()) || "42S02".equals(x.getSQLState())))
                {
                    StudyManager.getInstance().clearCaches(study.getContainer(), false);
                    continue; // retry
                }
                throw new RuntimeSQLException(x);
            }
        }
        return 0;
    }


    StudyImpl getStudy()
    {
        return _study;
    }

    private static class PurgeParticipantsTask extends TimerTask
    {
        @Override
        public void run()
        {
            try
            {
                // There's a purge in process, so grab the lock
                synchronized (PURGE_PARTICIPANT_LOCK)
                {
                    while (true)
                    {
                        Container container;
                        Set<String> potentiallyDeletedParticipants;

                        synchronized (POTENTIALLY_DELETED_PARTICIPANTS)
                        {
                            if (POTENTIALLY_DELETED_PARTICIPANTS.isEmpty())
                            {
                                return;
                            }
                            // Grab the first study to be purged, and exit the synchronized block quickly
                            Iterator<Map.Entry<Container, Set<String>>> i = POTENTIALLY_DELETED_PARTICIPANTS.entrySet().iterator();
                            Map.Entry<Container, Set<String>> entry = i.next();
                            i.remove();
                            container = entry.getKey();
                            potentiallyDeletedParticipants = entry.getValue();
                        }

                        // Now, outside the synchronization, do the actual purge
                        StudyImpl study = StudyManager.getInstance().getStudy(container);
                        if (study != null)
                        {
                            int deleted = performParticipantPurge(study, potentiallyDeletedParticipants);
                            if (deleted > 0)
                            {
                                StudyManager.getInstance().getVisitManager(study).updateParticipantVisitTable(null);
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                LOGGER.error("Failed to purge participants", e);
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }
    }

    private static class ParticipantPurgeContextListener implements ShutdownListener
    {
        @Override
        public void shutdownPre(ServletContextEvent servletContextEvent) {}

        @Override
        public void shutdownStarted(ServletContextEvent servletContextEvent)
        {
            if (TIMER != null)
            {
                TIMER.cancel();
            }
        }
    }
}
