/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.study.importer;

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.ImportTypesHelper;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyManager.ImportPropertyDescriptorsList;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.study.query.StudyQuerySchema;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by klum on 1/24/14.
 */
public class DefaultStudyDesignImporter
{
    /**
     * Removes previous data for the specified table and container
     */
    protected void deleteData(Container container, TableInfo tableInfo) throws ImportException
    {
        try {
            if (tableInfo instanceof FilteredTable)
            {
                Table.delete(((FilteredTable)tableInfo).getRealTable(), SimpleFilter.createContainerFilter(container));
            }
        }
        catch (RuntimeSQLException e)
        {
            throw new ImportException(e.getMessage());
        }
    }

    protected void importTableinfo(StudyImportContext ctx, VirtualFile root, String schemaFileName) throws Exception
    {
        TablesDocument tablesDoc;
        try
        {
            XmlObject schemaXml = root.getXmlBean(schemaFileName);
            if (schemaXml instanceof TablesDocument)
            {
                tablesDoc = (TablesDocument)schemaXml;
                XmlBeansUtil.validateXmlDocument(tablesDoc, schemaFileName);
            }
            else
            {
                ctx.getLogger().info("No table metadata file found to import: " + schemaFileName);
                return;
            }
        }
        catch (XmlValidationException e)
        {
            throw new InvalidFileException(schemaFileName, e);
        }

        TablesType tablesXml = tablesDoc.getTables();
        for (TableType tableXml : tablesXml.getTableArray())
        {
            final String tableName = tableXml.getTableName();

            // get the domain of the table we are updating
            StudyQuerySchema schema = StudyQuerySchema.createSchema(ctx.getStudy(), ctx.getUser(), true);
            TableInfo table = schema.getTable(tableName);

            if (table != null)
            {
                final Domain domain = schema.getTable(tableName).getDomain();

                if (domain != null)
                {
                    // study design table domains are rooted at the project level
                    final Container container = domain.getContainer();

                    ImportTypesHelper importHelper = new ImportTypesHelper(tableXml, "TableName", tableName);
                    List<String> propErrors = new ArrayList<>();

                    // Create a map of existing properties
                    Map<String, PropertyDescriptor> current = new CaseInsensitiveHashMap<>();
                    Set<String> currentPropertyURIs = new HashSet<>();
                    for (DomainProperty dp : domain.getProperties())
                    {
                        PropertyDescriptor pd = dp.getPropertyDescriptor();
                        current.put(pd.getName(), pd);
                        currentPropertyURIs.add(pd.getPropertyURI());
                    }

                    DomainURIFactory factory = name -> new Pair<>(domain.getTypeURI(), container);

                    ImportPropertyDescriptorsList pds = importHelper.getImportPropertyDescriptors(factory, propErrors, container);
                    if (!propErrors.isEmpty())
                        throw new ImportException("Unable to get an instance of TablesDocument from " + schemaFileName);

                    boolean isDirty = false;
                    for (OntologyManager.ImportPropertyDescriptor ipd : pds.properties)
                    {
                        if (!current.containsKey(ipd.pd.getName()))
                        {
                            // issue 19943, renamed property descriptors retain their original name-based uri
                            if (currentPropertyURIs.contains(ipd.pd.getPropertyURI()))
                                ipd.pd.setPropertyURI(getUniquePropertyURI(ipd.pd.getPropertyURI(), currentPropertyURIs));

                            DomainProperty domainProperty = domain.addPropertyOfPropertyDescriptor(ipd.pd);
                            ipd.validators.forEach(domainProperty::addValidator);
                            domainProperty.setConditionalFormats(ipd.formats);
                            isDirty = true;
                        }
                        else
                            ctx.getLogger().warn("Table: " + tableName + " already has a field named: " + ipd.pd.getName() + ", ignoring the imported field");
                    }

                    if (isDirty)
                    {
                        if (domain.getDomainKind().canEditDefinition(ctx.getUser(), domain))
                            domain.save(ctx.getUser());
                        else
                            ctx.getLogger().error("Unable to update the domain for table: " + tableName + " because the user does not have edit privileges.");
                    }
                }
                else
                    ctx.getLogger().warn("Unable to get domain for table: " + tableName);
            }
            else
                ctx.getLogger().warn("No tableinfo for table : " + tableName);
        }
    }

    private String getUniquePropertyURI(String uri, Set<String> existingURIs)
    {
        for (int i=1; i < 50; i++)
        {
            String newUri = uri + "." + i;
            if (!existingURIs.contains(newUri))
                return newUri;
        }
        return uri;
    }

