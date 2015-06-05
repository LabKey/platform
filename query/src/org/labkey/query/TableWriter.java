/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoWriter;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.ZipFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.query.controllers.QueryController.ExportTablesForm;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: davebradlee
 * Date: 12/17/12
 * Time: 4:08 PM
 */
public class TableWriter
{
    static final String SCHEMA_FILENAME = "metadata.xml";

    public boolean write(Container c, User user, VirtualFile dir) throws Exception
    {
        return write(c, user, dir, null);
    }

    public boolean write(Container c, User user, VirtualFile dir, ExportTablesForm form) throws Exception
    {
        QueryService queryService = QueryService.get();
        List<Pair<QueryDefinition, CustomView>> queries = new ArrayList<>();
        if (null == form || null == form.getSchemas() || form.getSchemas().size() == 0)
        {
            // If no form, get all user queries in container
            for (QueryDefinition queryDef : queryService.getQueryDefs(user, c))
                queries.add(Pair.<QueryDefinition, CustomView>of(queryDef, null));
        }
        else
        {
            Map<String, List<Map<String, String>>> schemaMap = form.getSchemas();

            for (String schemaName : schemaMap.keySet())
            {
                UserSchema schema = queryService.getUserSchema(user, c, schemaName);
                if (null == schema)
                    continue;
                Map<String, QueryDefinition> schemaQueries = schema.getQueryDefs();
                List<Map<String, String>> queryMaps = schemaMap.get(schemaName);
                if (queryMaps == null)
                {
                    // Export all queries in schema
                    for (Map.Entry<String, QueryDefinition> entry : schemaQueries.entrySet())
                        queries.add(Pair.<QueryDefinition, CustomView>of(entry.getValue(), null));
                }
                else
                {
                    for (Map<String, String> queryMap : queryMaps)
                    {
                        String queryName = queryMap.get(QueryParam.queryName.name());
                        String viewName = queryMap.get(QueryParam.viewName.name());

                        QueryDefinition queryDef = schemaQueries.get(queryName);    // user defined queries
                        if (null == queryDef)
                            queryDef = schema.getQueryDefForTable(queryName);       // builtins

                        if (null != queryDef)
                        {
                            CustomView view = queryDef.getCustomView(user, null, viewName);

                            queries.add(Pair.of(queryDef, view));
                        }
                    }
                }
            }
        }

        if (!queries.isEmpty())
        {
            // Create meta data doc
            TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
            TablesType tablesXml = tablesDoc.addNewTables();

            // Insert standard comment explaining where the data lives, who exported it, and when
            XmlBeansUtil.addStandardExportComment(tablesXml, c, user);

            for (Pair<QueryDefinition, CustomView> pair : queries)
            {
                QueryDefinition queryDef = pair.first;
                CustomView view = pair.second;

                UserSchema schema =  queryDef.getSchema();
                TableInfo tableInfo = schema.getTable(queryDef.getName());

                // Get list of all columns to export
                Collection<ColumnInfo> allColumns = getColumns(tableInfo, view);

                // Write meta data
                TableType tableXml = tablesXml.addNewTable();
                TableTableInfoWriter xmlWriter = new TableTableInfoWriter(tableInfo, queryDef, getColumnsToExport(tableInfo, allColumns, true, false));
                xmlWriter.writeTable(tableXml);

                // Write data
                Collection<ColumnInfo> columns = getColumnsToExport(tableInfo, allColumns, false, false);

                if (!columns.isEmpty())
                {
                    List<DisplayColumn> displayColumns = new LinkedList<>();

                    for (ColumnInfo col : columns)
                        displayColumns.add(new ExportDataColumn(col));

                    ActionURL url = new ActionURL();
                    SimpleFilter filter = new SimpleFilter();
                    Sort sort = new Sort();
                    if (view != null && view.hasFilterOrSort())
                    {
                        view.applyFilterAndSortToURL(url, QueryView.DATAREGIONNAME_DEFAULT);
                        filter.addUrlFilters(url, QueryView.DATAREGIONNAME_DEFAULT);
                        sort.addURLSort(url, QueryView.DATAREGIONNAME_DEFAULT);
                    }
                    else if (tableInfo != null && tableInfo.getPkColumnNames().size() > 0)
                    {
                        // Sort the data rows by PK, #11261
                        sort = new Sort(tableInfo.getPkColumnNames().get(0));
                    }

                    Results rs = QueryService.get().select(tableInfo, columns, filter, sort);
                    TSVGridWriter tsvWriter = new TSVGridWriter(rs, displayColumns);
                    tsvWriter.setApplyFormats(false);
                    if (form != null && form.getHeaderType() != null)
                        tsvWriter.setColumnHeaderType(form.getHeaderType());
                    PrintWriter out = dir.getPrintWriter(queryDef.getName() + ".tsv");
                    tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
                }
            }

            dir.saveXmlBean(SCHEMA_FILENAME, tablesDoc);

            return true;
        }
        else
        {
            return false;
        }
    }

