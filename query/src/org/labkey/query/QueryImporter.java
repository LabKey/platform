/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryChangeListener.QueryProperty;
import org.labkey.api.query.QueryChangeListener.QueryPropertyChange;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.query.QueryDocument;
import org.labkey.data.xml.query.QueryType;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:21:56 PM
 */
public class QueryImporter implements FolderImporter
{
    @Override
    public String getDataType()
    {
        return FolderArchiveDataTypes.QUERIES;
    }

    @Override
    public String getDescription()
    {
        return FolderArchiveDataTypes.QUERIES.toLowerCase();
    }

    private static class QueryImportContext extends FolderImportContext
    {
        // Map of metadata xml files that didn't resolve to a built-in table during import.
        // The map of files will be validated during postProcess
        Map<String, QueryDocument> unresolvedMetadataFiles = new LinkedHashMap<>();
    }

    @Override
    public void process(PipelineJob job, ImportContext ctx, VirtualFile root) throws ServletException, IOException, SQLException, ImportException
    {
        if (isValidForImportArchive(ctx))
        {
            VirtualFile queriesDir = ctx.getDir("queries");

            if (null != job)
                job.setStatus("IMPORT " + getDescription());
            ctx.getLogger().info("Loading " + getDescription());

            // get the list of files and split them into sql and xml file name arrays
            ArrayList<String> sqlFileNames = new ArrayList<>();
            Map<String, QueryDocument> metaFilesMap = new LinkedHashMap<>();

            for (String fileName : queriesDir.list())
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

            // Map of new and updated queries by schema
            Map<SchemaKey, List<String>> createdQueries = new LinkedHashMap<>();
            Map<Pair<SchemaKey, QueryProperty>, List<QueryPropertyChange>> changedQueries = new LinkedHashMap<>();

            // import custom queries along with metadata xml
            for (String sqlFileName : sqlFileNames)
            {
                String baseFilename = sqlFileName.substring(0, sqlFileName.length() - QueryWriter.FILE_EXTENSION.length());
                String metaFileName = baseFilename + QueryWriter.META_FILE_EXTENSION;
                QueryDocument queryDoc = metaFilesMap.get(metaFileName);
                // Remove it from the map so we know it was consumed
                metaFilesMap.remove(metaFileName);

                if (null == queryDoc)
                    throw new ServletException("QueryImport: SQL file \"" + sqlFileName + "\" has no corresponding meta data file.");

                String sql = PageFlowUtil.getStreamContentsAsString(queriesDir.getInputStream(sqlFileName));

                createQueryDef(ctx, createdQueries, changedQueries, metaFileName, queryDoc, sqlFileName, sql);
            }

            // import context used to stash the metadata files for postProcess validation
            QueryImportContext qic = new QueryImportContext();
            ctx.addContext(QueryImportContext.class, qic);

            // import any remaining metadata xml overrides
            for (Map.Entry<String, QueryDocument> entry : metaFilesMap.entrySet())
            {
                String metaFileName = entry.getKey();

                QueryDocument queryDoc = entry.getValue();
                QueryType queryXml = queryDoc.getQuery();

                String queryName = queryXml.getName();
                String schemaName = queryXml.getSchemaName();
                SchemaKey schemaKey = SchemaKey.fromString(schemaName);
                UserSchema schema = QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), schemaKey);
                if (schema == null)
                {
                    ctx.getLogger().warn("Skipping import: " + queryImportMessage(schemaName, queryName, null, metaFileName, "schema doesn't exist."));
                    continue;
                }

                if (!schema.getTableNames().contains(queryName))
                {
                    // warn if the table doesn't exist -- it may be created later during the import (e.g., a SampleType may be created as a part of the import process)
                    ctx.getLogger().warn("Importing: " + queryImportMessage(schemaName, queryName, null, metaFileName, "Creating metadata xml override for table that doesn't exist"));
                    qic.unresolvedMetadataFiles.put(metaFileName, queryDoc);//
                }

                createQueryDef(ctx, createdQueries, changedQueries, metaFileName, queryDoc, null, null);
            }

            // fire query created events (one set of changes per container/schema)
            for (Map.Entry<SchemaKey, List<String>> entry : createdQueries.entrySet())
            {
                SchemaKey schemaKey = entry.getKey();
                List<String> queries = entry.getValue();
                QueryService.get().fireQueryCreated(ctx.getUser(), ctx.getContainer(), null, schemaKey, queries);
            }

