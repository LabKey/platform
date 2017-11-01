/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

package org.labkey.query;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoWriter;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.ZipFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.query.controllers.QueryController.ExportTablesForm;
import org.springframework.beans.MutablePropertyValues;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: davebradlee
 * Date: 12/17/12
 * Time: 4:08 PM
 */
public class TableWriter
{
    static final String SCHEMA_FILENAME = "metadata.xml";

    public boolean write(Container c, User user, VirtualFile dir) throws IOException
    {
        return write(c, user, dir, null, null);
    }

    public boolean write(Container c, User user, VirtualFile dir, Map<String, List<Map<String, Object>>> schemas, ColumnHeaderType header) throws IOException
    {
        QueryService queryService = QueryService.get();
        List<QueryView> views = new ArrayList<>();
        if (null == schemas || schemas.size() == 0)
        {
            // TBD
        }
        else
        {
            Map<String, List<Map<String, Object>>> schemaMap = schemas;

            for (String schemaName : schemaMap.keySet())
            {
                UserSchema schema = queryService.getUserSchema(user, c, schemaName);
                if (null == schema)
                    continue;

                List<Map<String, Object>> querySettingsMapList = schemaMap.get(schemaName);
                if (querySettingsMapList == null)
                {
                    // Export all queries in schema
                    List<String> names = schema.getTableAndQueryNames(false);
                    querySettingsMapList = new ArrayList<>(names.size());
                    for (String queryName : names)
                    {
                        querySettingsMapList.add(Collections.singletonMap(QueryParam.queryName.name(), queryName));
                    }
                }

                // Create QueryViews
                for (Map<String, Object> querySettingsMap : querySettingsMapList)
                {
                    String queryName = (String)querySettingsMap.get(QueryParam.queryName.name());
                    String viewName = (String)querySettingsMap.get(QueryParam.viewName.name());
                    Map<String, Object> filters = (Map<String, Object>)querySettingsMap.get("filters");

                    MutablePropertyValues pvs = new MutablePropertyValues(querySettingsMap);
                    QuerySettings settings = schema.getSettings(pvs, QueryView.DATAREGIONNAME_DEFAULT, queryName, viewName);
                    if (filters != null)
                        settings.addSortFilters(filters);

                    settings.setShowRows(ShowRows.ALL);

                    QueryView view = new QueryView(schema, settings, null);
                    views.add(view);
                }
            }
        }

        if (!views.isEmpty())
        {
            // Create meta data doc
            TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
            TablesType tablesXml = tablesDoc.addNewTables();

            // Insert standard comment explaining where the data lives, who exported it, and when
            XmlBeansUtil.addStandardExportComment(tablesXml, c, user);

            for (QueryView view : views)
            {
                TableInfo tableInfo = view.getTable();
                QueryDefinition queryDef = view.getQueryDef();
                DataRegion rgn = view.createDataView().getDataRegion();

                // Get list of all columns to export
                List<DisplayColumn> exportDisplayColumns = view.getExportColumns(rgn.getDisplayColumns());
                List<ColumnInfo> cols = new ArrayList<>(exportDisplayColumns.size());
                for (DisplayColumn dc : exportDisplayColumns)
                {
                    ColumnInfo col = dc.getColumnInfo();
                    if (col != null)
                        cols.add(col);
                }

                // Write meta data
                TableType tableXml = tablesXml.addNewTable();
                TableTableInfoWriter xmlWriter = new TableTableInfoWriter(tableInfo, queryDef, cols);
                xmlWriter.writeTable(tableXml);

                // Write data
                TSVGridWriter tsv = view.getTsvWriter();
                tsv.setDelimiterCharacter(TSVWriter.DELIM.TAB);
                tsv.setQuoteCharacter(TSVWriter.QUOTE.DOUBLE);
                if (header != null)
                    tsv.setColumnHeaderType(header);

                String name = view.getQueryDef().getName();
                PrintWriter out = dir.getPrintWriter(name + ".tsv");
                tsv.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
            }

            dir.saveXmlBean(SCHEMA_FILENAME, tablesDoc);
            return true;
        }
        else
        {
            return false;
        }
    }

    private static class TableTableInfoWriter extends TableInfoWriter
    {
        private final TableInfo _tableInfo;

        protected TableTableInfoWriter(TableInfo tableInfo, QueryDefinition def, Collection<ColumnInfo> columns)
        {
            super(def.getContainer(), tableInfo, columns);
            _tableInfo = tableInfo;
        }

        @Override
        public void writeTable(TableType tableXml)
        {
            super.writeTable(tableXml);
            if (_tableInfo.getPkColumnNames().size() > 0)
                tableXml.setPkColumnName(_tableInfo.getPkColumnNames().get(0));
        }

        @Override
        public void writeColumn(ColumnInfo column, ColumnType columnXml)
        {
            super.writeColumn(column, columnXml);

            String columnName = column.getName();

            if (_tableInfo.getPkColumnNames().size() > 0 && columnName.equals(_tableInfo.getPkColumnNames().get(0)))
            {
                columnXml.setIsKeyField(true);

                if (column.isAutoIncrement())
                    columnXml.setIsAutoInc(true);
            }
        }

        @Override  // TODO: Update this to match Dataset version?
        protected String getPropertyURI(ColumnInfo column)
        {
            return null;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test() throws IOException
        {
            TestContext testContext = TestContext.get();
            ExportTablesForm form = new ExportTablesForm();
            List<Map<String, Object>> queries = new ArrayList<>();
            queries.add(Collections.singletonMap(QueryParam.queryName.name(), "Containers"));

            Map<String, Object> users = new HashMap<>();
            users.put(QueryParam.queryName.name(), "Users");
            Map<String, Object> filterMap = new HashMap<>();
            filterMap.put("query.Email~doesnotcontain", "example.com");
            filterMap.put("query.sort", "DisplayName");
            users.put("filters", filterMap);

            queries.add(users);

            Map<String, List<Map<String, Object>>> schemas = new HashMap<>();
            schemas.put("core", queries);
            form.setSchemas(schemas);

            Container container = ContainerManager.getContainerService().getForPath("/");
            File file = FileUtil.getTempDirectory();

            try (ZipFile zip = new ZipFile(file, FileUtil.makeFileNameWithTimestamp("JunitTest", "tables.zip")))
            {
                TableWriter tableWriter = new TableWriter();
                tableWriter.write(container, testContext.getUser(), zip, form.getSchemas(), form.getHeaderType());
            }
        }
    }
}
