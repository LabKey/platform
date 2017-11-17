/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.security.User;
import org.labkey.api.study.DataspaceContainerFilter;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.api.util.DateUtil;
import org.labkey.study.CohortFilter;
import org.labkey.study.StudySchema;
import org.labkey.study.StudyUnionTableInfo;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.query.DatasetTableImpl;
import org.labkey.study.query.ParticipantGroupFilterClause;
import org.labkey.study.query.StudyQuerySchema;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Manages bookkeeping for studies using assigned visit identifiers
 * User: brittp
 * Created: Feb 29, 2008 11:23:56 AM
 */
public class SequenceVisitManager extends VisitManager
{
    public SequenceVisitManager(StudyImpl study)
    {
        super(study);
    }

    @Override
    protected SQLFragment getVisitSummarySql(User user, CohortFilter cohortFilter, QCStateSet qcStates, String statsSql, String alias, boolean showAll)
    {
        TableInfo studyData = showAll ?
                StudySchema.getInstance().getTableInfoStudyData(getStudy(), null) :
                StudySchema.getInstance().getTableInfoStudyDataVisible(getStudy(), null);
        TableInfo participantTable = StudySchema.getInstance().getTableInfoParticipant();

        if (_study.isDataspaceStudy())
        {
            StudyQuerySchema querySchema = (StudyQuerySchema)DefaultSchema.get(user, _study.getContainer(), "study");

            studyData = new FilteredTable<>(studyData, querySchema, new DataspaceContainerFilter(user, _study));

            ParticipantGroup group = querySchema.getSessionParticipantGroup();
            if (null != group)
            {
                FieldKey participantFieldKey = new FieldKey(null,"ParticipantId");
                ParticipantGroupFilterClause pgfc = new ParticipantGroupFilterClause(participantFieldKey, group);
                ((FilteredTable)studyData).addCondition(new SimpleFilter(pgfc));
            }
        }

        SQLFragment sql = new SQLFragment();
        sql.appendComment("<SequenceVisitManager.getVisitSummarySql>", participantTable.getSqlDialect());

        SQLFragment sqlSequenceVisitMap = new SQLFragment();
        sqlSequenceVisitMap.appendComment("<MapSequenceNumToVisitRowId>", participantTable.getSqlDialect());
        sqlSequenceVisitMap.append("\nSELECT Container, SequenceNum, MIN(VisitRowId) AS VisitId");
         sqlSequenceVisitMap.append("\n\tFROM ").append(StudySchema.getInstance().getTableInfoParticipantVisit().getFromSQL("PV"));
        if (!_study.isDataspaceStudy())
        {
            sqlSequenceVisitMap.append("\n\tWHERE ").append("PV.Container = ?");
            sqlSequenceVisitMap.add(_study.getContainer());
        }
        sqlSequenceVisitMap.append("\n\tGROUP BY Container, SequenceNum");
        sqlSequenceVisitMap.appendComment("</MapSequenceNumToVisitRowId>", participantTable.getSqlDialect());

        SQLFragment keyCols = new SQLFragment("DatasetId, SVM.VisitId");

        if (cohortFilter == null)
        {
            sql.append("SELECT ").append(keyCols);
            sql.append(statsSql);
            sql.append("\nFROM ").append(studyData.getFromSQL(alias));
            sql.append(" LEFT OUTER JOIN (").append(sqlSequenceVisitMap).append(") AS SVM ON ")
                    .append(alias).append(".SequenceNum = SVM.SequenceNum AND ")
                    .append(alias).append(".container = SVM.container");

            String where = "\nWHERE ";
            if (null != qcStates)
                sql.append(where).append(qcStates.getStateInClause(DatasetTableImpl.QCSTATE_ID_COLNAME));
            sql.append("\nGROUP BY ").append(keyCols);
            sql.append("\nORDER BY 1, 2");
        }
        else
        {
            switch (cohortFilter.getType())
            {
                case DATA_COLLECTION:
                {
                    sql.append("SELECT ").append(keyCols).append(statsSql);
                    sql.append("\nFROM ").append(studyData.getFromSQL(alias));
                    sql.append(" INNER JOIN study.ParticipantVisit PV ON (")
                            .append(alias).append(".container = ? AND \n\tPV.ParticipantId = ").append(alias).append(".ParticipantId AND \n\tPV.SequenceNum = ")
                            .append(alias).append(".SequenceNum AND\n\t").append(alias).append(".container=PV.Container AND \n" + "\tPV.CohortID = ?)\n");
                    sql.add(cohortFilter.getCohortId());
                    sql.append(" LEFT OUTER JOIN (").append(sqlSequenceVisitMap).append(") AS SVM ON ")
                            .append(alias).append(".SequenceNum = SVM.SequenceNum AND ")
                            .append(alias).append(".container = SVM.container");
                    String where = "\nWHERE ";
                    if (qcStates != null)
                        sql.append(where).append(qcStates.getStateInClause(DatasetTableImpl.QCSTATE_ID_COLNAME));
                    sql.append("\nGROUP BY ").append(keyCols);
                    sql.append("\nORDER BY 1, 2");
                    break;
                }
                case PTID_CURRENT:
                case PTID_INITIAL:
                {
                    sql.append("SELECT ").append(keyCols).append(statsSql);
                    sql.append("\nFROM ").append(studyData.getFromSQL(alias))
                        .append(" INNER JOIN ").append(participantTable.getFromSQL("P")).append(" ON (P.ParticipantId = ").append(alias).append(".ParticipantId")
                        .append(" AND ").append(alias).append(".container=P.Container AND P.").append(cohortFilter.getType() == CohortFilter.Type.PTID_CURRENT ? "CurrentCohortId" : "InitialCohortId").append(" = ?)\n");
                    sql.add(cohortFilter.getCohortId());
                    sql.append(" LEFT OUTER JOIN (").append(sqlSequenceVisitMap).append(") AS SVM ON ")
                            .append(alias).append(".SequenceNum = SVM.SequenceNum AND ")
                            .append(alias).append(".container = SVM.container");
                    String where = "\nWHERE ";
                    if (qcStates != null)
                        sql.append(where).append(qcStates.getStateInClause(DatasetTableImpl.QCSTATE_ID_COLNAME));
                    sql.append("\nGROUP BY ").append(keyCols);
                    sql.append("\nORDER BY 1, 2");
                    break;
                }
            }
        }
        sql.appendComment("</SequenceVisitManager.getVisitSummarySql>", participantTable.getSqlDialect());
        return sql;
    }


