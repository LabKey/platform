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

import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector.ForEachBlock;
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
import org.labkey.study.model.VisitImpl;
import org.labkey.study.query.DataSetTableImpl;
import org.labkey.study.query.DataspaceContainerFilter;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;

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
    protected SQLFragment getVisitSummarySql(User user, CohortFilter cohortFilter, QCStateSet qcStates, String statsSql, String alias, boolean showAll)
    {
        TableInfo studyData = showAll ?
                StudySchema.getInstance().getTableInfoStudyData(getStudy(), user) :
                StudySchema.getInstance().getTableInfoStudyDataVisible(getStudy(), user);
        TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo participantTable = StudySchema.getInstance().getTableInfoParticipant();

        SQLFragment studyDataContainerFilter = new SQLFragment(alias + ".container=?", _study.getContainer());
        if (_study.isDataspaceStudy())
            studyDataContainerFilter = new DataspaceContainerFilter(user).getSQLFragment(studyData.getSchema(), new SQLFragment(alias+".container"),getStudy().getContainer());

        SQLFragment sql = new SQLFragment();
        sql.appendComment("<RelativeDateVisitManager.getVisitSummarySql>", participantTable.getSqlDialect());

        SQLFragment keyCols = new SQLFragment("DatasetId, ");
        keyCols.append("PV.VisitRowId");

        if (cohortFilter == null)
        {
            sql.append("SELECT ").append(keyCols).append(statsSql);
            sql.append("\nFROM ").append(studyData.getFromSQL(alias))
                .append(" JOIN ").append(participantVisit.getFromSQL("PV")).append(" ON ").append(alias)
                .append(".ParticipantId = PV.ParticipantId AND ").append(alias).append(".SequenceNum = PV.SequenceNum AND ").append(alias).append(".container = PV.Container");
            sql.append("\nWHERE ");
            sql.append(studyDataContainerFilter);
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
                    throw new UnsupportedOperationException("Unsupported cohort filter for date-based study");
                case PTID_CURRENT:
                case PTID_INITIAL:
                    sql.append("SELECT ").append(keyCols).append(statsSql);
                    sql.append("\nFROM ").append(studyData.getFromSQL(alias))
                        .append("\nJOIN ").append(participantVisit.getFromSQL("PV")).append(" ON (").append(alias)
                        .append(".ParticipantId = PV.ParticipantId AND ").append(alias).append(".SequenceNum = PV.SequenceNum AND ").append(alias).append(".container = PV.Container)\n" + "JOIN ")
                        .append(participantTable.getFromSQL("P")).append(" ON (").append(alias).append(".ParticipantId = P.ParticipantId AND ").append(alias).append(".container = P.Container)\n");
                    sql.append("\nWHERE ")
                        .append(studyDataContainerFilter)
                        .append(" AND P.").append(cohortFilter.getType() == CohortFilter.Type.PTID_CURRENT ? "CurrentCohortId" : "InitialCohortId")
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


    /* call updateParticipants() first */
    protected void updateParticipantVisitTable(@Nullable User user)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        Container container = getStudy().getContainer();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableStudyData = StudySchema.getInstance().getTableInfoStudyData(getStudy(), user);
        TableInfo tableSpecimen = getSpecimenTable(getStudy());
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
        String tableParticipantVisitSelectName = tableParticipantVisit.getSelectName();
        String tableParticipantSelectName = tableParticipant.getSelectName();

        //
        // populate ParticipantVisit
        //
        SQLFragment sqlInsertParticipantVisit = new SQLFragment();
        sqlInsertParticipantVisit.append("INSERT INTO ").append(tableParticipantVisit.getSelectName());
        sqlInsertParticipantVisit.append(" (Container, ParticipantId, SequenceNum, VisitDate, ParticipantSequenceNum)\n");
        sqlInsertParticipantVisit.append("SELECT ? as Container, ParticipantId, SequenceNum, MIN(_VisitDate), \n");
        sqlInsertParticipantVisit.add(container);
        sqlInsertParticipantVisit.append("MIN(").append(getParticipantSequenceNumExpr(schema, "ParticipantId", "SequenceNum")).append(") AS ParticipantSequenceNum\n");
        sqlInsertParticipantVisit.append("FROM ").append(tableStudyData.getFromSQL("SD")).append("\n");
        sqlInsertParticipantVisit.append("WHERE NOT EXISTS (SELECT ParticipantId, SequenceNum FROM ");
        sqlInsertParticipantVisit.append(tableParticipantVisit, "PV").append("\n");
        sqlInsertParticipantVisit.append("WHERE Container = ? AND SD.ParticipantId = PV.ParticipantId AND SD.SequenceNum = PV.SequenceNum)\n");
        sqlInsertParticipantVisit.add(container);
        sqlInsertParticipantVisit.append("GROUP BY ParticipantId, SequenceNum");
        new SqlExecutor(schema).execute(sqlInsertParticipantVisit);

        SQLFragment sqlInsertParticipantVisit2 = new SQLFragment();
        sqlInsertParticipantVisit2.append("INSERT INTO ").append(tableParticipantVisitSelectName);
        sqlInsertParticipantVisit2.append(" (Container, ParticipantId, SequenceNum, VisitDate, ParticipantSequenceNum)\n");
        sqlInsertParticipantVisit2.append("SELECT ? As Container, Ptid AS ParticipantId, VisitValue AS SequenceNum, ");
        sqlInsertParticipantVisit2.add(container);
        sqlInsertParticipantVisit2.append("MIN(").append(schema.getSqlDialect().getDateTimeToDateCast("DrawTimestamp")).append(") AS VisitDate, \n");
        sqlInsertParticipantVisit2.append("MIN(").append(getParticipantSequenceNumExpr(schema, "Ptid", "VisitValue")).append(") AS ParticipantSequenceNum\n");
        sqlInsertParticipantVisit2.append("FROM ").append(tableSpecimen, "Specimen").append("\n");
        sqlInsertParticipantVisit2.append("WHERE Ptid IS NOT NULL AND VisitValue IS NOT NULL AND NOT EXISTS (\n");
        sqlInsertParticipantVisit2.append("SELECT ParticipantId, SequenceNum FROM ").append(tableParticipantVisit.getFromSQL("PV")).append("\n");
        sqlInsertParticipantVisit2.append("WHERE Container = ? AND Specimen.Ptid=PV.ParticipantId AND Specimen.VisitValue=PV.SequenceNum)\n");
        sqlInsertParticipantVisit2.add(container);
        sqlInsertParticipantVisit2.append("GROUP BY Ptid, VisitValue");
        new SqlExecutor(schema).execute(sqlInsertParticipantVisit2);

        //
        // Delete ParticipantVisit where the participant does not exist anymore
        //   obviously the participants table needs to be updated first
        //
        purgeParticipantsFromParticipantsVisitTable(container);

        SQLFragment sqlStartDate = new SQLFragment("(SELECT StartDate FROM ");
        sqlStartDate.append(tableParticipantSelectName)
                .append(" WHERE ").append(tableParticipant.getColumn("ParticipantId").getValueSql(tableParticipantSelectName))
                .append("=").append(tableParticipantVisit.getColumn("ParticipantId").getValueSql(tableParticipantVisitSelectName))
                .append(" AND ").append(tableParticipant.getColumn("Container").getValueSql(tableParticipantSelectName))
                .append("=").append(tableParticipantVisit.getColumn("Container").getValueSql(tableParticipantVisitSelectName)).append(")");
        SQLFragment sqlUpdateDays = new SQLFragment("UPDATE ");
        sqlUpdateDays.append(tableParticipantVisitSelectName).append(" SET Day = CASE WHEN SequenceNum=? THEN 0 ELSE ")
                .append(schema.getSqlDialect().getDateDiff(Calendar.DATE, "VisitDate", sqlStartDate.toString()))
                .append(" END WHERE Container=? AND NOT VisitDate IS NULL");
        sqlUpdateDays.add(VisitImpl.DEMOGRAPHICS_VISIT);
        sqlUpdateDays.add(container);
        new SqlExecutor(schema).execute(sqlUpdateDays);
//            for (DataSetDefinition dataSet : _study.getDataSets())
//            {
//                TableInfo tempTableInfo = dataSet.getMaterializedTempTableInfo(user, false);
//                if (tempTableInfo != null)
//                {
//                    executor.execute(new SQLFragment("UPDATE " + tempTableInfo + " SET Day = (SELECT Day FROM " + tableParticipantVisit + " pv WHERE pv.ParticipantSequenceNum = " + tempTableInfo + ".ParticipantSequenceNum" +
//                            " AND pv.Container = ?)",  _study.getContainer()));
//                }
//            }

        StringBuilder participantSequenceNum = new StringBuilder("(");
        participantSequenceNum.append(getParticipantSequenceNumExpr(schema, "ParticipantId", "SequenceNum"));
        participantSequenceNum.append(")");

        String sqlUpdateParticipantSeqNum = "UPDATE " + tableParticipantVisit + " SET ParticipantSequenceNum = " +
                participantSequenceNum + " WHERE Container = ?  AND ParticipantSequenceNum IS NULL";
        new SqlExecutor(schema).execute(sqlUpdateParticipantSeqNum, container);

        _updateVisitRowId();
    }


    private void _updateVisitRowId()
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        Container container = getStudy().getContainer();
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

        new SqlExecutor(schema).execute(sqlUpdateVisitRowId, getStudy().getContainer(), getStudy().getContainer());
    }


    /** Make sure there is a Visit for each row in StudyData otherwise rows will be orphaned */
    protected void updateVisitTable(final User user)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();

        SQLFragment sql = new SQLFragment("SELECT DISTINCT Day " +
            "FROM " + tableParticipantVisit + "\n" +
            "WHERE container = ? AND VisitRowId IS NULL");
        sql.add(getStudy().getContainer());

        final MutableInt days = new MutableInt(0);

        new SqlSelector(schema, sql).forEach(new ForEachBlock<Integer>()
        {
            @Override
            public void exec(Integer day) throws SQLException
            {
                double seqNum = null != day ? day : 0;
                StudyManager.getInstance().ensureVisit(getStudy(), user, seqNum, null, true);
                days.increment();
            }
        }, Integer.class);

        if (days.intValue() > 0)
            _updateVisitRowId();
    }

    public void recomputeDates(Date oldStartDate, User user)
    {
        if (null != oldStartDate)
        {
            String c = getStudy().getContainer().getId();
            DbSchema schema = StudySchema.getInstance().getSchema();
            SqlExecutor executor = new SqlExecutor(schema);
            TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
            TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
            TableInfo tableVisit = StudySchema.getInstance().getTableInfoVisit();

            int rowsUpdated = executor.execute("UPDATE " + tableParticipant + " SET StartDate=NULL WHERE StartDate= ? AND Container = ?", oldStartDate, c);

            if (rowsUpdated > 0)
            {
                //Now just start over computing *everything* as if we have brand new data...
                executor.execute("DELETE FROM " + tableParticipantVisit + " WHERE Container=?", c);
                executor.execute("DELETE FROM " + tableVisit + " WHERE Container=?", c);

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
