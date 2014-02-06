/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.Results;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoWriter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Created by klum on 1/24/14.
 */
public abstract class DefaultStudyDesignWriter
{
    protected void writeTableData(StudyExportContext ctx, VirtualFile vf, Set<TableInfo> tables, @Nullable ContainerFilter containerFilter) throws SQLException, IOException
    {
        for (TableInfo tinfo : tables)
        {
            writeTableData(ctx, vf, tinfo, getDefaultColumns(tinfo), containerFilter);
        }
    }

    protected void writeTableData(StudyExportContext ctx, VirtualFile vf, TableInfo table, List<ColumnInfo> columns,
                                @Nullable ContainerFilter containerFilter) throws SQLException, IOException
    {
        // Write each table as a separate .tsv
        if (table != null)
        {
            if (containerFilter != null)
            {
                if (table instanceof ContainerFilterable)
                {
                    ((ContainerFilterable)table).setContainerFilter(containerFilter);
                }
            }
            Results rs = QueryService.get().select(table, columns, null, null);
            writeResultsToTSV(rs, vf, getFileName(table));
        }
    }

    protected String getFileName(TableInfo tableInfo)
    {
        return tableInfo.getName().toLowerCase() + ".tsv";
    }

    protected void writeResultsToTSV(Results rs, VirtualFile vf, String fileName) throws SQLException, IOException
    {
        TSVGridWriter tsvWriter = new TSVGridWriter(rs);
        tsvWriter.setApplyFormats(false);
        tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.queryColumnName);
        PrintWriter out = vf.getPrintWriter(fileName);
        tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
    }

    /**
     * Returns the default visible columns for a table but ignores the standard columns
     */
    protected List<ColumnInfo> getDefaultColumns(TableInfo tableInfo)
    {
        List<ColumnInfo> columns = new ArrayList<>();

        for (ColumnInfo col : tableInfo.getColumns())
        {
            if (FieldKey.fromParts("Container").equals(col.getFieldKey()))
                continue;
            if (FieldKey.fromParts("Created").equals(col.getFieldKey()))
                continue;
            if (FieldKey.fromParts("CreatedBy").equals(col.getFieldKey()))
                continue;
            if (FieldKey.fromParts("Modified").equals(col.getFieldKey()))
                continue;
            if (FieldKey.fromParts("ModifiedBy").equals(col.getFieldKey()))
                continue;

            columns.add(col);
        }
        return columns;
    }

    protected void writeTableInfos(StudyExportContext ctx, VirtualFile vf, Set<TableInfo> tables, String schemaFileName) throws IOException
    {
        // Create dataset metadata file
        TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
        TablesType tablesXml = tablesDoc.addNewTables();

        for (TableInfo tinfo : tables)
        {
            TableType tableXml = tablesXml.addNewTable();

            TableInfoWriter writer = new TreatementTableWriter(ctx.getContainer(), tinfo, tinfo.getColumns());
            writer.writeTable(tableXml);
        }
        vf.saveXmlBean(schemaFileName, tablesDoc);
    }

    private static class TreatementTableWriter extends TableInfoWriter
    {
        public TreatementTableWriter(Container c, TableInfo tinfo, Collection<ColumnInfo> columns)
        {
            super(c, tinfo, columns);
        }
    }
}