    /**
     * TODO: this is a performance HACK
     * TODO: we should be incrementally updating ParticipantVisit, rather than trying to speed up resync!
     * TDOO: see 19867: Speed issues when inserting into study datasets
     */
    protected void updateParticipantVisitTableAfterInsert(@Nullable User user, DatasetDefinition ds, @Nullable Set<String> potentiallyAddedParticipants, @Nullable Logger logger)
    {
        info(logger, "SequenceVisitManager: updateParticipantVisitTableAfterInsert");
        DbSchema schema = StudySchema.getInstance().getSchema();
        Container container = getStudy().getContainer();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        StudyUnionTableInfo tableStudyData = (StudyUnionTableInfo)StudySchema.getInstance()
                .getTableInfoStudyDataFiltered(getStudy(), Collections.singleton(ds), user);

        //
        // populate ParticipantVisit
        //
        SQLFragment sqlSelect = new SQLFragment();
        sqlSelect.append("SELECT DISTINCT ParticipantId, SequenceNum, ParticipantSequenceNum FROM ");
        sqlSelect.append(tableStudyData.getParticipantSequenceNumSQL("SD"));
        sqlSelect.append(" WHERE Container = ? AND ParticipantId IS NOT NULL and SequenceNum IS NOT NULL AND ParticipantSequenceNum IS NOT NULL");
        sqlSelect.add(container);
        if (null != potentiallyAddedParticipants && !potentiallyAddedParticipants.isEmpty() && potentiallyAddedParticipants.size() < 450)
        {
            sqlSelect.append(" AND SD.ParticipantId ");
            schema.getSqlDialect().appendInClauseSql(sqlSelect, potentiallyAddedParticipants);
        }
        sqlSelect.append("\nEXCEPT\n");
        sqlSelect.append("SELECT DISTINCT ParticipantId, SequenceNum, ParticipantSequenceNum FROM ").append(tableParticipantVisit.getFromSQL("PV")).append(" WHERE Container = ?");
        sqlSelect.add(container);
        if (null != potentiallyAddedParticipants && !potentiallyAddedParticipants.isEmpty() && potentiallyAddedParticipants.size() < 450)
        {
            sqlSelect.append(" AND PV.ParticipantId ");
            schema.getSqlDialect().appendInClauseSql(sqlSelect, potentiallyAddedParticipants);
        }

        SQLFragment sqlInsertParticipantVisit = new SQLFragment();
        sqlSelect.appendComment("<SequenceVisitManager.updateParticipantVisitTableAfterInsert>", schema.getSqlDialect());
        sqlInsertParticipantVisit.append("INSERT INTO ").append(tableParticipantVisit.getSelectName());
        sqlInsertParticipantVisit.append(" (Container, ParticipantId, SequenceNum, ParticipantSequenceNum)\n");
        sqlInsertParticipantVisit.append("SELECT ? AS Container, ParticipantId, SequenceNum, ParticipantSequenceNum FROM (");
        sqlInsertParticipantVisit.add(container);
        sqlInsertParticipantVisit.append(sqlSelect);
        sqlInsertParticipantVisit.append(") _");
        sqlInsertParticipantVisit.appendComment("/<SequenceVisitManager.updateParticipantVisitTableAfterInsert>", schema.getSqlDialect());
        SqlExecutor executor = new SqlExecutor(schema);
        executor.execute(sqlInsertParticipantVisit);

        _updateVisitRowId(false, logger);
        _updateVisitDate(user, ds, logger);
    }


