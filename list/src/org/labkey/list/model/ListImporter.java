/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

package org.labkey.list.model;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PHI;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.ImportTypesHelper;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListDefinition.KeyType;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.PHIType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.list.pipeline.ListReloadTask;
import org.labkey.list.xml.ListsDocument;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/*
* User: adam
* Date: Aug 27, 2009
* Time: 2:12:01 PM
*/
public class ListImporter
{
    private static final String TYPE_NAME_COLUMN = "ListName";
    private ListImportContext _importContext;

    public ListImporter(){
        _importContext = new ListImportContext(null, false);
    }

    public ListImporter(ListImportContext importContext)
    {
        _importContext = importContext;
    }

    public boolean processSingle(VirtualFile sourceDir, String fileName, Container c, User user, List<String> errors, Logger log) throws Exception
    {
        //Since we don't have a definition here, try to get one from the fileName & context

        Map<String, ListDefinition> lists = ListService.get().getLists(c);
        ListDefinition def = lists.get(FileUtil.getBaseName(fileName));

        if (_importContext.getInputDataMap() != null)
        {
            for (Map.Entry<String, Pair<String, String>> entry : _importContext.getInputDataMap().entrySet())
            {
                if (entry.getKey().equals(fileName))
                {
                    Pair<String, String> dataKey = entry.getValue();
                    if (dataKey.first.equals(ListReloadTask.LIST_NAME_KEY))
                    {
                        def = lists.get(dataKey.second);
                        if (def == null)
                            log.error("Could not locate a list with name:" + dataKey.second);
                    }
                    else if (dataKey.first.equals(ListReloadTask.LIST_ID_KEY))
                    {
                        try
                        {
                            def = ListService.get().getList(c, Integer.parseInt(dataKey.second));
                            if (def == null)
                                log.error("Could not locate a list with Id:" + dataKey.second);
                        }
                        catch (NumberFormatException e)
                        {
                            errors.add("Failed to parse list id from context. " +  e.getMessage());
                        }
                    }
                }
            }
        }

        return processSingle(sourceDir, def, fileName, false, c, user,  errors, log);
    }

    public boolean processSingle(VirtualFile sourceDir, ListDefinition def, String fileName, boolean hasXmlMetadata, Container c, User user, List<String> errors, Logger log) throws Exception
    {
        if (null != def)
        {
            try (InputStream stream = sourceDir.getInputStream(fileName))
            {
                if (null != stream)
                {
                    BatchValidationException batchErrors = new BatchValidationException();
                    DataLoader loader = DataLoader.get().createLoader(fileName, null, stream, true, null, null);

                    if (!hasXmlMetadata && !resolveDomainChanges(c, user, loader, def, log, errors))
                    {
                        log.warn("Skipping filed-based import of '" + def.getName() + "' due to domain resolution errors.");
                        return false;
                    }

                    boolean supportAI = false;

                    // Support for importing auto-incremented keys
                    if (def.getKeyType().equals(KeyType.AutoIncrementInteger))
                    {
                        // Check that the key column is being provided, otherwise we'll generate the IDs for them
                        ColumnDescriptor[] columns = loader.getColumns();
                        for (ColumnDescriptor cd : columns)
                        {
                            if (cd.getColumnName().equalsIgnoreCase(def.getKeyName()))
                            {
                                supportAI = true;
                                break;
                            }
                        }
                    }

                    TableInfo ti = def.getTable(user);
                    String tableName =  ListManager.get().getListTableName(ti);

                    if (null != ti && null != tableName)
                    {
                        try (DbScope.Transaction transaction = ti.getSchema().getScope().ensureTransaction())
                        {
                            if (!hasXmlMetadata)
                            {
                                QueryUpdateService qus = ti.getUpdateService();
                                if (qus != null && !_importContext.useMerge())
                                {
                                    int deletedRows = ti.getUpdateService().truncateRows(user, c, null, null);
                                    log.info("Deleted " + deletedRows + " row(s) from list: " + def.getName() + " for reload preparation");
                                }
                            }

                            // pre-process
                            if (supportAI)
                            {
                                SqlDialect dialect = ti.getSqlDialect();

                                if (dialect.isSqlServer())
                                {
                                    SQLFragment check = new SQLFragment("SET IDENTITY_INSERT ").append(tableName).append(" ON\n");
                                    new SqlExecutor(ti.getSchema()).execute(check);
                                }
                            }

                            def.insertListItems(user, c, loader, batchErrors, sourceDir.getDir(FileUtil.makeLegalName(def.getName())), null, supportAI, false, _importContext.useMerge());

                            for (ValidationException v : batchErrors.getRowErrors())
                                errors.add(v.getMessage());

                            if (supportAI)
                            {
                                SqlDialect dialect = ti.getSqlDialect();

                                // If auto-increment based need to reset the sequence counter on the DB
                                if (dialect.isPostgreSQL())
                                {
                                    String src = ti.getColumn(def.getKeyName()).getJdbcDefaultValue();
                                    if (null != src)
                                    {
                                        String sequence = "";

                                        int start = src.indexOf('\'');
                                        int end = src.lastIndexOf('\'');

                                        if (end > start)
                                        {
                                            sequence = src.substring(start + 1, end);
                                            if (!sequence.toLowerCase().startsWith("list."))
                                                sequence = "list." + sequence;
                                        }

                                        SQLFragment keyupdate = new SQLFragment("SELECT setval('").append(sequence).append("'");
                                        keyupdate.append(", coalesce((SELECT MAX(").append(dialect.quoteIdentifier(def.getKeyName().toLowerCase())).append(")+1 FROM ").append(tableName);
                                        keyupdate.append("), 1), false);");
                                        new SqlExecutor(ti.getSchema()).execute(keyupdate);
                                    }
                                }
                                else if (dialect.isSqlServer())
                                {
                                    SQLFragment check = new SQLFragment("SET IDENTITY_INSERT ").append(tableName).append(" OFF\n");
                                    new SqlExecutor(ti.getSchema()).execute(check);
                                }
                            }

                            if (errors.isEmpty())
                                transaction.commit();
                        }
                        finally
                        {
                            if (supportAI)
                            {
                                SqlDialect dialect = ti.getSqlDialect();

                                if (dialect.isSqlServer())
                                {
                                    SQLFragment check = new SQLFragment("SET IDENTITY_INSERT ").append(tableName).append(" OFF\n");
                                    new SqlExecutor(ti.getSchema()).execute(check);
                                }
                            }
                        }
                    }
                    else
                    {
                        throw new IllegalStateException("Table information not available for list: " + def.getName());
                    }
                }
            }
            return true;
        }
        errors.add("Could not locate a list on the server to associate with the file: " + fileName);
        return false;
    }

