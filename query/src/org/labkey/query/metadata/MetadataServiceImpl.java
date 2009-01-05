/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.query.metadata;

import org.labkey.query.metadata.client.MetadataService;
import org.labkey.query.metadata.client.GWTTableInfo;
import org.labkey.query.metadata.client.GWTColumnInfo;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.security.ACL;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.ColumnType;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;

import java.util.*;
import java.sql.SQLException;

public class MetadataServiceImpl extends DomainEditorServiceBase implements MetadataService
{
    public MetadataServiceImpl(ViewContext context)
    {
        super(context);
    }

    public GWTTableInfo getMetadata(String schemaName, String tableName)
    {
        Map<String, GWTColumnInfo> columnInfos = new CaseInsensitiveHashMap<GWTColumnInfo>();
        List<GWTColumnInfo> orderedPDs = new ArrayList<GWTColumnInfo>();
        Set<String> injectedColumnNames = new CaseInsensitiveHashSet();
        GWTTableInfo gwtTableInfo = new GWTTableInfo();
        gwtTableInfo.setName(tableName);

        UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), schemaName);
        if (schema == null)
        {
            return null;
        }
        TableInfo table = schema.getTable(tableName, "alias");
        if (table == null)
        {
            return null;
        }
        
        for (ColumnInfo columnInfo : table.getColumns())
        {
            GWTColumnInfo gwtColumnInfo = new GWTColumnInfo();
            gwtColumnInfo.setPropertyId(-1);
            gwtColumnInfo.setName(columnInfo.getName());
            columnInfos.put(gwtColumnInfo.getName(), gwtColumnInfo);
            orderedPDs.add(gwtColumnInfo);

            gwtColumnInfo.setRequired(!columnInfo.isNullable());
            gwtColumnInfo.setLabel(columnInfo.getCaption());
            gwtColumnInfo.setFormat(columnInfo.getFormatString());
            gwtColumnInfo.setDescription(columnInfo.getDescription());
            gwtColumnInfo.setRangeURI(PropertyType.getFromClass(columnInfo.getJavaObjectClass()).getTypeUri());
            if (columnInfo.getFk() != null)
            {
                ForeignKey fk = columnInfo.getFk();
                if (fk.getLookupSchemaName() == null || fk.getLookupTableName() == null)
                {
                    gwtColumnInfo.setLookupCustom(true);
                }
                else
                {
                    gwtColumnInfo.setLookupSchema(fk.getLookupSchemaName());
                    gwtColumnInfo.setLookupQuery(fk.getLookupTableName());
                }
            }
        }

        QueryDef queryDef = QueryManager.get().getQueryDef(schema.getContainer(), schema.getSchemaName(), tableName, false);
        if (queryDef != null)
        {
            TableType tableType = getTableType(tableName, parseDocument(queryDef.getMetaData()));
            if (tableType != null)
            {
                if (tableType.getColumns() != null)
                {
                    for (ColumnType column : tableType.getColumns().getColumnArray())
                    {
                        GWTColumnInfo gwtColumnInfo = columnInfos.get(column.getColumnName());
                        if (gwtColumnInfo == null)
                        {
                            gwtColumnInfo = new GWTColumnInfo();
                            gwtColumnInfo.setPropertyId(-1);
                            gwtColumnInfo.setName(column.getColumnName());
                            columnInfos.put(gwtColumnInfo.getName(), gwtColumnInfo);
                            orderedPDs.add(gwtColumnInfo);
                        }
                        if (column.isSetColumnTitle())
                        {
                            gwtColumnInfo.setLabel(column.getColumnTitle());
                        }
                        if (column.isSetDescription())
                        {
                            gwtColumnInfo.setDescription(column.getDescription());
                        }
                        if (column.isSetFormatString())
                        {
                            gwtColumnInfo.setFormat(column.getFormatString());
                        }
                        if (column.getFk() != null)
                        {
                            gwtColumnInfo.setLookupQuery(column.getFk().getFkTable());
                            gwtColumnInfo.setLookupSchema(column.getFk().getFkDbSchema());
                        }
                        if (column.getWrappedColumnName() != null)
                        {
                            injectedColumnNames.add(column.getColumnName());
                            gwtColumnInfo.setWrappedColumnName(column.getWrappedColumnName());
                            ColumnInfo tableColumn = table.getColumn(column.getWrappedColumnName());
                            if (tableColumn != null)
                            {
                                gwtColumnInfo.setRangeURI(PropertyType.getFromClass(tableColumn.getJavaObjectClass()).getTypeUri());
                            }
                        }
                        else
                        {
                            ColumnInfo tableColumn = table.getColumn(column.getColumnName());
                            if (tableColumn != null)
                            {
                                gwtColumnInfo.setRangeURI(PropertyType.getFromClass(tableColumn.getJavaObjectClass()).getTypeUri());
                            }
                        }
                    }
                }
            }
        }

        Set<String> builtInColumnNames = new CaseInsensitiveHashSet(columnInfos.keySet());
        builtInColumnNames.removeAll(injectedColumnNames);
        gwtTableInfo.setMandatoryFieldNames(builtInColumnNames);
        gwtTableInfo.setFields(orderedPDs);
        return gwtTableInfo;
    }

    private TableType getTableType(String name, TablesDocument doc)
    {
        if (doc.getTables() != null)
        {
            TablesDocument.Tables tables = doc.getTables();
            for (TableType tableType : tables.getTableArray())
            {
                if (name.equalsIgnoreCase(tableType.getTableName()))
                {
                    return tableType;
                }
            }
        }
        return null;
    }

    private TablesDocument parseDocument(String xml)
    {
        if (xml == null)
        {
            return null;
        }
        
        TablesDocument doc;
        try
        {
            doc = TablesDocument.Factory.parse(xml);
        }
        catch (XmlException e)
        {
            throw new RuntimeException(e);
        }
        return doc;
    }

    public GWTTableInfo saveMetadata(GWTTableInfo gwtTableInfo, String schemaName)
    {
        validatePermissions();

        UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), schemaName);
        QueryDef queryDef = QueryManager.get().getQueryDef(schema.getContainer(), schema.getSchemaName(), gwtTableInfo.getName(), false);
        TableInfo rawTableInfo = schema.getTable(gwtTableInfo.getName(), "alias", false);

        TablesDocument doc;
        TableType xmlTable = null; 

        if (queryDef != null)
        {
            doc = parseDocument(queryDef.getMetaData());
            xmlTable = getTableType(gwtTableInfo.getName(), doc);
        }
        else
        {
            queryDef = new QueryDef();
            queryDef.setSchema(schemaName);
            queryDef.setContainer(getViewContext().getContainer().getId());
            queryDef.setName(gwtTableInfo.getName());
            doc = TablesDocument.Factory.newInstance();
        }

        if (xmlTable == null)
        {
            TablesDocument.Tables tables = doc.addNewTables();
            xmlTable = tables.addNewTable();
            xmlTable.setTableName(gwtTableInfo.getName());
        }

        if (xmlTable.getColumns() == null)
        {
            xmlTable.addNewColumns();
        }

        if (xmlTable.getTableDbType() == null)
        {
            xmlTable.setTableDbType("NOT_IN_DB");
        }

        Map<String, ColumnType> columnsToDelete = new CaseInsensitiveHashMap<ColumnType>();
        for (ColumnType columnType : xmlTable.getColumns().getColumnArray())
        {
            // Remember all the columns in the metadata overrides so that we can delete any that the user
            // has removed completely.
            columnsToDelete.put(columnType.getColumnName(), columnType);
        }


        for (GWTColumnInfo gwtColumnInfo : gwtTableInfo.getFields())
        {
            ColumnType xmlColumn = columnsToDelete.get(gwtColumnInfo.getName());
            ColumnInfo rawColumnInfo = rawTableInfo.getColumn(gwtColumnInfo.getName());
            if (rawColumnInfo == null)
            {
                rawColumnInfo = new ColumnInfo((String)null);
            }

            if (xmlColumn != null)
            {
                // Still valid, don't delete it from the metadata overrides
                columnsToDelete.remove(gwtColumnInfo.getName());
            }
            else
            {
                // This column was not in the overrides before, so add it now
                xmlColumn = xmlTable.getColumns().addNewColumn();
                xmlColumn.setColumnName(gwtColumnInfo.getName());

                if (gwtColumnInfo.getWrappedColumnName() != null)
                {
                    // This is a newly created column that wraps another column
                    xmlColumn.setWrappedColumnName(gwtColumnInfo.getWrappedColumnName());
                }
            }

            // Set the description
            if (shouldStoreValue(gwtColumnInfo.getDescription(), rawColumnInfo.getDescription()))
            {
                xmlColumn.setDescription(gwtColumnInfo.getDescription());
            }
            else if (xmlColumn.isSetDescription())
            {
                xmlColumn.unsetDescription();
            }

            // Set the format
            if (shouldStoreValue(gwtColumnInfo.getFormat(), rawColumnInfo.getFormatString()))
            {
                xmlColumn.setFormatString(gwtColumnInfo.getFormat());
            }
            else if (xmlColumn.isSetFormatString())
            {
                xmlColumn.unsetFormatString();
            }

            // Set the label
            if (shouldStoreValue(gwtColumnInfo.getLabel(), rawColumnInfo.getCaption()))
            {
                xmlColumn.setColumnTitle(gwtColumnInfo.getLabel());
            }
            else if (xmlColumn.isSetColumnTitle())
            {
                xmlColumn.unsetColumnTitle();
            }

            // Set the FK
            if (!gwtColumnInfo.isLookupCustom() && gwtColumnInfo.getLookupQuery() != null && gwtColumnInfo.getLookupSchema() != null)
            {
                UserSchema fkSchema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), gwtColumnInfo.getLookupSchema());
                if (fkSchema != null)
                {
                    TableInfo fkTableInfo = fkSchema.getTable(gwtColumnInfo.getLookupQuery(), "alias");
                    if (fkTableInfo != null)
                    {
                        List<String> pkCols = fkTableInfo.getPkColumnNames();
                        if (pkCols.size() == 1)
                        {
                            ColumnType.Fk fk = xmlColumn.getFk();
                            if (fk == null)
                            {
                                fk = xmlColumn.addNewFk();
                            }
                            fk.setFkDbSchema(gwtColumnInfo.getLookupSchema());
                            fk.setFkTable(gwtColumnInfo.getLookupQuery());
                            fk.setFkColumnName(pkCols.get(0));
                        }
                    }
                }
            }
            else if (xmlColumn.isSetFk())
            {
                xmlColumn.unsetFk();
            }

            if (!xmlColumn.isSetDescription() &&
                    !xmlColumn.isSetFk() &&
                    !xmlColumn.isSetFormatString() &&
                    !xmlColumn.isSetWrappedColumnName() &&
                    !xmlColumn.isSetColumnTitle())
            {
                removeColumn(xmlTable, xmlColumn);
            }
        }


        // Yank out the columns that were in the metadata that aren't in the list from the client
        for (ColumnType columnType : columnsToDelete.values())
        {
            removeColumn(xmlTable, columnType);
        }

        XmlOptions xmlOptions = new XmlOptions();
        xmlOptions.setSavePrettyPrint();
        queryDef.setMetaData(doc.xmlText(xmlOptions));
        try
        {
            if (queryDef.getQueryDefId() == 0)
            {
                QueryManager.get().insert(getViewContext().getUser(), queryDef);
            }
            else
            {
                QueryManager.get().update(getViewContext().getUser(), queryDef);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return getMetadata(schemaName, gwtTableInfo.getName());
    }

    private void validatePermissions()
    {
        if (!getViewContext().hasPermission(ACL.PERM_ADMIN))
        {
            throw new IllegalStateException("You do not have permissions to modify the metadata");
        }
    }

    public GWTTableInfo resetToDefault(String schemaName, String queryName)
    {
        validatePermissions();

        QueryDef queryDef = QueryManager.get().getQueryDef(getViewContext().getContainer(), schemaName, queryName, false);
        if (queryDef != null)
        {
            try
            {
                QueryManager.get().delete(getViewContext().getUser(), queryDef);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        return getMetadata(schemaName, queryName);
    }

    private void removeColumn(TableType tableType, ColumnType columnType)
    {
        for (int i = 0; i < tableType.getColumns().getColumnArray().length; i++)
        {
            if (tableType.getColumns().getColumnArray(i) == columnType)
            {
                tableType.getColumns().removeColumn(i);
                break;
            }
        }
    }

    private boolean shouldStoreValue(String userValue, String defaultValue)
    {
        return userValue != null && !userValue.equals(defaultValue);
    }
}