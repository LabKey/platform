/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;

import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: davebradlee
 * Date: 12/17/12
 * Time: 4:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class TableWriter
{
    static final String SCHEMA_FILENAME = "metadata.xml";

    public boolean write(Container c, User user, VirtualFile dir) throws Exception
    {
        return write(c, user, dir, null);
    }

    public boolean write(Container c, User user, VirtualFile dir, ImportContext ctx) throws Exception
    {
        List<QueryDefinition> queries = QueryService.get().getQueryDefs(user, c);

        if (!queries.isEmpty())
        {
            // Create meta data doc
            TablesDocument tablesDoc = TablesDocument.Factory.newInstance();
            TablesType tablesXml = tablesDoc.addNewTables();

            // Insert standard comment explaining where the data lives, who exported it, and when
            XmlBeansUtil.addStandardExportComment(tablesXml, c, user);

            for (QueryDefinition queryDef : queries)
            {
                UserSchema schema =  queryDef.getSchema();
                TableInfo tableInfo = schema.getTable(queryDef.getName());

                // Write meta data
                TableType tableXml = tablesXml.addNewTable();
                ListTableInfoWriter xmlWriter = new ListTableInfoWriter(tableInfo, queryDef, getColumnsToExport(tableInfo, true, false));
                xmlWriter.writeTable(tableXml);

                // Write data
                Collection<ColumnInfo> columns = getColumnsToExport(tableInfo, false, false);

                if (!columns.isEmpty())
                {
                    List<DisplayColumn> displayColumns = new LinkedList<DisplayColumn>();

                    for (ColumnInfo col : columns)
                        displayColumns.add(new ExportDataColumn(col));

                    // Sort the data rows by PK, #11261
                    Sort sort = tableInfo.getPkColumnNames().size() == 0 ? null : new Sort(tableInfo.getPkColumnNames().get(0));

                    try
                    {
                        Results rs = QueryService.get().select(tableInfo, columns, null, sort);
                        TSVGridWriter tsvWriter = new TSVGridWriter(rs, displayColumns);
                        tsvWriter.setApplyFormats(false);
                        tsvWriter.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.queryColumnName);
                        PrintWriter out = dir.getPrintWriter(queryDef.getName() + ".tsv");
                        tsvWriter.write(out);     // NOTE: TSVGridWriter closes PrintWriter and ResultSet
                    }
                    catch (Exception e)
                    {
                        // Could be a bad user query
                        int i = 1;
                    }
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

    private Collection<ColumnInfo> getColumnsToExport(TableInfo tinfo, boolean metaData, boolean removeProtected)
    {
        Collection<ColumnInfo> columns = new LinkedHashSet<ColumnInfo>();
        Set<ColumnInfo> pks = new HashSet<ColumnInfo>(tinfo.getPkColumns());

        for (ColumnInfo column : tinfo.getColumns())
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

                columns.add(column);

                // If the column is MV enabled, export the data in the indicator column as well
                if (!metaData && column.isMvEnabled())
                    columns.add(tinfo.getColumn(column.getMvColumnName()));
            }
        }

        return columns;
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

    private static class ListTableInfoWriter extends org.labkey.api.data.TableInfoWriter
    {
        private final QueryDefinition _def;
        private final TableInfo _tableInfo;
        private final Map<String, DomainProperty> _properties = new HashMap<String, DomainProperty>();
        private Domain _domain;

        protected ListTableInfoWriter(TableInfo tableInfo, QueryDefinition def, Collection <ColumnInfo> columns)
        {
            super(tableInfo, columns, null);
            _def = def;
            _tableInfo = tableInfo;
     //       _domain = _def.get

//            for (DomainProperty prop : _domain.getProperties())
//                _properties.put(prop.getName(), prop);
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
}
