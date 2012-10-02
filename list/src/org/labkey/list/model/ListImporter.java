/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListDefinition.KeyType;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Type;
import org.labkey.api.gwt.client.ui.domain.ImportException;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.FacetingBehaviorType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.list.xml.ListsDocument;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
* User: adam
* Date: Aug 27, 2009
* Time: 2:12:01 PM
*/
public class ListImporter
{
    private static final String TYPE_NAME_COLUMN = "ListName";

    public void process(VirtualFile listsDir, Container c, User user, List<String> errors, Logger log) throws Exception
    {
        TablesDocument tablesDoc;
        try
        {
            XmlObject listXml = listsDir.getXmlBean(ListWriter.SCHEMA_FILENAME);
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

        Map<String, ListsDocument.Lists.List> listSettingsMap = new HashMap<String, ListsDocument.Lists.List>();

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
        List<String> names = new LinkedList<String>();

        Map<String, ListDefinition> lists = ListService.get().getLists(c);

        for (TableType tableType : tablesXml.getTableArray())
        {
            String name = tableType.getTableName();
            names.add(name);

            ListDefinition def = lists.get(name);

            if (null != def)
                def.delete(user);

            try
            {
                createNewList(c, user, name, tableType, listSettingsMap.get(name), errors);
            }
            catch (ImportException e)
            {
                throw new ImportException("Error creating list \"" + name + "\": " + e.getMessage());
            }
        }

        lists = ListService.get().getLists(c);

        for (String name : names)
        {
            ListDefinition def = lists.get(name);

            if (null != def)
            {
                String legalName = FileUtil.makeLegalName(name);
                String fileName = legalName + ".tsv";
                InputStream tsv = listsDir.getInputStream(fileName);
                if (null != tsv)
                {
                    BatchValidationException batchErrors = new BatchValidationException();
                    DataLoader loader = DataLoader.get().createLoader(fileName, tsv, false, null);
                    int count = def.insertListItems(user, loader, batchErrors, listsDir.getDir(legalName), null);
                    for (ValidationException v : batchErrors.getRowErrors())
                        errors.add(v.getMessage());
                    // TODO: Error the entire job on import error?
                }
            }
        }

        log.info(names.size() + " list" + (1 == names.size() ? "" : "s") + " imported");
    }

    private void createNewList(Container c, User user, String listName, TableType listXml, @Nullable ListsDocument.Lists.List listSettingsXml, List<String> errors) throws Exception
    {
        String keyName = listXml.getPkColumnName();

        if (null == keyName)
        {
            errors.add("List \"" + listName + "\": no pkColumnName set.");
        }

        KeyType pkType = getKeyType(listXml, keyName);

        ListDefinition list = ListService.get().createList(c, listName);
        list.setKeyName(keyName);
        list.setKeyType(pkType);
        list.setDescription(listXml.getDescription());

        if (listXml.isSetTitleColumn())
            list.setTitleColumn(listXml.getTitleColumn());

        if (null != listSettingsXml)
        {
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
        }

        list.save(user);

        // TODO: This code is largely the same as SchemaXmlReader -- should consolidate

        // Set up RowMap with all the keys that OntologyManager.importTypes() handles
        RowMapFactory<Object> mapFactory = new RowMapFactory<Object>(TYPE_NAME_COLUMN, "Property", "PropertyURI", "Label", "Description",
                "RangeURI", "NotNull", "ConceptURI", "Format", "InputType", "HiddenColumn", "MvEnabled", "LookupFolderPath",
                "LookupSchema", "LookupQuery", "URL", "ImportAliases", "ShownInInsertView", "ShownInUpdateView",
                "ShownInDetailsView", "Measure", "Dimension", "ConditionalFormats", "FacetingBehaviorType", "Protected");
        List<Map<String, Object>> importMaps = new LinkedList<Map<String, Object>>();

        for (ColumnType columnXml : listXml.getColumns().getColumnArray())
        {
            String columnName = columnXml.getColumnName();

            if (columnXml.getIsKeyField())
            {
                if (!columnName.equalsIgnoreCase(keyName))
                    throw new ImportException("More than one key specified: '" + keyName + "' and '" + columnName + "'");

                continue;  // Skip the key columns
            }

            String dataType = columnXml.getDatatype();
            Type t = Type.getTypeBySqlTypeName(dataType);

            if (t == null)
                t = Type.getTypeByLabel(dataType);

            if (t == null)
                throw new ImportException("Unknown property type \"" + dataType + "\" for property \"" + columnXml.getColumnName() + "\".");

            // Assume nullable if not specified
            boolean notNull = columnXml.isSetNullable() && !columnXml.getNullable();

            boolean mvEnabled = columnXml.isSetIsMvEnabled() ? columnXml.getIsMvEnabled() : null != columnXml.getMvColumnName();

            // These default to being visible if nothing's specified in the XML
            boolean shownInInsertView = !columnXml.isSetShownInInsertView() || columnXml.getShownInInsertView();
            boolean shownInUpdateView = !columnXml.isSetShownInUpdateView() || columnXml.getShownInUpdateView();
            boolean shownInDetailsView = !columnXml.isSetShownInDetailsView() || columnXml.getShownInDetailsView();

            boolean measure = columnXml.isSetMeasure() && columnXml.getMeasure();
            boolean dimension = columnXml.isSetDimension() && columnXml.getDimension();

            FacetingBehaviorType.Enum type = columnXml.getFacetingBehavior();
            String facetingBehaviorType = FacetingBehaviorType.AUTOMATIC.toString();
            if (type != null)
                facetingBehaviorType = type.toString();

            boolean isProtected = columnXml.isSetProtected() && columnXml.getProtected();

            Set<String> importAliases = new LinkedHashSet<String>();
            if (columnXml.isSetImportAliases())
            {
                importAliases.addAll(Arrays.asList(columnXml.getImportAliases().getImportAliasArray()));
            }

            ColumnType.Fk fk = columnXml.getFk();

            Map<String, Object> map = mapFactory.getRowMap(new Object[]{
                listName,
                columnName,
                columnXml.getPropertyURI(),
                columnXml.getColumnTitle(),
                columnXml.getDescription(),
                t.getXsdType(),
                notNull,
                columnXml.getConceptURI(),
                columnXml.getFormatString(),
                columnXml.isSetInputType() ? columnXml.getInputType() : null,
                columnXml.getIsHidden(),
                mvEnabled,
                null != fk ? fk.getFkFolderPath() : null,
                null != fk ? fk.getFkDbSchema() : null,
                null != fk ? fk.getFkTable() : null,
                columnXml.getUrl(),
                ColumnRenderProperties.convertToString(importAliases),
                shownInInsertView,
                shownInUpdateView,
                shownInDetailsView,
                measure,
                dimension,
                ConditionalFormat.convertFromXML(columnXml.getConditionalFormats()),
                facetingBehaviorType,
                isProtected
            });

            importMaps.add(map);
        }

        final String typeURI = list.getDomain().getTypeURI();

        DomainURIFactory factory = new DomainURIFactory() {
            public String getDomainURI(String name)
            {
                return typeURI;
            }
        };

        OntologyManager.importTypes(factory, TYPE_NAME_COLUMN, importMaps, errors, c, true, user);
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
}