    private Collection<ColumnInfo> getColumns(TableInfo tinfo, CustomView view)
    {
        if (view == null)
            return tinfo.getColumns();

        List<FieldKey> fields = view.getColumns();
        Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(tinfo, fields);
        return Collections.unmodifiableCollection(colMap.values());
    }

    private Collection<ColumnInfo> getColumnsToExport(TableInfo tinfo, Collection<ColumnInfo> columns, boolean metaData, boolean removeProtected)
    {
        Set<ColumnInfo> pks = new HashSet<>(tinfo.getPkColumns());

        List<ColumnInfo> ret = new ArrayList<>(columns.size());
        for (ColumnInfo column : columns)
        {
            /*
                We export:

                - All user-editable columns (meta data & values)
                - All primary keys, including the values of auto-increment key columns (meta data & values)
                - Other key columns (meta data only)
             */
            if ((column.isUserEditable() || pks.contains(column) || (metaData && column.isKeyField())))
            {
                // Exclude columns marked as Protected, if removeProtected is true (except key columns marked as protected, those must be exported)
                if (removeProtected && column.isProtected() && !pks.contains(column) && !column.isKeyField())
                    continue;

                ret.add(column);

                // If the column is MV enabled, export the data in the indicator column as well
                if (!metaData && column.isMvEnabled())
                    ret.add(tinfo.getColumn(column.getMvColumnName()));
            }
        }

        return ret;
    }


    // We just want the underlying value, not the lookup
    private static class ExportDataColumn extends DataColumn
    {
        private ExportDataColumn(ColumnInfo col)
        {
            super(col);
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            return getValue(ctx);
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
            else
            {
//                PropertyType propType = _properties.get(columnName).getPropertyDescriptor().getPropertyType();

//                if (propType == PropertyType.ATTACHMENT)
//                    columnXml.setDatatype(propType.getXmlName());
            }
        }

        @Override  // TODO: Update this to match Dataset version?
        protected String getPropertyURI(ColumnInfo column)
        {
            //String propertyURI = column.getPropertyURI();
            //if (propertyURI != null && !propertyURI.startsWith(_domain.getTypeURI()) && !propertyURI.startsWith(ColumnInfo.DEFAULT_PROPERTY_URI_PREFIX))
            //    return propertyURI;

            return null;
        }
    }

    public static class TestCase extends Assert
    {

        @Test
        public void test()
        {
            TestContext testContext = TestContext.get();
            ExportTablesForm form = new ExportTablesForm();
            List<Map<String, String>> queries = new ArrayList<>();
            queries.add(Collections.singletonMap(QueryParam.queryName.name(), "Containers"));
            queries.add(Collections.singletonMap(QueryParam.queryName.name(), "Users"));
            Map<String, List<Map<String, String>>> schemas = new HashMap<>();
            schemas.put("core", queries);
            form.setSchemas(schemas);

            try
            {
                Container container = ContainerManager.getContainerService().getForPath("/");
                File file = FileUtil.getTempDirectory();

                try (ZipFile zip = new ZipFile(file, FileUtil.makeFileNameWithTimestamp("JunitTest", "tables.zip")))
                {
                    TableWriter tableWriter = new TableWriter();
                    tableWriter.write(container, testContext.getUser(), zip, form);
                }
            }
            catch (Exception e)
            {
                assertTrue(false);
            }
        }
    }
}