            // fire query changed events (one set of changes per container/schema/queryproperty)
            for (Map.Entry<Pair<SchemaKey, QueryProperty>, List<QueryPropertyChange>> entry : changedQueries.entrySet())
            {
                SchemaKey schemaKey = entry.getKey().first;
                QueryProperty property = entry.getKey().second;
                List<QueryPropertyChange> changes = entry.getValue();
                QueryService.get().fireQueryChanged(ctx.getUser(), ctx.getContainer(), null, schemaKey, property, changes);
            }

            ctx.getLogger().info(sqlFileNames.size() + " quer" + (1 == sqlFileNames.size() ? "y" : "ies") + " imported");
            ctx.getLogger().info("Done importing " + getDescription());
        }
    }

    private void createQueryDef(ImportContext ctx,
                                Map<SchemaKey, List<String>> createdQueries,
                                Map<Pair<SchemaKey, QueryProperty>, List<QueryPropertyChange>> changedQueries,
                                @NotNull String metaFileName, @NotNull QueryDocument queryDoc,
                                @Nullable String sqlFileName, @Nullable String sql)
            throws SQLException
    {
        QueryType queryXml = queryDoc.getQuery();

        String metadataXml = queryXml.getMetadata() == null ? null : queryXml.getMetadata().xmlText();

        String queryName = queryXml.getName();
        String schemaName = queryXml.getSchemaName();
        SchemaKey schemaKey = SchemaKey.fromString(schemaName);

        // Reuse the existing queryDef so created or change events will be fired appropriately.
        boolean created = false;
        QueryDefinition queryDef;
        if (sql != null)
        {
            // custom sql query
            queryDef = QueryService.get().getQueryDef(ctx.getUser(), ctx.getContainer(), schemaName, queryName);
        }
        else
        {
            // metadata override only
            queryDef = QueryServiceImpl.get().getQueryDefMetadataOverride(ctx.getUser(), ctx.getContainer(), schemaName, queryName);
        }

        if (queryDef != null)
        {
            // Don't attempt to replace an existing module-based query... that won't go well. Just warn and move on. #30081
            if (!StringUtils.isEmpty(queryDef.getModuleName()))
            {
                ctx.getLogger().warn("Skipping import: " + queryImportMessage(schemaName, queryName, sqlFileName, metaFileName, "can't overwrite existing module-based query"));
                return;
            }

            if (!queryDef.getDefinitionContainer().equals(ctx.getContainer()))
            {
                // We have a query of the same name being inherited from another container

                // Use normalizeSpace() as there may be differences in line endings
                if (!Objects.equals(StringUtils.normalizeSpace(queryDef.getSql()), StringUtils.normalizeSpace(sql)) ||
                        !Objects.equals(StringUtils.normalizeSpace(queryDef.getDescription()), StringUtils.normalizeSpace(queryXml.getDescription())) ||
                        !Objects.equals(StringUtils.normalizeSpace(queryDef.getMetadataXml()), StringUtils.normalizeSpace(metadataXml)))
                {
                    // Query is different, so we want to create a separate, local copy
                    queryDef = null;
                }
                else
                {
                    // We already have a matching query, so we can skip any additional processing
                    return;
                }
            }
        }

        if (queryDef == null)
        {
            created = true;
            queryDef = QueryService.get().createQueryDef(ctx.getUser(), ctx.getContainer(), schemaKey, queryName);
        }

        if (sql != null)
        {
            queryDef.setSql(sql);
        }
        else if (queryDef.getSql() != null)
        {
            ctx.getLogger().warn("Skipping import: " + queryImportMessage(schemaName, queryName, sqlFileName, metaFileName, "can't overwrite existing custom query with only metadata xml override"));
            return;
        }

        queryDef.setDescription(queryXml.getDescription());
        queryDef.setMetadataXml(metadataXml);

        Collection<QueryPropertyChange> changes = queryDef.save(ctx.getUser(), ctx.getContainer(), false);
        if (created)
        {
            List<String> queries = createdQueries.computeIfAbsent(schemaKey, k -> new ArrayList<>());
            queries.add(queryName);
        }
        else if (changes != null)
        {
            for (QueryPropertyChange change : changes)
            {
                // Group changed queries by schemaKey/QueryProperty
                Pair<SchemaKey, QueryProperty> key = Pair.of(schemaKey, change.getProperty());
                List<QueryPropertyChange> changesBySchemaProperty = changedQueries.computeIfAbsent(key, k -> new ArrayList<>());
                changesBySchemaProperty.add(change);
            }
        }
    }

    String queryImportMessage(String schemaName, String queryName, @Nullable String sqlFileName, @Nullable String metadataFileName, String msg)
    {
        StringBuilder sb = new StringBuilder();
        if (sqlFileName != null)
            sb.append("query \"").append(sqlFileName).append("\" ");
        if (sqlFileName != null && metadataFileName != null)
            sb.append("and ");
        if (metadataFileName != null)
            sb.append("metadata \"").append(metadataFileName).append("\" ");
        sb.append("for \"" + schemaName + "." + queryName + "\": ");
        sb.append(msg);
        return sb.toString();
    }

    @Override
    @NotNull
    public Collection<PipelineJobWarning> postProcess(ImportContext ctx, VirtualFile root)
    {
        List<PipelineJobWarning> warnings = new ArrayList<>();

        //validate all queries in all schemas in the container
        ctx.getLogger().info("Post-processing " + getDescription());

        if (ctx.isSkipQueryValidation())
        {
            ctx.getLogger().info("Skipping query validation.");
        }
        else
        {
            // check that each meta xml file was applied to a built-in table after import of all data structures has completed
            QueryImportContext qic = (QueryImportContext)ctx.getContext(QueryImportContext.class);
            if (qic != null)
            {
                for (Map.Entry<String, QueryDocument> entry : qic.unresolvedMetadataFiles.entrySet())
                {
                    String metaFileName = entry.getKey();
                    QueryDocument queryDoc = entry.getValue();
                    QueryType queryXml = queryDoc.getQuery();

                    String queryName = queryXml.getName();
                    String schemaName = queryXml.getSchemaName();
                    SchemaKey schemaKey = SchemaKey.fromString(schemaName);
                    UserSchema schema = QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), schemaKey);
                    if (schema == null)
                        continue;

                    if (!schema.getTableNames().contains(queryName))
                    {
                        // error if the table doesn't exist -- it wan't created during the import (e.g., a SampleType may be created as a part of the import process)
                        ctx.getLogger().error(queryImportMessage(schemaName, queryName, null, metaFileName, "Created metadata xml override for table that doesn't exist"));
                    }
                }
            }

            ctx.getLogger().info("Validating all queries in all schemas...");
            Container container = ctx.getContainer();
            User user = ctx.getUser();
            DefaultSchema defSchema = DefaultSchema.get(user, container);

            boolean clearEnvironment = false;
            try
            {
                // Some invocations will have already set this, so avoid clearing when we're just piggybacking on the
                // existing context
                if (QueryService.get().getEnvironment(QueryService.Environment.USER) == null)
                {
                    // Retrieve userid for queries being validated through the pipeline (study import).
                    QueryService.get().setEnvironment(QueryService.Environment.USER, user);
                    QueryService.get().setEnvironment(QueryService.Environment.CONTAINER, container);
                    clearEnvironment = true;
                }

                ValidateQueriesVisitor validator = new ValidateQueriesVisitor();
                Boolean valid = validator.visitTop(defSchema, ctx.getLogger());

                ctx.getLogger().info("Finished validating queries.");
                if (valid != null && valid)
                {
                    assert validator.getTotalCount() == validator.getValidCount();
                    ctx.getLogger().info(String.format("Finished validating queries: All %d passed validation.", validator.getTotalCount()));
                }
                else
                {
                    ctx.getLogger().info(String.format("Finished validating queries: %d of %d failed validation.", validator.getInvalidCount(), validator.getTotalCount()));
                }


                for (Pair<String, ? extends Throwable> warn : validator.getWarnings())
                {
                    warnings.add(new PipelineJobWarning(warn.first, warn.second));
                }
            }
            finally
            {
                if (clearEnvironment)
                {
                    QueryService.get().clearEnvironment();
                }
            }
        }

        ctx.getLogger().info("Done post-processing " + getDescription());
        return warnings;
    }

    @Override
    public boolean isValidForImportArchive(ImportContext ctx) throws ImportException
    {
        return ctx.getDir("queries") != null;
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        @Override
        public FolderImporter create()
        {
            return new QueryImporter();
        }
    }
}