    protected void importTableData(StudyImportContext ctx, VirtualFile vf, StudyQuerySchema.TablePackage tablePackage,
                                                     @Nullable MapBuilder transformBuilder,
                                                     @Nullable TransformHelper transformHelper) throws Exception
    {
        TableInfo tableInfo = tablePackage.getTableInfo();
        Container container = tablePackage.getContainer();

        // TODO: Why isn't tablePackage.isProjectLevel() true in STUDY_TABLE_NAME case
        if (!StudyQuerySchema.STUDY_TABLE_NAME.equalsIgnoreCase(tableInfo.getName()) && !tablePackage.isProjectLevel())
        {
            // Consider: defer to QueryUpdateService?
            deleteData(container, tableInfo);
        }

        BatchValidationException errors = new BatchValidationException();
        if (null != tableInfo)
        {
            String fileName = getFileName(tableInfo);
            try (InputStream tsv = vf.getInputStream(fileName))
            {
                if (null != tsv)
                {
                    DataLoader loader = DataLoader.get().createLoader(fileName, null, tsv, true, null, TabLoader.TSV_FILE_TYPE);
                    QueryUpdateService qus = tableInfo.getUpdateService();

                    if (transformBuilder != null || transformHelper != null)
                    {
                        // optimally we would use DIB to convert imported FKs so that the relationships are intact, but since
                        // the underlying tableInfo's do not implement UpdateableTableInfo, we are forced to use the deprecated insertRows
                        // method on QueryUpdateService.

                        List<Map<String, Object>> rows = loader.load();
                        List<Map<String, Object>> insertedRows;

                        if (transformHelper != null)
                        {
                            if (!(transformHelper instanceof TransformHelper))
                                throw new ImportException("The specified transform helper does not implement the TransformHelper interface");
                            insertedRows = qus.insertRows(ctx.getUser(), container, transformHelper.transform(ctx, rows), errors, null, null);
                        }
                        else
                            insertedRows = qus.insertRows(ctx.getUser(), container, rows, errors, null, null);

                        if (transformBuilder != null)
                        {
                            if (!(transformBuilder instanceof MapBuilder))
                                throw new ImportException("The specified transform builder does not implement the MapBuilder interface");
                            transformBuilder.createTransformInfo(ctx, rows, insertedRows);
                        }
                    }
                    else
                    {
                        qus.importRows(ctx.getUser(), container, loader, errors, null, null);
                    }
                }
                else
                    ctx.getLogger().warn("Unable to open the file at: " + fileName);
            }
        }
        else
            ctx.getLogger().warn("NULL tableInfo passed into importTableData.");

        if (errors.hasErrors())
            throw new ImportException(errors.getMessage());
    }

    protected String getFileName(TableInfo tableInfo)
    {
        return tableInfo.getName().toLowerCase() + ".tsv";
    }

    /**
     * Interface which allows the transform to create and initialize the transform based on the original
     * and inserted data.
     */
    interface MapBuilder
    {
        void createTransformInfo(StudyImportContext ctx, List<Map<String, Object>> origRows, List<Map<String, Object>> insertedRows) throws ImportException;
    }

    /**
     * Interface which allows the transform to update the original data before insertion
     */
    interface TransformHelper
    {
        List<Map<String, Object>> transform(StudyImportContext ctx, List<Map<String, Object>> origRows) throws ImportException;
    }

    protected class TransformHelperComposition implements TransformHelper
    {
        private List<TransformHelper> _transformHelpers = new ArrayList<>();

        public TransformHelperComposition(List<TransformHelper> transformHelpers)
        {
            _transformHelpers = transformHelpers;
        }

        @Override
        public List<Map<String, Object>> transform(StudyImportContext ctx, List<Map<String, Object>> origRows) throws ImportException
        {
            List<Map<String, Object>> result = origRows;
            for (TransformHelper transformHelper : _transformHelpers)
                result = transformHelper.transform(ctx, result);
            return result;
        }
    }

    /**
     * Transform which manages foreign keys to the rowId of a shared table with RowId PK
     *      Adds rowId mappings for inserted rows
     */
    protected class SharedTableMapBuilder implements MapBuilder

    {
        protected String _fieldName;
        protected Map<Object, Object> _idMap;

        public SharedTableMapBuilder(Map<Object, Object> idMap, String fieldName)
        {
            _idMap = idMap;
            _fieldName = fieldName;
        }

        @Override
        public void createTransformInfo(StudyImportContext ctx, List<Map<String, Object>> origRows, List<Map<String, Object>> insertedRows) throws ImportException
        {
            Map<String, Map<String, Object>> rowMap = new CaseInsensitiveHashMap<>();
            for (Map<String, Object> row: origRows)
                if (null != row.get(_fieldName))
                    rowMap.put(row.get(_fieldName).toString(), row);

            for (Map<String, Object> inserted : insertedRows)
            {
                if (null != inserted.get(_fieldName))
                {
                    Map<String, Object> orig = rowMap.get(inserted.get(_fieldName));
                    if (orig.containsKey("RowId") && inserted.containsKey("RowId"))
                    {
                        _idMap.put(orig.get("RowId"), inserted.get("RowId"));
                    }
                }
            }
        }
    }

    /**
     * Transform which manages foreign keys to a non-shared table
     */
    protected class NonSharedTableMapBuilder implements MapBuilder
    {
        protected Map<Object, Object> _idMap;

        public NonSharedTableMapBuilder(Map<Object, Object> idMap)
        {
            _idMap = idMap;
        }

