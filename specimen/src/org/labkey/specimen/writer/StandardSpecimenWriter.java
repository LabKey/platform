/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import org.labkey.api.admin.ImportExportContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ResultsFactory;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.importer.ImportableColumn;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.study.xml.StudyDocument;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: adam
 * Date: May 7, 2009
 * Time: 9:51:36 AM
 */
class StandardSpecimenWriter implements Writer<StandardSpecimenWriter.QueryInfo, ImportExportContext<StudyDocument.Study>>
{
    @Override
    public String getDataType()
    {
        return null;
    }

    @Override
    public void write(QueryInfo queryInfo, ImportExportContext<StudyDocument.Study> ctx, VirtualFile vf) throws Exception
    {
        TableInfo tinfo = queryInfo.getTableInfo();
        Collection<ImportableColumn> columns = queryInfo.getColumns();

        PrintWriter pw = vf.getPrintWriter(queryInfo.getFilename() + ".tsv");

        pw.print("# ");
        pw.println(queryInfo.getFilename());

        List<DisplayColumn> displayColumns = new ArrayList<>(columns.size());
        for (ImportableColumn column : columns)
        {
            ColumnInfo ci = tinfo.getColumn(column.getDbColumnName());
            DisplayColumn dc = new DataColumn(ci);
            dc.setCaption(column.getPrimaryTsvColumnName());

            if (column.getJavaClass() == Boolean.class)
                dc.setTsvFormatString("1;0;");

            displayColumns.add(dc);
        }

        SQLFragment sql = generateSql(ctx, tinfo, columns);

        // TSVGridWriter generates and closes the Results
        ResultsFactory factory = ()->new ResultsImpl(new SqlSelector(SpecimenSchema.get().getSchema(), sql).getResultSet(false));

        try (TSVGridWriter gridWriter = new TSVGridWriter(factory, displayColumns))
        {
            gridWriter.write(pw);
        }
    }

    protected SQLFragment generateSql(ImportExportContext<StudyDocument.Study> ctx, TableInfo tinfo, Collection<ImportableColumn> columns)
    {
        SQLFragment sql = new SQLFragment().append("SELECT ");
        String comma = "";

        for (ImportableColumn column : columns)
        {
            sql.append(comma);
            sql.append(column.getDbColumnName());
            comma = ", ";
        }

        sql.append(" FROM ");
        sql.append(tinfo, "ti");
        sql.append(" WHERE Container = ? ORDER BY ExternalId");

        sql.add(ctx.getContainer());
        return sql;
    }

    public static class QueryInfo
    {
        private final TableInfo _tinfo;
        private final String _filename;
        private final Collection<ImportableColumn> _columns;

        public QueryInfo(TableInfo tinfo, String filename, Collection<ImportableColumn> columns)
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

        public Collection<ImportableColumn> getColumns()
        {
            return _columns;
        }
    }
}
