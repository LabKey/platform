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

import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.security.User;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.externalSchema.ExportedSchemaType;
import org.labkey.data.xml.externalSchema.ExternalSchemaDocument;
import org.labkey.data.xml.externalSchema.ExternalSchemaType;
import org.labkey.data.xml.externalSchema.LinkedSchemaDocument;
import org.labkey.data.xml.externalSchema.LinkedSchemaType;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.query.controllers.AbstractExternalSchemaForm;
import org.labkey.query.controllers.ExternalSchemaForm;
import org.labkey.query.controllers.LinkedSchemaForm;
import org.labkey.query.persist.ExternalSchemaDef;
import org.labkey.query.persist.LinkedSchemaDef;
import org.labkey.query.persist.QueryManager;

import java.util.Collection;
import java.util.Collections;

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
                    if (schemaFileName.endsWith(ExternalSchemaDefWriterFactory.EXTERNAL_SCHEMA_FILE_EXTENSION) || schemaFileName.endsWith(ExternalSchemaDefWriterFactory.LINKED_SCHEMA_FILE_EXTENSION))
                        importSchema(ctx, root, externalSchemaDir, schemaFileName);
                }

                ctx.getLogger().info(schemaXmlFileNames.length + " external schema definition" + (schemaXmlFileNames.length > 1 ? "s" : "") + " imported");
                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        private void importSchema(ImportContext<FolderDocument.Folder> ctx, VirtualFile root, VirtualFile externalSchemaDir, String schemaFileName) throws Exception
        {
            XmlObject schemaXmlFile = externalSchemaDir.getXmlBean(schemaFileName);
            String relativePath = root.getRelativePath(schemaFileName);

            ExportedSchemaType exportedXml;
            AbstractExternalSchemaForm form;
            if (schemaXmlFile instanceof ExternalSchemaDocument)
            {
                ExternalSchemaDocument schemaDoc = (ExternalSchemaDocument)schemaXmlFile;
                XmlBeansUtil.validateXmlDocument(schemaDoc, relativePath);

                ExternalSchemaType schemaXml = schemaDoc.getExternalSchema();
                exportedXml = schemaXml;

                ExternalSchemaDef existingDef = QueryManager.get().getExternalSchemaDef(ctx.getContainer(), schemaXml.getUserSchemaName());
                if (null != existingDef)
                    QueryManager.get().delete(ctx.getUser(), existingDef);

                form = new ExternalSchemaForm();
                form.setTypedValue("schematype", "external");
                form.setTypedValue("dataSource", schemaXml.getDataSource());
                form.setTypedValue("editable", schemaXml.getEditable());
                form.setTypedValue("indexable", schemaXml.getIndexable());
            }
            else if (schemaXmlFile instanceof LinkedSchemaDocument)
            {
                LinkedSchemaDocument schemaDoc = (LinkedSchemaDocument)schemaXmlFile;
                XmlBeansUtil.validateXmlDocument(schemaDoc, relativePath);

                LinkedSchemaType schemaXml = schemaDoc.getLinkedSchema();
                exportedXml = schemaXml;

                LinkedSchemaDef existingDef = QueryManager.get().getLinkedSchemaDef(ctx.getContainer(), schemaXml.getUserSchemaName());
                if (null != existingDef)
                    QueryManager.get().delete(ctx.getUser(), existingDef);

                form = new LinkedSchemaForm();
                form.setTypedValue("schematype", "linked");
                String containerPath = schemaXml.getSourceContainer();
                Container container = ContainerManager.getForPath(containerPath);
                if (container == null)
                    container = ContainerManager.getForId(containerPath);
                if (container != null)
                    form.setTypedValue("dataSource", container.getId());
            }
            else
                throw new ImportException("Unable to get an instance of external or linked schema from " + relativePath);

            addCommonProperties(ctx.getContainer(), ctx.getUser(), exportedXml, form);

            // TODO: this should use QueryManager.get().insert(ctx.getUser(), externalSchemaDef);
            form.doInsert();
        }

        private void addCommonProperties(Container c, User user, ExportedSchemaType exportedXml, AbstractExternalSchemaForm form)
        {
            form.setContainer(c);
            form.setUser(user);
            form.setTypedValue("userSchemaName", exportedXml.getUserSchemaName());

            if (exportedXml.isSetSchemaTemplate())
                form.setTypedValue("schemaTemplate", exportedXml.getSchemaTemplate());

            String sourceSchemaName = null;
            if (exportedXml.isSetSourceSchemaName())
                sourceSchemaName = exportedXml.getSourceSchemaName();
            else if (exportedXml instanceof ExternalSchemaType && ((ExternalSchemaType)exportedXml).isSetDbSchemaName())
                sourceSchemaName = ((ExternalSchemaType)exportedXml).getDbSchemaName();

            form.setTypedValue("sourceSchemaName", sourceSchemaName);

            String schemaTables = "*";
            if (exportedXml.isSetTables())
            {
                String[] tables = exportedXml.getTables().getTableNameArray();
                StringBuilder tablesSb = new StringBuilder();
                String sep = "";
                for (String table : tables)
                {
                    tablesSb.append(sep).append(table);
                    sep = ",";
                }
                schemaTables = tablesSb.toString();
            }
            form.setTypedValue("tables", schemaTables);

            if (exportedXml.isSetMetadata())
                form.setTypedValue("metaData", exportedXml.getMetadata().xmlText());
        }


        @NotNull
        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return Collections.emptyList();
        }

        @Override
        public boolean supportsVirtualFile()
        {
            return true;
        }
    }
}
