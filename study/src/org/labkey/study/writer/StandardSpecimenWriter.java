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

import org.labkey.api.data.*;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.api.study.StudyExportContext;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.SpecimenImporter.ImportableColumn;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 9:51:36 AM
 */
public class StandardSpecimenWriter implements Writer<StandardSpecimenWriter.QueryInfo, StudyExportContext>
{
    public String getSelectionText()
    {
        return null;
    }

    public void write(QueryInfo queryInfo, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        TableInfo tinfo = queryInfo.getTableInfo();
        ImportableColumn[] columns = queryInfo.getColumns();

        PrintWriter pw = vf.getPrintWriter(queryInfo.getFilename() + ".tsv");

        pw.print("# ");
        pw.println(queryInfo.getFilename());

        SQLFragment sql = new SQLFragment().append("SELECT ");
        List<DisplayColumn> displayColumns = new ArrayList<DisplayColumn>(columns.length);
        String comma = "";

        for (ImportableColumn column : columns)
        {
            sql.append(comma);
            sql.append(column.getDbColumnName());
            comma = ", ";

            ColumnInfo ci = tinfo.getColumn(column.getDbColumnName());
            ci.setLabel(column.getTsvColumnName());
            DisplayColumn dc = new DataColumn(ci);

            if (column.getJavaType() == Boolean.class)
                dc.setTsvFormatString("1;0;");

            displayColumns.add(dc);
        }

        sql.append(" FROM ");
        sql.append(queryInfo.getTableInfo());
        sql.append(" WHERE Container = ? ORDER BY ExternalId");

        sql.add(ctx.getContainer());

        ResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql);

        TSVGridWriter gridWriter = new TSVGridWriter(rs, displayColumns);
        gridWriter.write(pw);
        gridWriter.close();  // Closes ResultSet and PrintWriter
    }

    public static class QueryInfo
    {
        private TableInfo _tinfo;
        private String _filename;
        private ImportableColumn[] _columns;

        public QueryInfo(TableInfo tinfo, String filename, ImportableColumn[] columns)
        {
            _tinfo = tinfo;
            _filename = filename;
            _columns = columns;
        }

        public TableInfo getTableInfo()
        {
            return _tinfo;
        }

        public String getFilename()
        {
            return _filename;
        }

        public ImportableColumn[] getColumns()
        {
            return _columns;
        }
    }
}