    public void processMany(VirtualFile listsDir, Container c, User user, List<String> errors, Logger log) throws Exception
    {
        XmlObject listXml = listsDir.getXmlBean(ListWriter.SCHEMA_FILENAME);

        if (listXml != null)
            createDefinedLists(listsDir, listXml, c, user, errors, log);

        Map<String, String> fileTypeMap = new HashMap<>();
        for (String f : listsDir.list())
        {
            if (f.endsWith(".tsv") || f.endsWith(".xlsx") || f.endsWith(".xls"))
            {
                fileTypeMap.put(FileUtil.makeLegalName(FileUtil.getBaseName(f)), FileUtil.getExtension(f));
            }
        }

        Map<String, ListDefinition> lists = ListService.get().getLists(c);
        int failedLists = 0;
        for (String listName : lists.keySet())
        {
            ListDefinition def = lists.get(listName);
            String legalName = FileUtil.makeLegalName(listName);
            String fileName = legalName + "." + fileTypeMap.remove(legalName);

            if (!processSingle(listsDir, def, fileName, listXml != null, c, user, errors, log))
            {
                failedLists++;
            }
        }

        int size = lists.size() - failedLists;
        log.info(size + " list" + (1 == size ? "" : "s") + " imported");
        log.warn(failedLists + " list" + (1 == failedLists ? "" : "s") + " failed to import");
        if (fileTypeMap.size() > 0)
        {
            log.info("The following files were not imported because the server could not find a list with matching name: ");
            for (String s : fileTypeMap.keySet())
            {
                log.info("\tSkipped " + s + fileTypeMap.get(s));
            }
        }
    }

