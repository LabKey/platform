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

import org.jetbrains.annotations.Nullable;
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
import org.labkey.study.model.VisitImpl;
import org.labkey.study.query.DataSetTableImpl;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/** 
 * User: brittp
 * Created: Feb 29, 2008 11:23:37 AM
 */
public class RelativeDateVisitManager extends VisitManager
{
    public RelativeDateVisitManager(StudyImpl study)
    {
        super(study);
    }

    @Override
    public String getLabel()
    {
        return "Timepoint";
    }

    @Override
    public String getPluralLabel()
    {
        return "Timepoints";
    }
    

    @Override
    protected SQLFragment getVisitSummarySql(CohortFilter cohortFilter, QCStateSet qcStates, String statsSql, String alias, boolean showAll, boolean useVisitId)
    {
        TableInfo studyData = showAll ?
                StudySchema.getInstance().getTableInfoStudyData(getStudy(), null) :
                StudySchema.getInstance().getTableInfoStudyDataVisible(getStudy(), null);
        TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo participantTable = StudySchema.getInstance().getTableInfoParticipant();

        SQLFragment sql = new SQLFragment();
        sql.appendComment("<RelativeDateVisitManager.getVisitSummarySql>", participantTable.getSqlDialect());

        SQLFragment keyCols = new SQLFragment("DatasetId, ");
        if (useVisitId)
            keyCols.append("PV.VisitRowId");
        else
            keyCols.append("PV.Day");

        if (cohortFilter == null)
        {
            sql.append("SELECT ").append(keyCols).append(statsSql);
            sql.append("\nFROM ").append(studyData.getFromSQL(alias))
                .append(" JOIN ").append(participantVisit.getFromSQL("PV")).append(" ON ").append(alias)
                .append(".ParticipantId = PV.ParticipantId AND ").append(alias).append(".SequenceNum = PV.SequenceNum AND ? = PV.Container");
            sql.add(getStudy().getContainer());
            if (null != qcStates)
                sql.append("\nWHERE ").append(qcStates.getStateInClause(DataSetTableImpl.QCSTATE_ID_COLNAME));
            sql.append("\nGROUP BY ").append(keyCols);
            sql.append("\nORDER BY 1, 2");
        }
        else
        {
            switch (cohortFilter.getType())
            {
                case DATA_COLLECTION:
                    throw new UnsupportedOperationException("Unsupported cohort filter for date-based study");
                case PTID_CURRENT:
                case PTID_INITIAL:
                    sql.append("SELECT ").append(keyCols).append(statsSql);
                    sql.append("\nFROM ").append(studyData.getFromSQL(alias))
                        .append("\nJOIN ").append(participantVisit.getFromSQL("PV")).append(" ON (").append(alias)
                        .append(".ParticipantId = PV.ParticipantId AND ").append(alias).append(".SequenceNum = PV.SequenceNum AND ? = PV.Container)\n" + "JOIN ")
                        .append(participantTable.getFromSQL("P")).append(" ON (").append(alias).append(".ParticipantId = P.ParticipantId AND ? = P.Container)\n");
                    sql.add(getStudy().getContainer());
                    sql.add(getStudy().getContainer());
                    sql.append("\nWHERE P.")
                        .append(cohortFilter.getType() == CohortFilter.Type.PTID_CURRENT ? "CurrentCohortId" : "InitialCohortId")
                        .append(" = ?\n").append(qcStates != null ? "AND " + qcStates.getStateInClause(DataSetTableImpl.QCSTATE_ID_COLNAME) + "\n" : "");
                    sql.add(cohortFilter.getCohortId());
                    sql.append("\nGROUP BY ").append(keyCols);
                    sql.append("\nORDER BY 1, 2");
                    break;
            }
        }

        sql.appendComment("</RelativeDateVisitManager.getVisitSummarySql>", participantTable.getSqlDialect());
        return sql;
    }


    protected void updateParticipantVisitTable(@Nullable User user)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableStudyData = StudySchema.getInstance().getTableInfoStudyData(getStudy(), user);
        TableInfo tableSpecimen = getSpecimenTable(getStudy());
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();

