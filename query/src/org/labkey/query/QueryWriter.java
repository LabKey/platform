/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.data.Container;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.util.FileNameUniquifier;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.query.QueryDocument;
import org.labkey.data.xml.query.QueryType;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: Apr 16, 2009
 * Time: 4:49:55 PM
 */
public class QueryWriter extends BaseFolderWriter
{
    private static final Logger _log = LogManager.getLogger(QueryWriter.class);

    public static final String FILE_EXTENSION = ".sql";
    public static final String META_FILE_EXTENSION =  ".query.xml";

    private static final String DEFAULT_DIRECTORY = "queries";

    @Override
    public String getDataType()
    {
        return FolderArchiveDataTypes.QUERIES;
    }

    @Override
    public void write(Container c, FolderExportContext ctx, VirtualFile root) throws Exception
    {
        assert ctx.getContainer().equals(c); // TODO: Temporary check - remove

        // get all custom queries and metadata xml overrides of built-in tables that have been overridden
        List<QueryDefinition> queries = new ArrayList<>(QueryServiceImpl.get().getQueryDefsAndMetadataOverrides(ctx.getUser(), c));
        FileNameUniquifier fileNameUniquifier = new FileNameUniquifier();

        Set<String> queryKeysToExport = ctx.getQueryKeys();
        if (queryKeysToExport != null)
            queries.removeIf(queryDef -> !queryKeysToExport.contains(queryDef.getQueryKey()));

        if (queries.size() > 0)
        {
            ctx.getXml().addNewQueries().setDir(DEFAULT_DIRECTORY);
            VirtualFile queriesDir = root.getDir(DEFAULT_DIRECTORY);

            for (QueryDefinition query : queries)
            {
                // sql is only present for custom queries -- metadata xml overrides of built-in tables will not have sql
                String sql = query.getSql();
                boolean metadata = StringUtils.isEmpty(sql);

                if (metadata && !query.getSchema().getTableNames().contains(query.getName()))
                    continue;

                // issue 20662: handle query name collisions across schemas
                String queryExportName = fileNameUniquifier.uniquify(query.getName());

                if (!metadata)
                {
                    try (PrintWriter pw = queriesDir.getPrintWriter(queryExportName + FILE_EXTENSION))
                    {
                        pw.println(sql);
                    }
                }

                QueryDocument qDoc = QueryDocument.Factory.newInstance();
                QueryType queryXml = qDoc.addNewQuery();
                queryXml.setName(query.getName());

                if (null != query.getDescription())
                    queryXml.setDescription(query.getDescription());

                if (query.isHidden())
                    queryXml.setHidden(true);

                queryXml.setSchemaName(query.getSchemaName());

                if (null != query.getMetadataXml())
                {
                    XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
                    XmlObject xObj = XmlObject.Factory.parse(query.getMetadataXml(), options);

                    try
                    {
                        XmlBeansUtil.validateXmlDocument(xObj, "metadata for " + query.getSchemaName() + "." + query.getName());
                        queryXml.setMetadata(xObj);
                    }
                    catch (XmlValidationException e)
                    {
                        _log.error("Invalid meta data set on query " + query.getSchemaName() + "." + query.getName() + ". Meta data will not be exported for this query.", e);
                    }
                }

                queriesDir.saveXmlBean(queryExportName + META_FILE_EXTENSION, qDoc);
            }
        }
    }

    public static class Factory implements FolderWriterFactory
    {
        @Override
        public FolderWriter create()
        {
            return new QueryWriter();
        }
    }
}