    private boolean createNewList(Container c, User user, String listName, Collection<Integer> preferredListIds, TableType listXml, @Nullable ListsDocument.Lists.List listSettingsXml, List<String> errors) throws Exception
    {
        final String keyName = listXml.getPkColumnName();

        if (null == keyName)
        {
            errors.add("List \"" + listName + "\": no pkColumnName set.");
        }

        KeyType pkType = getKeyType(listXml, keyName);

        ListDefinition list = ListService.get().createList(c, listName, pkType);
        list.setKeyName(keyName);
        list.setDescription(listXml.getDescription());

        if (listXml.isSetTitleColumn())
            list.setTitleColumn(listXml.getTitleColumn());

        if (null != listSettingsXml)
        {
            // If an ID is set for this list in the archive then attempt to use it
            if (listSettingsXml.isSetId())
                preferredListIds.add(listSettingsXml.getId());

            list.setDiscussionSetting(ListDefinition.DiscussionSetting.getForValue(listSettingsXml.getDiscussions()));
            list.setAllowDelete(listSettingsXml.getAllowDelete());
            list.setAllowUpload(listSettingsXml.getAllowUpload());
            list.setAllowExport(listSettingsXml.getAllowExport());

            list.setEachItemIndex(listSettingsXml.getEachItemIndex());
            list.setEachItemTitleSetting(ListDefinition.TitleSetting.getForValue(listSettingsXml.getEachItemTitleSetting()));
            list.setEachItemTitleTemplate(listSettingsXml.getEachItemTitleTemplate());
            list.setEachItemBodySetting(ListDefinition.BodySetting.getForValue(listSettingsXml.getEachItemBodySetting()));
            list.setEachItemBodyTemplate(listSettingsXml.getEachItemBodyTemplate());

            list.setEntireListIndex(listSettingsXml.getEntireListIndex());
            list.setEntireListIndexSetting(ListDefinition.IndexSetting.getForValue(listSettingsXml.getEntireListIndexSetting()));
            list.setEntireListTitleSetting(ListDefinition.TitleSetting.getForValue(listSettingsXml.getEntireListTitleSetting()));
            list.setEntireListTitleTemplate(listSettingsXml.getEntireListTitleTemplate());
            list.setEntireListBodySetting(ListDefinition.BodySetting.getForValue(listSettingsXml.getEntireListBodySetting()));
            list.setEntireListBodyTemplate(listSettingsXml.getEntireListBodyTemplate());

            list.setFileAttachmentIndex(listSettingsXml.getFileAttachmentIndex());
        }

        list.setPreferredListIds(preferredListIds);

        ImportTypesHelper importHelper = new ImportTypesHelper(listXml, TYPE_NAME_COLUMN, listName)
        {
            @Override
            protected boolean acceptColumn(String columnName, ColumnType columnXml) throws Exception
            {
                if (columnXml.getIsKeyField() && !columnName.equalsIgnoreCase(keyName))
                    throw new ImportException("More than one key specified: '" + keyName + "' and '" + columnName + "'");


                return true;
            }
        };
        return ListManager.get().importListSchema(list, TYPE_NAME_COLUMN, importHelper, user, errors);
    }

    private KeyType getKeyType(TableType listXml, String keyName) throws ImportException
    {
        for (ColumnType columnXml : listXml.getColumns().getColumnArray())
        {
            if (columnXml.getColumnName().equals(keyName))
            {
                String datatype = columnXml.getDatatype();

                if (datatype.equalsIgnoreCase("varchar"))
                    return KeyType.Varchar;

                if (datatype.equalsIgnoreCase("integer"))
                    return columnXml.getIsAutoInc() ? KeyType.AutoIncrementInteger : KeyType.Integer;

                throw new ImportException("unknown key type \"" + datatype + "\" for key \"" + keyName + "\".");
            }
        }

        throw new ImportException("pkColumnName is set to \"" + keyName + "\" but column is not defined.");
    }

