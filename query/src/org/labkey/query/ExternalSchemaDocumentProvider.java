/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.webdav.SimpleDocumentResource;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Apr 29, 2010
 * Time: 9:25:48 AM
 */
public class ExternalSchemaDocumentProvider implements SearchService.DocumentProvider
{
    private static final Logger LOG = Logger.getLogger(ExternalSchemaDocumentProvider.class);
    private static final SearchService.DocumentProvider _instance = new ExternalSchemaDocumentProvider();

    public static final SearchService.SearchCategory externalTableCategory = new SearchService.SearchCategory("externalTable", "External Table");

    private ExternalSchemaDocumentProvider()
    {
    }

    public static SearchService.DocumentProvider getInstance()
    {
        return _instance;
    }

    public void enumerateDocuments(SearchService.IndexTask t, final @NotNull Container c, Date since)
    {
        SearchService ss = SearchService.get();
        if (ss == null)
            return;

        final SearchService.IndexTask task = null==t ? ss.defaultTask() : t;

        Runnable r = () ->
        {
            User user = User.getSearchUser();
            QueryServiceImpl svc = (QueryServiceImpl)QueryService.get();
            Map<String, UserSchema> externalSchemas = svc.getExternalSchemas(user, c);

            // First, delete all external schema docs in this container.  This addresses schemas/tables that have
            // disappeared from the data source plus existing schemas that get changed to not index.
            SearchService ss1 = SearchService.get();

            if (null != ss1)
                ss1.deleteResourcesForPrefix("externalTable:" + c.getId());

            for (UserSchema schema : externalSchemas.values())
            {
                // TODO: move shouldIndexMetaData() into UserSchema or higher
                if (null == schema || !((ExternalSchema)schema).shouldIndexMetaData())
                    continue;

                String schemaName = schema.getName();
                Set<String> tableNames = schema.getTableNames();

                for (String mdName : tableNames)
                {
                    TableInfo table;

                    try
                    {
                        table = schema.getTable(mdName);

                        // Address likely race condition from exception report #19080
                        if (null == table)
                            continue;

                        assert mdName.equalsIgnoreCase(table.getName());
                    }
                    catch (UnauthorizedException e)
                    {
                        // Shouldn't happen, but if it does, log it and continue
                        LOG.error("Unauthorized", e);
                        continue;
                    }
                    catch (Exception e)
                    {
                        // Shouldn't happen, but if it does, continue with the next table
                        ExceptionUtil.logExceptionToMothership(null, e);
                        continue;
                    }

                    // Use the canonical name for search display, etc.
                    String tableName = table.getName();
                    StringBuilder body = new StringBuilder();
                    Map<String, Object> props = new HashMap<>();

                    props.put(SearchService.PROPERTY.categories.toString(), externalTableCategory.toString());
                    props.put(SearchService.PROPERTY.title.toString(), "Table " + schemaName + "." + tableName);
                    props.put(SearchService.PROPERTY.keywordsMed.toString(), schemaName + " " + tableName);

                    if (!StringUtils.isEmpty(table.getDescription()))
                        body.append(table.getDescription()).append("\n");

                    String sep = "";

                    for (ColumnInfo column : table.getColumns())
                    {
                        String n = StringUtils.trimToEmpty(column.getName());
                        String l = StringUtils.trimToEmpty(column.getLabel());
                        if (n.equals(l))
                            l = "";
                        String d = StringUtils.trimToEmpty(column.getDescription());
                        if (n.equals(d) || l.equals(d))
                            d = "";
                        String colProps = StringUtilsLabKey.joinNonBlank(" ", n, l, d);
                        body.append(sep).append(colProps);
                        sep = ",\n";
                    }

                    ActionURL url = QueryService.get().urlFor(user, c, QueryAction.executeQuery, schemaName, tableName);
                    url.setExtraPath(c.getId());
                    String documentId = "externalTable:" + c.getId() + ":" + schemaName + "." + tableName;
                    SimpleDocumentResource r1 = new SimpleDocumentResource(
                            new Path(documentId),
                            documentId,
                            c.getId(),
                            "text/plain",
                            body.toString(),
                            url,
                            props);
                    task.addResource(r1, SearchService.PRIORITY.item);
                }
            }
        };

        task.addRunnable(r, SearchService.PRIORITY.group);
    }


    public void indexDeleted() throws SQLException
    {
    }
}
