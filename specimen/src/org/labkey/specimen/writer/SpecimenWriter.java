/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
package org.labkey.specimen.writer;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PHI;
import org.labkey.api.data.ResultsFactory;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.UserSchema;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.SpecimenTableManager;
import org.labkey.api.specimen.importer.SpecimenColumn;
import org.labkey.api.specimen.importer.TargetTable;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.writer.SimpleStudyExportContext;
import org.labkey.api.writer.VirtualFile;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 3:49:32 PM
 */
public class SpecimenWriter extends AbstractSpecimenWriter
{
    @Override
    public String getDataType()
    {
        return null;
    }

    @Override
    public void write(Study study, SimpleStudyExportContext ctx, VirtualFile vf) throws Exception
    {
        SpecimenSchema schema = SpecimenSchema.get();
        UserSchema querySchema = StudyService.get().getStudyQuerySchema(study, ctx.getUser()); // to use for checking overlayed XMl metadata
        Container c = ctx.getContainer();
        SpecimenTableManager specimenTableManager = new SpecimenTableManager(c, ctx.getUser());
        Collection<SpecimenColumn> columns = specimenTableManager.getSpecimenColumns();

        PrintWriter pw = vf.getPrintWriter("specimens.tsv");

        pw.println("# specimens");

        SQLFragment sql = new SQLFragment().append("\nSELECT ");
        List<DisplayColumn> displayColumns = new ArrayList<>(columns.size());
        List<ColumnInfo> selectColumns = new ArrayList<>(columns.size());
        String comma = "";

        TableInfo tableInfoSpecimenDetail = querySchema.getTable("SpecimenWrap");
        TableInfo tableInfoSpecimenEvent = schema.getTableInfoSpecimenEvent(c);
        TableInfo queryTableSpecimenDetail = querySchema.getTable("SpecimenDetail");
        TableInfo queryTableSpecimenEvent = querySchema.getTable("SpecimenEvent");
        if (null == tableInfoSpecimenDetail || null == queryTableSpecimenDetail || null == queryTableSpecimenEvent)
            throw new IllegalStateException("TableInfos not found.");

        SqlDialect dialect = schema.getSqlDialect();
        for (SpecimenColumn column : columns)
        {
            TargetTable tt = column.getTargetTable();
            TableInfo tinfo = tt.isEvents() ? tableInfoSpecimenEvent : tableInfoSpecimenDetail;
            TableInfo queryTable = tt.isEvents() ? queryTableSpecimenEvent : queryTableSpecimenDetail;
            ColumnInfo ci = tinfo.getColumn(column.getDbColumnName());
            if (null == ci)
            {
                ctx.getLogger().warn("Specimen Column '" + column.getDbColumnName() + "' not found in table '" +
                                     tinfo.getPublicName() + "'");
                continue;
            }
            DataColumn dc = new DataColumn(ci);
            selectColumns.add(dc.getDisplayColumn());
            dc.setCaption(column.getPrimaryTsvColumnName());
            displayColumns.add(dc);
            SQLFragment col;

            // column info that includes the XML metadata override properties
            ColumnInfo queryColumn = getSpecimenQueryColumn(queryTable, column, dialect);

            // export alternate ID in place of Ptid if set in StudyExportContext
            if (ctx.isAlternateIds() && column.getDbColumnName().equals("Ptid"))
            {
                col = new SQLFragment("ParticipantLookup.AlternateId AS Ptid");
            }
            else if (null == column.getFkColumn())
            {
                // Note that columns can be events, vials, or specimens (grouped vials); the SpecimenDetail view that's
                // used for export joins vials and specimens into a single view which we're calling 's'.  isEvents catches
                // those columns that are part of the events table, while !isEvents() catches the rest.  (equivalent to
                // isVials() || isSpecimens().)
                col = ci.getValueSql(tt.isEvents() ? "se" : "s");

                // add expression to shift the date columns
                if (ctx.isShiftDates() && column.isDateType() && !queryColumn.isExcludeFromShifting())
                {
                    col = new SQLFragment("{fn timestampadd(SQL_TSI_DAY, -ParticipantLookup.DateOffset, ").append(col).append(")}");
                }

                // Don't export values for columns set at or above the PHI export level
                if (shouldRemovePhi(ctx.getPhiLevel(), column, queryColumn))
                {
                    col = new SQLFragment("NULL");
                }
                col.append(" AS ").append(dc.getDisplayColumnInfo().getAlias());
            }
            else
            {
                // DisplayColumn will use getAlias() to retrieve the value from the map
                col = new SQLFragment(column.getFkTableAlias() + "." + column.getFkColumn() + " AS " + dc.getDisplayColumn().getAlias());

                // Don't export values for columns set at or above the PHI export level
                if (shouldRemovePhi(ctx.getPhiLevel(), column, queryColumn))
                {
                    col = new SQLFragment("NULL AS " + dc.getDisplayColumn().getAlias());
                }
            }

            sql.append(comma);
            sql.append(col);
            comma = ", ";
        }

        sql.append("\nFROM ").append(tableInfoSpecimenEvent.getFromSQL("se")).append(" JOIN ")
                .append(tableInfoSpecimenDetail.getFromSQL("s")).append(" ON se.VialId = s.RowId");

        for (SpecimenColumn column : columns)
        {
            if (null != column.getFkColumn())
            {
                assert column.getTargetTable().isEvents();

                TargetTable tt = column.getTargetTable();
                TableInfo tinfo = tt.isEvents() ? tableInfoSpecimenEvent : tableInfoSpecimenDetail;
                ColumnInfo ci = tinfo.getColumn(column.getDbColumnName());
                sql.append("\n    ");
                if (column.getJoinType() != null)
                    sql.append(column.getJoinType()).append(" ");
                sql.append("JOIN ").append(specimenTableManager.getTableInfoFromFkTableName(column.getFkTable()).getSelectName()).append(" AS ").append(column.getFkTableAlias()).append(" ON ");
                sql.append("(se.");
                sql.append(ci.getSelectName()).append(" = ").append(column.getFkTableAlias()).append(".RowId)");
            }
        }

        // add join to study.Participant table if we are using alternate IDs or shifting dates
        if (ctx.isAlternateIds() || ctx.isShiftDates())
        {
            sql.append("\n    LEFT JOIN study.Participant AS ParticipantLookup ON (s.Ptid = ParticipantLookup.ParticipantId AND s.Container = ParticipantLookup.Container)");
        }
        // add join to study.ParticipantVisit table if we are filtering by visit IDs
        if (ctx.getVisitIds() != null && !ctx.getVisitIds().isEmpty())
        {
            sql.append("\n    LEFT JOIN study.ParticipantVisit AS ParticipantVisitLookup ON (s.Ptid = ParticipantVisitLookup.ParticipantId AND s.ParticipantSequenceNum = ParticipantVisitLookup.ParticipantSequenceNum AND s.Container = ParticipantVisitLookup.Container)");
        }

        sql.append("\n");

        // add filter for selected participant IDs and Visits IDs
        String conjunction = "WHERE ";
        if (ctx.getVisitIds() != null && !ctx.getVisitIds().isEmpty())
        {
            sql.append(conjunction).append("\n ParticipantVisitLookup.VisitRowId IN (");
            sql.append(convertListToString(new ArrayList<>(ctx.getVisitIds()), false));
            sql.append(")");
            conjunction = " AND ";
        }

        if (null != ctx.getParticipants() && !ctx.getParticipants().isEmpty())
        {
            sql.append(conjunction);
            if (ctx.isAlternateIds())
                sql.append("\n ParticipantLookup.AlternateId IN (");
            else
                sql.append("\n se.Ptid IN (");

            sql.append(convertListToString(ctx.getParticipants(), true));
            sql.append(")");
            conjunction = " AND ";
        }

        sql.append("\nORDER BY se.ExternalId");

        // Note: must be uncached result set -- this query can be very large
        ResultsFactory factory = ()->new ResultsImpl(new SqlSelector(schema.getSchema(), sql).getResultSet(false), selectColumns);

        // TSVGridWriter generates and closes the Results
        try (TSVGridWriter gridWriter = new TSVGridWriter(factory, displayColumns))
        {
            gridWriter.write(pw);
        }
    }

