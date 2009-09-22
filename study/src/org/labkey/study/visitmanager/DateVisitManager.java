package org.labkey.study.visitmanager;

import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.StudySchema;
import org.labkey.study.CohortFilter;
import org.labkey.study.query.DataSetTable;
import org.labkey.study.model.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Copyright (c) 2008-2009 LabKey Corporation
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
* Created: Feb 29, 2008 11:23:37 AM
*/
public class DateVisitManager extends VisitManager
{
    public DateVisitManager(StudyImpl study)
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
    
    public Map<VisitMapKey, Integer> getVisitSummary(CohortFilter cohortFilter, QCStateSet qcStates) throws SQLException
    {
        Map<VisitMapKey, Integer> visitSummary = new HashMap<VisitMapKey, Integer>();
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo studyData = StudySchema.getInstance().getTableInfoStudyData();
        TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo participantTable = StudySchema.getInstance().getTableInfoParticipant();
        ResultSet rows = null;

        try
        {
            if (cohortFilter == null)
            {
                rows = Table.executeQuery(schema,
                        "SELECT DatasetId, Day, CAST(COUNT(*) AS INT)\n" +
                        "FROM " + studyData + " SD\n" +
                        "JOIN " + participantVisit + " PV ON SD.ParticipantId=PV.ParticipantId AND SD.SequenceNum=PV.SequenceNum AND SD.Container=PV.Container\n" +
                        "WHERE SD.Container = ? \n" +
                        (qcStates != null ? "AND " + qcStates.getStateInClause(DataSetTable.QCSTATE_ID_COLNAME) + "\n" : "") +
                        "GROUP BY DatasetId, Day\n" +
                        "ORDER BY 1, 2",
                        new Object[] {_study.getContainer().getId()}, 0, false);
            }
            else
            {
                String sql = null;
                switch (cohortFilter.getType())
                {
                    case DATA_COLLECTION:
                        break;
                    case PTID_CURRENT:
                    case PTID_INITIAL:
                        sql = "SELECT DatasetId, Day, CAST(COUNT(*) AS INT)\n" +
                            "FROM " + studyData + " SD\n" +
                            "JOIN " + participantVisit + " PV ON SD.ParticipantId=PV.ParticipantId AND SD.SequenceNum=PV.SequenceNum AND SD.Container=PV.Container\n" +
                            "JOIN " + participantTable + " P ON SD.ParticipantId=P.ParticipantId AND SD.Container=P.Container\n" +
                            "WHERE SD.Container = ? AND P." + (cohortFilter.getType() == CohortFilter.Type.PTID_CURRENT ? "CurrentCohortId" : "InitialCohortId") + " = ?\n" +
                            (qcStates != null ? "AND " + qcStates.getStateInClause(DataSetTable.QCSTATE_ID_COLNAME) + "\n" : "") +
                            "GROUP BY DatasetId, Day\n" +
                            "ORDER BY 1, 2";

                        break;
                }
                rows = Table.executeQuery(schema, sql, new Object[] { _study.getContainer().getId(), cohortFilter.getCohortId() }, 0, false);
            }
            VisitMapKey key = null;
            int cumulative = 0;
            while (rows.next())
            {
                int datasetId = rows.getInt(1);
                int day = rows.getInt(2);
                int count = rows.getInt(3);
                VisitImpl v = findVisitBySequence((double) day);
                if (null == v)
                    continue;
                int visitRowId = v.getRowId();

                if (null == key || key.datasetId  != datasetId || key.visitRowId != visitRowId)
                {
                    if (key != null)
                        visitSummary.put(key, cumulative);
                    key = new VisitMapKey(datasetId, visitRowId);
                    cumulative = 0;
                }
                cumulative += count;
            }
            if (key != null)
                visitSummary.put(key, cumulative);

            return visitSummary;
        }
        finally
        {
            ResultSetUtil.close(rows);
        }
    }


    protected void updateParticipantVisitTable(User user)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableStudyData = StudySchema.getInstance().getTableInfoStudyData();
        TableInfo tableSpecimen = StudySchema.getInstance().getTableInfoSpecimen();
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();

