/*
 * Copyright (c) 2012 LabKey Corporation
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

import org.apache.xmlbeans.XmlObject;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.externalSchema.ExternalSchemaDocument;
import org.labkey.data.xml.externalSchema.ExternalSchemaType;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.query.controllers.ExternalSchemaForm;
import org.labkey.query.persist.ExternalSchemaDef;
import org.labkey.query.persist.QueryManager;

import java.util.Collection;

/**
 * User: cnathe
 * Date: Apr 10, 2012
 */
public class ExternalSchemaDefImporterFactory extends AbstractFolderImportFactory
{
    @Override
    public FolderImporter create()
    {
        return new ExternalSchemaDefImporter();
    }

    public class ExternalSchemaDefImporter implements FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDescription()
        {
            return "external schema definitions";
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            VirtualFile externalSchemaDir = ctx.getDir("externalSchemas");

            if (null != externalSchemaDir)
            {
                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());

                String[] schemaXmlFileNames = externalSchemaDir.list();

                for (String schemaFileName : schemaXmlFileNames)
                {
                    // skip over any files that don't end with the expected extension
                    if (!schemaFileName.endsWith(ExternalSchemaDefWriterFactory.FILE_EXTENSION))
                        continue;

                    XmlObject schemaXmlFile = externalSchemaDir.getXmlBean(schemaFileName);

                    ExternalSchemaDocument schemaDoc;
                    try
                    {
                        if (schemaXmlFile instanceof ExternalSchemaDocument)
                        {
                            schemaDoc = (ExternalSchemaDocument)schemaXmlFile;
                            XmlBeansUtil.validateXmlDocument(schemaDoc);
                        }
                        else
                            throw new ImportException("Unable to get an instance of ExternalSchemaDocument from " + schemaXmlFile);
                    }
                    catch (XmlValidationException e)
                    {
                        throw new InvalidFileException(root.getRelativePath(schemaFileName), e);
                    }

                    ExternalSchemaType schemaXml = schemaDoc.getExternalSchema();

                    ExternalSchemaDef existingDef = QueryManager.get().getExternalSchemaDef(ctx.getContainer(), schemaXml.getUserSchemaName());
                    if (null != existingDef)
                    {
                        QueryManager.get().delete(ctx.getUser(), existingDef);
                    }

                    ExternalSchemaForm form = new ExternalSchemaForm();
                    form.setContainer(ctx.getContainer());
                    form.setUser(ctx.getUser());
                    form.setTypedValue("dataSource", schemaXml.getDataSource());
                    form.setTypedValue("dbSchemaName", schemaXml.getDbSchemaName());
                    form.setTypedValue("userSchemaName", schemaXml.getUserSchemaName());
                    form.setTypedValue("editable", schemaXml.getEditable());
                    form.setTypedValue("indexable", schemaXml.getIndexable());

                    if (schemaXml.isSetTables())
                    {
                        String[] tables = schemaXml.getTables().getTableNameArray();
                        StringBuilder tablesSb = new StringBuilder();
                        String sep = "";
                        for (String table : tables)
                        {
                            tablesSb.append(sep).append(table);
                            sep = ",";
                        }
                        form.setTypedValue("tables", tablesSb.toString());
                    }

                    if (schemaXml.isSetMetadata())
                    {
                        form.setTypedValue("metaData", schemaXml.getMetadata().xmlText());
                    }

                    // TODO: this should use QueryManager.get().insert(ctx.getUser(), externalSchemaDef);
                    form.doInsert();
                }

                ctx.getLogger().info(schemaXmlFileNames.length + " external schema definition" + (schemaXmlFileNames.length > 1 ? "s" : "") + " imported");
                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return null;
        }

        @Override
        public boolean supportsVirtualFile()
        {
            return true;
        }
    }
}