        try
        {
            //
            // populate ParticipantVisit
            //
            SQLFragment sqlInsertParticipantVisit = new SQLFragment();
            sqlInsertParticipantVisit.append("INSERT INTO ").append(tableParticipantVisit.getSelectName());
            sqlInsertParticipantVisit.append(" (Container, ParticipantId, SequenceNum, VisitDate, ParticipantSequenceNum)\n");
            sqlInsertParticipantVisit.append("SELECT ? as Container, ParticipantId, SequenceNum, MIN(_VisitDate), \n");
            sqlInsertParticipantVisit.add(getStudy().getContainer());
            sqlInsertParticipantVisit.append("MIN(").append(getParticipantSequenceNumExpr(schema, "ParticipantId", "SequenceNum")).append(") AS ParticipantSequenceNum\n");
            sqlInsertParticipantVisit.append("FROM ").append(tableStudyData.getFromSQL("SD")).append("\n");
            sqlInsertParticipantVisit.append("WHERE NOT EXISTS (SELECT ParticipantId, SequenceNum FROM ");
            sqlInsertParticipantVisit.append(tableParticipantVisit, "PV").append("\n");
            sqlInsertParticipantVisit.append("WHERE Container = ? AND SD.ParticipantId = PV.ParticipantId AND SD.SequenceNum = PV.SequenceNum)\n");
            sqlInsertParticipantVisit.add(getStudy().getContainer());
            sqlInsertParticipantVisit.append("GROUP BY ParticipantId, SequenceNum");
            Table.execute(schema, sqlInsertParticipantVisit);

            SQLFragment sqlInsertParticipantVisit2 = new SQLFragment();
            sqlInsertParticipantVisit2.append("INSERT INTO ").append(tableParticipantVisit.getSelectName());
            sqlInsertParticipantVisit2.append(" (Container, ParticipantId, SequenceNum, VisitDate, ParticipantSequenceNum)\n");
            sqlInsertParticipantVisit2.append("SELECT Container, Ptid AS ParticipantId, VisitValue AS SequenceNum, ");
            sqlInsertParticipantVisit2.append("MIN(").append(schema.getSqlDialect().getDateTimeToDateCast("DrawTimestamp")).append(") AS VisitDate, \n");
            sqlInsertParticipantVisit2.append("MIN(").append(getParticipantSequenceNumExpr(schema, "Ptid", "VisitValue")).append(") AS ParticipantSequenceNum\n");
            sqlInsertParticipantVisit2.append("FROM ").append(tableSpecimen, "Specimen").append("\n");
            sqlInsertParticipantVisit2.append("WHERE Container = ?  AND Ptid IS NOT NULL AND VisitValue IS NOT NULL AND NOT EXISTS (\n");
            sqlInsertParticipantVisit2.add(getStudy().getContainer());
            sqlInsertParticipantVisit2.append("SELECT ParticipantId, SequenceNum FROM ").append(tableParticipantVisit.getFromSQL("PV")).append("\n");
            sqlInsertParticipantVisit2.append("WHERE Container = ? AND Specimen.Ptid=PV.ParticipantId AND Specimen.VisitValue=PV.SequenceNum)\n");
            sqlInsertParticipantVisit2.add(getStudy().getContainer());
            sqlInsertParticipantVisit2.append("GROUP BY Container, Ptid, VisitValue");
            Table.execute(schema, sqlInsertParticipantVisit2);
            //
            // Delete ParticipantVisit where the participant does not exist anymore
            //
            String sqlDeleteParticiapantVisit = "DELETE FROM " + tableParticipantVisit + " WHERE Container = ? AND ParticipantId NOT IN (SELECT ParticipantId FROM " + tableParticipant + " WHERE Container= ?)";
            Table.execute(schema, sqlDeleteParticiapantVisit, getStudy().getContainer(), getStudy().getContainer());

            String sqlStartDate = "(SELECT StartDate FROM " + tableParticipant + " WHERE " + tableParticipant + ".ParticipantId=" + tableParticipantVisit + ".ParticipantId AND " + tableParticipant + ".Container=" + tableParticipantVisit + ".Container)";
            String sqlUpdateDays = "UPDATE " + tableParticipantVisit + " SET Day = CASE WHEN SequenceNum=? THEN 0 ELSE " + schema.getSqlDialect().getDateDiff(Calendar.DATE, "VisitDate", sqlStartDate) + " END WHERE Container=? AND NOT VisitDate IS NULL";
            Table.execute(schema, sqlUpdateDays, VisitImpl.DEMOGRAPHICS_VISIT, getStudy().getContainer());
//            for (DataSetDefinition dataSet : _study.getDataSets())
//            {
//                TableInfo tempTableInfo = dataSet.getMaterializedTempTableInfo(user, false);
//                if (tempTableInfo != null)
//                {
//                    Table.execute(schema, new SQLFragment("UPDATE " + tempTableInfo + " SET Day = (SELECT Day FROM " + tableParticipantVisit + " pv WHERE pv.ParticipantSequenceNum = " + tempTableInfo + ".ParticipantSequenceNum" +
//                            " AND pv.Container = ?)",  _study.getContainer()));
//                }
//            }

            StringBuilder participantSequenceNum = new StringBuilder("(");
            participantSequenceNum.append(getParticipantSequenceNumExpr(schema, "ParticipantId", "SequenceNum"));
            participantSequenceNum.append(")");

            String sqlUpdateParticipantSeqNum = "UPDATE " + tableParticipantVisit + " SET ParticipantSequenceNum = " +
                    participantSequenceNum + " WHERE Container = ?  AND ParticipantSequenceNum IS NULL";
            Table.execute(schema, sqlUpdateParticipantSeqNum, getStudy().getContainer());

            _updateVisitRowId();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    private void _updateVisitRowId()
            throws SQLException
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableVisit = StudySchema.getInstance().getTableInfoVisit();

        String sqlUpdateVisitRowId = "UPDATE " + tableParticipantVisit + "\n" +
                "SET VisitRowId = \n" +
                " (\n" +
                " SELECT V.RowId\n" +
                " FROM " + tableVisit + " V\n" +
                " WHERE ParticipantVisit.Day BETWEEN V.SequenceNumMin AND V.SequenceNumMax AND\n" +
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
            DbSchema schema = StudySchema.getInstance().getSchema();
            TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();

            SQLFragment sql = new SQLFragment("SELECT DISTINCT Day " +
                "FROM " + tableParticipantVisit + "\n" +
                "WHERE container = ? AND VisitRowId IS NULL");
            sql.add(getStudy().getContainer());

            List<Integer> days = new ArrayList<Integer>();
            Table.TableResultSet rs = Table.executeQuery(schema, sql);

            try
            {
                while (rs.next())
                {
                    days.add(rs.getInt(1));
                }
            }
            finally
            {
                rs.close();
            }

            for (int day : days)
            {
                StudyManager.getInstance().ensureVisit(getStudy(), user, day, null, true);
            }

            if (days.size() > 0)
                _updateVisitRowId();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public void recomputeDates(Date oldStartDate, User user) throws SQLException
    {
        if (null != oldStartDate)
        {
            String c = getStudy().getContainer().getId();
            DbSchema schema = StudySchema.getInstance().getSchema();
            TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
            TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
            TableInfo tableVisit = StudySchema.getInstance().getTableInfoVisit();

            int rowsUpdated = Table.execute(schema, "UPDATE " + tableParticipant + " SET StartDate=NULL WHERE StartDate= ? AND Container = ?", oldStartDate, c);

            if (rowsUpdated > 0)
            {
                //Now just start over computing *everything* as if we have brand new data...
                Table.execute(schema, "DELETE FROM " + tableParticipantVisit + " WHERE Container=?", c);

                Table.execute(schema, "DELETE FROM " + tableVisit + " WHERE Container=?", c);

                //Now recompute everything
                updateParticipantVisits(user, getStudy().getDataSets());
            }
        }
    }

    // Return sql for fetching all datasets and their visit sequence numbers, given a container
    protected SQLFragment getDatasetSequenceNumsSQL(Study study)
    {
        SQLFragment sql = new SQLFragment();
        sql.append(
            "SELECT sd.datasetid, v.sequencenummin " +
            // There's only one implementation of Study, so it's safe enough to cast it
            "FROM ").append(StudySchema.getInstance().getTableInfoStudyData((StudyImpl)study, null).getFromSQL("SD")).append("\n");
        sql.append(
            "JOIN study.ParticipantVisit pv ON  \n" +
            "     sd.SequenceNum = pv.SequenceNum AND \n" +
            "     sd.ParticipantId = pv.ParticipantId AND \n" +
            "     ? = pv.Container \n" +
            "JOIN study.Visit v ON \n" +
            "     pv.VisitRowId = v.RowId AND \n" +
            "     pv.Container = v.Container \n" +
            "GROUP BY sd.datasetid, v.sequencenummin");
        sql.add(study.getContainer());
        return sql;
    }
}
