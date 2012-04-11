package org.labkey.query;

import org.apache.xmlbeans.XmlException;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.externalSchemaDef.ExternalSchemaDefDocument;
import org.labkey.data.xml.externalSchemaDef.ExternalSchemaDefType;
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
        public void process(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            File defDir = ctx.getDir("externalSchemaDefs");
            if (null != defDir)
            {
                File[] defXmlFiles = defDir.listFiles(new FilenameFilter() {
                    public boolean accept(File dir, String name)
                    {
                        return name.endsWith(ExternalSchemaDefWriterFactory.FILE_EXTENSION);
                    }
                });

                int count = 0;
                for (File defFile : defXmlFiles)
                {
                    ExternalSchemaDefDocument defDoc;
                    try
                    {
                        defDoc = ExternalSchemaDefDocument.Factory.parse(defFile, XmlBeansUtil.getDefaultParseOptions());
                        XmlBeansUtil.validateXmlDocument(defDoc);
                    }
                    catch (XmlException e)
                    {
                        throw new InvalidFileException(root, defFile, e);
                    }
                    catch (XmlValidationException e)
                    {
                        throw new InvalidFileException(root, defFile, e);
                    }

                    ExternalSchemaDefType defXml = defDoc.getExternalSchemaDef();

                    ExternalSchemaDef existingDef = QueryManager.get().getExternalSchemaDef(ctx.getContainer(), defXml.getUserSchemaName()); 
                    if (null != existingDef)
                    {
                        // TODO: should we instead be deleting the existing definition to replace with the new one?
                        ctx.getLogger().warn("A schema by the name " + defXml.getUserSchemaName() + " is already defined in this folder.");
                    }
                    else
                    {
                        ExternalSchemaForm form = new ExternalSchemaForm();
                        form.setContainer(ctx.getContainer());
                        form.setUser(ctx.getUser());
                        form.setTypedValue("dataSource", defXml.getDataSource());
                        form.setTypedValue("dbSchemaName", defXml.getDbSchemaName());
                        form.setTypedValue("userSchemaName", defXml.getUserSchemaName());
                        form.setTypedValue("editable", defXml.getEditable());
                        form.setTypedValue("indexable", defXml.getIndexable());

                        if (defXml.isSetTables())
                        {
                            String[] tables = defXml.getTables().getTableNameArray();
                            StringBuilder tablesSb = new StringBuilder();
                            String sep = "";
                            for (String table : tables)
                            {
                                tablesSb.append(sep).append(table);
                                sep = ",";
                            }
                            form.setTypedValue("tables", tablesSb.toString());
                        }

                        if (defXml.isSetMetadata())
                        {
                            form.setTypedValue("metaData", defXml.getMetadata().xmlText());
                        }

                        form.doInsert();
                        count++;
                    }
                }

                if (count > 0)
                    ctx.getLogger().info(count + " external schema definition" + (defXmlFiles.length > 1 ? "s" : "") + " imported");
            }
        }

        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return null;
        }
    }
}
