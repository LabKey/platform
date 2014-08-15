/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.study.CohortFilter;
import org.labkey.study.StudySchema;
import org.labkey.study.StudyUnionTableInfo;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DataSetTableImpl;
import org.labkey.study.query.DataspaceContainerFilter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
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

        SQLFragment studyDataContainerFilter = new SQLFragment(alias + ".container=?", _study.getContainer());
        if (_study.isDataspaceStudy())
            studyDataContainerFilter = new DataspaceContainerFilter(user).getSQLFragment(studyData.getSchema(), new SQLFragment(alias+".container"),getStudy().getContainer());

        SQLFragment sql = new SQLFragment();
        sql.appendComment("<SequenceVisitManager.getVisitSummarySql>", participantTable.getSqlDialect());

        SQLFragment sqlSequenceVisitMap = new SQLFragment();
        sqlSequenceVisitMap.appendComment("<MapSequenceNumToVisitRowId>", participantTable.getSqlDialect());
        sqlSequenceVisitMap.append("\nSELECT Container, SequenceNum, MIN(VisitRowId) AS VisitId");
         sqlSequenceVisitMap.append("\n\tFROM ").append(StudySchema.getInstance().getTableInfoParticipantVisit().getFromSQL("PV"));
        if (!_study.isDataspaceStudy())
        {
            sqlSequenceVisitMap.append("\n\tWHERE ").append("PV.container=?");
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
            sql.append("\nWHERE ").append(studyDataContainerFilter);
            if (null != qcStates)
                sql.append(" AND ").append(qcStates.getStateInClause(DataSetTableImpl.QCSTATE_ID_COLNAME));
            sql.append("\nGROUP BY ").append(keyCols);
            sql.append("\nORDER BY 1, 2");
        }
        else
        {
            switch (cohortFilter.getType())
            {
                case DATA_COLLECTION:
                    sql.append("SELECT ").append(keyCols).append(statsSql);
                    sql.append("\nFROM ").append(studyData.getFromSQL(alias));
                    sql.append(" INNER JOIN study.ParticipantVisit PV ON (")
                        .append(alias).append(".container = ? AND \n\tPV.ParticipantId = ").append(alias).append(".ParticipantId AND \n\tPV.SequenceNum = ")
                        .append(alias).append(".SequenceNum AND\n\t").append(alias).append(".container=PV.Container AND \n" + "\tPV.CohortID = ?)\n");
                    sql.add(cohortFilter.getCohortId());
                    sql.append(" LEFT OUTER JOIN (").append(sqlSequenceVisitMap).append(") AS SVM ON ")
                            .append(alias).append(".SequenceNum = SVM.SequenceNum AND ")
                            .append(alias).append(".container = SVM.container");
                    sql.append("\nWHERE ").append(studyDataContainerFilter);
                    if (qcStates != null)
                        sql.append(" AND ").append(qcStates.getStateInClause(DataSetTableImpl.QCSTATE_ID_COLNAME));
                    sql.append("\nGROUP BY ").append(keyCols);
                    sql.append("\nORDER BY 1, 2");
                    break;
                case PTID_CURRENT:
                case PTID_INITIAL:
                    sql.append("SELECT ").append(keyCols).append(statsSql);
                    sql.append("\nFROM ").append(studyData.getFromSQL(alias))
                        .append(" INNER JOIN ").append(participantTable.getFromSQL("P")).append(" ON (P.ParticipantId = ").append(alias).append(".ParticipantId")
                        .append(" AND ").append(alias).append(".container=P.Container AND P.").append(cohortFilter.getType() == CohortFilter.Type.PTID_CURRENT ? "CurrentCohortId" : "InitialCohortId").append(" = ?)\n");
                    sql.add(cohortFilter.getCohortId());
                    sql.append(" LEFT OUTER JOIN (").append(sqlSequenceVisitMap).append(") AS SVM ON ")
                            .append(alias).append(".SequenceNum = SVM.SequenceNum AND ")
                            .append(alias).append(".container = SVM.container");
                    sql.append("\nWHERE ").append(studyDataContainerFilter);
                    if (qcStates != null)
                        sql.append(" AND ").append(qcStates.getStateInClause(DataSetTableImpl.QCSTATE_ID_COLNAME));
                    sql.append("\nGROUP BY ").append(keyCols);
                    sql.append("\nORDER BY 1, 2");
                    break;
            }
        }
        sql.appendComment("</SequenceVisitManager.getVisitSummarySql>", participantTable.getSqlDialect());
        return sql;
    }


    /*
    // TODO: this is a peformance HACK
    // TODO: we should be incrementally updating ParticipantVisit, rather than trying to speed up resync!
    // TDOO: see 19867: Speed issues when inserting into study datasets
    */
    protected void updateParticipantVisitTableAfterInsert(@Nullable User user, DataSetDefinition ds, @Nullable Set<String> potentiallyAddedParticipants)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        Container container = getStudy().getContainer();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        StudyUnionTableInfo tableStudyData = (StudyUnionTableInfo)StudySchema.getInstance().getTableInfoStudyData(getStudy(), user);

        //
        // populate ParticipantVisit
        //
        SQLFragment sqlSelect = new SQLFragment();
        sqlSelect.append("SELECT DISTINCT ParticipantId, SequenceNum, ParticipantSequenceNum FROM ");
        sqlSelect.append(tableStudyData.getParticipantSequenceNumSQL("SD"));
        if (null != potentiallyAddedParticipants && !potentiallyAddedParticipants.isEmpty() && potentiallyAddedParticipants.size() < 450)
        {
            sqlSelect.append(" WHERE SD.ParticipantId ");
            schema.getSqlDialect().appendInClauseSql(sqlSelect, potentiallyAddedParticipants);
        }
        sqlSelect.append("\nEXCEPT\n");
        sqlSelect.append("SELECT DISTINCT ParticipantId, SequenceNum, ParticipantSequenceNum FROM ").append(tableParticipantVisit.getFromSQL("PV")).append(" WHERE Container=?");
        sqlSelect.add(container);


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

        _updateVisitRowId(false);
        _updateVisitDate(user, ds);
    }


    protected void updateParticipantVisitTable(@Nullable User user)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        SqlDialect d = schema.getSqlDialect();
        Container container = getStudy().getContainer();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableSpecimen = getSpecimenTable(getStudy());
        StudyUnionTableInfo tableStudyData = (StudyUnionTableInfo)StudySchema.getInstance().getTableInfoStudyData(getStudy(), user);

        //
        // populate ParticipantVisit
        //
        SQLFragment sqlSelect = new SQLFragment();
        sqlSelect.append("SELECT DISTINCT ParticipantId, SequenceNum, ParticipantSequenceNum FROM ");
        sqlSelect.append(tableStudyData.getParticipantSequenceNumSQL("SD"));
        sqlSelect.append("\nEXCEPT\n");
        sqlSelect.append("SELECT DISTINCT ParticipantId, SequenceNum, ParticipantSequenceNum FROM ").append(tableParticipantVisit.getFromSQL("PV")).append(" WHERE Container=CAST(? AS " + d.getGuidType() + ")\n");
        sqlSelect.add(container);

        SQLFragment sqlInsertParticipantVisit = new SQLFragment();
        sqlInsertParticipantVisit.appendComment("<SequenceVisitManager.updateParticipantVisitTableAfterInsert>", schema.getSqlDialect());
        sqlInsertParticipantVisit.append("INSERT INTO ").append(tableParticipantVisit.getSelectName());
        sqlInsertParticipantVisit.append(" (Container, ParticipantId, SequenceNum, ParticipantSequenceNum)\n");
        sqlInsertParticipantVisit.append("SELECT CAST(? AS " + d.getGuidType() +"), ParticipantId, SequenceNum, ParticipantSequenceNum\n");
        sqlInsertParticipantVisit.add(container);
        sqlInsertParticipantVisit.append("FROM (");
        sqlInsertParticipantVisit.append(sqlSelect);
        sqlInsertParticipantVisit.append(") _");
        sqlInsertParticipantVisit.appendComment("/<SequenceVisitManager.updateParticipantVisitTableAfterInsert>", schema.getSqlDialect());
        SqlExecutor executor = new SqlExecutor(schema);
        executor.execute(sqlInsertParticipantVisit);

        //
        // Delete ParticipantVisit where the participant does not exist anymore
        //   obviously the participants table needs to be udpated first
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
        _updateVisitRowId(true);

        //
        // upate VisitDate
        //

        _updateVisitDate(user);
    }


    private void _updateVisitDate(User user)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        Container container = getStudy().getContainer();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableVisit = StudySchema.getInstance().getTableInfoVisit();
        SqlExecutor executor = new SqlExecutor(schema);

        // update ParticipantVisit.VisitDate based on declared Visit.visitDateDatasetId
        ArrayList<DataSetDefinition> defsWithVisitDates = new ArrayList<>();
        for (DataSetDefinition def : _study.getDatasets())
            if (null != def.getVisitDateColumnName())
                defsWithVisitDates.add(def);

        if (defsWithVisitDates.isEmpty())
        {
            SQLFragment sqlUpdateVisitDates = new SQLFragment();
            sqlUpdateVisitDates.append("UPDATE " + tableParticipantVisit + "\n")
                .append("SET VisitDate = NULL, Day = NULL")
                .append(" WHERE Container=?");
            sqlUpdateVisitDates.add(container);
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
                    .append("   SD.DatasetId = V.VisitDateDatasetId AND V.Container=? AND PV.Container=?\n");

            sqlUpdateVisitDates.add(container);
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


    private void _updateVisitDate(User user, DataSetDefinition def)
    {
        if (null == def.getVisitDateColumnName())
            return;

        // are there any visits marking this as the visitdataset?
        boolean isVisitDateDataset = false;
        for (Visit v : getStudy().getVisits(Visit.Order.SEQUENCE_NUM))
        {
            if (v.getVisitDateDatasetId() == def.getDatasetId())
            {
                isVisitDateDataset = true;
                break;
            }
        }
        if (!isVisitDateDataset)
            return;

        DbSchema schema = StudySchema.getInstance().getSchema();
        Container container = getStudy().getContainer();
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
                .append(" FROM ").append(tableStudyDataFiltered.getFromSQL("SD1")).append(") SD,  ")
                .append(tableVisit.getFromSQL("V"));

        if (schema.getSqlDialect().isSqlServer())
            sqlUpdateVisitDates.append(", ").append(tableParticipantVisit.getFromSQL("PV"));     // Have to put the "PV" here for MSSQL

        sqlUpdateVisitDates.append("\n WHERE  PV.VisitRowId = V.RowId AND")    // 'join' V
                .append("   SD.ParticipantId = PV.ParticipantId AND SD.SequenceNum = PV.SequenceNum AND\n")   // 'join' SD
                .append("   ? = V.VisitDateDatasetId AND V.Container=? AND PV.Container=?\n");

        sqlUpdateVisitDates.add(def.getDatasetId());
        sqlUpdateVisitDates.add(container);
        sqlUpdateVisitDates.add(container);
        executor.execute(sqlUpdateVisitDates);
    }


    private void _updateVisitRowId(boolean updateAll)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();

        SQLFragment seqnum2visit = new SQLFragment();
        seqnum2visit.append(
                "  SELECT sequencenum, V.RowId\n" +
                "  FROM (SELECT DISTINCT SequenceNum from study.participantvisit where Container=?) seqnumPV, study.visit V\n" +
                "  WHERE SequenceNum BETWEEN V.SequenceNumMin AND V.SequenceNumMax AND V.Container=?\n");
        seqnum2visit.add(getStudy().getContainer());
        seqnum2visit.add(getStudy().getContainer());

        // NOTE (seqnum2visit.RowId != VisitRowId OR VisitRowId IS NULL) is because postgres doesn't seem to optimize
        // updating a column to the existing value
        SQLFragment sqlUpdateVisitRowId = new SQLFragment();
        if (schema.getSqlDialect().isPostgreSQL())
        {
            sqlUpdateVisitRowId.append(
                "UPDATE study.participantvisit PV\n" +
                "        SET VisitRowId = RowId\n" +
                "        FROM (\n");
            sqlUpdateVisitRowId.append(seqnum2visit);
            sqlUpdateVisitRowId.append(
                ") seqnum2visit\n" +
                "        WHERE seqnum2visit.SequenceNum = PV.SequenceNum AND (VisitRowId IS NULL OR seqnum2visit.RowId IS NULL OR seqnum2visit.RowId <> VisitRowId) AND PV.Container=?");
            sqlUpdateVisitRowId.add(getStudy().getContainer());
        }
        else
        {
            sqlUpdateVisitRowId.append(
                "UPDATE PV\n" +
                "        SET VisitRowId = RowId\n" +
                "        FROM study.participantvisit PV, (\n");
            sqlUpdateVisitRowId.append(seqnum2visit);
            sqlUpdateVisitRowId.append(
                ") seqnum2visit\n" +
                "        WHERE seqnum2visit.SequenceNum = PV.SequenceNum AND (VisitRowId IS NULL OR seqnum2visit.RowId IS NULL OR seqnum2visit.RowId <> VisitRowId) AND PV.Container=?");
            sqlUpdateVisitRowId.add(getStudy().getContainer());
        }
        if (!updateAll)
            sqlUpdateVisitRowId.append(" AND VisitRowId IS NULL");
        new SqlExecutor(schema).execute(sqlUpdateVisitRowId);
    }


    /** Make sure there is a Visit for each row in StudyData otherwise rows will be orphaned */
    protected void updateVisitTable(User user)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();

        SQLFragment sql = new SQLFragment("SELECT DISTINCT SequenceNum\n" +
                "FROM " + tableParticipantVisit + "\n"+
                "WHERE container = ? AND VisitRowId IS NULL");
        sql.add(getStudy().getContainer().getId());

        final TreeSet<Double> sequenceNums = new TreeSet<>();

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
            StudyManager.getInstance().ensureVisits(getStudy(), user, sequenceNums, null);
            _updateVisitRowId(true);
        }
    }

    // Return sql for fetching all datasets and their visit sequence numbers, given a container
    protected SQLFragment getDatasetSequenceNumsSQL(Study study)
    {
        SQLFragment sql = new SQLFragment();
        sql.append(
            "SELECT x.datasetid as datasetid, CAST(x.SequenceNum AS FLOAT) AS sequencenum\n" +
            "FROM (" +
            "     SELECT DISTINCT SequenceNum, DatasetId\n" +
            "     FROM ").append(StudySchema.getInstance().getTableInfoStudyData(getStudy(), null).getFromSQL("SD") + "").append(
            ") x\n" +
            "ORDER BY datasetid,sequencenum");
        return sql;
    }
}
