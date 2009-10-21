/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.experiment.list;

import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListDefinition.KeyType;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Type;
import org.labkey.api.gwt.client.ui.domain.ImportException;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.study.ExternalStudyImporter;
import org.labkey.api.study.ExternalStudyImporterFactory;
import org.labkey.api.study.InvalidFileException;
import org.labkey.api.study.StudyContext;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.util.FileUtil;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.study.xml.StudyDocument;

import java.io.File;
import java.util.*;

/*
* User: adam
* Date: Aug 27, 2009
* Time: 2:12:01 PM
*/
public class ListImporter implements ExternalStudyImporter
{
    private static final String TYPE_NAME_COLUMN = "ListName";

    public String getDescription()
    {
        return "lists";
    }

    public void process(StudyContext ctx, File root) throws Exception
    {
        StudyDocument.Study.Lists listsXml = ctx.getStudyXml().getLists();

        if (null != listsXml)
        {
            File listsDir = ctx.getStudyDir(root, listsXml.getDir());
            File schemaFile = new File(listsDir, "lists.xml");

            TablesDocument tablesDoc;

            try
            {
                tablesDoc = TablesDocument.Factory.parse(schemaFile, XmlBeansUtil.getDefaultParseOptions());
                XmlBeansUtil.validateXmlDocument(tablesDoc);
            }
            catch (XmlValidationException xve)
            {
                // Note: different constructor than the one below
                throw new InvalidFileException(root, schemaFile, xve);
            }
            catch (Exception e)
            {
                throw new InvalidFileException(root, schemaFile, e);
            }

            TablesDocument.Tables tablesXml = tablesDoc.getTables();
            List<String> names = new LinkedList<String>();

            Map<String, ListDefinition> lists = ListService.get().getLists(ctx.getContainer());

            for (TableType tableType : tablesXml.getTableArray())
            {
                String name = tableType.getTableName();
                names.add(name);

                ListDefinition def = lists.get(name);

                if (null != def)
                    def.delete(ctx.getUser());

                try
                {
                    createNewList(ctx, name, tableType);
                }
                catch (ImportException e)
                {
                    throw new ImportException("Error creating list \"" + name + "\": " + e.getMessage());
                }
            }

            lists = ListService.get().getLists(ctx.getContainer());

            for (String name : names)
            {
                ListDefinition def = lists.get(name);

                if (null != def)
                {
                    String legalName = FileUtil.makeLegalName(name);
                    File tsv = new File(listsDir, legalName + ".tsv");

                    if (tsv.exists())
                    {
                        List<String> errors = def.insertListItems(ctx.getUser(), DataLoader.getDataLoaderForFile(tsv), new File(listsDir, legalName));

                        for (String error : errors)
                            ctx.getLogger().error(error);

                        // TODO: Error the entire job on import error?
                    }
                }
            }

            ctx.getLogger().info(names.size() + " list" + (1 == names.size() ? "" : "s") + " imported");
        }
    }

    private void createNewList(StudyContext ctx, String listName, TableType listXml) throws Exception
    {
        String keyName = listXml.getPkColumnName();

        if (null == keyName)
        {
            ctx.getLogger().error("List \"" + listName + "\": no pkColumnName set.");
            return;
        }

        KeyType pkType = getKeyType(listXml, keyName);

        ListDefinition list = ListService.get().createList(ctx.getContainer(), listName);
        list.setKeyName(keyName);
        list.setKeyType(pkType);
        list.setDescription(listXml.getDescription());
        list.save(ctx.getUser());

        // TODO: This code is largely the same as SchemaXmlReader -- should consolidate

        // Set up RowMap with all the keys that OntologyManager.importTypes() handles
        RowMapFactory<Object> mapFactory = new RowMapFactory<Object>(TYPE_NAME_COLUMN, "Property", "Label", "Description", "RangeURI", "NotNull", "ConceptURI", "Format", "HiddenColumn", "MvEnabled", "LookupFolderPath", "LookupSchema", "LookupQuery", "URL", "ImportAliases");
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

            Set<String> importAliases = new LinkedHashSet<String>();
            if (columnXml.isSetImportAliases())
            {
                importAliases.addAll(Arrays.asList(columnXml.getImportAliases().getImportAliasArray()));
            }

            ColumnType.Fk fk = columnXml.getFk();

            Map<String, Object> map = mapFactory.getRowMap(new Object[]{
                listName,
                columnName,
                columnXml.getColumnTitle(),
                columnXml.getDescription(),
                t.getXsdType(),
                notNull,
                null,  // TODO: conceptURI
                columnXml.getFormatString(),
                columnXml.getIsHidden(),
                mvEnabled,
                null != fk ? fk.getFkFolderPath() : null,
                null != fk ? fk.getFkDbSchema() : null,
                null != fk ? fk.getFkTable() : null,
                columnXml.getUrl(),
                ColumnRenderProperties.convertToString(importAliases)
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

        List<String> importErrors = new LinkedList<String>();
        OntologyManager.importTypes(factory, TYPE_NAME_COLUMN, importMaps, importErrors, ctx.getContainer(), true);

        for (String error : importErrors)
            ctx.getLogger().error(error);
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

    public Collection<PipelineJobWarning> postProcess(StudyContext ctx, File root) throws Exception
    {
        //nothing for now
        return null;
    }

    public static class Factory implements ExternalStudyImporterFactory
    {
        public ExternalStudyImporter create()
        {
            return new ListImporter();
        }
    }
}
