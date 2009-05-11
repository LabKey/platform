/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.util.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.importer.SpecimenImporter.SpecimenColumn;
import org.labkey.study.model.Study;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 3:49:32 PM
 */
public class SpecimenWriter implements Writer<Study>
{
    private static final Logger _log = Logger.getLogger(SpecimenWriter.class);

    public void write(Study study, ExportContext ctx, VirtualFile fs) throws Exception
    {
        SpecimenColumn[] columns = SpecimenImporter.SPECIMEN_COLUMNS;
        StudySchema schema = StudySchema.getInstance();
        Container c = ctx.getContainer();

        PrintWriter pw = fs.getPrintWriter("specimens.tsv");

        pw.println("# specimens");

        SQLFragment sql = new SQLFragment().append("\nSELECT ");
        List<DisplayColumn> displayColumns = new ArrayList<DisplayColumn>(columns.length);
        String comma = "";

        for (SpecimenColumn column : columns)
        {
            SpecimenImporter.TargetTable tt = column.getTargetTable();
            TableInfo tinfo = tt.isSpecimens() ? schema.getTableInfoSpecimen() : schema.getTableInfoSpecimenEvent();
            ColumnInfo ci = tinfo.getColumn(column.getDbColumnName());
            ci.setCaption(column.getTsvColumnName());
            DataColumn dc = new DataColumn(ci);
            displayColumns.add(dc);

            sql.append(comma);

            if (null == column.getFkColumn())
            {
                sql.append(tt.isSpecimens() ? "s." : "se.").append(column.getDbColumnName());
            }
            else
            {
                sql.append(column.getFkTableAlias()).append(".").append(column.getFkColumn());
                sql.append(" AS ").append(dc.getDisplayColumn().getAlias());  // DisplayColumn will use getAlias() to retrieve the value from the map
            }

            comma = ", ";
        }

        sql.append("\nFROM ").append(schema.getTableInfoSpecimenEvent()).append(" se JOIN ").append(schema.getTableInfoSpecimen()).append(" s ON SpecimenId = s.RowId");

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

        _log.info(sql);

        // Note: must be uncached result set -- this query can be very large
        ResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql.getSQL(), sql.getParamsArray(), 0, false);

        TSVGridWriter gridWriter = new TSVGridWriter(rs, displayColumns);
        gridWriter.write(pw);
        gridWriter.close();  // Closes ResultSet and PrintWriter
    }
}
