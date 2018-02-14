/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.di.steps;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.dataiterator.CopyConfig;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformPipelineJob;
import org.labkey.di.pipeline.TransformTaskFactory;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.GuestCredentialsProvider;
import org.labkey.remoteapi.RemoteConnections;
import org.labkey.remoteapi.SelectRowsStreamHack;
import org.labkey.remoteapi.query.SelectRowsCommand;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: gktaylor
 * Date: 2013-10-08
 */
public class RemoteQueryTransformStep extends SimpleQueryTransformStep
{
    public RemoteQueryTransformStep(TransformTaskFactory f, PipelineJob job, SimpleQueryTransformStepMeta meta, TransformJobContext context)
    {
        super(f, job, meta, context);
        _validateSource = false;
    }

    @Override
    public boolean hasWork()
    {
        return !isEtlGatedByStep();
    }

    @Override
    protected DbScope getSourceScope(QuerySchema sourceSchema, DbScope targetScope)
    {
        // there is no source scope for a remote query
        return null;
    }

    @Override
    protected DataIteratorBuilder selectFromSource(CopyConfig meta, Container c, User u, DataIteratorContext context, Logger log)
    {
        // find the category to look up in the property manager, provided by the .xml
        if (! (meta instanceof RemoteQueryTransformStepMeta) )
            throw new UnsupportedOperationException("This xml parser was expected an instance of RemoteQueryTransformStepMeta");
        String name = ((RemoteQueryTransformStepMeta)meta).getRemoteSource();
        if (name == null)
        {
            log.error("The remoteSource option provided in the xml must refer to a Remote Connection.");
            return null;
        }

        // Check that an entry for the remote connection name exists
        Map<String, String> connectionMap = PropertyManager.getEncryptedStore().getProperties(c, RemoteConnections.REMOTE_QUERY_CONNECTIONS_CATEGORY);
        if (connectionMap.get(RemoteConnections.REMOTE_QUERY_CONNECTIONS_CATEGORY + ":" + name) == null)
        {
            log.error("The remote connection " + name + " has not yet been setup in the remote connection manager.  You may configure a new remote connection through the schema browser.");
            return null;
        }

        // Extract the username, password, and container from the secure property store
        Map<String, String> singleConnectionMap = PropertyManager.getEncryptedStore().getProperties(c, RemoteConnections.REMOTE_QUERY_CONNECTIONS_CATEGORY + ":" + name);
        String url = singleConnectionMap.get(RemoteConnections.FIELD_URL);
        String user = singleConnectionMap.get(RemoteConnections.FIELD_USER);
        String password = singleConnectionMap.get(RemoteConnections.FIELD_PASSWORD);
        String container = singleConnectionMap.get(RemoteConnections.FIELD_CONTAINER);
        if (url == null || user == null || password == null || container == null)
        {
            log.error("Invalid login credentials in the secure user store");
            return null;
        }
        // Pass in named query parameters
        Map<String, String> parameters = new HashMap<>();
        ((TransformPipelineJob) getJob()).getTransformDescriptor().getDeclaredVariables().forEach((pd, o) -> parameters.put(pd.getName(), o.toString()));
        try
        {
            return selectFromSource(meta.getSourceSchema().toString(), meta.getSourceQuery(), url, user, password, container, meta.getSourceColumns(), parameters);
        }
        catch (IOException | CommandException exception)
        {
            log.error(exception.getMessage());
            return null;
        }
    }

    // Variant that takes valid, non-null credentials
    private static DataIteratorBuilder selectFromSource(String schemaName, String queryName, String url, @NotNull String user, @NotNull String password, String container, @Nullable List<String> columns, Map<String, String> parameters)
            throws IOException, CommandException
    {
        return selectFromSource(new Connection(url, user, password), schemaName, queryName, container, columns, parameters);
    }

    // Version that connects as guest; for testing purposes only
    private static DataIteratorBuilder selectFromSource(String schemaName, String queryName, String url, String container)
            throws IOException, CommandException
    {
        return selectFromSource(new Connection(url, new GuestCredentialsProvider()), schemaName, queryName, container, null, Collections.emptyMap());
    }

    private static DataIteratorBuilder selectFromSource(Connection cn, String schemaName, String queryName, String container, @Nullable List<String> columns, Map<String, String> parameters)
            throws IOException, CommandException
    {
        // connect to the remote server and retrieve an input stream
        final SelectRowsCommand cmd = new SelectRowsCommand(schemaName, queryName);
        if (columns != null)
        {
            cmd.setColumns(columns);
        }
        if (!parameters.isEmpty())
        {
            cmd.setQueryParameters(parameters);
        }
        return SelectRowsStreamHack.go(cn, container, cmd);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void selectRows() throws Exception
        {
            // Execute a 'remote' query against the currently running server.
            // We use the home container since we won't need to authenticate the user.
            String url = AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath();
            Container home = ContainerManager.getHomeContainer();
            DataIteratorBuilder b = selectFromSource("core", "Containers", url, home.getPath());

            DataIteratorContext context = new DataIteratorContext();
            try (DataIterator iter = b.getDataIterator(context))
            {
                if(context.getErrors().hasErrors())
                {
                    throw context.getErrors();
                }
                int idxEntityId = -1;
                int idxID = -1;
                int idxName = -1;
                int idxCreated = -1;
                int idxType = -1;

                for (int i = 1; i <= iter.getColumnCount(); i++)
                {
                    ColumnInfo col = iter.getColumnInfo(i);
                    switch (col.getName())
                    {
                        case "EntityId": idxEntityId = i; break;
                        case "ID":       idxID = i;       break;
                        case "Name":     idxName = i;     break;
                        case "Created":  idxCreated = i;  break;
                        case "Type":     idxType = i;     break;
                    }
                }

                assertTrue("Expected to find EntityId column: " + idxEntityId, idxEntityId > 0);
                assertTrue("Expected to find ID column: " + idxID, idxID > 0);
                assertTrue("Expected to find Name column: " + idxName, idxName > 0);
                assertTrue("Expected to find Created column: " + idxCreated, idxCreated > 0);

                assertTrue("Expected to select a single row for the Home container.", iter.next());

                // Check the select rows returns the Home container details
                assertEquals(home.getId(), iter.get(idxEntityId));
                assertTrue(iter.get(idxEntityId) instanceof String);

                assertEquals(home.getRowId(), iter.get(idxID));
                assertTrue(iter.get(idxID) instanceof Integer);

                assertEquals(home.getName(), iter.get(idxName));
                assertTrue(iter.get(idxName) instanceof String);

                assertTrue(iter.get(idxCreated) instanceof Date);
                // The remoteapi Date doesn't have milliseconds so the Dates won't be equal -- just compare day instead.
                assertEquals(home.getCreated().getDay(), ((Date)iter.get(idxCreated)).getDay());

                // We expect any other rows to be workbooks
                while (iter.next())
                    assertTrue("workbook".equals(iter.get(idxType)));
            }
        }
    }
}
