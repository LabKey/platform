/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.apache.commons.httpclient.HttpMethod;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.etl.CopyConfig;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.reader.JSONDataLoader;
import org.labkey.api.security.User;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTaskFactory;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
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
    }

    @Override
    public boolean hasWork()
    {
        return true;
    }

    public boolean validate(CopyConfig meta, Container c, User u, Logger log)
    {
        // sourceSchema is remote and is not used

        QuerySchema targetSchema = DefaultSchema.get(u, c, meta.getTargetSchema());
        if (null == targetSchema || null == targetSchema.getDbSchema())
        {
            log.error("ERROR: Target schema not found: " + meta.getTargetSchema());
            return false;
        }

        return true;
    }

    DataIteratorBuilder selectFromSource(CopyConfig meta, Container c, User u, DataIteratorContext context, Logger log) throws SQLException
    {
        final String CATEGORY = "remote-connections";
        String name = "Conn2";
        // TODO: grab connection name from .xml

        // Extract the username, password, and container from the secure property store
        Map<String, String> map = PropertyManager.getWritableProperties(CATEGORY + ":" + name, true);
        String url = map.get("URL");
        String user = map.get("user");
        String password = map.get("password");
        String container = map.get("container");

        try
        {
            // connect to the remote server and retrieve an input stream
            Connection cn = new Connection(url, user, password);
            SelectRowsCommand cmd = new SelectRowsCommand(meta.getSourceSchema().toString(), meta.getSourceQuery().toString());
            InputStream is = cmd.executeStream(cn, container);

            // transform the InputStream into a DataIteratorBuild by parsing the JSON
            DataIteratorBuilder source = new JSONDataLoader(is, true, c);
            return source;
        }
        catch (IOException | CommandException exception)
        {
            log.error(exception.getMessage());
            return null;
        }
    }

}