    private void createDefinedLists(VirtualFile listsDir, XmlObject listXml, Container c, User user, List<String> errors, Logger log) throws Exception
    {
        TablesDocument tablesDoc;
        try
        {
            if (listXml instanceof TablesDocument)
            {
                tablesDoc = (TablesDocument)listXml;
                XmlBeansUtil.validateXmlDocument(tablesDoc, ListWriter.SCHEMA_FILENAME);
            }
            else
                throw new ImportException("Unable to get an instance of TablesDocument from " + ListWriter.SCHEMA_FILENAME);
        }
        catch (XmlValidationException xve)
        {
            // Note: different constructor than the one below
            throw new InvalidFileException(ListWriter.SCHEMA_FILENAME, xve);
        }
        catch (Exception e)
        {
            throw new InvalidFileException(ListWriter.SCHEMA_FILENAME, e);
        }

        Map<String, ListsDocument.Lists.List> listSettingsMap = new HashMap<>();

        try
        {
            XmlObject listSettingsXml = listsDir.getXmlBean(ListWriter.SETTINGS_FILENAME);

            // Settings file is optional
            if (listSettingsXml instanceof ListsDocument)
            {
                ListsDocument listSettingsDoc = (ListsDocument)listSettingsXml;
                XmlBeansUtil.validateXmlDocument(listSettingsDoc, ListWriter.SETTINGS_FILENAME);

                ListsDocument.Lists.List[] listArray = listSettingsDoc.getLists().getListArray();

                // Create a name->list setting map
                for (ListsDocument.Lists.List list : listArray)
                    listSettingsMap.put(list.getName(), list);
            }
        }
        catch (XmlValidationException xve)
        {
            // Note: different constructor than the one below
            throw new InvalidFileException(ListWriter.SETTINGS_FILENAME, xve);
        }
        catch (Exception e)
        {
            throw new InvalidFileException(ListWriter.SETTINGS_FILENAME, e);
        }

        TablesType tablesXml = tablesDoc.getTables();

        Map<String, ListDefinition> lists = ListService.get().getLists(c);

        for (TableType tableType : tablesXml.getTableArray())
        {
            boolean replaced = false;
            String name = tableType.getTableName();

            int maxXmlIntPHI = PHIType.INT_NOT_PHI;
            for (ColumnType column : tableType.getColumns().getColumnArray())
            {
                maxXmlIntPHI = Math.max(maxXmlIntPHI, Optional.ofNullable(column.getPhi()).orElse(PHIType.NOT_PHI).intValue());
            }
            PHI maxListPHI = PHI.valueOf(PHIType.Enum.forInt(maxXmlIntPHI).toString());
            PHI maxAllowedPHI = ComplianceService.get().getMaxAllowedPhi(c, user);
            if (!maxListPHI.isLevelAllowed(maxAllowedPHI))
                throw new ImportException("PHI level in list \"" + name + "\" exceeds level allowed for current user. List contains PHI level \"" + maxListPHI.getLabel() + "\" but user is only allowed up to \"" + maxAllowedPHI.getLabel() + "\"." );

            Set<Integer> preferredListIds = new LinkedHashSet<>();
            ListDefinition def = lists.get(name);

            if (null != def)
            {
                if (!_importContext.useMerge())
                {
                    preferredListIds.add(def.getListId());

                    try
                    {
                        log.info("Truncating list: " + def.getName());
                        def.delete(user);
                        replaced = true;
                    }
                    catch (OptimisticConflictException e)
                    {
                        throw new ImportException("Error deleting list \"" + name + "\": " + e.getMessage());
                    }
                }
            }

            if (replaced || null == def)
            {
                try
                {
                    log.info("Recreating list: " + name);
                    boolean success = createNewList(c, user, name, preferredListIds, tableType, listSettingsMap.get(name), errors);
                    assert success;
                }
                catch (ImportException e)
                {
                    throw new ImportException("Error creating list \"" + name + "\": " + e.getMessage());
                }
            }
        }
    }

    private boolean resolveDomainChanges(Container c, User user, DataLoader loader, ListDefinition listDef, Logger log, List<String> errors) throws IOException
    {
        Domain domain = listDef.getDomain();
        boolean isDirty = false;
        if (domain != null)
        {
            log.info("resolving domain of list: " + listDef.getName());
            Map<String, DomainProperty> currentColumns = listDef.getDomain().getProperties().stream().collect(Collectors.toMap(DomainProperty::getName, e -> e));

            // Do a pass over the loader's columns
            for (ColumnDescriptor loaderCol : loader.getColumns())
            {
                JdbcType jdbcType = JdbcType.valueOf(loaderCol.clazz);

                // check for prop/type mismatches
                if (currentColumns.containsKey(loaderCol.name)
                        && !currentColumns.get(loaderCol.name).getPropertyDescriptor().getJdbcType().equals(jdbcType))
                {
                    log.warn("Failed to import data for '" + listDef.getName() + "'. Column '" + loaderCol.name + "' in the incoming data has type " + jdbcType.name()
                            + " which does not match existing type: " + currentColumns.get(loaderCol.name).getPropertyDescriptor().getJdbcType());
                    return false;
                }
                //add new properties found in the incoming file
                else if (!currentColumns.containsKey(loaderCol.name))
                {
                    PropertyType type = PropertyType.getFromJdbcType(jdbcType);
                    PropertyDescriptor pd = new PropertyDescriptor(domain.getTypeURI() + "." + loaderCol.name, type, loaderCol.name, c);
                    domain.addPropertyOfPropertyDescriptor(pd);
                    isDirty = true;
                    log.info("\tAdded column " + loaderCol.name + " of type \"" + type.getXarName() + "\" to " + listDef.getName());
                }
                currentColumns.remove(loaderCol.name);
            }

            // Remove properties in the original domain that aren't found in the incoming file
            for (String columnName : currentColumns.keySet())
            {
                if (listDef.getKeyName().equals(columnName) && !listDef.getKeyType().getLabel().equals("Auto-Increment Integer"))
                {
                    log.warn("Failed to import data for '" + listDef.getName() + "'. Primary Key '" + columnName + "' not present in file.");
                    return false;
                }
                else if (!listDef.getKeyName().equals(columnName) && !currentColumns.get(columnName).isRequired())
                {
                    currentColumns.get(columnName).delete();
                    isDirty = true;
                    log.info("\tDeleted column " + columnName);
                }
            }

            try
            {
                if (isDirty)
                    domain.save(user);
            }
            catch (Exception e)
            {
                errors.add(e.getMessage());
                return false;
            }
        }
        return true;
    }
}
