/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderPropertiesImpl;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.gwt.client.LockedPropertyType;
import org.labkey.api.gwt.client.model.GWTConditionalFormat;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.DefaultScaleType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class MetadataTableJSON extends GWTDomain<MetadataColumnJSON>
{
    private boolean _userDefinedQuery;
    /** If metadata is not stored in the current container, the folder path where it is stored */
    private String _definitionFolder;

    @Override
    public boolean isEditable(MetadataColumnJSON field)
    {
        return true;
    }

    public boolean isUserDefinedQuery()
    {
        return _userDefinedQuery;
    }

    public void setUserDefinedQuery(boolean userDefinedQuery)
    {
        _userDefinedQuery = userDefinedQuery;
    }

    public String getDefinitionFolder()
    {
        return _definitionFolder;
    }

    public void setDefinitionFolder(String definitionFolder)
    {
        _definitionFolder = definitionFolder;
    }

    public  MetadataTableJSON saveMetadata(String schemaName, User user, Container container) throws MetadataUnavailableException
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, schemaName);
        QueryDef queryDef = QueryManager.get().getQueryDef(schema.getContainer(), schema.getSchemaName(), this.getName(), this.isUserDefinedQuery());
        TableInfo rawTableInfo = schema.getTable(this.getName(), false);

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
            xmlTable = getTableType(this.getName(), doc);
        }
        else
        {
            queryDef = new QueryDef();
            queryDef.setSchema(schemaName);
            queryDef.setContainer(container.getId());
            queryDef.setName(this.getName());
        }

        if (doc == null)
        {
            doc = TablesDocument.Factory.newInstance();
        }

        if (xmlTable == null)
        {
            TablesType tables = doc.addNewTables();
            xmlTable = tables.addNewTable();
            xmlTable.setTableName(this.getName());
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


        for (MetadataColumnJSON metadataColumnJSON : this.getFields())
        {
            ColumnType xmlColumn = columnsToDelete.get(metadataColumnJSON.getName());
            ColumnInfo rawColumnInfo = rawTableInfo.getColumn(metadataColumnJSON.getName());
            if (rawColumnInfo == null)
            {
                rawColumnInfo = new BaseColumnInfo((String)null);
                // Establish the type of the column
                if (metadataColumnJSON.getWrappedColumnName() != null)
                {
                    ColumnInfo columnToBeWrapped = rawTableInfo.getColumn(metadataColumnJSON.getWrappedColumnName());
                    if (columnToBeWrapped == null)
                    {
                        continue;
                    }
                    ((BaseColumnInfo)rawColumnInfo).setJdbcType(columnToBeWrapped.getJdbcType());
                }
                else
                {
                    throw new MetadataUnavailableException("No such column: " + metadataColumnJSON.getName());
                }
            }

            if (xmlColumn != null)
            {
                // Still valid, don't delete it from the metadata overrides
                columnsToDelete.remove(metadataColumnJSON.getName());
            }
            else
            {
                // This column was not in the overrides before, so add it now
                xmlColumn = xmlTable.getColumns().addNewColumn();
                xmlColumn.setColumnName(metadataColumnJSON.getName());

                if (metadataColumnJSON.getWrappedColumnName() != null)
                {
                    // This is a newly created column that wraps another column
                    xmlColumn.setWrappedColumnName(metadataColumnJSON.getWrappedColumnName());
                }
            }

            // Set the description
            if (shouldStoreValue(metadataColumnJSON.getDescription(), rawColumnInfo.getDescription()))
            {
                xmlColumn.setDescription(metadataColumnJSON.getDescription());
            }
            else if (xmlColumn.isSetDescription())
            {
                xmlColumn.unsetDescription();
            }

            // Set the format
            if (shouldStoreValue(metadataColumnJSON.getFormat(), rawColumnInfo.getFormat()))
            {
                xmlColumn.setFormatString(metadataColumnJSON.getFormat());
            }
            else if (xmlColumn.isSetFormatString())
            {
                xmlColumn.unsetFormatString();
            }

            // Set visibility info
            if (metadataColumnJSON.isHidden() != rawColumnInfo.isHidden())
            {
                xmlColumn.setIsHidden(metadataColumnJSON.isHidden());
            }
            else if (xmlColumn.isSetIsHidden())
            {
                xmlColumn.unsetIsHidden();
            }
            if (metadataColumnJSON.isShownInInsertView() != rawColumnInfo.isShownInInsertView())
            {
                xmlColumn.setShownInInsertView(metadataColumnJSON.isShownInInsertView());
            }
            else if (xmlColumn.isSetShownInInsertView())
            {
                xmlColumn.unsetShownInInsertView();
            }
            if (metadataColumnJSON.isShownInUpdateView() != rawColumnInfo.isShownInUpdateView())
            {
                xmlColumn.setShownInUpdateView(metadataColumnJSON.isShownInUpdateView());
            }
            else if (xmlColumn.isSetShownInUpdateView())
            {
                xmlColumn.unsetShownInUpdateView();
            }
            if (metadataColumnJSON.isShownInDetailsView() != rawColumnInfo.isShownInDetailsView())
            {
                xmlColumn.setShownInDetailsView(metadataColumnJSON.isShownInDetailsView());
            }
            else if (xmlColumn.isSetShownInDetailsView())
            {
                xmlColumn.unsetShownInDetailsView();
            }
            if (metadataColumnJSON.isMeasure() != rawColumnInfo.isMeasure())
            {
                xmlColumn.setMeasure(metadataColumnJSON.isMeasure());
            }
            else if (xmlColumn.isSetMeasure())
            {
                xmlColumn.unsetMeasure();
            }

            if (metadataColumnJSON.isDimension() != rawColumnInfo.isDimension())
            {
                xmlColumn.setDimension(metadataColumnJSON.isDimension());
            }
            else if (xmlColumn.isSetDimension())
            {
                xmlColumn.unsetDimension();
            }

            if (metadataColumnJSON.isRecommendedVariable() != rawColumnInfo.isRecommendedVariable())
            {
                xmlColumn.setRecommendedVariable(metadataColumnJSON.isRecommendedVariable());
            }
            else if (xmlColumn.isSetRecommendedVariable())
            {
                xmlColumn.unsetRecommendedVariable();
            }

            if (!StringUtils.equals(metadataColumnJSON.getDefaultScale(), rawColumnInfo.getDefaultScale().name()))
            {
                xmlColumn.setDefaultScale(DefaultScaleType.Enum.forString(metadataColumnJSON.getDefaultScale()));
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

            if (metadataColumnJSON.isExcludeFromShifting() != rawColumnInfo.isExcludeFromShifting())
            {
                xmlColumn.setExcludeFromShifting(metadataColumnJSON.isExcludeFromShifting());
            }
            else if (xmlColumn.isSetExcludeFromShifting())
            {
                xmlColumn.unsetExcludeFromShifting();
            }

            // Set the label
            if (shouldStoreValue(metadataColumnJSON.getLabel(), rawColumnInfo.getLabel()))
            {
                xmlColumn.setColumnTitle(metadataColumnJSON.getLabel());
            }
            else if (xmlColumn.isSetColumnTitle())
            {
                xmlColumn.unsetColumnTitle();
            }

            // Set the URL
            String originalURL = rawColumnInfo.getURL() == null ? null : rawColumnInfo.getURL().toString();
            if (shouldStoreValue(metadataColumnJSON.getURL(), originalURL))
            {
                if (metadataColumnJSON.getURL() != null)
                {
                    try
                    {
                        StringExpression expr = StringExpressionFactory.createURL(metadataColumnJSON.getURL());
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

            // Set the ImportAliases
            Set<String> importAliasSet = rawColumnInfo.getImportAliasSet();
            if (metadataColumnJSON.getImportAliases() != null)
            {
                Set<String> passedImportAliasSet = ColumnRenderPropertiesImpl.convertToSet(metadataColumnJSON.getImportAliases());

                if (shouldStoreValue(importAliasSet, passedImportAliasSet))
                {
                    // add-to/remove-from existing import aliases set
                    if (xmlColumn.getImportAliases() != null)
                    {
                        xmlColumn.unsetImportAliases();
                    }
                    // when there is no existing import aliases, add import aliases xml
                    if (!metadataColumnJSON.getImportAliases().equals(""))
                    {
                        addImportAliases(xmlColumn, metadataColumnJSON.getImportAliases());
                    }
                }
                // wipe off import aliases xml
                else if (xmlColumn.isSetImportAliases())
                {
                    xmlColumn.unsetImportAliases();
                }
            }

            // Set the FK
            if (!metadataColumnJSON.isLookupCustom() && metadataColumnJSON.getLookupQuery() != null && metadataColumnJSON.getLookupSchema() != null)
            {
                Pair<Lookup, Boolean> lookup = createLookup(rawColumnInfo.getFk(), container);

                // Check if it's the same FK, based on schema, query, and container
                if (lookup == null ||
                        !metadataColumnJSON.getLookupSchema().equals(lookup.first.getSchemaName()) ||
                        !metadataColumnJSON.getLookupQuery().equals(lookup.first.getQueryName()) ||
                        !Objects.equals(metadataColumnJSON.getLookupContainer(), lookup.first.getContainer() != null ? lookup.first.getContainer().getPath() : null))
                {
                    Container targetContainer = metadataColumnJSON.getLookupContainer() != null ? ContainerManager.getForPath(metadataColumnJSON.getLookupContainer()) : null;
                    UserSchema fkSchema = QueryService.get().getUserSchema(user, targetContainer == null ? container : targetContainer, metadataColumnJSON.getLookupSchema());
                    if (fkSchema != null)
                    {
                        TableInfo fkTableInfo = fkSchema.getTable(metadataColumnJSON.getLookupQuery());
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
                                fk.setFkDbSchema(metadataColumnJSON.getLookupSchema());
                                fk.setFkTable(metadataColumnJSON.getLookupQuery());
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
            if (shouldStoreValue(metadataColumnJSON.getConditionalFormats(), convertToGWT(rawColumnInfo.getConditionalFormats())))
            {
                ConditionalFormat.convertToXML(metadataColumnJSON.getConditionalFormats(), xmlColumn);
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
            QueryManager.get().insert(user, queryDef);
        }
        else
        {
            QueryManager.get().update(user, queryDef);
        }

        return getMetadata(schemaName, this.getName(), user, container);
    }

    private void addImportAliases(ColumnType xmlColumn, String importAliases)
    {
        Set<String> aliasesSet = ColumnRenderPropertiesImpl.convertToSet(importAliases);
        ColumnType.ImportAliases importAliasesXml = xmlColumn.addNewImportAliases();
        aliasesSet.forEach(importAliasesXml::addImportAlias);
    }

    private static TableType getTableType(String name, TablesDocument doc)
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

    private static boolean shouldStoreValue(Object userValue, Object defaultValue)
    {
        return userValue != null && !userValue.equals(defaultValue);
    }

    private static void removeColumn(TableType tableType, ColumnType columnType)
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

    public static MetadataTableJSON getMetadata(String schemaName, String tableName, User user, Container container) throws MetadataUnavailableException
    {
        Map<String, MetadataColumnJSON> columnInfos = new CaseInsensitiveHashMap<>();
        List<MetadataColumnJSON> orderedPDs = new ArrayList<>();
        Set<String> injectedColumnNames = new CaseInsensitiveHashSet();
        MetadataTableJSON metadataTableJSON = new MetadataTableJSON();
        metadataTableJSON.setSchemaName(schemaName);
        metadataTableJSON.setQueryName(tableName);
        metadataTableJSON.setName(tableName);

        UserSchema schema = QueryService.get().getUserSchema(user, container, schemaName);
        if (schema == null)
        {
            return null;
        }
        TableInfo table;
        try
        {
            table = schema.getTable(tableName, null);
            if (null == table)
                return null;

            Domain domain = table.getDomain();
            if (domain != null)
                metadataTableJSON.setProvisioned(domain.isProvisioned());
        }
        catch (QueryParseException e)
        {
            throw new MetadataUnavailableException(e.getMessage());
        }

        for (ColumnInfo columnInfo : table.getColumns())
        {
            MetadataColumnJSON metadataColumnJSON = new MetadataColumnJSON();

            metadataColumnJSON.setPropertyId(-1);
            metadataColumnJSON.setName(columnInfo.getName());
            columnInfos.put(metadataColumnJSON.getName(), metadataColumnJSON);
            orderedPDs.add(metadataColumnJSON);

            metadataColumnJSON.setRequired(!columnInfo.isNullable());
            metadataColumnJSON.setLabel(columnInfo.getLabel());
            metadataColumnJSON.setFormat(columnInfo.getFormat());
            metadataColumnJSON.setHidden(columnInfo.isHidden());
            metadataColumnJSON.setShownInDetailsView(columnInfo.isShownInDetailsView());
            metadataColumnJSON.setShownInInsertView(columnInfo.isShownInInsertView());
            metadataColumnJSON.setShownInUpdateView(columnInfo.isShownInUpdateView());
            metadataColumnJSON.setDimension(columnInfo.isDimension());
            metadataColumnJSON.setMeasure(columnInfo.isMeasure());
            metadataColumnJSON.setRecommendedVariable(columnInfo.isRecommendedVariable());
            metadataColumnJSON.setDefaultScale(columnInfo.getDefaultScale().name());
            /* NOTE: explicitly not supporting this metadata via this pathway, do not uncomment
            gwtColumnInfo.setPHI(columnInfo.getPHI().name());*/
            metadataColumnJSON.setExcludeFromShifting(columnInfo.isExcludeFromShifting());
            metadataColumnJSON.setURL(columnInfo.getURL() == null ? null : columnInfo.getURL().toString());
            metadataColumnJSON.setRangeURI(PropertyType.getFromClass(columnInfo.getJavaObjectClass()).getTypeUri());
            if (columnInfo.getFk() != null)
            {
                ForeignKey fk = columnInfo.getFk();
                Pair<Lookup, Boolean> lookup = createLookup(fk, container);
                if (lookup != null)
                {
                    if (lookup.second)
                        metadataColumnJSON.setLookupCustom(true);
                    else
                    {
                        metadataColumnJSON.setLookupSchema(lookup.first.getSchemaName());
                        metadataColumnJSON.setLookupQuery(lookup.first.getQueryName());
                        if (lookup.first.getContainer() != null)
                            metadataColumnJSON.setLookupContainer(lookup.first.getContainer().getPath());
                    }
                }
            }

            List<GWTConditionalFormat> formats = convertToGWT(columnInfo.getConditionalFormats());
            metadataColumnJSON.setConditionalFormats(formats);
        }

        List<QueryDef> queryDefs = QueryServiceImpl.get().findMetadataOverrideImpl(schema, tableName, false, false, null);
        if (queryDefs == null)
        {
            queryDefs = QueryServiceImpl.get().findMetadataOverrideImpl(schema, tableName, true, false, null);
            if (queryDefs != null)
            {
                metadataTableJSON.setUserDefinedQuery(true);
            }
        }

        if (queryDefs != null && !queryDefs.isEmpty())
        {
            // Use the last QueryDef's metadata -- this should be the user's metadata override in the database if it exists
            QueryDef queryDef = queryDefs.get(queryDefs.size()-1);

            if (!container.getId().equals(queryDef.getContainerId()))
            {
                Container c = ContainerManager.getForId(queryDef.getContainerId());
                if (c != null)
                {
                    metadataTableJSON.setDefinitionFolder(c.getPath());
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
                        MetadataColumnJSON metadataColumnJSON = columnInfos.get(column.getColumnName());
                        if (metadataColumnJSON == null)
                        {
                            // Omit columns that are in the XML but are no longer in the underlying table/query
                            break;
                        }
                        if (column.isSetImportAliases())
                        {
                            String importAliases = ColumnRenderPropertiesImpl.convertToString(new HashSet<>(Arrays.asList(column.getImportAliases().getImportAliasArray())));
                            metadataColumnJSON.setImportAliases(importAliases);
                        }
                        if (column.isSetColumnTitle())
                        {
                            metadataColumnJSON.setLabel(column.getColumnTitle());
                        }
                        if (column.isSetDescription())
                        {
                            metadataColumnJSON.setDescription(column.getDescription());
                        }
                        if (column.isSetFormatString())
                        {
                            metadataColumnJSON.setFormat(column.getFormatString());
                        }
                        if (column.isSetShownInDetailsView())
                        {
                            metadataColumnJSON.setShownInDetailsView(column.getShownInDetailsView());
                        }
                        if (column.isSetDimension())
                        {
                            metadataColumnJSON.setDimension(column.getDimension());
                        }
                        if (column.isSetMeasure())
                        {
                            metadataColumnJSON.setMeasure(column.getMeasure());
                        }
                        if (column.isSetRecommendedVariable())
                        {
                            metadataColumnJSON.setRecommendedVariable(column.getRecommendedVariable());
                        }
                        else if (column.isSetKeyVariable())
                        {
                            metadataColumnJSON.setRecommendedVariable(column.getKeyVariable());
                        }
                        if (column.isSetDefaultScale())
                        {
                            metadataColumnJSON.setDefaultScale(column.getDefaultScale().toString());
                        }
                        /* NOTE: explicitly not supporting this metadata via this pathway, do not uncomment
                        if (column.isSetPhi())
                        {
                            gwtColumnInfo.setPHI(column.getPhi().toString());
                        }*/
                        if (column.isSetExcludeFromShifting())
                        {
                            metadataColumnJSON.setExcludeFromShifting(column.getExcludeFromShifting());
                        }
                        if (column.isSetIsHidden())
                        {
                            metadataColumnJSON.setHidden(column.getIsHidden());
                        }
                        if (column.isSetShownInInsertView())
                        {
                            metadataColumnJSON.setShownInInsertView(column.getShownInInsertView());
                        }
                        if (column.isSetShownInUpdateView())
                        {
                            metadataColumnJSON.setShownInUpdateView(column.getShownInUpdateView());
                        }
                        if (column.getFk() != null)
                        {
                            metadataColumnJSON.setLookupQuery(column.getFk().getFkTable());
                            metadataColumnJSON.setLookupSchema(column.getFk().getFkDbSchema());
                        }
                        if (column.isSetConditionalFormats())
                        {
                            List<ConditionalFormat> serverFormats = ConditionalFormat.convertFromXML(column.getConditionalFormats());
                            List<GWTConditionalFormat> gwtFormats = new ArrayList<>();
                            for (ConditionalFormat serverFormat : serverFormats)
                            {
                                gwtFormats.add(new GWTConditionalFormat(serverFormat));
                            }
                            metadataColumnJSON.setConditionalFormats(gwtFormats);
                        }
                        if (column.getWrappedColumnName() != null)
                        {
                            injectedColumnNames.add(column.getColumnName());
                            metadataColumnJSON.setWrappedColumnName(column.getWrappedColumnName());
                            ColumnInfo tableColumn = table.getColumn(column.getWrappedColumnName());
                            if (tableColumn != null)
                            {
                                metadataColumnJSON.setRangeURI(PropertyType.getFromClass(tableColumn.getJavaObjectClass()).getTypeUri());
                            }
                        }
                        else
                        {
                            metadataColumnJSON.setLockType(LockedPropertyType.PartiallyLocked.name());
                            ColumnInfo tableColumn = table.getColumn(column.getColumnName());
                            if (tableColumn != null)
                            {
                                metadataColumnJSON.setRangeURI(PropertyType.getFromClass(tableColumn.getJavaObjectClass()).getTypeUri());
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
        metadataTableJSON.setMandatoryFieldNames(builtInColumnNames);
        metadataTableJSON.setFields(orderedPDs);

        // TODO: figure out something better for defaultValuesURL
        metadataTableJSON.setDefaultValuesURL(" ");
        return metadataTableJSON;
    }

    private static List<GWTConditionalFormat> convertToGWT(List<ConditionalFormat> formats)
    {
        List<GWTConditionalFormat> result = new ArrayList<>();
        for (ConditionalFormat format : formats)
        {
            result.add(new GWTConditionalFormat(format));
        }
        return result;
    }

    private static Pair<Lookup, Boolean> createLookup(ForeignKey fk, Container currentContainer)
    {
        if (fk == null)
            return null;

        boolean custom = false;
        Lookup lookup = new Lookup();

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
                lookup.setSchemaName(lookupTarget.getPublicSchemaName());
                lookup.setQueryName(lookupTarget.getPublicName());
            }
            else
            {
                custom = true;
            }
        }
        else
        {
            lookup.setSchemaName(fk.getLookupSchemaName());
            lookup.setQueryName(fk.getLookupTableName());
        }

        // Set the lookup's container if it targets some other container
        if (lookupTarget != null && lookupTarget.getUserSchema() != null && !lookupTarget.getUserSchema().getContainer().equals(currentContainer))
        {
            lookup.setContainer(lookupTarget.getUserSchema().getContainer());
        }

        return Pair.of(lookup, custom);
    }

    private static TablesDocument parseDocument(String xml) throws XmlException
    {
        if (xml == null)
        {
            return null;
        }

        return TablesDocument.Factory.parse(xml);
    }

    public static MetadataTableJSON resetToDefault(String schemaName, String queryName, User user, Container container) throws MetadataUnavailableException
    {
        QueryDef queryDef = QueryManager.get().getQueryDef(container, schemaName, queryName, false);
        if (queryDef != null)
        {
            // Delete the metadata override on a built-in table
            QueryManager.get().delete(queryDef);
        }
        else
        {
            queryDef = QueryManager.get().getQueryDef(container, schemaName, queryName, true);
            if (queryDef != null)
            {
                queryDef.setMetaData(null);
                QueryManager.get().update(user, queryDef);
            }
        }
        return getMetadata(schemaName, queryName, user, container);
    }
}
