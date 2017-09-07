/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.gwt.client.model.GWTConditionalFormat;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.DefaultScaleType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.query.QueryServiceImpl;
import org.labkey.query.metadata.client.GWTColumnInfo;
import org.labkey.query.metadata.client.GWTTableInfo;
import org.labkey.query.metadata.client.MetadataService;
import org.labkey.query.metadata.client.MetadataUnavailableException;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MetadataServiceImpl extends DomainEditorServiceBase implements MetadataService
{
    public MetadataServiceImpl(ViewContext context)
    {
        super(context);
    }

    public GWTTableInfo getMetadata(String schemaName, String tableName) throws MetadataUnavailableException
    {
        Map<String, GWTColumnInfo> columnInfos = new CaseInsensitiveHashMap<>();
        List<GWTColumnInfo> orderedPDs = new ArrayList<>();
        Set<String> injectedColumnNames = new CaseInsensitiveHashSet();
        GWTTableInfo gwtTableInfo = new GWTTableInfo();
        gwtTableInfo.setSchemaName(schemaName);
        gwtTableInfo.setQueryName(tableName);
        gwtTableInfo.setName(tableName);

        UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), schemaName);
        if (schema == null)
        {
            return null;
        }
        TableInfo table;
        try
        {
            table = schema.getTable(tableName);
            Domain domain = table.getDomain();
            if (domain != null)
                gwtTableInfo.setProvisioned(domain.isProvisioned());
        }
        catch (QueryParseException e)
        {
            throw new MetadataUnavailableException(e.getMessage());
        }
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
            gwtColumnInfo.setLabel(columnInfo.getLabel());
            gwtColumnInfo.setFormat(columnInfo.getFormat());
            gwtColumnInfo.setHidden(columnInfo.isHidden());
            gwtColumnInfo.setShownInDetailsView(columnInfo.isShownInDetailsView());
            gwtColumnInfo.setShownInInsertView(columnInfo.isShownInInsertView());
            gwtColumnInfo.setShownInUpdateView(columnInfo.isShownInUpdateView());
            gwtColumnInfo.setDimension(columnInfo.isDimension());
            gwtColumnInfo.setMeasure(columnInfo.isMeasure());
            gwtColumnInfo.setRecommendedVariable(columnInfo.isRecommendedVariable());
            gwtColumnInfo.setDefaultScale(columnInfo.getDefaultScale().name());
            /* NOTE: explicitly not supporting this metadata via this pathway, do not uncomment
            gwtColumnInfo.setPHI(columnInfo.getPHI().name());*/
            gwtColumnInfo.setExcludeFromShifting(columnInfo.isExcludeFromShifting());
            gwtColumnInfo.setURL(columnInfo.getURL() == null ? null : columnInfo.getURL().toString());
            gwtColumnInfo.setRangeURI(PropertyType.getFromClass(columnInfo.getJavaObjectClass()).getTypeUri());
            if (columnInfo.getFk() != null)
            {
                ForeignKey fk = columnInfo.getFk();
                TableInfo lookupTarget = null;
                try
                {
                    lookupTarget = fk.getLookupTableInfo();
                }
                catch (QueryParseException ignored)
                {
                    // Be tolerant of problematic lookup targets
                }
                if (fk.getLookupSchemaName() == null || fk.getLookupTableName() == null)
                {
                    if (lookupTarget != null && lookupTarget.isPublic() && lookupTarget.getPublicSchemaName() != null && lookupTarget.getPublicName() != null)
                    {
                        gwtColumnInfo.setLookupSchema(lookupTarget.getPublicSchemaName());
                        gwtColumnInfo.setLookupQuery(lookupTarget.getPublicName());
                    }
                    else
                    {
                        gwtColumnInfo.setLookupCustom(true);
                    }
                }
                else
                {
                    gwtColumnInfo.setLookupSchema(fk.getLookupSchemaName());
                    gwtColumnInfo.setLookupQuery(fk.getLookupTableName());
                }
                // Set the lookup's container if it targets some other container
                if (lookupTarget != null && lookupTarget.getUserSchema() != null && !lookupTarget.getUserSchema().getContainer().equals(getViewContext().getContainer()))
                {
                    gwtColumnInfo.setLookupContainer(lookupTarget.getUserSchema().getContainer().getPath());
                }
            }
            List<GWTConditionalFormat> formats = convertToGWT(columnInfo.getConditionalFormats());
            gwtColumnInfo.setConditionalFormats(formats);
        }

        List<QueryDef> queryDefs = QueryServiceImpl.get().findMetadataOverrideImpl(schema, tableName, false, false, null);
        if (queryDefs == null)
        {
            queryDefs = QueryServiceImpl.get().findMetadataOverrideImpl(schema, tableName, true, false, null);
            if (queryDefs != null)
            {
                gwtTableInfo.setUserDefinedQuery(true);
            }
        }

        if (queryDefs != null && !queryDefs.isEmpty())
        {
            // Use the last QueryDef's metadata -- this should be the user's metadata override in the database if it exists
            QueryDef queryDef = queryDefs.get(queryDefs.size()-1);

            if (!getContainer().getId().equals(queryDef.getContainerId()))
            {
                Container c = ContainerManager.getForId(queryDef.getContainerId());
                if (c != null)
                {
                    gwtTableInfo.setDefinitionFolder(c.getPath());
                }
            }
            TablesDocument doc = null;
            try
            {
                doc = parseDocument(queryDef.getMetaData());
            }
            catch (XmlException e)
            {
                // Just ignore the metadata override if it doesn't parse correctly
            }
            TableType tableType = getTableType(tableName, doc);
            if (tableType != null)
            {
                if (tableType.getColumns() != null)
                {
                    for (ColumnType column : tableType.getColumns().getColumnArray())
                    {
                        GWTColumnInfo gwtColumnInfo = columnInfos.get(column.getColumnName());
                        if (gwtColumnInfo == null)
                        {
                            // Omit columns that are in the XML but are no longer in the underlying table/query
                            break;
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
                        if (column.isSetShownInDetailsView())
                        {
                            gwtColumnInfo.setShownInDetailsView(column.getShownInDetailsView());
                        }
                        if (column.isSetDimension())
                        {
                            gwtColumnInfo.setDimension(column.getDimension());
                        }
                        if (column.isSetMeasure())
                        {
                            gwtColumnInfo.setMeasure(column.getMeasure());
                        }
                        if (column.isSetRecommendedVariable())
                        {
                            gwtColumnInfo.setRecommendedVariable(column.getRecommendedVariable());
                        }
                        else if (column.isSetKeyVariable())
                        {
                            gwtColumnInfo.setRecommendedVariable(column.getKeyVariable());
                        }
                        if (column.isSetDefaultScale())
                        {
                            gwtColumnInfo.setDefaultScale(column.getDefaultScale().toString());
                        }
                        /* NOTE: explicitly not supporting this metadata via this pathway, do not uncomment
                        if (column.isSetPhi())
                        {
                            gwtColumnInfo.setPHI(column.getPhi().toString());
                        }*/
                        if (column.isSetExcludeFromShifting())
                        {
                            gwtColumnInfo.setExcludeFromShifting(column.getExcludeFromShifting());
                        }
                        if (column.isSetIsHidden())
                        {
                            gwtColumnInfo.setHidden(column.getIsHidden());
                        }
                        if (column.isSetShownInInsertView())
                        {
                            gwtColumnInfo.setShownInInsertView(column.getShownInInsertView());
                        }
                        if (column.isSetShownInUpdateView())
                        {
                            gwtColumnInfo.setShownInUpdateView(column.getShownInUpdateView());
                        }
                        if (column.getFk() != null)
                        {
                            gwtColumnInfo.setLookupQuery(column.getFk().getFkTable());
                            gwtColumnInfo.setLookupSchema(column.getFk().getFkDbSchema());
                        }
                        if (column.isSetConditionalFormats())
                        {
                            List<ConditionalFormat> serverFormats = ConditionalFormat.convertFromXML(column.getConditionalFormats());
                            List<GWTConditionalFormat> gwtFormats = new ArrayList<>();
                            for (ConditionalFormat serverFormat : serverFormats)
                            {
                                gwtFormats.add(new GWTConditionalFormat(serverFormat));
                            }
                            gwtColumnInfo.setConditionalFormats(gwtFormats);
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
                            else
                            {
                                injectedColumnNames.add(column.getColumnName());
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

    private List<GWTConditionalFormat> convertToGWT(List<ConditionalFormat> formats)
    {
        List<GWTConditionalFormat> result = new ArrayList<>();
        for (ConditionalFormat format : formats)
        {
            result.add(new GWTConditionalFormat(format));
        }
        return result;
    }

    private TableType getTableType(String name, TablesDocument doc)
    {
        if (doc != null && doc.getTables() != null)
        {
            TablesType tables = doc.getTables();
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

    private TablesDocument parseDocument(String xml) throws XmlException
    {
        if (xml == null)
        {
            return null;
        }
        
        return TablesDocument.Factory.parse(xml);
    }

    public GWTTableInfo saveMetadata(GWTTableInfo gwtTableInfo, String schemaName) throws MetadataUnavailableException
    {
        validatePermissions();

        UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), schemaName);
        QueryDef queryDef = QueryManager.get().getQueryDef(schema.getContainer(), schema.getSchemaName(), gwtTableInfo.getName(), gwtTableInfo.isUserDefinedQuery());
        TableInfo rawTableInfo = schema.getTable(gwtTableInfo.getName(), false);

        TablesDocument doc = null;
        TableType xmlTable = null; 

        if (queryDef != null)
        {
            try
            {
                doc = parseDocument(queryDef.getMetaData());
            }
            catch (XmlException e)
            {
                throw new MetadataUnavailableException(e.getMessage());
            }
            xmlTable = getTableType(gwtTableInfo.getName(), doc);
        }
        else
        {
            queryDef = new QueryDef();
            queryDef.setSchema(schemaName);
            queryDef.setContainer(getViewContext().getContainer().getId());
            queryDef.setName(gwtTableInfo.getName());
        }

        if (doc == null)
        {
            doc = TablesDocument.Factory.newInstance();
        }
        
        if (xmlTable == null)
        {
            TablesType tables = doc.addNewTables();
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

        Map<String, ColumnType> columnsToDelete = new CaseInsensitiveHashMap<>();
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
                // Establish the type of the column
                if (gwtColumnInfo.getWrappedColumnName() != null)
                {
                    ColumnInfo columnToBeWrapped = rawTableInfo.getColumn(gwtColumnInfo.getWrappedColumnName());
                    if (columnToBeWrapped == null)
                    {
                        continue;
                    }
                    rawColumnInfo.setJdbcType(columnToBeWrapped.getJdbcType());
                }
                else
                {
                    throw new MetadataUnavailableException("No such column: " + gwtColumnInfo.getName());
                }
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
            if (shouldStoreValue(gwtColumnInfo.getFormat(), rawColumnInfo.getFormat()))
            {
                xmlColumn.setFormatString(gwtColumnInfo.getFormat());
            }
            else if (xmlColumn.isSetFormatString())
            {
                xmlColumn.unsetFormatString();
            }

            // Set visibility info
            if (gwtColumnInfo.isHidden() != rawColumnInfo.isHidden())
            {
                xmlColumn.setIsHidden(gwtColumnInfo.isHidden());
            }
            else if (xmlColumn.isSetIsHidden())
            {
                xmlColumn.unsetIsHidden();
            }
            if (gwtColumnInfo.isShownInInsertView() != rawColumnInfo.isShownInInsertView())
            {
                xmlColumn.setShownInInsertView(gwtColumnInfo.isShownInInsertView());
            }
            else if (xmlColumn.isSetShownInInsertView())
            {
                xmlColumn.unsetShownInInsertView();
            }
            if (gwtColumnInfo.isShownInUpdateView() != rawColumnInfo.isShownInUpdateView())
            {
                xmlColumn.setShownInUpdateView(gwtColumnInfo.isShownInUpdateView());
            }
            else if (xmlColumn.isSetShownInUpdateView())
            {
                xmlColumn.unsetShownInUpdateView();
            }
            if (gwtColumnInfo.isShownInDetailsView() != rawColumnInfo.isShownInDetailsView())
            {
                xmlColumn.setShownInDetailsView(gwtColumnInfo.isShownInDetailsView());
            }
            else if (xmlColumn.isSetShownInDetailsView())
            {
                xmlColumn.unsetShownInDetailsView();
            }
            if (gwtColumnInfo.isMeasure() != rawColumnInfo.isMeasure())
            {
                xmlColumn.setMeasure(gwtColumnInfo.isMeasure());
            }
            else if (xmlColumn.isSetMeasure())
            {
                xmlColumn.unsetMeasure();
            }

            if (gwtColumnInfo.isDimension() != rawColumnInfo.isDimension())
            {
                xmlColumn.setDimension(gwtColumnInfo.isDimension());
            }
            else if (xmlColumn.isSetDimension())
            {
                xmlColumn.unsetDimension();
            }

            if (gwtColumnInfo.isRecommendedVariable() != rawColumnInfo.isRecommendedVariable())
            {
                xmlColumn.setRecommendedVariable(gwtColumnInfo.isRecommendedVariable());
            }
            else if (xmlColumn.isSetRecommendedVariable())
            {
                xmlColumn.unsetRecommendedVariable();
            }

            if (!StringUtils.equals(gwtColumnInfo.getDefaultScale(), rawColumnInfo.getDefaultScale().name()))
            {
                xmlColumn.setDefaultScale(DefaultScaleType.Enum.forString(gwtColumnInfo.getDefaultScale()));
            }
            else if (xmlColumn.isSetDefaultScale())
            {
                xmlColumn.unsetDefaultScale();
            }

            /* NOTE: explicitly not supporting this metadata via this pathway, do not uncomment

            if (!StringUtils.equals(gwtColumnInfo.getPHI(), rawColumnInfo.getPHI().name()))
            {
                xmlColumn.setPhi(PHIType.Enum.forString(gwtColumnInfo.getPHI()));
            }
            else if (xmlColumn.isSetPhi())
            {
                xmlColumn.unsetPhi();
            }*/

            if (gwtColumnInfo.isExcludeFromShifting() != rawColumnInfo.isExcludeFromShifting())
            {
                xmlColumn.setExcludeFromShifting(gwtColumnInfo.isExcludeFromShifting());
            }
            else if (xmlColumn.isSetExcludeFromShifting())
            {
                xmlColumn.unsetExcludeFromShifting();
            }

            // Set the label
            if (shouldStoreValue(gwtColumnInfo.getLabel(), rawColumnInfo.getLabel()))
            {
                xmlColumn.setColumnTitle(gwtColumnInfo.getLabel());
            }
            else if (xmlColumn.isSetColumnTitle())
            {
                xmlColumn.unsetColumnTitle();
            }

            // Set the URL
            String originalURL = rawColumnInfo.getURL() == null ? null : rawColumnInfo.getURL().toString();
            if (shouldStoreValue(gwtColumnInfo.getURL(), originalURL))
            {
                if (gwtColumnInfo.getURL() != null)
                {
                    try
                    {
                        StringExpression expr = StringExpressionFactory.createURL(gwtColumnInfo.getURL());
                        xmlColumn.setUrl(expr.toXML());
                    }
                    catch (Exception e)
                    {
                        throw new MetadataUnavailableException(e.getMessage());
                    }
                }
            }
            else if (xmlColumn.isSetUrl())
            {
                xmlColumn.unsetUrl();
            }

            // Set the FK
            if (!gwtColumnInfo.isLookupCustom() && gwtColumnInfo.getLookupQuery() != null && gwtColumnInfo.getLookupSchema() != null)
            {
                ForeignKey rawFK = rawColumnInfo.getFk();
                // Check if it's the same FK, based on schema, query, and container
                String rawTargetContainer = (rawFK == null || rawFK.getLookupContainer() == null) ? null : rawFK.getLookupContainer().getPath();
                if (rawFK == null ||
                    !gwtColumnInfo.getLookupSchema().equals(rawFK.getLookupSchemaName()) ||
                    !Objects.equals(gwtColumnInfo.getLookupContainer(), rawTargetContainer) ||
                    !gwtColumnInfo.getLookupQuery().equals(rawFK.getLookupTableName()))
                {
                    Container targetContainer = gwtColumnInfo.getLookupContainer() != null ? ContainerManager.getForPath(gwtColumnInfo.getLookupContainer()) : null;
                    UserSchema fkSchema = QueryService.get().getUserSchema(getViewContext().getUser(), targetContainer == null ? getViewContext().getContainer() : targetContainer, gwtColumnInfo.getLookupSchema());
                    if (fkSchema != null)
                    {
                        TableInfo fkTableInfo = fkSchema.getTable(gwtColumnInfo.getLookupQuery());
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
                                if (targetContainer != null)
                                {
                                    fk.setFkFolderPath(targetContainer.getPath());
                                }
                            }
                        }
                    }
                }
                else if (xmlColumn.isSetFk())
                {
                    xmlColumn.unsetFk();
                }
            }
            else if (xmlColumn.isSetFk())
            {
                xmlColumn.unsetFk();
            }

            // Always clear it out the conditional formats if they've been set
            if (xmlColumn.isSetConditionalFormats())
            {
                xmlColumn.unsetConditionalFormats();
            }
            // Set the conditional formats
            if (shouldStoreValue(gwtColumnInfo.getConditionalFormats(), convertToGWT(rawColumnInfo.getConditionalFormats())))
            {
                ConditionalFormat.convertToXML(gwtColumnInfo.getConditionalFormats(), xmlColumn);
            }

            if (xmlColumn.getWrappedColumnName() == null)
            {
                NodeList childNodes = xmlColumn.getDomNode().getChildNodes();
                // May be empty, or may have empty text between the start and end tags
                if (childNodes.getLength() == 0 ||
                    (childNodes.getLength() == 1 && childNodes.item(0) instanceof Text && ((Text)childNodes.item(0)).getData().trim().length() == 0))
                {
                    // Remove columns that no longer have any metadata set on them
                    removeColumn(xmlTable, xmlColumn);
                }
            }
        }


        // Yank out the columns that were in the metadata that aren't in the list from the client
        for (ColumnType columnType : columnsToDelete.values())
        {
            removeColumn(xmlTable, columnType);
        }

        XmlOptions xmlOptions = new XmlOptions();
        xmlOptions.setSavePrettyPrint();
        // Don't use an explicit namespace, making the XML much more readable
        xmlOptions.setUseDefaultNamespace();
        queryDef.setMetaData(doc.xmlText(xmlOptions));
        if (queryDef.getQueryDefId() == 0)
        {
            QueryManager.get().insert(getViewContext().getUser(), queryDef);
        }
        else
        {
            QueryManager.get().update(getViewContext().getUser(), queryDef);
        }

        return getMetadata(schemaName, gwtTableInfo.getName());
    }

    private void validatePermissions()
    {
        if (!getViewContext().hasPermission(AdminPermission.class))
        {
            throw new UnauthorizedException("You do not have permissions to modify the metadata");
        }
    }

    public GWTTableInfo resetToDefault(String schemaName, String queryName) throws MetadataUnavailableException
    {
        validatePermissions();

        QueryDef queryDef = QueryManager.get().getQueryDef(getViewContext().getContainer(), schemaName, queryName, false);
        if (queryDef != null)
        {
            // Delete the metadata override on a built-in table
            QueryManager.get().delete(queryDef);
        }
        else
        {
            queryDef = QueryManager.get().getQueryDef(getViewContext().getContainer(), schemaName, queryName, true);
            if (queryDef != null)
            {
                queryDef.setMetaData(null);
                QueryManager.get().update(getViewContext().getUser(), queryDef);
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

    private boolean shouldStoreValue(Object userValue, Object defaultValue)
    {
        return userValue != null && !userValue.equals(defaultValue);
    }
}
