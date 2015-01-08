/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseSelector;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ExceptionFramework;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector.ForEachBlock;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.api.util.ContextListener;
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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
        updateParticipantVisits(user, changedDatasets, null, null, true, null);
    }

    private void info(@Nullable Logger logger, String message)
    {
        if (null != logger)
            logger.info(message);
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
     * @param logger Log4j logger to use for detailed performance information
     */
    public void updateParticipantVisits(User user, Collection<DataSetDefinition> changedDatasets, @Nullable Set<String> potentiallyAddedParticipants, @Nullable Set<String> potentiallyDeletedParticipants, boolean participantVisitResyncRequired, @Nullable Logger logger)
    {
        info(logger, "Updating participants");
        updateParticipants(changedDatasets, potentiallyAddedParticipants, potentiallyDeletedParticipants);
        if (participantVisitResyncRequired)
        {
            boolean mightHaveDeletedParticipants = null==potentiallyDeletedParticipants || !potentiallyDeletedParticipants.isEmpty();
            boolean exactlyOneDataset = null != changedDatasets && 1==changedDatasets.size();
            if (!mightHaveDeletedParticipants && exactlyOneDataset)
            {
                Iterator<DataSetDefinition> it = changedDatasets.iterator();
                it.hasNext();
                DataSetDefinition ds = it.next();
                info(logger, "Updating participant visit table for single dataset " + ds.getName());
                updateParticipantVisitTableAfterInsert(user, ds, potentiallyAddedParticipants);
            }
            else
            {
                info(logger, "Updating participant visit table");
                updateParticipantVisitTable(user);
            }
        }
        info(logger, "Updating visit table");
        updateVisitTable(user);

        info(logger, "Updating cohorts");
        Integer cohortDatasetId = _study.getParticipantCohortDatasetId();
        // Only bother updating cohort assignment if we're doing automatic cohort assignment and there's an edit
        // to the dataset that specifies the cohort
        if (!_study.isManualCohortAssignment() &&
                cohortDatasetId != null &&
                changedDatasets.contains(StudyManager.getInstance().getDatasetDefinition(_study, cohortDatasetId)))
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

        info(logger, "Clearing participant visit caches");
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

        return "(" + dialect.concatenate(ptidColumnName, "'|'", "CAST(CAST(" + sequenceNumColumnName + " AS NUMERIC(15,4)) AS " + strType + ")") + ")";
    }


    public String getPluralLabel()
    {
        return "Visits";
    }

    protected abstract void updateParticipantVisitTable(@Nullable User user);
    protected void updateParticipantVisitTableAfterInsert(@Nullable User user, @Nullable DataSetDefinition ds, @Nullable Set<String> potentiallyAddedParticipants)
    {
        updateParticipantVisitTable(user);
    }
    protected abstract void updateVisitTable(User user);

    // Produce appropriate SQL for getVisitSummary().  The SQL must select dataset ID, sequence number, and then the specified statistics;
    // it also needs to filter by cohort and qcstates.  Tables providing the statistics must be aliased using the provided alias.
    protected abstract SQLFragment getVisitSummarySql(User user, CohortFilter cohortFilter, QCStateSet qcStates, String stats, String alias, boolean showAll);

    public Map<VisitMapKey, VisitStatistics> getVisitSummary(User user, CohortFilter cohortFilter, QCStateSet qcStates, Set<VisitStatistic> stats, boolean showAll) throws SQLException
    {
        String alias = "SD";
        StringBuilder statsSql = new StringBuilder();

        for (VisitStatistic stat : stats)
        {
            statsSql.append(", ");
            statsSql.append(stat.getSql(alias));
        }

        Map<VisitMapKey, VisitStatistics> visitSummary = new HashMap<>();
        VisitMapKey key = null;
        VisitStatistics statistics = new VisitStatistics();

        SQLFragment sql = getVisitSummarySql(user, cohortFilter, qcStates, statsSql.toString(), alias, showAll);

        try (ResultSet rows = new SqlSelector(StudySchema.getInstance().getSchema(), sql).getResultSet(false, false))
        {
            while (rows.next())
            {
                int datasetId = rows.getInt(1);
                int visitRowId;

                visitRowId = rows.getInt(2);
                if (rows.wasNull())
                    continue;

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
        }

        if (key != null)
            visitSummary.put(key, statistics);

//            assert dump(visitSummary, stats);
        return visitSummary;
    }



    @SuppressWarnings("UnusedDeclaration")
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
            _map = new EnumMap<>(VisitStatistic.class);

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
        final Map<Integer, List<Double>> ret = new HashMap<>();

        new SqlSelector(StudySchema.getInstance().getSchema(), sql).forEach(new ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                Integer datasetId = rs.getInt(1);
                Double sequenceNum = rs.getDouble(2);
                List<Double> l = ret.get(datasetId);
                if (null == l)
                {
                    l = new ArrayList<>();
                    ret.put(datasetId, l);
                }
                l.add(sequenceNum);
            }
        });

        return ret;
    }

    /**
     * Return a map mapping the minimum value of a visit to the visit.
     * In the case of a date-based study, this is actually a "Day" map, not a sequence map
     */
    public TreeMap<Double, VisitImpl> getVisitSequenceMap()
    {
        List<VisitImpl> visits = getStudy().getVisits(Visit.Order.DISPLAY);
        TreeMap<Double, VisitImpl> visitMap = new TreeMap<>();
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
     */
    public boolean isVisitOverlapping(VisitImpl visit)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo visitTable = StudySchema.getInstance().getTableInfoVisit();

        SQLFragment sql = new SQLFragment("SELECT * FROM ");
        sql.append(visitTable, "vt");
        sql.append(" WHERE SequenceNumMax >= ? AND SequenceNumMin <= ? AND Container = ? AND RowId <> ?");
        sql.add(visit.getSequenceNumMin());
        sql.add(visit.getSequenceNumMax());
        sql.add(visit.getContainer());
        sql.add(visit.getRowId()); // exclude the specified visit itself; new visits will have a rowId of 0, which shouldn't conflict

        return new SqlSelector(schema, sql).exists();
    }

    /** Update the Participants table to match the entries in StudyData. */
    protected void updateParticipants(Collection<DataSetDefinition> changedDatasets,
                                      final Set<String> potentiallyInsertedParticipants,
                                      Set<String> potentiallyDeletedParticipants)
    {
        String c = getStudy().getContainer().getId();

        DbSchema schema = StudySchema.getInstance().getSchema();

        SQLFragment studyPtidsFragment = studyDataPtids(changedDatasets); // 17167

        // Don't bother if we explicitly know that no participants were added, or that no datasets were edited
        if (null != studyPtidsFragment && !changedDatasets.isEmpty() && (potentiallyInsertedParticipants == null || !potentiallyInsertedParticipants.isEmpty()))
        {
            // Postgres does not optimize NOT IN very well, so do a more complicated query
            // INSERT INTO participant FROM participantvisit WHERE not in participant...

            SQLFragment datasetParticipantsSQL = new SQLFragment();
            datasetParticipantsSQL.append(
                    "SELECT participantid FROM (").append(studyPtidsFragment).append(") _ptids\n");
            // Databases limit the size of IN clauses, so check that we won't blow the cap
            if (potentiallyInsertedParticipants != null && potentiallyInsertedParticipants.size() < 450)
            {
                // We have an explicit list of potentially added participants, so filter to only look at them
                datasetParticipantsSQL.append("WHERE participantid ");
                schema.getSqlDialect().appendInClauseSql(datasetParticipantsSQL, potentiallyInsertedParticipants);
            }
            datasetParticipantsSQL.append("\nEXCEPT\n");
            datasetParticipantsSQL.append(
                    "SELECT participantid FROM study.participant p WHERE container=?");
            datasetParticipantsSQL.add(c);

            SQLFragment insert = new SQLFragment();
            insert.append(
                    "INSERT INTO study.participant (container, participantid)\n" +
                    "SELECT ? AS container, participantid FROM (\n");
            insert.add(c);
            insert.append(datasetParticipantsSQL);
            insert.append(") _insert");

            new SqlExecutor(schema).execute(insert);
        }

        // If we don't know which participants might have been deleted, or we know and there are some,
        // keep the participant list up to date
        if (potentiallyDeletedParticipants == null || !potentiallyDeletedParticipants.isEmpty())
        {
            scheduleParticipantPurge(potentiallyDeletedParticipants);
        }

        updateStartDates();


        //
        // tell the search service about potential new ptids
        //
        final SearchService ss = ServiceRegistry.get(SearchService.class);
        final String cid = _study.getContainer().getId();
        if (null != ss)
        {
            if (null != potentiallyInsertedParticipants)
            {
                final ArrayList<String> ptids = new ArrayList<>(potentiallyInsertedParticipants);
                Runnable r = new Runnable(){ public void run(){
                    StudyManager.indexParticipants(ss.defaultTask(), _study.getContainer(), ptids);
                }};
                ss.defaultTask().addRunnable(r, SearchService.PRIORITY.group);
            }
            else
            {
                Runnable r = new Runnable() { public void run()
                {
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), cid);
                    filter.addCondition(FieldKey.fromParts("LastIndexed"), null, CompareType.ISBLANK);
                    List<String> ptids = new TableSelector(StudySchema.getInstance().getTableInfoParticipant(), Collections.singleton("ParticipantId"), filter, null).getArrayList(String.class);
                    StudyManager.indexParticipants(ss.defaultTask(), _study.getContainer(), ptids);
                } };
                ss.defaultTask().addRunnable(r, SearchService.PRIORITY.group);
            }
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
                mergedPTIDs = new HashSet<>(potentiallyDeletedParticipants);
            }
            POTENTIALLY_DELETED_PARTICIPANTS.put(container, mergedPTIDs);

            if (TIMER == null)
            {
                // This is the first request, so start the timer
                TIMER = new Timer("Participant purge", true);
                TimerTask task = new PurgeParticipantsTask(POTENTIALLY_DELETED_PARTICIPANTS);
                TIMER.scheduleAtFixedRate(task, PURGE_PARTICIPANT_INTERVAL, PURGE_PARTICIPANT_INTERVAL);
                ShutdownListener listener = new ParticipantPurgeContextListener();
                // Add a shutdown listener to stop the worker thread if the webapp is shut down
                ContextListener.addShutdownListener(listener);
            }
        }
    }

    /** Study container -> set of participants that may no longer be referenced. Set is null if we don't know specific PTIDs. */
    private static final Map<Container, Set<String>> POTENTIALLY_DELETED_PARTICIPANTS = new HashMap<>();
    private static Timer TIMER;
    /** Number of milliseconds to wait between batches of participant purges */
    private static final long PURGE_PARTICIPANT_INTERVAL = DateUtils.MILLIS_PER_MINUTE * 5;

    private static SQLFragment studyDataPtids(Collection<DataSetDefinition> defs)
    {
        SQLFragment f = new SQLFragment();
        String union = "";
        for (DataSetDefinition d : defs)
        {
            TableInfo sti = null;
            try
            {
                sti = d.getStorageTableInfo();
            }
            catch (Exception x)
            {
                // 19189: IllegalArgumentException in org.labkey.api.exp.api.StorageProvisioner.createTableInfo()
                // parial fix, but see 14603 and related
            }
            if (null == sti)
                continue;
            f.append(union);
            f.append("SELECT DISTINCT participantid FROM ").append(sti.getFromSQL("_"));
            if (d.isShared())
                f.append(" WHERE container=?").add(d.getContainer().getId());
            union = " UNION\n";
        }
        return f.isEmpty() ? null : f;
    }


    protected void updateStartDates()
    {
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
        //See if there are any demographic datasets that contain a start date
        DbSchema schema = StudySchema.getInstance().getSchema();

        for (DataSetDefinition dataset : getStudy().getDatasets())
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
                    new SqlExecutor(schema).execute(sql, dataset.getContainer().getId(), dataset.getContainer().getId(), dataset.getContainer().getId());
                    break;
                }
            }
        }
        //No demographic data, so just set to study start date.
        String sqlUpdateStartDates = "UPDATE " + tableParticipant + " SET StartDate = ? WHERE Container = ? AND StartDate IS NULL";
        Parameter.TypedValue startDateParam = new Parameter.TypedValue(getStudy().getStartDate(), JdbcType.TIMESTAMP);

        new SqlExecutor(schema).execute(sqlUpdateStartDates, startDateParam, getStudy().getContainer());
    }

    protected static TableInfo getSpecimenTable(StudyImpl study)
    {
        // If this is an ancillary study, the specimen table may be subject to special filtering, so we need to use
        // the query table, rather than the underlying database table.  We don't do this in all cases for performance
        // reasons.
        if (study.isAncillaryStudy())       // TODO: maybe we can always use SimpleSpecimen
        {
            StudyQuerySchema studyQuerySchema = StudyQuerySchema.createSchema(study, null, false);
            studyQuerySchema.setDontAliasColumns(true);
            return studyQuerySchema.getTable(StudyQuerySchema.SIMPLE_SPECIMEN_TABLE_NAME);
        }
        else
            return StudySchema.getInstance().getTableInfoSpecimen(study.getContainer());
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
                SQLFragment studyDataPtids = studyDataPtids(study.getDatasets());
                if (null != studyDataPtids)
                {
                    ptids.append(studyDataPtids);
                    ptids.append(" UNION\n");
                }
                ptids.append("SELECT DISTINCT ptid AS participantid FROM ");
                ptids.append(tableSpecimen, "_specimens_");

                SQLFragment ptidsP = new SQLFragment();
                ptidsP.append("SELECT participantid FROM ").append(tableParticipant.getSelectName()).append(" WHERE container=?");
                ptidsP.add(study.getContainer().getId());
                // Databases limit the size of IN clauses, so check that we won't blow the cap
                if (potentiallyDeletedParticipants != null && potentiallyDeletedParticipants.size() < 450)
                {
                    // We have an explicit list of potentially deleted participants, so filter to only look at them
                    ptidsP.append(" AND participantid ");
                    tableParticipant.getSqlDialect().appendInClauseSql(ptidsP, potentiallyDeletedParticipants);
                }

                SQLFragment del = new SQLFragment();
                del.append("DELETE FROM ").append(tableParticipant.getSelectName());
                del.append("\nWHERE container=? ");
                del.add(study.getContainer().getId());
                del.append(" AND participantid IN\n");
                del.append("(\n");
                del.append("    ").append(ptidsP).append("\n");
                del.append("    EXCEPT\n");
                del.append("    (SELECT ParticipantId FROM (").append(ptids).append(") _existing_)\n");
                del.append(")");

                SqlExecutor executor = new SqlExecutor(schema).setExceptionFramework(ExceptionFramework.JDBC);
                executor.execute(del);
            }
            catch (Exception x)
            {
                if (retry != 0 && (SqlDialect.isObjectNotFoundException(x)))
                {
                    StudyManager.getInstance().clearCaches(study.getContainer(), false);
                    continue; // retry
                }
                throw x;
            }
        }
        return 0;
    }


    /** remove rows in participantvisits that are not in the participant table */
    protected int purgeParticipantsFromParticipantsVisitTable(Container c)
    {
        /** Postgres at least seems to have major performance issues with the simple DELETE NOT IN version
         * DELETE FROM study.participantvisit WHERE Container = ? AND ParticipantId NOT IN (SELECT ParticipantId FROM study.participant WHERE Container= ?)
         */
        StudySchema study = StudySchema.getInstance();
        SQLFragment sqlSelect = new SQLFragment();
        sqlSelect.append(
                "SELECT DISTINCT ParticipantId FROM study.participantvisit WHERE Container=? EXCEPT SELECT DISTINCT ParticipantId FROM study.participant WHERE Container=?"
        );
        sqlSelect.add(c);
        sqlSelect.add(c);

        SQLFragment sqlDelete = new SQLFragment();
        sqlDelete.appendComment("<VisitManager.purgeParticipantsFromParticipantsVisitTable>", study.getSqlDialect());
        sqlDelete.append("DELETE FROM study.participantvisit WHERE Container= ? AND ParticipantId IN (");
        sqlDelete.add(c);
        sqlDelete.append(sqlSelect);
        sqlDelete.append(")");
        sqlDelete.appendComment("</VisitManager.purgeParticipantsFromParticipantsVisitTable>", study.getSqlDialect());
        return new SqlExecutor(study.getScope()).execute(sqlDelete);
    }


    StudyImpl getStudy()
    {
        return _study;
    }

    private static class ParticipantPurgeContextListener implements ShutdownListener
    {
        @Override
        public String getName()
        {
            return "Participant purge timer";
        }

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

    void _dump(String sql)
    {
        LOGGER.debug("DUMP -- " + sql);
        DbScope s = StudySchema.getInstance().getScope();
        new SqlExecutor(s).executeWithResults(new SQLFragment(sql), new BaseSelector.ResultSetHandler()
        {
            @Override
            public Object handle(ResultSet rs, Connection conn) throws SQLException
            {
                ResultSetUtil.logData(rs, LOGGER);
                return null;
            }
        });
    }
}