        @Override
        public void createTransformInfo(StudyImportContext ctx, List<Map<String, Object>> origRows, List<Map<String, Object>> insertedRows) throws ImportException
        {
            if (origRows.size() != insertedRows.size())
                throw new ImportException("All original rows should be inserted into non-shared table.");

            for (int i=0; i < origRows.size(); i++)
            {
                Map<String, Object> orig = origRows.get(i);
                Map<String, Object> inserted = insertedRows.get(i);

                if (orig.containsKey("RowId") && inserted.containsKey("RowId"))
                {
                    _idMap.put(orig.get("RowId"), inserted.get("RowId"));
                }
            }
        }
    }


    /**
     * A transform helper which checks whether a data value already exists at the project level before importing the
     * same value at the folder level.
     */
    protected class PreserveExistingProjectData implements TransformHelper
    {
        protected final User _user;
        protected final TableInfo _tableInfo;
        protected final SortedSet<String> _fieldNames = new TreeSet<>();    // fields to match on
        protected final String _keyName;            // key field name
        protected final Map _keyMap;
        protected final boolean _verifyDuplicateFieldKeys;
        protected Map<String, Object> _existingValues;

        public PreserveExistingProjectData(User user, TableInfo table, String fieldName, @Nullable String keyName, @Nullable Map<Object, Object> keyMap)
        {
            this(user, table, fieldName, keyName, keyMap, false);
        }

        public PreserveExistingProjectData(User user, TableInfo table, Set<String> fieldNames)
        {
            _user = user;
            _tableInfo = table;
            _fieldNames.addAll(fieldNames);
            _keyName = null;
            _keyMap = null;
            _verifyDuplicateFieldKeys = false;
        }

        public PreserveExistingProjectData(User user, TableInfo table, String fieldName, @Nullable String keyName, @Nullable Map<Object, Object> keyMap,
                                           boolean verifyDuplicateFieldKeys)
        {
            _user = user;
            _tableInfo = table;
            _fieldNames.add(fieldName);
            _keyName = keyName;
            _keyMap = keyMap;
            _verifyDuplicateFieldKeys = verifyDuplicateFieldKeys;
        }

        protected void initializeData()
        {
            if (_existingValues == null)
            {
                ContainerFilter currentFilter = null;
                try {

                    if (_tableInfo instanceof ContainerFilterable)
                    {
                        currentFilter = _tableInfo.getContainerFilter();
                        ((ContainerFilterable)_tableInfo).setContainerFilter(new ContainerFilter.Project(_user));
                    }

                    Set<String> columnNames = new HashSet<>();
                    columnNames.addAll(_fieldNames);
                    if (null != _keyName)
                        columnNames.add(_keyName);
                    Collection<Map<String, Object>> existingRows;
                    TableSelector selector = new TableSelector(_tableInfo, columnNames);
                    existingRows = selector.getMapCollection();

                    _existingValues = new CaseInsensitiveHashMap<>();
                    for (Map<String, Object> row : existingRows)
                    {
                        String existingValueKey = getExistingValueKey(row, _fieldNames);
                        _existingValues.put(existingValueKey, row.get(_keyName));
                    }
                }
                finally
                {
                        if (_tableInfo instanceof ContainerFilterable && currentFilter != null)
                        ((ContainerFilterable)_tableInfo).setContainerFilter(currentFilter);
                }
            }
        }

        @Override
        public List<Map<String, Object>> transform(StudyImportContext ctx, List<Map<String, Object>> origRows) throws ImportException
        {
            initializeData();
            List<Map<String, Object>> newRows = new ArrayList<>();
            Set<String> keys = new HashSet<>();

            for (Map<String, Object> row : origRows)
            {
                Map<String, Object> currentRow = new CaseInsensitiveHashMap<>();
                currentRow.putAll(row);

                String existingValueKey = getExistingValueKey(currentRow, _fieldNames);
                if (StringUtils.isNotEmpty(existingValueKey))
                {
                    if (!_existingValues.containsKey(existingValueKey))
                    {
                        // make sure there are no duplicate keys
                        if (!keys.contains(existingValueKey))
                        {
                            newRows.add(currentRow);
                            if (_verifyDuplicateFieldKeys)
                                keys.add(existingValueKey);
                        }
                    }
                    else if (null != _keyMap)
                    {
                        Object key = currentRow.get(_keyName);
                        if (null != key)
                            _keyMap.put(key, _existingValues.get(existingValueKey));
                    }
                }
            }
            return newRows;
        }

        protected String getExistingValueKey(Map<String, Object> currentRow, Set<String> fieldNames)
        {
            StringBuilder result = new StringBuilder();
            String sep = "";
            for (String fieldName : fieldNames)
                if (currentRow.containsKey(fieldName))
                {
                    Object obj = currentRow.get(fieldName);
                    if (null != obj)
                    {
                        result.append(sep).append(obj.toString());
                        sep = "-";
                    }
                }
            return result.toString();
        }
    }
}
