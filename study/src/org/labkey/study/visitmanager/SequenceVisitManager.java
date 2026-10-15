/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyUtils;
import org.labkey.api.study.Visit;
import org.labkey.api.study.model.ParticipantGroup;
import org.labkey.api.util.DateUtil;
import org.labkey.study.StudySchema;
import org.labkey.study.StudyUnionTableInfo;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.query.DatasetTableImpl;
import org.labkey.study.query.ParticipantGroupFilterClause;
import org.labkey.study.query.StudyQuerySchema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
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
    public Set<CohortFilter.Type> supportedCohortFilterTypes()
    {
        return Set.of(CohortFilter.Type.PTID_CURRENT, CohortFilter.Type.PTID_INITIAL, CohortFilter.Type.DATA_COLLECTION);
    }

    @Override
    @Nullable
    protected SQLFragment getVisitSummarySql(StudyQuerySchema sqs, CohortFilter cohortFilter, QCStateSet qcStates, Set<VisitStatistic> stats, boolean showAll)
    {
        String alias = "SD";
        FilteredTable studyData = sqs.getStudyDatasetsUnion(showAll);
        if (null == studyData)
            return null;
        ColumnInfo datasetId = studyData.getColumn("DatasetId");
        if (null == datasetId)
            datasetId = studyData.getColumn("Dataset");
        ColumnInfo container = studyData.getColumn("Container");
        ColumnInfo participant = studyData.getColumn("ParticipantId");
        if (null == participant)
            participant = studyData.getColumn(sqs.getSubjectColumnName());
        ColumnInfo sequencenum = studyData.getColumn("SequenceNum");
        assert null!=datasetId && null!=container && null!=participant && null!=sequencenum;

        if (_study.isDataspaceStudy())
        {
            ParticipantGroup group = sqs.getSessionParticipantGroup();
            if (null != group)
            {
                FieldKey participantFieldKey = new FieldKey(null,"ParticipantId");
                ParticipantGroupFilterClause pgfc = new ParticipantGroupFilterClause(participantFieldKey, group);
                studyData.addCondition(new SimpleFilter(pgfc));
            }
        }

        TableInfo participantTable = StudySchema.getInstance().getTableInfoParticipant();

        SQLFragment sql = new SQLFragment();
        sql.appendComment("<SequenceVisitManager.getVisitSummarySql>", participantTable.getSqlDialect());

        SQLFragment sqlSequenceVisitMap = new SQLFragment();
        sqlSequenceVisitMap.appendComment("<MapSequenceNumToVisitRowId>", participantTable.getSqlDialect());
        sqlSequenceVisitMap.unsafeAppend("\nSELECT Container, SequenceNum, MIN(VisitRowId) AS VisitId");
        sqlSequenceVisitMap.unsafeAppend("\n\tFROM ").append(StudySchema.getInstance().getTableInfoParticipantVisit().getFromSQL("PV"));
        if (!_study.isDataspaceStudy())
        {
            sqlSequenceVisitMap.unsafeAppend("\n\tWHERE ").unsafeAppend("PV.Container = ?");
            sqlSequenceVisitMap.add(_study.getContainer());
        }
        sqlSequenceVisitMap.unsafeAppend("\n\tGROUP BY Container, SequenceNum");
        sqlSequenceVisitMap.appendComment("</MapSequenceNumToVisitRowId>", participantTable.getSqlDialect());

        SQLFragment keyCols = new SQLFragment().append(datasetId.getValueSql(alias)).unsafeAppend(", ").unsafeAppend("SVM.VisitId").unsafeAppend(" ");
        SQLFragment selectCols = new SQLFragment(keyCols);
        for (var stat : stats)
            selectCols.unsafeAppend(",").append(stat.getSql(participant.getValueSql(alias)));

        if (cohortFilter == null)
        {
            sql.unsafeAppend("SELECT ").append(selectCols);
            sql.unsafeAppend("\nFROM ").append(studyData.getFromSQL(alias));
            sql.unsafeAppend(" LEFT OUTER JOIN (").append(sqlSequenceVisitMap).unsafeAppend(") AS SVM ON ")
                    .append(sequencenum.getValueSql(alias)).unsafeAppend(" = SVM.SequenceNum AND ")
                    .append(container.getValueSql(alias)).unsafeAppend(" = SVM.container");

            String where = "\nWHERE ";
            if (null != qcStates)
                sql.unsafeAppend(where).append(qcStates.getStateInClause(DatasetTableImpl.QCSTATE_ID_COLNAME));
            sql.unsafeAppend("\nGROUP BY ").append(keyCols);
            sql.unsafeAppend("\nORDER BY 1, 2");
        }
        else
        {
            switch (cohortFilter.getType())
            {
                case DATA_COLLECTION:
                {
                    sql.unsafeAppend("SELECT ").append(selectCols);
                    sql.unsafeAppend("\nFROM ").append(studyData.getFromSQL(alias));
                    sql.unsafeAppend(" INNER JOIN study.ParticipantVisit PV ON (")
                            .append(container.getValueSql(alias)).unsafeAppend(" = ? AND \n\tPV.ParticipantId = ").append(participant.getValueSql(alias)).unsafeAppend(" AND \n\tPV.SequenceNum = ")
                            .append(sequencenum.getValueSql(alias)).unsafeAppend(" AND\n\t").append(container.getValueSql(alias)).unsafeAppend("=PV.Container AND \n" + "\tPV.CohortID = ?)\n");
                    sql.add(getStudy().getContainer());
                    sql.add(cohortFilter.getCohortId());
                    sql.unsafeAppend(" LEFT OUTER JOIN (").append(sqlSequenceVisitMap).unsafeAppend(") AS SVM ON ")
                            .append(sequencenum.getValueSql(alias)).unsafeAppend(" = SVM.SequenceNum AND ")
                            .append(container.getValueSql(alias)).unsafeAppend(" = SVM.container");
                    String where = "\nWHERE ";
                    if (qcStates != null)
                        sql.unsafeAppend(where).append(qcStates.getStateInClause(DatasetTableImpl.QCSTATE_ID_COLNAME));
                    sql.unsafeAppend("\nGROUP BY ").append(keyCols);
                    sql.unsafeAppend("\nORDER BY 1, 2");
                    break;
                }
                case PTID_CURRENT:
                case PTID_INITIAL:
                {
                    sql.unsafeAppend("SELECT ").append(selectCols);
                    sql.unsafeAppend("\nFROM ").append(studyData.getFromSQL(alias))
                        .unsafeAppend(" INNER JOIN ").append(participantTable.getFromSQL("P")).unsafeAppend(" ON (P.ParticipantId = ").append(participant.getValueSql(alias))
                        .unsafeAppend(" AND ").append(container.getValueSql(alias)).unsafeAppend("=P.Container AND P.").appendIdentifier(cohortFilter.getType() == CohortFilter.Type.PTID_CURRENT ? "CurrentCohortId" : "InitialCohortId").unsafeAppend(" = ?)\n");
                    sql.add(cohortFilter.getCohortId());
                    sql.unsafeAppend(" LEFT OUTER JOIN (").append(sqlSequenceVisitMap).unsafeAppend(") AS SVM ON ")
                            .append(sequencenum.getValueSql(alias)).unsafeAppend(" = SVM.SequenceNum AND ")
                            .append(container.getValueSql(alias)).unsafeAppend(" = SVM.container");
                    String where = "\nWHERE ";
                    if (qcStates != null)
                        sql.unsafeAppend(where).append(qcStates.getStateInClause(DatasetTableImpl.QCSTATE_ID_COLNAME));
                    sql.unsafeAppend("\nGROUP BY ").append(keyCols);
                    sql.unsafeAppend("\nORDER BY 1, 2");
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
    @Override
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
        sqlSelect.unsafeAppend("SELECT DISTINCT ParticipantId, SequenceNum, ParticipantSequenceNum FROM ");
        sqlSelect.append(tableStudyData.getParticipantSequenceNumSQL("SD"));
        sqlSelect.unsafeAppend(" WHERE Container = ? AND ParticipantId IS NOT NULL and SequenceNum IS NOT NULL AND ParticipantSequenceNum IS NOT NULL");
        sqlSelect.add(container);
        if (null != potentiallyAddedParticipants && !potentiallyAddedParticipants.isEmpty() && potentiallyAddedParticipants.size() < 450)
        {
            sqlSelect.unsafeAppend(" AND SD.ParticipantId ");
            schema.getSqlDialect().appendInClauseSql(sqlSelect, potentiallyAddedParticipants);
        }
        sqlSelect.unsafeAppend("\nEXCEPT\n");
        sqlSelect.unsafeAppend("SELECT DISTINCT ParticipantId, SequenceNum, ParticipantSequenceNum FROM ").append(tableParticipantVisit.getFromSQL("PV")).unsafeAppend(" WHERE Container = ?");
        sqlSelect.add(container);
        if (null != potentiallyAddedParticipants && !potentiallyAddedParticipants.isEmpty() && potentiallyAddedParticipants.size() < 450)
        {
            sqlSelect.unsafeAppend(" AND PV.ParticipantId ");
            schema.getSqlDialect().appendInClauseSql(sqlSelect, potentiallyAddedParticipants);
        }

        SQLFragment sqlInsertParticipantVisit = new SQLFragment();
        sqlSelect.appendComment("<SequenceVisitManager.updateParticipantVisitTableAfterInsert>", schema.getSqlDialect());
        sqlInsertParticipantVisit.unsafeAppend("INSERT INTO ").append(tableParticipantVisit);
        sqlInsertParticipantVisit.unsafeAppend(" (Container, ParticipantId, SequenceNum, ParticipantSequenceNum)\n");
        sqlInsertParticipantVisit.unsafeAppend("SELECT ? AS Container, ParticipantId, SequenceNum, ParticipantSequenceNum FROM (");
        sqlInsertParticipantVisit.add(container);
        sqlInsertParticipantVisit.append(sqlSelect);
        sqlInsertParticipantVisit.unsafeAppend(") _");
        sqlInsertParticipantVisit.appendComment("/<SequenceVisitManager.updateParticipantVisitTableAfterInsert>", schema.getSqlDialect());
        SqlExecutor executor = new SqlExecutor(schema);
        executor.execute(sqlInsertParticipantVisit);

        _updateVisitRowId(false, logger);
        _updateVisitDate(user, ds, logger);
    }

    @Override
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
        sqlSelect.unsafeAppend("SELECT DISTINCT ParticipantId, SequenceNum, ParticipantSequenceNum FROM ");
        sqlSelect.append(tableStudyData.getParticipantSequenceNumSQL("SD"));
        sqlSelect.unsafeAppend("\nWHERE  ParticipantId IS NOT NULL and SequenceNum IS NOT NULL AND ParticipantSequenceNum IS NOT NULL");
        sqlSelect.unsafeAppend("\nEXCEPT\n");
        sqlSelect.unsafeAppend("SELECT DISTINCT ParticipantId, SequenceNum, ParticipantSequenceNum FROM ").append(tableParticipantVisit.getFromSQL("PV")).unsafeAppend(" WHERE Container=CAST(? AS ").append(d.getGuidTypeAsSql()).unsafeAppend(")\n");
        sqlSelect.add(container);

        SQLFragment sqlInsertParticipantVisit = new SQLFragment();
        sqlInsertParticipantVisit.appendComment("<SequenceVisitManager.updateParticipantVisitTable>", schema.getSqlDialect());
        sqlInsertParticipantVisit.unsafeAppend("INSERT INTO ").append(tableParticipantVisit);
        sqlInsertParticipantVisit.unsafeAppend(" (Container, ParticipantId, SequenceNum, ParticipantSequenceNum)\n");
        sqlInsertParticipantVisit.unsafeAppend("SELECT CAST(? AS ").append(d.getGuidTypeAsSql()).unsafeAppend("), ParticipantId, SequenceNum, ParticipantSequenceNum\n");
        sqlInsertParticipantVisit.add(container);
        sqlInsertParticipantVisit.unsafeAppend("FROM (");
        sqlInsertParticipantVisit.append(sqlSelect);
        sqlInsertParticipantVisit.unsafeAppend(") _");
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
        sqlInsertParticipantVisit.unsafeAppend("INSERT INTO ").append(tableParticipantVisit);
        sqlInsertParticipantVisit.unsafeAppend(" (Container, ParticipantId, SequenceNum, ParticipantSequenceNum)\n");
        sqlInsertParticipantVisit.unsafeAppend("SELECT ? As Container, Ptid AS ParticipantId, VisitValue AS SequenceNum,\n");
        sqlInsertParticipantVisit.add(container);
        sqlInsertParticipantVisit.unsafeAppend("MIN(").append(StudyUtils.getParticipantSequenceNumExpr(schema, "Ptid", "VisitValue")).unsafeAppend(") AS ParticipantSequenceNum\n");
        sqlInsertParticipantVisit.unsafeAppend("FROM ").append(tableSpecimen, "Specimen").unsafeAppend("\n");
        sqlInsertParticipantVisit.unsafeAppend("WHERE Ptid IS NOT NULL AND VisitValue IS NOT NULL AND NOT EXISTS (");
        sqlInsertParticipantVisit.unsafeAppend("SELECT ParticipantId, SequenceNum FROM ").append(tableParticipantVisit, "PV").unsafeAppend("\n");
        sqlInsertParticipantVisit.unsafeAppend("WHERE Container = ? AND Specimen.Ptid = PV.ParticipantId AND Specimen.VisitValue = PV.SequenceNum)\n");
        sqlInsertParticipantVisit.add(container);
        sqlInsertParticipantVisit.unsafeAppend("GROUP BY Ptid, VisitValue");
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
            sqlUpdateVisitDates.unsafeAppend("UPDATE ").append(tableParticipantVisit).unsafeAppend("\n")
                .unsafeAppend("SET VisitDate = NULL, Day = NULL")
                .unsafeAppend(" WHERE Container = ?");
            sqlUpdateVisitDates.add(visitStudy.getContainer());
            executor.execute(sqlUpdateVisitDates);
        }
        else
        {
            TableInfo tableStudyDataFiltered = StudySchema.getInstance().getTableInfoStudyDataFiltered(getStudy(), defsWithVisitDates, user);
            SQLFragment sqlUpdateVisitDates = new SQLFragment();
            sqlUpdateVisitDates.unsafeAppend("UPDATE ").append(tableParticipantVisit).unsafeAppend(" PV");
            sqlUpdateVisitDates.unsafeAppend("\n").unsafeAppend("SET VisitDate = _VisitDate, Day = _VisitDay FROM\n")
                .unsafeAppend(" (\n")
                .unsafeAppend(" SELECT DISTINCT _VisitDate, _VisitDay, SequenceNum, ParticipantId, DatasetId\n")
                .unsafeAppend(" FROM ").append(tableStudyDataFiltered.getFromSQL("SD1")).unsafeAppend(") SD,  ")
                .append(tableVisit.getFromSQL("V"));

            sqlUpdateVisitDates.unsafeAppend("\n WHERE  PV.VisitRowId = V.RowId AND")    // 'join' V
                .unsafeAppend("   SD.ParticipantId = PV.ParticipantId AND SD.SequenceNum = PV.SequenceNum AND\n")   // 'join' SD
                .unsafeAppend("   SD.DatasetId = V.VisitDateDatasetId AND V.Container = ? AND PV.Container = ?\n");

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
        sqlUpdateVisitDates.unsafeAppend("UPDATE ").append(tableParticipantVisit).unsafeAppend(" PV");
        sqlUpdateVisitDates.unsafeAppend("\n").unsafeAppend("SET VisitDate = _VisitDate, Day = _VisitDay FROM\n")
            .unsafeAppend(" (\n")
            .unsafeAppend(" SELECT DISTINCT _VisitDate, _VisitDay, SequenceNum, ParticipantId, DatasetId\n")
            .unsafeAppend(" FROM ").append(tableStudyDataFiltered.getFromSQL("SD1")).unsafeAppend(") SD, ")
            .append(tableVisit.getFromSQL("V"));

        sqlUpdateVisitDates.unsafeAppend("\n WHERE PV.VisitRowId = V.RowId AND")    // 'join' V
            .unsafeAppend("   SD.ParticipantId = PV.ParticipantId AND SD.SequenceNum = PV.SequenceNum AND\n")   // 'join' SD
            .unsafeAppend("   ? = V.VisitDateDatasetId AND V.Container = ? AND PV.Container = ?\n");

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
            SQLFragment caseStmt = generateSequenceToVisit(visitStudy, "SequenceNum");
            seqnum2visit.unsafeAppend("WITH seqnum2visit AS ("
                        + "  SELECT SequenceNum, ")
                    .append(caseStmt)
                    .unsafeAppend(" AS RowId\n"
                        + "  FROM (SELECT DISTINCT SequenceNum FROM study.ParticipantVisit WHERE Container = ?");
            if (!updateAll)
                seqnum2visit.unsafeAppend(" AND VisitRowId=-1");
            seqnum2visit.unsafeAppend(") seqnumPV)\n");
            seqnum2visit.add(getStudy().getContainer());
        }
        else
        {
            seqnum2visit.unsafeAppend(
                "WITH seqnum2visit AS (" +
                "  SELECT SequenceNum, COALESCE(V.RowId,-1) AS RowId\n" +
                "  FROM (SELECT DISTINCT SequenceNum FROM study.ParticipantVisit WHERE Container = ?");
            if (!updateAll)
                seqnum2visit.unsafeAppend(" AND VisitRowId=-1");
            seqnum2visit.unsafeAppend(") seqnumPV, study.Visit V\n" +
                "  WHERE SequenceNum BETWEEN V.SequenceNumMin AND V.SequenceNumMax AND V.Container = ?)\n");
            seqnum2visit.add(getStudy().getContainer());
            seqnum2visit.add(visitStudy.getContainer());
        }

        // NOTE (seqnum2visit.RowId != VisitRowId OR VisitRowId IS NULL) is because postgres doesn't seem to optimize
        // updating a column to the existing value
        SQLFragment sqlUpdateVisitRowId = new SQLFragment();
        sqlUpdateVisitRowId.append(seqnum2visit);
        sqlUpdateVisitRowId.unsafeAppend(
            "UPDATE study.ParticipantVisit PV\n" +
            "        SET VisitRowId = RowId\n" +
            "        FROM seqnum2visit\n" +
            "        WHERE seqnum2visit.SequenceNum = PV.SequenceNum AND PV.Container = ? AND seqnum2visit.RowId <> VisitRowId");
        sqlUpdateVisitRowId.add(getStudy().getContainer());
        if (!updateAll)
            sqlUpdateVisitRowId.unsafeAppend(" AND VisitRowId=-1");

        long start = System.currentTimeMillis();
        (null==logger?LogManager.getLogger(SequenceVisitManager.class):logger).trace("START UPDATE", new Throwable());
        new SqlExecutor(schema).execute(sqlUpdateVisitRowId);
        (null == logger ? LogManager.getLogger(SequenceVisitManager.class) : logger).trace("DONE UPDATE {}", DateUtil.formatDuration(System.currentTimeMillis() - start));
    }

    /** Make sure there is a Visit for each row in StudyData otherwise rows will be orphaned */
    @Override
    protected @NotNull ValidationException updateVisitTable(User user, @Nullable Logger logger, boolean failForUndefinedVisits)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        ValidationException errors = new ValidationException();

        SQLFragment sql = new SQLFragment("SELECT DISTINCT SequenceNum\n" +
                "FROM " + tableParticipantVisit + "\n"+
                "WHERE Container = ? AND (VisitRowId IS NULL OR VisitRowId = -1)");
        sql.add(getStudy().getContainer().getId());

        final TreeSet<BigDecimal> sequenceNums = new TreeSet<>();

        info(logger, "Select distinct sequence numbers from participant visit table");
        new SqlSelector(schema, sql).forEach(rs -> sequenceNums.add(rs.getBigDecimal(1)));

        if (!sequenceNums.isEmpty())
        {
            info(logger, "Ensure visits for " + sequenceNums.size() + " distinct sequence numbers");
            errors = StudyManager.getInstance().ensureVisits(getStudy(), user, sequenceNums, null, failForUndefinedVisits);
            _updateVisitRowId(true, logger);
        }
        return errors;
    }


    public void purgeParticipantVisit(User user)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        SqlDialect d = schema.getSqlDialect();
        Container container = getStudy().getContainer();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableSpecimen = getSpecimenTable(getStudy(), user);
        StudyUnionTableInfo tableStudyData = (StudyUnionTableInfo) StudySchema.getInstance().getTableInfoStudyData(getStudy(), user);
        String tempTableName = null;

        try
        {
            SQLFragment sqlParticipantSequenceNumTemp = new SQLFragment("""
                        SELECT ParticipantSequenceNum
                        FROM\s""").append(tableStudyData.getParticipantSequenceNumSQL("SD")).unsafeAppend("\n");
            sqlParticipantSequenceNumTemp.unsafeAppend("""
                        WHERE ParticipantId IS NOT NULL and SequenceNum IS NOT NULL AND ParticipantSequenceNum IS NOT NULL
                        UNION
                        SELECT\s""").append(StudyUtils.getParticipantSequenceNumExpr(schema, "ParticipantId", "SequenceNum")).unsafeAppend(" AS ParticipantSequenceNum\n");
            sqlParticipantSequenceNumTemp.unsafeAppend(
                    "FROM (SELECT DISTINCT Ptid AS ParticipantId, VisitValue AS SequenceNum FROM ").append(tableSpecimen).unsafeAppend(" WHERE Ptid IS NOT NULL AND VisitValue IS NOT NULL) specimen_");

// CONSIDER: temp table if warranted (this is not a common code path)
//            var tt = "svm" + GUID.makeHash();
//            SQLFragment sqlTempTableSelectInto = new SQLFragment()
//                    .append("SELECT ParticipantId, SequenceNum, ParticipantSequenceNum INTO temp.").appendIdentifier(tempTableName).append("\n")
//                    .append("FROM (").append(sqlParticipantSequenceNumTemp).append("pseq_union_")
//                    .append(")");
//            new SqlExecutor(schema).execute(sqlTempTableSelectInto);
//            tempTableName = tt;

            SQLFragment sqlPurgeParticipantVisit = new SQLFragment()
                    .unsafeAppend("DELETE FROM ").append(tableParticipantVisit.getSQLName()).unsafeAppend("\n")
                    .unsafeAppend("WHERE Container=").appendValue(container).unsafeAppend(" AND ParticipantSequenceNum NOT IN (")
                    .append(sqlParticipantSequenceNumTemp).unsafeAppend(")");
            new SqlExecutor(schema).execute(sqlPurgeParticipantVisit);
        }
        finally
        {
            if (null != tempTableName)
            {
                new SqlExecutor(schema).execute(new SQLFragment("DROP TABLE temp.").appendIdentifier(tempTableName));
            }
        }
    }


    // Return sql for fetching all datasets and their visit sequence numbers, given a container
    @Override
    protected SQLFragment getDatasetSequenceNumsSQL(Study study)
    {
        SQLFragment sql = new SQLFragment();
        sql.unsafeAppend(
            "SELECT x.DatasetId AS DatasetId, CAST(x.SequenceNum AS FLOAT) AS SequenceNum\n" +
                "FROM (" +
                "     SELECT DISTINCT SequenceNum, DatasetId\n" +
                "     FROM ").append(StudySchema.getInstance().getTableInfoStudyData(getStudy(), null).getFromSQL("SD")).unsafeAppend(
            ") x\nORDER BY DatasetId, SequenceNum");
        return sql;
    }

    private SQLFragment generateSequenceToVisit(StudyImpl study, String sn)
    {
        Collection<VisitImpl> visits = study.getVisits(Visit.Order.SEQUENCE_NUM);
        if (visits.isEmpty())
            return new SQLFragment("-1");
        return generateSequenceToVisit(visits, sn, 1);
    }

    private SQLFragment generateSequenceToVisit(Collection<VisitImpl> visits, String sn, int indent)
    {
        SQLFragment sql = new SQLFragment();
        if (visits.size() <= 16)
        {
            boolean allEqual = visits.stream().allMatch(v -> v.getSequenceNumMin().compareTo(v.getSequenceNumMax()) == 0);
            if (allEqual)
            {
                _indent(sql,indent);
                sql.unsafeAppend("CASE ").appendIdentifier(sn);
                for (VisitImpl v : visits)
                {
                    _indent(sql,indent);
                    sql.unsafeAppend("WHEN ");
                    v.appendSqlSequenceNumMin(sql);
                    sql.unsafeAppend(" THEN ").appendValue(v.getId());
                }
                _indent(sql,indent); sql.unsafeAppend("ELSE -1");
                _indent(sql,indent); sql.unsafeAppend("END");
            }
            else
            {
                _indent(sql,indent); sql.unsafeAppend("CASE");
                for (VisitImpl v : visits)
                {
                    if (v.getSequenceNumMin().compareTo(v.getSequenceNumMax()) == 0)
                    {
                        _indent(sql, indent);
                        sql.unsafeAppend(" WHEN ").appendIdentifier(sn).unsafeAppend(" = ");
                        v.appendSqlSequenceNumMin(sql);
                        sql.unsafeAppend(" THEN ").appendValue(v.getId());
                    }
                    else
                    {
                        _indent(sql,indent);
                        sql.unsafeAppend(" WHEN ").appendIdentifier(sn).unsafeAppend(" BETWEEN ");
                        v.appendSqlSequenceNumMin(sql).unsafeAppend(" AND ");
                        v.appendSqlSequenceNumMax(sql).unsafeAppend(" THEN ").appendValue(v.getId());
                    }
                }
                _indent(sql,indent); sql.unsafeAppend("ELSE -1");
                _indent(sql,indent); sql.unsafeAppend("END");
            }
        }
        else
        {
            List<VisitImpl> visitList = new ArrayList<>(visits);
            _indent(sql,indent); sql.unsafeAppend("CASE");
            int partStart = 0, partEnd;
            for (int part=1 ; part<4 ; part++)
            {
                partEnd = visitList.size()*part / 4;
                VisitImpl v = visitList.get(partEnd);
                _indent(sql,indent); sql.unsafeAppend("WHEN ").appendIdentifier(sn).unsafeAppend(" < ");
                v.appendSqlSequenceNumMin(sql).unsafeAppend(" THEN");
                sql.append(generateSequenceToVisit(visitList.subList(partStart, partEnd), sn, indent+1));
                partStart = partEnd;
            }
            _indent(sql,indent); sql.unsafeAppend("ELSE");
            sql.append(generateSequenceToVisit(visitList.subList(partStart, visitList.size()), sn, indent+1));
            _indent(sql,indent); sql.unsafeAppend("END");
        }
        return sql;
    }

    private void _indent(SQLFragment sql, int indent)
    {
        sql.unsafeAppend("\n                                             ".substring(0, indent));
    }
}
