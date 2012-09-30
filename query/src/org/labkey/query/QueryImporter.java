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
package org.labkey.query;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.query.QueryDocument;
import org.labkey.data.xml.query.QueryType;
import org.labkey.query.persist.QueryManager;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:21:56 PM
 */
public class QueryImporter implements FolderImporter
{
    public String getDescription()
    {
        return "queries";
    }

    public void process(PipelineJob job, ImportContext ctx, VirtualFile root) throws ServletException, XmlException, IOException, SQLException, ImportException
    {
        VirtualFile queriesDir = ctx.getDir("queries");

        if (null != queriesDir)
        {
            if (null != job)
                job.setStatus("IMPORT " + getDescription());
            ctx.getLogger().info("Loading " + getDescription());

            // get the list of files and split them into sql and xml file name arrays
            String[] queryFileNames = queriesDir.list();
            ArrayList<String> sqlFileNames = new ArrayList<String>();
            Map<String, QueryDocument> metaFilesMap = new HashMap<String, QueryDocument>();
            for (String fileName : queryFileNames)
            {
                if (fileName.endsWith(QueryWriter.FILE_EXTENSION))
                {
                    // make sure a SQL file/input stream exists before adding it to the array
                    if (null != queriesDir.getInputStream(fileName))
                        sqlFileNames.add(fileName);
                }
                else if (fileName.endsWith(QueryWriter.META_FILE_EXTENSION))
                {
                    // make sure the XML file is valid before adding it to the map
                    XmlObject metaXml = queriesDir.getXmlBean(fileName);
                    try
                    {
                        if (metaXml instanceof QueryDocument)
                        {
                            QueryDocument queryDoc = (QueryDocument)metaXml;
                            XmlBeansUtil.validateXmlDocument(queryDoc, fileName);
                            metaFilesMap.put(fileName, queryDoc);
                        }
                        else
                            throw new ImportException("Unable to get an instance of QueryDocument from " + fileName);
                    }
                    catch (XmlValidationException e)
                    {
                        throw new InvalidFileException(queriesDir.getRelativePath(fileName), e);
                    }
                }
            }

            for (String sqlFileName : sqlFileNames)
            {
                String baseFilename = sqlFileName.substring(0, sqlFileName.length() - QueryWriter.FILE_EXTENSION.length());
                String metaFileName = baseFilename + QueryWriter.META_FILE_EXTENSION;
                QueryDocument queryDoc = metaFilesMap.get(metaFileName);

                if (null == queryDoc)
                    throw new ServletException("QueryImport: SQL file \"" + sqlFileName + "\" has no corresponding meta data file.");

                String sql = PageFlowUtil.getStreamContentsAsString(queriesDir.getInputStream(sqlFileName));

                QueryType queryXml = queryDoc.getQuery();

                // For now, just delete if a query by this name already exists.
                QueryDefinition oldQuery = QueryService.get().getQueryDef(ctx.getUser(), ctx.getContainer(), queryXml.getSchemaName(), queryXml.getName());

                if (null != oldQuery)
                    oldQuery.delete(ctx.getUser());

                QueryDefinition newQuery = QueryService.get().createQueryDef(ctx.getUser(), ctx.getContainer(), queryXml.getSchemaName(), queryXml.getName());
                newQuery.setSql(sql);
                newQuery.setDescription(queryXml.getDescription());

                if (null != queryXml.getMetadata())
                    newQuery.setMetadataXml(queryXml.getMetadata().xmlText());

                newQuery.save(ctx.getUser(), ctx.getContainer());

                metaFilesMap.remove(metaFileName);
            }

            ctx.getLogger().info(sqlFileNames.size() + " quer" + (1 == sqlFileNames.size() ? "y" : "ies") + " imported");
            ctx.getLogger().info("Done importing " + getDescription());

            // check to make sure that each meta xml file was used
            if (metaFilesMap.size() > 0)
                throw new ImportException("Not all query meta xml files had corresponding sql.");
        }
    }

    public Collection<PipelineJobWarning> postProcess(ImportContext ctx, VirtualFile root) throws Exception
    {
        List<PipelineJobWarning> warnings = new ArrayList<PipelineJobWarning>();

        //validate all queries in all schemas in the container
        ctx.getLogger().info("Post-processing " + getDescription());
        ctx.getLogger().info("Validating all queries in all schemas...");
        Container container = ctx.getContainer();
        User user = ctx.getUser();
        DefaultSchema defSchema = DefaultSchema.get(user, container);
        QueryManager mgr = QueryManager.get();

        for (String sname : defSchema.getUserSchemaNames())
        {
            QuerySchema qschema = defSchema.getSchema(sname);
            if (!(qschema instanceof UserSchema))
                continue;
            UserSchema uschema = (UserSchema)qschema;
            for (String qname : uschema.getTableAndQueryNames(true))
            {
                ctx.getLogger().info("Validating query " + sname + "." + qname + "...");

                try
                {
                    mgr.validateQuery(sname, qname, user, container);
                }
                catch (Throwable e)
                {
                    ctx.getLogger().warn("VALIDATION ERROR: Query " + sname + "." + qname + " failed validation!", e);
                    warnings.add(new PipelineJobWarning("Query " + sname + "." + qname + " failed validation!", e));
                }
            }
        }

        ctx.getLogger().info("Finished validating queries.");
        ctx.getLogger().info("Done post-processing " + getDescription());
        return warnings;
    }
    
    @Override
    public boolean supportsVirtualFile()
    {
        return false;
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        public FolderImporter create()
        {
            return new QueryImporter();
        }
    }
}
