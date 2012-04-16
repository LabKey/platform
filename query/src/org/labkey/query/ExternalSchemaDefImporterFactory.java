package org.labkey.query;

import org.apache.xmlbeans.XmlException;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
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

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collection;

/**
 * User: cnathe
 * Date: Apr 10, 2012
 */
public class ExternalSchemaDefImporterFactory implements FolderImporterFactory
{
    @Override
    public FolderImporter create()
    {
        return new ExternalSchemaDefImporter();
    }

    @Override
    public boolean isFinalImporter()
    {
        return false;
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
            File externalSchemaDir = ctx.getDir(ExternalSchemaDefWriterFactory.DEFAULT_DIRECTORY);
            if (null != externalSchemaDir)
            {
                job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());

                File[] schemaXmlFiles = externalSchemaDir.listFiles(new FilenameFilter() {
                    public boolean accept(File dir, String name)
                    {
                        return name.endsWith(ExternalSchemaDefWriterFactory.FILE_EXTENSION);
                    }
                });

                for (File schemaFile : schemaXmlFiles)
                {
                    ExternalSchemaDocument schemaDoc;
                    try
                    {
                        schemaDoc = ExternalSchemaDocument.Factory.parse(schemaFile, XmlBeansUtil.getDefaultParseOptions());
                        XmlBeansUtil.validateXmlDocument(schemaDoc);
                    }
                    catch (XmlException e)
                    {
                        throw new InvalidFileException(root, schemaFile, e);
                    }
                    catch (XmlValidationException e)
                    {
                        throw new InvalidFileException(root, schemaFile, e);
                    }

                    ExternalSchemaType schemaXml = schemaDoc.getExternalSchema();

                    // TODO: make this a case-insensitive lookup
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

                ctx.getLogger().info(schemaXmlFiles.length + " external schema definition" + (schemaXmlFiles.length > 1 ? "s" : "") + " imported");
                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return null;
        }
    }
}
