/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.*;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.importer.SpecimenImporter.SpecimenColumn;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.StudyDocument;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 3:49:32 PM
 */
class SpecimenWriter implements Writer<StudyImpl, ImportContext<StudyDocument.Study>>
{
    public String getSelectionText()
    {
        return null;
    }

    public void write(StudyImpl study, ImportContext<StudyDocument.Study> ctx, VirtualFile vf) throws Exception
    {
        Collection<SpecimenColumn> columns = SpecimenImporter.SPECIMEN_COLUMNS;
        StudySchema schema = StudySchema.getInstance();
        Container c = ctx.getContainer();

        PrintWriter pw = vf.getPrintWriter("specimens.tsv");

        pw.println("# specimens");

        SQLFragment sql = new SQLFragment().append("\nSELECT ");
        List<DisplayColumn> displayColumns = new ArrayList<DisplayColumn>(columns.size());
        List<ColumnInfo> selectColumns = new ArrayList<ColumnInfo>(columns.size());
        String comma = "";

        for (SpecimenColumn column : columns)
        {
            SpecimenImporter.TargetTable tt = column.getTargetTable();
            TableInfo tinfo = tt.isEvents() ? schema.getTableInfoSpecimenEvent() : schema.getTableInfoSpecimenDetail();
            ColumnInfo ci = tinfo.getColumn(column.getDbColumnName());
            DataColumn dc = new DataColumn(ci);
            selectColumns.add(dc.getDisplayColumn());
            dc.setCaption(column.getTsvColumnName());
            displayColumns.add(dc);

            sql.append(comma);

            if (null == column.getFkColumn())
            {
                // Note that columns can be events, vials, or specimens (grouped vials); the SpecimenDetail view that's
                // used for export joins vials and specimens into a single view which we're calling 's'.  isEvents catches
                // those columns that are part of the events table, while !isEvents() catches the rest.  (equivalent to
                // isVials() || isSpecimens().)
                sql.append(tt.isEvents() ? "se." : "s.").append(column.getDbColumnName());
            }
            else
            {
                sql.append(column.getFkTableAlias()).append(".").append(column.getFkColumn());
                sql.append(" AS ").append(dc.getDisplayColumn().getAlias());  // DisplayColumn will use getAlias() to retrieve the value from the map
            }

            comma = ", ";
        }

        sql.append("\nFROM ").append(schema.getTableInfoSpecimenEvent()).append(" se JOIN ").append(schema.getTableInfoSpecimenDetail()).append(" s ON se.VialId = s.RowId");

        for (SpecimenColumn column : columns)
        {
            if (null != column.getFkColumn())
            {
                assert column.getTargetTable().isEvents();

                sql.append("\n    ");
                if (column.getJoinType() != null)
                    sql.append(column.getJoinType()).append(" ");
                sql.append("JOIN study.").append(column.getFkTable()).append(" AS ").append(column.getFkTableAlias()).append(" ON ");
                sql.append("(se.");
                sql.append(column.getDbColumnName()).append(" = ").append(column.getFkTableAlias()).append(".RowId)");
            }
        }

        sql.append("\nWHERE se.Container = ? ORDER BY se.ExternalId");
        sql.add(c);

        ResultSet rs = null;
        TSVGridWriter gridWriter = null;
        try
        {
            // Note: must be uncached result set -- this query can be very large
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql.getSQL(), sql.getParamsArray(), Table.ALL_ROWS, false);

            gridWriter = new TSVGridWriter(new ResultsImpl(rs, selectColumns), displayColumns);
            gridWriter.write(pw);
        }
        finally
        {
            if (gridWriter != null)
                gridWriter.close();  // Closes ResultSet and PrintWriter
            else if (rs != null)
                rs.close();
        }
    }
}
