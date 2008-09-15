package org.labkey.study.visitmanager;

import org.labkey.study.query.DataSetTable;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.study.StudySchema;
import org.labkey.study.model.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Copyright (c) 2008 LabKey Corporation
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
* Created: Feb 29, 2008 11:23:56 AM
*/
public class SequenceVisitManager extends VisitManager
{
    public SequenceVisitManager(Study study)
    {
        super(study);
    }

    public Map<VisitMapKey, Integer> getVisitSummary(Cohort cohort, QCStateSet qcStates) throws SQLException
    {
        Map<VisitMapKey, Integer> visitSummary = new HashMap<VisitMapKey, Integer>();
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo studyData = StudySchema.getInstance().getTableInfoStudyData();
        TableInfo participantTable = StudySchema.getInstance().getTableInfoParticipant();
        ResultSet rows = null;

        // This query is too slow on postgres (8.1 anyway), do the join in code
//        TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
//        ResultSet rows = Table.executeQuery(schema,
//                "SELECT DatasetId, VisitRowId, CAST(COUNT(*) AS INT)\n" +
//                "FROM " + participantVisit + " PV JOIN " + studyData + " SD ON PV.ParticipantId=SD.ParticipantId AND PV.SequenceNum = SD.SequenceNum AND PV.Container = SD.Container\n" +
//                "WHERE SD.Container = ? AND PV.Container = ?\n" +
//                "GROUP BY DatasetId, VisitRowId",
//                new Object[] {study.getContainer().getId(), study.getContainer().getId()});


        try
        {
            String sql;
            if (cohort == null)
            {
                 sql = "SELECT DatasetId, SequenceNum, CAST(COUNT(*) AS INT)\n" +
                    "FROM " + studyData + " SD\n" +
                    "WHERE SD.Container = ? \n" +
                     (qcStates != null ? "AND " + qcStates.getStateInClause(DataSetTable.QCSTATE_ID_COLNAME) + "\n" : "") +
                    "GROUP BY DatasetId, SequenceNum\n" +
                    "ORDER BY 1, 2";
                rows = Table.executeQuery(schema, sql, new Object[] {_study.getContainer().getId()}, 0, false);
            }
            else
            {
                sql = "SELECT DatasetId, SequenceNum, CAST(COUNT(*) AS INT)\n" +
                    "FROM " + studyData + " SD, " + participantTable + " P\n" +
                    "WHERE SD.Container = ? AND P.ParticipantId = SD.ParticipantId AND P.Container = SD.Container AND P.CohortID = ?\n" +
                    (qcStates != null ? "AND " + qcStates.getStateInClause(DataSetTable.QCSTATE_ID_COLNAME) + "\n" : "") +
                    "GROUP BY DatasetId, SequenceNum\n" +
                    "ORDER BY 1, 2";
                rows = Table.executeQuery(schema, sql, new Object[] {_study.getContainer().getId(), cohort.getRowId()}, 0, false);
            }

            VisitMapKey key = null;
            int cumulative = 0;
            while (rows.next())
            {
                int datasetId = rows.getInt(1);
                double sequenceNum = rows.getDouble(2);
                int count = rows.getInt(3);
                Visit v = findVisitBySequence(sequenceNum);
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


    /** return true if OK, false if validation error */
    public boolean validateVisitRanges(List<String> errors)
    {
        int errorSize = errors.size();
        TreeMap<Double,Visit> map = getVisitSequenceMap();
        Visit prev = null;
        for (Visit v : map.values())
        {
            // this shouldn't happen, as this gets validated early
            if (v.getSequenceNumMin() > v.getSequenceNumMax())
            {
                errors.add("Invalid sequence range: " + v.getDisplayString());
                continue;
            }
            if (prev != null)
            {
                if (prev.getSequenceNumMax() >= v.getSequenceNumMin())
                {
                    errors.add("Overrlapping sequence range: " + prev.getDisplayString() + " and " + v.getDisplayString());
                    continue;
                }
            }
            prev = v;
        }
        return errorSize == errors.size();
    }


    protected void updateParticipantVisitTable(User user)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo tableVisit = StudySchema.getInstance().getTableInfoVisit();
        TableInfo tableParticipantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
        TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
        TableInfo tableSpecimen = StudySchema.getInstance().getTableInfoSpecimen();
        TableInfo tableStudyData = StudySchema.getInstance().getTableInfoStudyData();

        try
        {
            //
            // populate ParticipantVisit
            //
            String sqlInsertParticipantVisit = "INSERT INTO " + tableParticipantVisit + " (Container, ParticipantId, SequenceNum)\n" +
                    "SELECT DISTINCT Container, ParticipantId, SequenceNum\n" +
                    "FROM " + tableStudyData + " SD\n" +
                    "WHERE Container = ? AND NOT EXISTS (" +
                    "  SELECT ParticipantId, SequenceNum FROM " + tableParticipantVisit + " PV\n" +
                    "  WHERE Container = ? AND SD.ParticipantId=PV.ParticipantId AND SD.SequenceNum=PV.SequenceNum)";
            Table.execute(schema, sqlInsertParticipantVisit,
                    new Object[] {_study.getContainer(), _study.getContainer()});

            //
            // Delete ParticipantVisit where the participant does not exist anymore
            //
            String sqlDeleteParticiapantVisit = "DELETE FROM " + tableParticipantVisit + " WHERE Container = ? AND ParticipantId NOT IN (SELECT ParticipantId FROM " + tableParticipant + " WHERE Container= ?)";
            Table.execute(schema, sqlDeleteParticiapantVisit,
                    new Object[] {_study.getContainer(), _study.getContainer()});

            // after assigning visit dates to all study data-generated visits, we insert any extra ptid/sequencenum/date combinations
            // that are found in the specimen archives.  We simply trust the specimen draw date in this case, rather than relying on the
            // visit table to tell us which date corresponds to which visit:
            sqlInsertParticipantVisit = "INSERT INTO " + tableParticipantVisit + " (Container, ParticipantId, SequenceNum)\n" +
                    "SELECT DISTINCT Container, Ptid AS ParticipantId, VisitValue AS SequenceNum\n" +
                    "FROM " + tableSpecimen + " Specimen\n" +
                    "WHERE Container = ? AND NOT EXISTS (" +
                    "  SELECT ParticipantId, SequenceNum FROM " + tableParticipantVisit + " PV\n" +
                    "  WHERE Container = ? AND Specimen.Ptid=PV.ParticipantId AND Specimen.VisitValue=PV.SequenceNum)";
            Table.execute(schema, sqlInsertParticipantVisit,
                    new Object[] {_study.getContainer(), _study.getContainer()});



            //
            // fill in VisitRowId (need this to do the VisitDate computation)
            //
            _updateVisitRowId();

            //
            // upate VisitDate
            //

            // update ParticpantVisit.VisitDate based on declared Visit.visitDateDatasetId
            String sqlUpdateVisitDates = "UPDATE " + tableParticipantVisit + "\n" +
                    "SET VisitDate = \n" +
                    " (\n" +
                    " SELECT SD._VisitDate\n" +
                    " FROM " + tableStudyData + " SD,  " + tableVisit + " V\n" +
                    " WHERE  ParticipantVisit.VisitRowId = V.RowId AND" +    // 'join' V
                    "   SD.ParticipantId = ParticipantVisit.ParticipantId AND SD.SequenceNum = ParticipantVisit.SequenceNum AND\n" +    // 'join' SD
                    "   SD.DatasetId = V.VisitDateDatasetId AND SD.Container=? AND V.Container=?\n" +
                    " )\n";
            if (schema.getSqlDialect().isSqlServer()) // for SQL Server 2000
                sqlUpdateVisitDates += "FROM " + tableParticipantVisit + " ParticipantVisit\n";
            sqlUpdateVisitDates += "WHERE Container=?";
            Table.execute(schema, sqlUpdateVisitDates,
                    new Object[]{_study.getContainer(), _study.getContainer(), _study.getContainer()});

            /* infer ParticpantVisit.VisitDate if it seems unambiguous
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
                " WHERE ParticipantVisit.SequenceNum BETWEEN V.SequenceNumMin AND V.SequenceNumMax AND\n" +
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
                StudyManager.getInstance().createVisit(_study, user, d, null, null);
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
    protected String getDatasetSequenceNumsSQL()
    {
        String sql = "SELECT x.datasetid as datasetid, CAST(x.SequenceNum AS FLOAT) AS sequencenum FROM (SELECT DISTINCT SequenceNum,DatasetId FROM " +
            StudySchema.getInstance().getTableInfoStudyData() +
            " where container=? ) x ORDER BY datasetid,sequencenum";

        return sql;
    }
}
