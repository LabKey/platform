/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.study.CohortFilter;
import org.labkey.study.StudySchema;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DataSetTable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
    protected SQLFragment getVisitSummarySql(CohortFilter cohortFilter, QCStateSet qcStates, String statsSql, String alias)
    {
        TableInfo studyData = StudySchema.getInstance().getTableInfoStudyData(getStudy(), null);
        TableInfo participantTable = StudySchema.getInstance().getTableInfoParticipant();

        // This query is too slow on postgres (8.1 anyway), do the join in code
//        TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
//        ResultSet rows = Table.executeQuery(schema,
//                "SELECT DatasetId, VisitRowId, CAST(COUNT(*) AS INT)\n" +
//                "FROM " + participantVisit + " PV JOIN " + studyData + " SD ON PV.ParticipantId=SD.ParticipantId AND PV.SequenceNum = SD.SequenceNum AND PV.Container = SD.Container\n" +
//                "WHERE SD.Container = ? AND PV.Container = ?\n" +
//                "GROUP BY DatasetId, VisitRowId",
//                new Object[] {study.getContainer().getId(), study.getContainer().getId()});

// make it a sqlfragment and use studyDate.getFromSql

        SQLFragment sql = new SQLFragment();

        if (cohortFilter == null)
        {
            sql.append("SELECT DatasetId, SequenceNum").append(statsSql).append("\nFROM ").append(studyData.getFromSQL(alias))
                .append("\n").append(qcStates != null ? "WHERE " + qcStates.getStateInClause(DataSetTable.QCSTATE_ID_COLNAME) + "\n" : "")
                .append("GROUP BY DatasetId, SequenceNum\nORDER BY 1, 2");
        }
        else
        {
            switch (cohortFilter.getType())
            {
                case DATA_COLLECTION:
                    sql.append("SELECT DatasetId, ").append(alias).append(".SequenceNum").append(statsSql).append("\nFROM ")
                        .append(studyData.getFromSQL(alias)).append("\n, study.ParticipantVisit PV\nWHERE ").append(alias)
                        .append(".Container = ? AND \n\tPV.ParticipantId = ").append(alias).append(".ParticipantId AND \n\tPV.SequenceNum = ")
                        .append(alias).append(".SequenceNum AND\n\tPV.Container = ? AND \n" + "\tPV.CohortID = ?\n")
                        .append(qcStates != null ? "\tAND " + qcStates.getStateInClause(DataSetTable.QCSTATE_ID_COLNAME) + "\n" : "")
                        .append("GROUP BY DatasetId, ").append(alias).append(".SequenceNum\n" + "ORDER BY 1, 2");
                    sql.add(getStudy().getContainer());
                    sql.add(cohortFilter.getCohortId());
                    break;
                case PTID_CURRENT:
                case PTID_INITIAL:
                    sql.append("SELECT DatasetId, SequenceNum").append(statsSql).append("\nFROM ").append(studyData.getFromSQL(alias))
                        .append(", ").append(participantTable.getFromSQL("P")).append("\n" + "WHERE P.ParticipantId = ").append(alias)
                        .append(".ParticipantId AND P.Container = ? AND P.").append(cohortFilter.getType() == CohortFilter.Type.PTID_CURRENT ? "CurrentCohortId" : "InitialCohortId")
                        .append(" = ?\n").append(qcStates != null ? "AND " + qcStates.getStateInClause(DataSetTable.QCSTATE_ID_COLNAME) + "\n" : "")
                        .append("GROUP BY DatasetId, SequenceNum\n" + "ORDER BY 1, 2");
                    sql.add(getStudy().getContainer());
                    sql.add(cohortFilter.getCohortId());
                    break;
            }
        }

        return sql;
    }


    protected void updateParticipantVisitTable(User user)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo tableVisit = StudySchema.getInstance().getTableInfoVisit();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
        TableInfo tableSpecimen = StudySchema.getInstance().getTableInfoSpecimen();
        TableInfo tableStudyData = StudySchema.getInstance().getTableInfoStudyData(getStudy(), user);

        try
        {
            //
            // populate ParticipantVisit
            //
            SQLFragment sqlInsertParticipantVisit = new SQLFragment();
            sqlInsertParticipantVisit.append("INSERT INTO " + tableParticipantVisit + " (Container, ParticipantId, SequenceNum, ParticipantSequenceKey)\n" +
                    "SELECT DISTINCT ?, ParticipantId, SequenceNum,\n(" +
                    getParticipantSequenceKeyExpr(schema, "ParticipantId", "SequenceNum") + ") AS ParticipantSequenceKey\n" +
                    "FROM ").append(tableStudyData.getFromSQL("SD")).append("\n" +
                    "WHERE NOT EXISTS (" +
                    "  SELECT ParticipantId, SequenceNum FROM " + tableParticipantVisit + " PV\n" +
                    "  WHERE Container = ? AND SD.ParticipantId=PV.ParticipantId AND SD.SequenceNum=PV.SequenceNum)");
            sqlInsertParticipantVisit.add(getStudy().getContainer());
            sqlInsertParticipantVisit.add(getStudy().getContainer());
            Table.execute(schema, sqlInsertParticipantVisit);

            //
            // Delete ParticipantVisit where the participant does not exist anymore
            //
            String sqlDeleteParticiapantVisit = "DELETE FROM " + tableParticipantVisit + " WHERE Container = ? AND ParticipantId NOT IN (SELECT ParticipantId FROM " + tableParticipant + " WHERE Container= ?)";
            Table.execute(schema, sqlDeleteParticiapantVisit, getStudy().getContainer(), getStudy().getContainer());

            // after assigning visit dates to all study data-generated visits, we insert any extra ptid/sequencenum/date combinations
            // that are found in the specimen archives.  We simply trust the specimen draw date in this case, rather than relying on the
            // visit table to tell us which date corresponds to which visit:
            sqlInsertParticipantVisit = new SQLFragment();
            sqlInsertParticipantVisit.append("INSERT INTO " + tableParticipantVisit + " (Container, ParticipantId, SequenceNum, ParticipantSequenceKey)\n" +
                    "SELECT DISTINCT Container, Ptid AS ParticipantId, VisitValue AS SequenceNum,\n(" +
                    getParticipantSequenceKeyExpr(schema, "Ptid", "VisitValue") + ") AS ParticipantSequenceKey\n" +
                    "FROM " + tableSpecimen + " Specimen\n" +
                    "WHERE Container = ? AND Ptid IS NOT NULL AND VisitValue IS NOT NULL AND NOT EXISTS (" +
                    "  SELECT ParticipantId, SequenceNum FROM " + tableParticipantVisit + " PV\n" +
                    "  WHERE Container = ? AND Specimen.Ptid=PV.ParticipantId AND Specimen.VisitValue=PV.SequenceNum)");
            sqlInsertParticipantVisit.add(getStudy().getContainer());
            sqlInsertParticipantVisit.add(getStudy().getContainer());
            Table.execute(schema, sqlInsertParticipantVisit);


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
                    " FROM ").append( tableStudyData.getFromSQL("SD")).append(",  " + tableVisit + " V\n" +
                    " WHERE  ParticipantVisit.VisitRowId = V.RowId AND" +    // 'join' V
                    "   SD.ParticipantId = ParticipantVisit.ParticipantId AND SD.SequenceNum = ParticipantVisit.SequenceNum AND\n" +    // 'join' SD
                    "   SD.DatasetId = V.VisitDateDatasetId AND V.Container=?\n" +
                    " )\n");
            if (schema.getSqlDialect().isSqlServer()) // for SQL Server 2000
                sqlUpdateVisitDates.append("FROM " + tableParticipantVisit + " ParticipantVisit\n");
            sqlUpdateVisitDates.append("WHERE Container=?");
            sqlUpdateVisitDates.add(getStudy().getContainer());
            sqlUpdateVisitDates.add(getStudy().getContainer());
            
            Table.execute(schema, sqlUpdateVisitDates);

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
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    private void _updateVisitRowId() throws SQLException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableVisit = StudySchema.getInstance().getTableInfoVisit();

        String sqlUpdateVisitRowId = "UPDATE " + tableParticipantVisit + "\n" +
                "SET VisitRowId = \n" +
                " (\n" +
                " SELECT V.RowId\n" +
                " FROM " + tableVisit + " V\n" +
                " WHERE ParticipantVisit.SequenceNum BETWEEN V.SequenceNumMin AND V.SequenceNumMax AND\n" +
                "   V.Container=?\n" +
                " )\n";
        if (schema.getSqlDialect().isSqlServer()) // for SQL Server 2000
            sqlUpdateVisitRowId += "FROM " + tableParticipantVisit + " ParticipantVisit\n";
        sqlUpdateVisitRowId += "WHERE Container=?";
        Table.execute(schema, sqlUpdateVisitRowId, getStudy().getContainer(), getStudy().getContainer());
    }


    /** Make sure there is a Visit for each row in StudyData otherwise rows will be orphaned */
    protected void updateVisitTable(User user)
    {
        try
        {
            String c = getStudy().getContainer().getId();

            DbSchema schema = StudySchema.getInstance().getSchema();
            TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();

            SQLFragment sql = new SQLFragment("SELECT DISTINCT SequenceNum\n" +
                    "FROM " + tableParticipantVisit + "\n"+
                    "WHERE container = ? AND VisitRowId IS NULL");
            sql.add(c);

            List<Double> sequenceNums = new ArrayList<Double>();
            Table.TableResultSet rs = Table.executeQuery(schema, sql);
            try
            {
                while (rs.next())
                {
                    sequenceNums.add(rs.getDouble(1));
                }
            }
            finally
            {
                rs.close();
            }

            for (double d : sequenceNums)
            {
                StudyManager.getInstance().ensureVisit(getStudy(), user, d, null, null);
            }
            if (sequenceNums.size() > 0)
                _updateVisitRowId();

        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
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