    protected void updateParticipantVisitTable(@Nullable User user, @Nullable Logger logger)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        SqlDialect d = schema.getSqlDialect();
        Container container = getStudy().getContainer();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableSpecimen = getSpecimenTable(getStudy(), user);
        StudyUnionTableInfo tableStudyData = (StudyUnionTableInfo)StudySchema.getInstance().getTableInfoStudyData(getStudy(), user);

        //
        // populate ParticipantVisit
        //
        SQLFragment sqlSelect = new SQLFragment();
        sqlSelect.append("SELECT DISTINCT ParticipantId, SequenceNum, ParticipantSequenceNum FROM ");
        sqlSelect.append(tableStudyData.getParticipantSequenceNumSQL("SD"));
        sqlSelect.append("\nWHERE  ParticipantId IS NOT NULL and SequenceNum IS NOT NULL AND ParticipantSequenceNum IS NOT NULL");
        sqlSelect.append("\nEXCEPT\n");
        sqlSelect.append("SELECT DISTINCT ParticipantId, SequenceNum, ParticipantSequenceNum FROM ").append(tableParticipantVisit.getFromSQL("PV")).append(" WHERE Container=CAST(? AS ").append(d.getGuidType()).append(")\n");
        sqlSelect.add(container);

        SQLFragment sqlInsertParticipantVisit = new SQLFragment();
        sqlInsertParticipantVisit.appendComment("<SequenceVisitManager.updateParticipantVisitTable>", schema.getSqlDialect());
        sqlInsertParticipantVisit.append("INSERT INTO ").append(tableParticipantVisit.getSelectName());
        sqlInsertParticipantVisit.append(" (Container, ParticipantId, SequenceNum, ParticipantSequenceNum)\n");
        sqlInsertParticipantVisit.append("SELECT CAST(? AS " + d.getGuidType() +"), ParticipantId, SequenceNum, ParticipantSequenceNum\n");
        sqlInsertParticipantVisit.add(container);
        sqlInsertParticipantVisit.append("FROM (");
        sqlInsertParticipantVisit.append(sqlSelect);
        sqlInsertParticipantVisit.append(") _");
        sqlInsertParticipantVisit.appendComment("/<SequenceVisitManager.updateParticipantVisitTable>", schema.getSqlDialect());
        SqlExecutor executor = new SqlExecutor(schema);
        executor.execute(sqlInsertParticipantVisit);

        //
        // Delete ParticipantVisit where the participant does not exist anymore
        //   obviously the participants table needs to be updated first
        //
        purgeParticipantsFromParticipantsVisitTable(container);

