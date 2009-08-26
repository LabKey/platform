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

package org.labkey.experiment.list;

import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.query.QueryService;
import org.labkey.api.study.StudyExportContext;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.api.writer.WriterFactory;

import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/*
* User: adam
* Date: Aug 25, 2009
* Time: 10:11:16 AM
*/
public class ListWriter implements Writer<Container, StudyExportContext>
{
    private static final Logger LOG = Logger.getLogger(ListWriter.class);

    public String getSelectionText()
    {
        return "Lists";
    }

    public void write(Container c, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        Map<String, ListDefinition> lists = ListService.get().getLists(c);

        for (Map.Entry<String, ListDefinition> entry : lists.entrySet())
        {
            ListDefinition def = entry.getValue();
            TableInfo tinfo = def.getTable(ctx.getUser());
            Collection<ColumnInfo> columns = getColumnsToExport(tinfo);
            ResultSet rs = QueryService.get().select(tinfo, columns, null, null);
            TSVGridWriter tsvWriter = new TSVGridWriter(rs);
            tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);
            PrintWriter out = vf.getPrintWriter(def.getName() + ".tsv");
            tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
        }
    }

    private Collection<ColumnInfo> getColumnsToExport(TableInfo tinfo)
    {
        Collection<ColumnInfo> columns = new LinkedList<ColumnInfo>();

        for (ColumnInfo column : tinfo.getColumns())
            if (column.isUserEditable())
                columns.add(column);

        return columns;
    }

    public static class Factory implements WriterFactory<Container, StudyExportContext>
    {
        public Writer<Container, StudyExportContext> create()
        {
            return new ListWriter();
        }
    }
}
