/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.study.CohortFilter;
import org.labkey.study.StudySchema;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DataSetTableImpl;

import java.sql.ResultSet;
import java.sql.SQLException;
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
    protected SQLFragment getVisitSummarySql(CohortFilter cohortFilter, QCStateSet qcStates, String statsSql, String alias, boolean showAll, boolean useVisitId)
    {
        TableInfo studyData = showAll ?
                StudySchema.getInstance().getTableInfoStudyData(getStudy(), null) :
                StudySchema.getInstance().getTableInfoStudyDataVisible(getStudy(), null);
        TableInfo participantTable = StudySchema.getInstance().getTableInfoParticipant();

        SQLFragment sql = new SQLFragment();
        sql.appendComment("<SequenceVisitManager.getVisitSummarySql>", participantTable.getSqlDialect());

        SQLFragment sqlSequenceVisitMap = new SQLFragment();
        sqlSequenceVisitMap.appendComment("<MapSequenceNumToVisitRowId>", participantTable.getSqlDialect());
        sqlSequenceVisitMap.append("\nSELECT SequenceNum, MIN(VisitRowId) AS VisitId");
        sqlSequenceVisitMap.append("\n\tFROM ").append(StudySchema.getInstance().getTableInfoParticipantVisit().getFromSQL("PV"));
        sqlSequenceVisitMap.append("\n\tWHERE container=?");
        sqlSequenceVisitMap.append("\n\tGROUP BY SequenceNum");
        sqlSequenceVisitMap.add(getStudy().getContainer().getId());
        sqlSequenceVisitMap.appendComment("</MapSequenceNumToVisitRowId>", participantTable.getSqlDialect());

        SQLFragment keyCols = new SQLFragment("DatasetId, ");
        if (useVisitId)
            keyCols.append("SVM.VisitId");
        else
            keyCols.append(alias).append(".SequenceNum");

        if (cohortFilter == null)
        {
            sql.append("SELECT ").append(keyCols);
            sql.append(statsSql);
            sql.append("\nFROM ").append(studyData.getFromSQL(alias));
            if (useVisitId)
                sql.append(" LEFT OUTER JOIN (").append(sqlSequenceVisitMap).append(") AS SVM ON ").append(alias).append(".SequenceNum = SVM.SequenceNum");
            sql.append(qcStates != null ? "\nWHERE " + qcStates.getStateInClause(DataSetTableImpl.QCSTATE_ID_COLNAME) : "");
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
                        .append(alias).append(".Container = ? AND \n\tPV.ParticipantId = ").append(alias).append(".ParticipantId AND \n\tPV.SequenceNum = ")
                        .append(alias).append(".SequenceNum AND\n\tPV.Container = ? AND \n" + "\tPV.CohortID = ?)\n");
                    sql.add(getStudy().getContainer());
                    sql.add(cohortFilter.getCohortId());
                    if (useVisitId)
                        sql.append(" LEFT OUTER JOIN (").append(sqlSequenceVisitMap).append(") AS SVM ON ").append(alias).append(".SequenceNum = SVM.SequenceNum");
                    if (qcStates != null)
                        sql.append("\nWHERE ").append(qcStates.getStateInClause(DataSetTableImpl.QCSTATE_ID_COLNAME));
                    sql.append("\nGROUP BY ").append(keyCols);
                    sql.append("\nORDER BY 1, 2");
                    break;
                case PTID_CURRENT:
                case PTID_INITIAL:
                    sql.append("SELECT ").append(keyCols).append(statsSql);
                    sql.append("\nFROM ").append(studyData.getFromSQL(alias))
                        .append(" INNER JOIN ").append(participantTable.getFromSQL("P")).append(" ON (P.ParticipantId = ").append(alias).append(".ParticipantId")
                        .append(" AND P.Container = ? AND P.").append(cohortFilter.getType() == CohortFilter.Type.PTID_CURRENT ? "CurrentCohortId" : "InitialCohortId").append(" = ?)\n");
                    sql.add(getStudy().getContainer());
                    sql.add(cohortFilter.getCohortId());
                    if (useVisitId)
                        sql.append(" LEFT OUTER JOIN (").append(sqlSequenceVisitMap).append(") AS SVM ON ").append(alias).append(".SequenceNum = SVM.SequenceNum");
                    if (qcStates != null)
                        sql.append("\nWHERE ").append(qcStates.getStateInClause(DataSetTableImpl.QCSTATE_ID_COLNAME));
                    sql.append("\nGROUP BY ").append(keyCols);
                    sql.append("\nORDER BY 1, 2");
                    break;
            }
        }
        sql.appendComment("</SequenceVisitManager.getVisitSummarySql>", participantTable.getSqlDialect());
        return sql;
    }


    protected void updateParticipantVisitTable(@Nullable User user)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo tableVisit = StudySchema.getInstance().getTableInfoVisit();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
        TableInfo tableSpecimen = getSpecimenTable(getStudy());
        TableInfo tableStudyData = StudySchema.getInstance().getTableInfoStudyData(getStudy(), user);

        //
        // populate ParticipantVisit
        //
        SQLFragment sqlInsertParticipantVisit = new SQLFragment();
        sqlInsertParticipantVisit.append("INSERT INTO ").append(tableParticipantVisit.getSelectName());
        sqlInsertParticipantVisit.append(" (Container, ParticipantId, SequenceNum, ParticipantSequenceNum)\n");
        sqlInsertParticipantVisit.append("SELECT ?, ParticipantId, SequenceNum,\n");
        sqlInsertParticipantVisit.add(getStudy().getContainer());
        sqlInsertParticipantVisit.append("MIN(").append(getParticipantSequenceNumExpr(schema, "ParticipantId", "SequenceNum")).append(") AS ParticipantSequenceNum\n");
        sqlInsertParticipantVisit.append("FROM ").append(tableStudyData, "SD").append("\n");
        sqlInsertParticipantVisit.append("WHERE NOT EXISTS (SELECT ParticipantId, SequenceNum FROM ");
        sqlInsertParticipantVisit.append(tableParticipantVisit, "PV").append("\n");
        sqlInsertParticipantVisit.append("WHERE Container = ? AND SD.ParticipantId = PV.ParticipantId AND SD.SequenceNum = PV.SequenceNum)\n");
        sqlInsertParticipantVisit.add(getStudy().getContainer());
        sqlInsertParticipantVisit.append("GROUP BY ParticipantId, SequenceNum");
        SqlExecutor executor = new SqlExecutor(schema);
        executor.execute(sqlInsertParticipantVisit);

        //
        // Delete ParticipantVisit where the participant does not exist anymore
        //
        String sqlDeleteParticiapantVisit = "DELETE FROM " + tableParticipantVisit + " WHERE Container = ? AND ParticipantId NOT IN (SELECT ParticipantId FROM " + tableParticipant + " WHERE Container= ?)";
        executor.execute(sqlDeleteParticiapantVisit, getStudy().getContainer(), getStudy().getContainer());

        // after assigning visit dates to all study data-generated visits, we insert any extra ptid/sequencenum/date combinations
        // that are found in the specimen archives.  We simply trust the specimen draw date in this case, rather than relying on the
        // visit table to tell us which date corresponds to which visit:
        sqlInsertParticipantVisit = new SQLFragment();
        sqlInsertParticipantVisit.append("INSERT INTO ").append(tableParticipantVisit.getSelectName());
        sqlInsertParticipantVisit.append(" (Container, ParticipantId, SequenceNum, ParticipantSequenceNum)\n");
        sqlInsertParticipantVisit.append("SELECT Container, Ptid AS ParticipantId, VisitValue AS SequenceNum,\n");
        sqlInsertParticipantVisit.append("MIN(").append(getParticipantSequenceNumExpr(schema, "Ptid", "VisitValue")).append(") AS ParticipantSequenceNum\n");
        sqlInsertParticipantVisit.append("FROM ").append(tableSpecimen, "Specimen").append("\n");
        sqlInsertParticipantVisit.append("WHERE Container = ? AND Ptid IS NOT NULL AND VisitValue IS NOT NULL AND NOT EXISTS (");
        sqlInsertParticipantVisit.add(getStudy().getContainer());
        sqlInsertParticipantVisit.append("SELECT ParticipantId, SequenceNum FROM ").append(tableParticipantVisit, "PV").append("\n");
        sqlInsertParticipantVisit.append("WHERE Container = ? AND Specimen.Ptid = PV.ParticipantId AND Specimen.VisitValue = PV.SequenceNum)\n");
        sqlInsertParticipantVisit.add(getStudy().getContainer());
        sqlInsertParticipantVisit.append("GROUP BY Container, Ptid, VisitValue");
        executor.execute(sqlInsertParticipantVisit);

        //
        // fill in VisitRowId (need this to do the VisitDate computation)
        //
        _updateVisitRowId();

        //
        // upate VisitDate
        //

        // update ParticipantVisit.VisitDate based on declared Visit.visitDateDatasetId
        SQLFragment sqlUpdateVisitDates = new SQLFragment();
        sqlUpdateVisitDates.append("UPDATE " + tableParticipantVisit + "\n" +
                "SET VisitDate = \n" +
                " (\n" +
                " SELECT DISTINCT(SD._VisitDate)\n" +
                " FROM ").append(tableStudyData.getFromSQL("SD")).append(",  " + tableVisit + " V\n" +
                " WHERE  ParticipantVisit.VisitRowId = V.RowId AND" +    // 'join' V
                "   SD.ParticipantId = ParticipantVisit.ParticipantId AND SD.SequenceNum = ParticipantVisit.SequenceNum AND\n" +    // 'join' SD
                "   SD.DatasetId = V.VisitDateDatasetId AND V.Container=?\n" +
                " )\n");
        if (schema.getSqlDialect().isSqlServer()) // for SQL Server 2000
            sqlUpdateVisitDates.append("FROM " + tableParticipantVisit + " ParticipantVisit\n");
        sqlUpdateVisitDates.append("WHERE Container=?");
        sqlUpdateVisitDates.add(getStudy().getContainer());
        sqlUpdateVisitDates.add(getStudy().getContainer());

        executor.execute(sqlUpdateVisitDates);

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
        Table.execute(schema, sqlCopyVisitDates,
                new Object[]{study.getContainer(), study.getContainer()});
        */
    }


    private void _updateVisitRowId()
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableVisit = StudySchema.getInstance().getTableInfoVisit();

        String sqlUpdateVisitRowId = "UPDATE " + tableParticipantVisit + "\n" +
                "SET VisitRowId = \n" +
                " (\n" +
                " SELECT MIN(V.RowId)\n" +
                " FROM " + tableVisit + " V\n" +
                " WHERE ParticipantVisit.SequenceNum BETWEEN V.SequenceNumMin AND V.SequenceNumMax AND\n" +
                "   V.Container=?\n" +
                " )\n";
        if (schema.getSqlDialect().isSqlServer()) // for SQL Server 2000
            sqlUpdateVisitRowId += "FROM " + tableParticipantVisit + " ParticipantVisit\n";
        sqlUpdateVisitRowId += "WHERE Container=?";
        new SqlExecutor(schema).execute(sqlUpdateVisitRowId, getStudy().getContainer(), getStudy().getContainer());
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
            _updateVisitRowId();
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