        // after assigning visit dates to all study data-generated visits, we insert any extra ptid/sequencenum/date combinations
        // that are found in the specimen archives.  We simply trust the specimen draw date in this case, rather than relying on the
        // visit table to tell us which date corresponds to which visit:
        sqlInsertParticipantVisit = new SQLFragment();
        sqlInsertParticipantVisit.appendComment("<SequenceVisitManager.updateParticipantVisitTable>",schema.getSqlDialect());
        sqlInsertParticipantVisit.append("INSERT INTO ").append(tableParticipantVisit.getSelectName());
        sqlInsertParticipantVisit.append(" (Container, ParticipantId, SequenceNum, ParticipantSequenceNum)\n");
        sqlInsertParticipantVisit.append("SELECT ? As Container, Ptid AS ParticipantId, VisitValue AS SequenceNum,\n");
        sqlInsertParticipantVisit.add(container);
        sqlInsertParticipantVisit.append("MIN(").append(getParticipantSequenceNumExpr(schema, "Ptid", "VisitValue")).append(") AS ParticipantSequenceNum\n");
        sqlInsertParticipantVisit.append("FROM ").append(tableSpecimen, "Specimen").append("\n");
        sqlInsertParticipantVisit.append("WHERE Ptid IS NOT NULL AND VisitValue IS NOT NULL AND NOT EXISTS (");
        sqlInsertParticipantVisit.append("SELECT ParticipantId, SequenceNum FROM ").append(tableParticipantVisit, "PV").append("\n");
        sqlInsertParticipantVisit.append("WHERE Container = ? AND Specimen.Ptid = PV.ParticipantId AND Specimen.VisitValue = PV.SequenceNum)\n");
        sqlInsertParticipantVisit.add(container);
        sqlInsertParticipantVisit.append("GROUP BY Ptid, VisitValue");
        sqlInsertParticipantVisit.appendComment("</SequenceVisitManager.updateParticipantVisitTable>",schema.getSqlDialect());
        executor.execute(sqlInsertParticipantVisit);

        //
        // fill in VisitRowId (need this to do the VisitDate computation)
        //
        _updateVisitRowId(true, logger);

        //
        // update VisitDate
        //