        try
        {
            //
            // populate ParticipantVisit
            //
            String sqlInsertParticipantVisit = "INSERT INTO " + tableParticipantVisit + " (Container, ParticipantId, SequenceNum, VisitDate)\n" +
                    "SELECT DISTINCT Container, ParticipantId, SequenceNum, _VisitDate\n" +
                    "FROM " + tableStudyData + " SD\n" +
                    "WHERE Container = ? AND NOT EXISTS (" +
                    "  SELECT ParticipantId, SequenceNum FROM " + tableParticipantVisit + " PV\n" +
                    "  WHERE Container = ? AND SD.ParticipantId=PV.ParticipantId AND SD.SequenceNum=PV.SequenceNum)";
            Table.execute(schema, sqlInsertParticipantVisit,
                    new Object[] {_study.getContainer(), _study.getContainer()});

            sqlInsertParticipantVisit = "INSERT INTO " + tableParticipantVisit + " (Container, ParticipantId, SequenceNum, VisitDate)\n" +
                    "SELECT DISTINCT Container, Ptid AS ParticipantId, VisitValue AS SequenceNum, " +
                    schema.getSqlDialect().getDateTimeToDateCast("DrawTimestamp") + " AS VisitDate\n" +
                    "FROM " + tableSpecimen + " AS Specimen\n" +
                    "WHERE Container = ?  AND Ptid IS NOT NULL AND VisitValue IS NOT NULL AND NOT EXISTS (" +
                    "  SELECT ParticipantId, SequenceNum FROM " + tableParticipantVisit + " PV\n" +
                    "  WHERE Container = ? AND Specimen.Ptid=PV.ParticipantId AND Specimen.VisitValue=PV.SequenceNum)";
            Table.execute(schema, sqlInsertParticipantVisit,
                    new Object[] {_study.getContainer(), _study.getContainer()});
            //
            // Delete ParticipantVisit where the participant does not exist anymore
            //
            String sqlDeleteParticiapantVisit = "DELETE FROM " + tableParticipantVisit + " WHERE Container = ? AND ParticipantId NOT IN (SELECT ParticipantId FROM " + tableParticipant + " WHERE Container= ?)";
            Table.execute(schema, sqlDeleteParticiapantVisit,
                    new Object[] {_study.getContainer(), _study.getContainer()});

            String sqlStartDate = "(SELECT StartDate FROM " + tableParticipant + " WHERE " + tableParticipant + ".ParticipantId=" + tableParticipantVisit + ".ParticipantId AND " + tableParticipant + ".Container=" + tableParticipantVisit + ".Container)";
            String sqlUpdateDays = "UPDATE " + tableParticipantVisit + " SET Day = CASE WHEN SequenceNum=? THEN 0 ELSE " + schema.getSqlDialect().getDateDiff(Calendar.DATE, "VisitDate", sqlStartDate) + " END WHERE Container=? AND NOT VisitDate IS NULL";
            Table.execute(schema, sqlUpdateDays, new Object[] {VisitImpl.DEMOGRAPHICS_VISIT, _study.getContainer()});

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
        Table.execute(schema, sqlUpdateVisitRowId,
                new Object[]{_study.getContainer(), _study.getContainer()});
    }


    /** Make sure there is a Visit for each row in StudyData otherwise rows will be orphaned */
    protected void updateVisitTable(User user)
    {
        try
        {
            String c = _study.getContainer().getId();

            DbSchema schema = StudySchema.getInstance().getSchema();
            TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
            TableInfo tableVisit = StudySchema.getInstance().getTableInfoVisit();

            SQLFragment sql = new SQLFragment("SELECT DISTINCT Day " +
                "FROM " + tableParticipantVisit + "\n" +
                "WHERE container = ? AND VisitRowId IS NULL");
            sql.add(c);

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
                StudyManager.getInstance().ensureVisit(_study, user, day, null, "Day " + day);
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
            String c = _study.getContainer().getId();
            DbSchema schema = StudySchema.getInstance().getSchema();
            TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
            TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
            TableInfo tableVisit = StudySchema.getInstance().getTableInfoVisit();

            int rowsUpdated = Table.execute(schema,
                    "UPDATE " + tableParticipant + " SET StartDate=NULL WHERE StartDate= ? AND Container = ?",
                    new Object[] {oldStartDate, c});

            if (rowsUpdated > 0)
            {
                //Now just start over computing *everything* as if we have brand new data...
                Table.execute(schema,
                        "DELETE FROM " + tableParticipantVisit + " WHERE Container=?",
                        new Object[] {c});

                Table.execute(schema, "DELETE FROM " + tableVisit + " WHERE Container=?", new Object[]{c});

                //Now recompute everything
                updateParticipantVisits(user);
            }
        }
    }

    // Return sql for fetching all datasets and their visit sequence numbers, given a container
    protected String getDatasetSequenceNumsSQL()
    {
        String sql = "SELECT sd.datasetid, v.sequencenummin FROM study.StudyData sd \n" +
            "JOIN study.ParticipantVisit pv ON  \n" +
            "     sd.SequenceNum = pv.SequenceNum AND \n" +
            "     sd.ParticipantId = pv.ParticipantId AND \n" +
            "     sd.Container = pv.Container \n" +
            "JOIN study.Visit v ON \n" +
            "     pv.VisitRowId = v.RowId AND \n" +
            "     pv.Container = v.Container \n" +
            "WHERE sd.Container = ?\n" +
            "group by sd.datasetid, v.sequencenummin";

        return sql;
    }
}
