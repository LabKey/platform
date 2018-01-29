/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.study.writer;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.PHI;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.importer.SpecimenImporter.SpecimenColumn;
import org.labkey.study.model.Vial;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.query.StudyQuerySchema;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 3:49:32 PM
 */
public class SpecimenWriter implements Writer<StudyImpl, StudyExportContext>
{
    public String getDataType()
    {
        return null;
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        StudySchema schema = StudySchema.getInstance();
        StudyQuerySchema querySchema = StudyQuerySchema.createSchema(study, ctx.getUser(), true); // to use for checking overlayed XMl metadata
        Container c = ctx.getContainer();
        SpecimenImporter specimenImporter = new SpecimenImporter(c, ctx.getUser());
        Collection<SpecimenColumn> columns = specimenImporter.getSpecimenColumns();

        PrintWriter pw = vf.getPrintWriter("specimens.tsv");

        pw.println("# specimens");

        SQLFragment sql = new SQLFragment().append("\nSELECT ");
        List<DisplayColumn> displayColumns = new ArrayList<>(columns.size());
        List<ColumnInfo> selectColumns = new ArrayList<>(columns.size());
        String comma = "";

        TableInfo tableInfoSpecimenDetail = querySchema.getTable(StudyQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
        TableInfo tableInfoSpecimenEvent = schema.getTableInfoSpecimenEvent(c);
        TableInfo queryTableSpecimenDetail = querySchema.getTable("SpecimenDetail");
        TableInfo queryTableSpecimenEvent = querySchema.getTable("SpecimenEvent");
        if (null == tableInfoSpecimenDetail || null == tableInfoSpecimenEvent ||
                null == queryTableSpecimenDetail || null == queryTableSpecimenEvent)
            throw new IllegalStateException("TableInfos not found.");

        SqlDialect dialect = schema.getSqlDialect();
        for (SpecimenColumn column : columns)
        {
            SpecimenImporter.TargetTable tt = column.getTargetTable();
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
            dc.setCaption(column.getTsvColumnName());
            displayColumns.add(dc);
            String col = "";

            // column info that includes the XML metadata override properties
            ColumnInfo queryColumn = getSpecimenQueryColumn(queryTable, column, dialect);

            // export alternate ID in place of Ptid if set in StudyExportContext
            if (ctx.isAlternateIds() && column.getDbColumnName().equals("Ptid"))
            {
                col = "ParticipantLookup.AlternateId AS Ptid";
            }
            else if (null == column.getFkColumn())
            {
                // Note that columns can be events, vials, or specimens (grouped vials); the SpecimenDetail view that's
                // used for export joins vials and specimens into a single view which we're calling 's'.  isEvents catches
                // those columns that are part of the events table, while !isEvents() catches the rest.  (equivalent to
                // isVials() || isSpecimens().)
                col = (tt.isEvents() ? "se." : "s.") + ci.getSelectName();

                // add expression to shift the date columns
                if (ctx.isShiftDates() && column.isDateType() && !queryColumn.isExcludeFromShifting())
                {
                    col = "{fn timestampadd(SQL_TSI_DAY, -ParticipantLookup.DateOffset, " + col + ")} AS " + ci.getSelectName();
                }

                // Don't export values for columns set at or above the PHI export level
                if (shouldRemovePhi(ctx.getPhiLevel(), column, queryColumn))
                {
                    col = "NULL AS " + ci.getSelectName();
                }
            }
            else
            {
                // DisplayColumn will use getAlias() to retrieve the value from the map
                col = column.getFkTableAlias() + "." + column.getFkColumn() + " AS " + dc.getDisplayColumn().getAlias();

                // Don't export values for columns set at or above the PHI export level
                if (shouldRemovePhi(ctx.getPhiLevel(), column, queryColumn))
                {
                    col = "NULL AS " + dc.getDisplayColumn().getAlias();
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

                SpecimenImporter.TargetTable tt = column.getTargetTable();
                TableInfo tinfo = tt.isEvents() ? tableInfoSpecimenEvent : tableInfoSpecimenDetail;
                ColumnInfo ci = tinfo.getColumn(column.getDbColumnName());
                sql.append("\n    ");
                if (column.getJoinType() != null)
                    sql.append(column.getJoinType()).append(" ");
                sql.append("JOIN ").append(specimenImporter.getTableInfoFromFkTableName(column.getFkTable()).getSelectName()).append(" AS ").append(column.getFkTableAlias()).append(" ON ");
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

        if (null != ctx.getVials() && !ctx.getVials().isEmpty())
        {
            List<Vial> vials = ctx.getVials();
            List<String> uniqueIds = new LinkedList<>();

            for (Vial vial : vials)
                uniqueIds.add(vial.getGlobalUniqueId());

            sql.append(conjunction).append("\n s.GlobalUniqueId IN (");
            sql.append(convertListToString(uniqueIds, true));
            sql.append(")");
            conjunction = " AND";
        }

        sql.append("\nORDER BY se.ExternalId");

        // Note: must be uncached result set -- this query can be very large
        try (
                ResultSet rs = new SqlSelector(StudySchema.getInstance().getSchema(), sql).getResultSet(false);
                TSVGridWriter gridWriter = new TSVGridWriter(new ResultsImpl(rs, selectColumns), displayColumns)
            )
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
            if ((queryColumn != null) && !(queryColumn.getPHI().isExportLevelAllowed(exportPhiLevel)))
                return true;
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
            ColumnInfo ciNotPhi = new ColumnInfo("test");
            ciNotPhi.setPHI(PHI.NotPHI);
            ColumnInfo ciLimitedPhi = new ColumnInfo("test");
            ciLimitedPhi.setPHI(PHI.Limited);
            ColumnInfo ciPhi = new ColumnInfo("test");
            ciPhi.setPHI(PHI.PHI);
            ColumnInfo ciRestrictedPhi = new ColumnInfo("test");
            ciRestrictedPhi.setPHI(PHI.Restricted);

            SpecimenColumn notKeyCol = new SpecimenColumn("test", "test", "INT", SpecimenImporter.TargetTable.SPECIMEN_EVENTS);
            SpecimenColumn keyCol = new SpecimenColumn("test", "test", "INT", true, SpecimenImporter.TargetTable.SPECIMEN_EVENTS);

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
