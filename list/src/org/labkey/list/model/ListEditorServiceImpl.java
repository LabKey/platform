package org.labkey.list.model;

import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.view.ViewContext;
import org.labkey.list.client.GWTList;
import org.labkey.list.client.ListEditorService;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Mar 23, 2010
 * Time: 1:15:41 PM
 */
public class ListEditorServiceImpl extends DomainEditorServiceBase implements ListEditorService
{
    public ListEditorServiceImpl(ViewContext context)
    {
        super(context);
    }


    public GWTList getList(int id)
    {
        ListDef def = ListManager.get().getList(getContainer(), id);

        GWTList ret = new GWTList();
        ret._listId(id);
        ret.setName(def.getName());
        ret.setAllowDelete(def.getAllowDelete());
        ret.setAllowExport(def.getAllowExport());
        ret.setAllowUpload(def.getAllowUpload());
        ret.setDescription(def.getDescription());
        ret.setDiscussionSetting(def.getDiscussionSetting());
        ret.setKeyPropertyName(def.getKeyName());
        ret.setKeyPropertyType(def.getKeyType());
        ret.setTitleField(def.getTitleColumn());
        return ret;
    }


    public List<String> updateListDefinition(GWTList list, GWTDomain orig, GWTDomain dd)
    {
        try
        {
            ListDef def = ListManager.get().getList(getContainer(), list.getListId());
            if (def.getDomainId() != orig.getDomainId() || def.getDomainId() != dd.getDomainId() || !orig.getDomainURI().equals(dd.getDomainURI()))
                throw new IllegalArgumentException();
            super.updateDomainDescriptor(orig, dd);
        }
        catch (ChangePropertyDescriptorException e)
        {
            return Collections.singletonList(e.getMessage());
        }
        return new ArrayList<String>(); // GWT error Collections.emptyList();
    }


    public GWTDomain getDomainDescriptor(GWTList list)
    {
        if (null == list)
            return null;
        ListDef def = ListManager.get().getList(getContainer(), list.getListId());
        if (null == def)
            return null;
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(def.getDomainId());
        if (null == dd)
            return null;
        GWTDomain<GWTPropertyDescriptor> ret = DomainUtil.getDomainDescriptor(getUser(), dd.getDomainURI(), dd.getContainer());
        if (null == ret)
            return null;
        List<GWTPropertyDescriptor> fields = new ArrayList<GWTPropertyDescriptor>(ret.getFields());
        GWTPropertyDescriptor key = null;
        for (GWTPropertyDescriptor f : fields)
        {
            if (def.getKeyName().equalsIgnoreCase(f.getName()))
            {
                key = f;
                break;
            }
        }
        if (null == key)
        {
            key = new GWTPropertyDescriptor(def.getKeyName(), PropertyType.INTEGER.getTypeUri());
            fields.add(0,key);
            // HACK: if we don't have a property descriptor, make name NOT editable (otherwise we'll be confused when we save)
            key.setNameEditable(false);
        }
        try { key.setRangeURI(ListDefinition.KeyType.valueOf(def.getKeyType()).getPropertyType().getTypeUri()); } catch (Exception x) {/* */}
        key.setTypeEditable(false);
        ret.setFields(fields);
//        Set<String> names = new HashSet<String>();
//        names.add(def.getKeyName());
//        ret.setMandatoryFieldNames(names);
        return ret;
    }
    

/*
    public GWTTableInfo getMetadata(String schemaName, String tableName) throws MetadataUnavailableException
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
        TableInfo table;
        try
        {
            table = schema.getTable(tableName);
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
            gwtColumnInfo.setURL(columnInfo.getURL() == null ? null : columnInfo.getURL().toString());
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

        QueryDef queryDef = QueryServiceImpl.get().findMetadataOverride(schema.getContainer(), schema.getSchemaName(), tableName, false);
        if (queryDef == null)
        {
            queryDef = QueryServiceImpl.get().findMetadataOverride(schema.getContainer(), schema.getSchemaName(), tableName, true);
            if (queryDef != null)
            {
                gwtTableInfo.setUserDefinedQuery(true);
            }
        }

        if (queryDef != null)
        {
            if (!getContainer().getId().equals(queryDef.getContainerId()))
            {
                Container c = ContainerManager.getForId(queryDef.getContainerId());
                if (c != null)
                {
                    gwtTableInfo.setDefinitionFolder(c.getPath());
                }
            }
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
                        if (column.isSetShownInDetailsView())
                        {
                            gwtColumnInfo.setShownInDetailsView(column.getShownInDetailsView());
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
        if (doc != null && doc.getTables() != null)
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
            doc = parseDocument(queryDef.getMetaData());
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
                        StringExpressionFactory.createURL(gwtColumnInfo.getURL());
                    }
                    catch (Exception e)
                    {
                        throw new MetadataUnavailableException(e.getMessage());
                    }
                }
                xmlColumn.setUrl(gwtColumnInfo.getURL());
            }
            else if (xmlColumn.isSetUrl())
            {
                xmlColumn.unsetUrl();
            }

            // Set the FK
            if (!gwtColumnInfo.isLookupCustom() && gwtColumnInfo.getLookupQuery() != null && gwtColumnInfo.getLookupSchema() != null)
            {
                ForeignKey rawFK = rawColumnInfo.getFk();
                // Check if it's the same FK
                if (rawFK == null || (!gwtColumnInfo.getLookupSchema().equals(rawFK.getLookupSchemaName()) && !gwtColumnInfo.getLookupQuery().equals(rawFK.getLookupTableName())))
                {
                    UserSchema fkSchema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), gwtColumnInfo.getLookupSchema());
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
                            }
                        }
                    }
                }
            }
            else if (xmlColumn.isSetFk())
            {
                xmlColumn.unsetFk();
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

    public GWTTableInfo resetToDefault(String schemaName, String queryName) throws MetadataUnavailableException
    {
        validatePermissions();

        try
        {
            QueryDef queryDef = QueryManager.get().getQueryDef(getViewContext().getContainer(), schemaName, queryName, false);
            if (queryDef != null)
            {
                // Delete the metadata override on a built-in table
                QueryManager.get().delete(getViewContext().getUser(), queryDef);
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
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
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
    */
}