    private ColumnInfo getSpecimenQueryColumn(TableInfo queryTable, SpecimenColumn column, SqlDialect dialect)
    {
        // if the query table contains the column using the DBColumnName, use that, otherwise try removing the 'id' from the end of the column name
        if (queryTable != null && column != null)
        {
            if (null != queryTable.getColumn(column.getDbColumnName()))
                return queryTable.getColumn(column.getDbColumnName());

            String legalName = PropertyDescriptor.getLegalSelectNameFromStorageName(dialect, column.getDbColumnName());
            if (null != queryTable.getColumn(legalName))
                return queryTable.getColumn(legalName);

            if (column.getDbColumnName().toLowerCase().endsWith("id"))
            {
                String tempColName = column.getDbColumnName().substring(0, column.getDbColumnName().length() - 2);
                return queryTable.getColumn(tempColName);
            }
        }
        return null;
    }

    private static boolean shouldRemovePhi(PHI exportPhiLevel, SpecimenColumn column, ColumnInfo queryColumn)
    {
        if (!column.isKeyColumn())
        {
            return (queryColumn != null) && !(queryColumn.getPHI().isExportLevelAllowed(exportPhiLevel));
        }

        return false;
    }

    private static String convertListToString(List list, boolean withQuotes)
    {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (Object obj : list)
        {
            sb.append(sep);
            if (withQuotes) sb.append("'");
            sb.append(obj.toString());
            if (withQuotes) sb.append("'");
            sep = ",";
        }
        return sb.toString();
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testConvertListToString()
        {
            List<Integer> ints = new ArrayList<>();
            ints.add(1);
            ints.add(2);
            ints.add(3);
            assertEquals("1,2,3", convertListToString(ints, false));
            assertEquals("'1','2','3'", convertListToString(ints, true));

            List<String> ptids = new ArrayList<>();
            ptids.add("Ptid1");
            ptids.add("Ptid2");
            ptids.add("Ptid3");
            assertEquals("Ptid1,Ptid2,Ptid3", convertListToString(ptids, false));
            assertEquals("'Ptid1','Ptid2','Ptid3'", convertListToString(ptids, true));
        }

        @Test
        public void testShouldRemovePhi()
        {
            var ciNotPhi = new BaseColumnInfo("test", JdbcType.OTHER);
            ciNotPhi.setPHI(PHI.NotPHI);
            var ciLimitedPhi = new BaseColumnInfo("test", JdbcType.OTHER);
            ciLimitedPhi.setPHI(PHI.Limited);
            var ciPhi = new BaseColumnInfo("test", JdbcType.OTHER);
            ciPhi.setPHI(PHI.PHI);
            var ciRestrictedPhi = new BaseColumnInfo("test", JdbcType.OTHER);
            ciRestrictedPhi.setPHI(PHI.Restricted);

            SpecimenColumn notKeyCol = new SpecimenColumn("test", "test", "INT", TargetTable.SPECIMEN_EVENTS);
            SpecimenColumn keyCol = new SpecimenColumn("test", "test", "INT", true, TargetTable.SPECIMEN_EVENTS);

            // should remove if not a key column and it is at or above PHI export level
            assertTrue(shouldRemovePhi(PHI.PHI, notKeyCol, ciRestrictedPhi));
            assertTrue(shouldRemovePhi(PHI.Limited, notKeyCol, ciRestrictedPhi));
            assertTrue(shouldRemovePhi(PHI.NotPHI, notKeyCol, ciRestrictedPhi));
            assertTrue(shouldRemovePhi(PHI.Limited, notKeyCol, ciPhi));
            assertTrue(shouldRemovePhi(PHI.NotPHI, notKeyCol, ciPhi));
            assertTrue(shouldRemovePhi(PHI.NotPHI, notKeyCol, ciLimitedPhi));

            // shouldn't remove if not a key column and it is not at or above PHI export level
            assertFalse(shouldRemovePhi(PHI.Restricted, notKeyCol, ciPhi));
            assertFalse(shouldRemovePhi(PHI.PHI, notKeyCol, ciPhi));
            assertFalse(shouldRemovePhi(PHI.Restricted, notKeyCol, ciLimitedPhi));
            assertFalse(shouldRemovePhi(PHI.PHI, notKeyCol, ciLimitedPhi));
            assertFalse(shouldRemovePhi(PHI.Limited, notKeyCol, ciLimitedPhi));
            assertFalse(shouldRemovePhi(PHI.Restricted, notKeyCol, ciNotPhi));
            assertFalse(shouldRemovePhi(PHI.PHI, notKeyCol, ciNotPhi));
            assertFalse(shouldRemovePhi(PHI.Limited, notKeyCol, ciNotPhi));
            assertFalse(shouldRemovePhi(PHI.NotPHI, notKeyCol, ciNotPhi));

            // shouldn't remove if it is a key column
            assertFalse(shouldRemovePhi(PHI.Restricted, keyCol, ciRestrictedPhi));
            assertFalse(shouldRemovePhi(PHI.PHI, keyCol, ciRestrictedPhi));
            assertFalse(shouldRemovePhi(PHI.Limited, keyCol, ciRestrictedPhi));
            assertFalse(shouldRemovePhi(PHI.PHI, keyCol, ciPhi));
            assertFalse(shouldRemovePhi(PHI.Limited, keyCol, ciPhi));
            assertFalse(shouldRemovePhi(PHI.Limited, keyCol, ciLimitedPhi));
        }
    }
}