        _updateVisitDate(user);
    }


    private void _updateVisitDate(User user)
    {
        Study study = getStudy();
        Study visitStudy = StudyManager.getInstance().getStudyForVisits(getStudy());

        DbSchema schema = StudySchema.getInstance().getSchema();
        Container container = study.getContainer();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableVisit = StudySchema.getInstance().getTableInfoVisit();
        SqlExecutor executor = new SqlExecutor(schema);

        // update ParticipantVisit.VisitDate based on declared Visit.visitDateDatasetId
        ArrayList<DatasetDefinition> defsWithVisitDates = new ArrayList<>();
        for (DatasetDefinition def : _study.getDatasets())
            if (null != def.getVisitDateColumnName())
                defsWithVisitDates.add(def);

        if (defsWithVisitDates.isEmpty())
        {
            SQLFragment sqlUpdateVisitDates = new SQLFragment();
            sqlUpdateVisitDates.append("UPDATE ").append(tableParticipantVisit).append("\n")
                .append("SET VisitDate = NULL, Day = NULL")
                .append(" WHERE Container = ?");
            sqlUpdateVisitDates.add(visitStudy.getContainer());
            executor.execute(sqlUpdateVisitDates);
        }
        else
        {
            TableInfo tableStudyDataFiltered = StudySchema.getInstance().getTableInfoStudyDataFiltered(getStudy(), defsWithVisitDates, user);
            SQLFragment sqlUpdateVisitDates = new SQLFragment();
            sqlUpdateVisitDates.append("UPDATE ").append(tableParticipantVisit.getSelectName());
            if (!schema.getSqlDialect().isSqlServer())
                sqlUpdateVisitDates.append(" PV");          // For Postgres put "PV" here
            sqlUpdateVisitDates.append("\n").append("SET VisitDate = _VisitDate, Day = _VisitDay FROM\n")
                    .append(" (\n")
                    .append(" SELECT DISTINCT _VisitDate, _VisitDay, SequenceNum, ParticipantId, DatasetId\n")
                    .append(" FROM ").append(tableStudyDataFiltered.getFromSQL("SD1")).append(") SD,  ")
                    .append(tableVisit.getFromSQL("V"));

            if (schema.getSqlDialect().isSqlServer())
                sqlUpdateVisitDates.append(", ").append(tableParticipantVisit.getFromSQL("PV"));     // Have to put the "PV" here for MSSQL

            sqlUpdateVisitDates.append("\n WHERE  PV.VisitRowId = V.RowId AND")    // 'join' V
                    .append("   SD.ParticipantId = PV.ParticipantId AND SD.SequenceNum = PV.SequenceNum AND\n")   // 'join' SD
                    .append("   SD.DatasetId = V.VisitDateDatasetId AND V.Container = ? AND PV.Container = ?\n");

            sqlUpdateVisitDates.add(visitStudy.getContainer());
            sqlUpdateVisitDates.add(container);
            executor.execute(sqlUpdateVisitDates);
        }

        /* infer ParticipantVisit.VisitDate if it seems unambiguous
        String sqlCopyVisitDates = "UPDATE " + tableParticipantVisit + "\n" +
                "SET VisitDate = \n" +
                " (\n" +
                " SELECT MIN(SD.VisitDate)\n" +
                " FROM " + tableStudyData + " SD\n" +
                " WHERE SD.ParticipantId = ParticipantVisit.ParticipantId AND SD.SequenceNum = ParticipantVisit.SequenceNum AND\n" +
                "   SD.Container=? AND SD.VisitDate IS NOT NULL\n" +
                " GROUP BY SD.ParticipantId, SD.SequenceNum\n" +
                " HAVING COUNT(DISTINCT SD.VisitDate) = 1\n" +
                " )\n";
        if (schema.getSqlDialect() instanceof SqlDialectMicrosoftSQLServer) // for SQL Server 2000
            sqlCopyVisitDates += "FROM " + tableParticipantVisit + " ParticipantVisit\n";
        sqlCopyVisitDates += "WHERE Container=? AND VisitDate IS NULL";
        new SqlExecutor(schema).execute(sqlCopyVisitDates,
                new Object[]{study.getContainer(), study.getContainer()});
        */
    }


    private void _updateVisitDate(User user, DatasetDefinition def, @Nullable Logger logger)
    {
        info(logger, "Update visit dates");
        if (null == def.getVisitDateColumnName())
            return;

        Study study = getStudy();
        Study visitStudy = StudyManager.getInstance().getStudyForVisits(getStudy());

        // are there any visits marking this as the visitdataset?
        boolean isVisitDateDataset = false;
        for (Visit v : study.getVisits(Visit.Order.SEQUENCE_NUM))
        {
            if (null!=v.getVisitDateDatasetId() && v.getVisitDateDatasetId().intValue() == def.getDatasetId())
            {
                isVisitDateDataset = true;
                break;
            }
        }
        if (!isVisitDateDataset)
            return;

        DbSchema schema = StudySchema.getInstance().getSchema();
        Container container = study.getContainer();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableVisit = StudySchema.getInstance().getTableInfoVisit();
        SqlExecutor executor = new SqlExecutor(schema);

        // update ParticipantVisit.VisitDate based on declared Visit.visitDateDatasetId
        TableInfo tableStudyDataFiltered = StudySchema.getInstance().getTableInfoStudyDataFiltered(getStudy(), Collections.singleton(def), user);
        SQLFragment sqlUpdateVisitDates = new SQLFragment();
        sqlUpdateVisitDates.append("UPDATE ").append(tableParticipantVisit.getSelectName());
        if (!schema.getSqlDialect().isSqlServer())
            sqlUpdateVisitDates.append(" PV");          // For Postgres put "PV" here
        sqlUpdateVisitDates.append("\n").append("SET VisitDate = _VisitDate, Day = _VisitDay FROM\n")
                .append(" (\n")
                .append(" SELECT DISTINCT _VisitDate, _VisitDay, SequenceNum, ParticipantId, DatasetId\n")
                .append(" FROM ").append(tableStudyDataFiltered.getFromSQL("SD1")).append(") SD, ")
                .append(tableVisit.getFromSQL("V"));

        if (schema.getSqlDialect().isSqlServer())
            sqlUpdateVisitDates.append(", ").append(tableParticipantVisit.getFromSQL("PV"));     // Have to put the "PV" here for MSSQL

        sqlUpdateVisitDates.append("\n WHERE PV.VisitRowId = V.RowId AND")    // 'join' V
                .append("   SD.ParticipantId = PV.ParticipantId AND SD.SequenceNum = PV.SequenceNum AND\n")   // 'join' SD
                .append("   ? = V.VisitDateDatasetId AND V.Container = ? AND PV.Container = ?\n");

        sqlUpdateVisitDates.add(def.getDatasetId());
        sqlUpdateVisitDates.add(visitStudy.getContainer());
        sqlUpdateVisitDates.add(container);
        executor.execute(sqlUpdateVisitDates);
    }


    private void _updateVisitRowId(boolean updateAll, @Nullable Logger logger)
    {
        final boolean USE_CASE_STATEMENT = true;

        info(logger, "Update visit row IDs");
        DbSchema schema = StudySchema.getInstance().getSchema();

        final StudyImpl visitStudy = (StudyImpl)StudyManager.getInstance().getStudyForVisits(getStudy());

        SQLFragment seqnum2visit = new SQLFragment();
        if (USE_CASE_STATEMENT)
        {
            String caseStmt = generateSequenceToVisit(visitStudy, "SequenceNum");
            seqnum2visit.append(
                    "WITH seqnum2visit AS (" +
                            "  SELECT SequenceNum, " + caseStmt + " AS RowId\n" +
                            "  FROM (SELECT DISTINCT SequenceNum FROM study.ParticipantVisit WHERE Container = ?");
            if (!updateAll)
                seqnum2visit.append(" AND VisitRowId=-1");
            seqnum2visit.append(") seqnumPV)\n");
            seqnum2visit.add(getStudy().getContainer());
        }
        else
        {
            seqnum2visit.append(
                    "WITH seqnum2visit AS (" +
                    "  SELECT SequenceNum, COALESCE(V.RowId,-1) AS RowId\n" +
                    "  FROM (SELECT DISTINCT SequenceNum FROM study.ParticipantVisit WHERE Container = ?");
            if (!updateAll)
                seqnum2visit.append(" AND VisitRowId=-1");
            seqnum2visit.append(") seqnumPV, study.Visit V\n" +
                    "  WHERE SequenceNum BETWEEN V.SequenceNumMin AND V.SequenceNumMax AND V.Container = ?)\n");
            seqnum2visit.add(getStudy().getContainer());
            seqnum2visit.add(visitStudy.getContainer());
        }

        // NOTE (seqnum2visit.RowId != VisitRowId OR VisitRowId IS NULL) is because postgres doesn't seem to optimize
        // updating a column to the existing value
        SQLFragment sqlUpdateVisitRowId = new SQLFragment();
        sqlUpdateVisitRowId.append(seqnum2visit);
        if (schema.getSqlDialect().isPostgreSQL())
        {
            sqlUpdateVisitRowId.append(
                "UPDATE study.ParticipantVisit PV\n" +
                "        SET VisitRowId = RowId\n" +
                "        FROM seqnum2visit\n" +
                "        WHERE seqnum2visit.SequenceNum = PV.SequenceNum AND PV.Container = ? AND seqnum2visit.RowId <> VisitRowId");
            sqlUpdateVisitRowId.add(getStudy().getContainer());
        }
        else
        {
            sqlUpdateVisitRowId.append(
                "UPDATE PV\n" +
                "        SET VisitRowId = RowId\n" +
                "        FROM study.ParticipantVisit PV WITH (INDEX(ix_participantvisit_sequencenum)), seqnum2visit\n"+
                "        WHERE seqnum2visit.SequenceNum = PV.SequenceNum AND PV.Container = ? AND seqnum2visit.RowId <> VisitRowId");
            sqlUpdateVisitRowId.add(getStudy().getContainer());
        }
        if (!updateAll)
            sqlUpdateVisitRowId.append(" AND VisitRowId=-1");

        long start = System.currentTimeMillis();
        (null==logger?Logger.getLogger(SequenceVisitManager.class):logger).trace("START UPDATE", new Throwable());
        new SqlExecutor(schema).execute(sqlUpdateVisitRowId);
        (null==logger?Logger.getLogger(SequenceVisitManager.class):logger).trace("DONE UPDATE " + DateUtil.formatDuration(System.currentTimeMillis()-start));
    }


    /** Make sure there is a Visit for each row in StudyData otherwise rows will be orphaned */
    protected void updateVisitTable(User user, @Nullable Logger logger)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();

        SQLFragment sql = new SQLFragment("SELECT DISTINCT SequenceNum\n" +
                "FROM " + tableParticipantVisit + "\n"+
                "WHERE Container = ? AND (VisitRowId IS NULL OR VisitRowId = -1)");
        sql.add(getStudy().getContainer().getId());

        final TreeSet<Double> sequenceNums = new TreeSet<>();

        info(logger, "Select distinct sequence numbers from participant visit table");
        new SqlSelector(schema, sql).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                sequenceNums.add(rs.getDouble(1));
            }
        });

        if (sequenceNums.size() > 0)
        {
            info(logger, "Ensure visits for " + sequenceNums.size() + " distinct sequence numbers");
            StudyManager.getInstance().ensureVisits(getStudy(), user, sequenceNums, null);
            _updateVisitRowId(true, logger);
        }
    }

    // Return sql for fetching all datasets and their visit sequence numbers, given a container
    protected SQLFragment getDatasetSequenceNumsSQL(Study study)
    {
        SQLFragment sql = new SQLFragment();
        sql.append(
                "SELECT x.DatasetId AS DatasetId, CAST(x.SequenceNum AS FLOAT) AS SequenceNum\n" +
                        "FROM (" +
                        "     SELECT DISTINCT SequenceNum, DatasetId\n" +
                        "     FROM ").append(StudySchema.getInstance().getTableInfoStudyData(getStudy(), null).getFromSQL("SD")).append(
                ") x\nORDER BY DatasetId, SequenceNum");
        return sql;
    }



    private String generateSequenceToVisit(StudyImpl study, String sn)
    {
        List<VisitImpl> visits = study.getVisits(Visit.Order.SEQUENCE_NUM);
        if (visits.isEmpty())
            return "-1";
        return generateSequenceToVisit(visits, sn, 1);
    }
    private String generateSequenceToVisit(List<VisitImpl> visits, String sn, int indent)
    {
        if (visits.size() <= 16)
        {
            StringBuilder sb = new StringBuilder();
            boolean allEqual = visits.stream().allMatch(v -> v.getSequenceNumMin()==v.getSequenceNumMax());
            if (allEqual)
            {
                _indent(sb,indent);
                sb.append("CASE ").append(sn);
                for (VisitImpl v : visits)
                {
                    _indent(sb,indent);
                    sb.append("WHEN ");
                    v.appendSqlSequenceNumMin(sb);
                    sb.append(" THEN " ).append(v.getId());
                }
                _indent(sb,indent); sb.append("ELSE -1");
                _indent(sb,indent); sb.append("END");
            }
            else
            {
                _indent(sb,indent); sb.append("CASE");
                for (VisitImpl v : visits)
                {
                    if (v.getSequenceNumMin() == v.getSequenceNumMax())
                    {
                        _indent(sb, indent);
                        sb.append(" WHEN ").append(sn).append(" = ");
                        v.appendSqlSequenceNumMin(sb);
                        sb.append(" THEN ").append(v.getId());
                    }
                    else
                    {
                        _indent(sb,indent);
                        sb.append(" WHEN ").append(sn).append(" BETWEEN ");
                        v.appendSqlSequenceNumMin(sb).append(" AND ");
                        v.appendSqlSequenceNumMax(sb).append(" THEN ").append(v.getId());
                    }
                }
                _indent(sb,indent); sb.append("ELSE -1");
                _indent(sb,indent); sb.append("END");
            }
            return sb.toString();
        }
        else
        {
            StringBuilder sb = new StringBuilder();
            _indent(sb,indent); sb.append("CASE");
            int partStart = 0, partEnd = 0;
            for (int part=1 ; part<4 ; part++)
            {
                partEnd = visits.size()*part / 4;
                VisitImpl v = visits.get(partEnd);
                _indent(sb,indent); sb.append("WHEN ").append(sn).append(" < ");
                v.appendSqlSequenceNumMin(sb).append(" THEN");
                sb.append(generateSequenceToVisit(visits.subList(partStart, partEnd), sn, indent+1));
                partStart = partEnd;
            }
            _indent(sb,indent); sb.append("ELSE");
            sb.append(generateSequenceToVisit(visits.subList(partStart, visits.size()), sn, indent+1));
            _indent(sb,indent); sb.append("END");
            return sb.toString();
        }
    }
    private void _indent(StringBuilder sb, int indent)
    {
        sb.append("\n                                             ".substring(0,indent));
    }
}
