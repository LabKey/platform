/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.query.olap;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.query.OlapSchemaInfo;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.olap4j.OlapConnection;
import org.olap4j.impl.Named;
import org.olap4j.impl.NamedListImpl;
import org.olap4j.metadata.Catalog;
import org.olap4j.metadata.NamedList;
import org.olap4j.metadata.Schema;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

/**
 * User: matthew
 * Date: 10/30/13
 *
 * This maps to a mondrian schema definition file.  It can be used to get an OlapConnection that makes this schema available.
 */
public class OlapSchemaDescriptor
{
    final static Logger _log = Logger.getLogger(OlapSchemaDescriptor.class);

    final String _cacheGUID = GUID.makeGUID();
    final Module _module;
    final String _id;
    final String _name;
    final Resource _resource;
    final String _queryTag;

    public OlapSchemaDescriptor(String id, Module module, Resource resource)
    {
        _module = module;
        _id = id;
        _name = id.substring(id.indexOf("/")+1);
        _resource = resource;

        // See if the module has any extra schema information about Olap queries.  Right now, we
        // only look for module-specific query tags used for auditing.
        OlapSchemaInfo olapSchemaInfo = module.getOlapSchemaInfo();
        _queryTag = (olapSchemaInfo == null) ? "" : olapSchemaInfo.getQueryTag();
    }

    public String getQueryTag()
    {
        return _queryTag;
    }

    public String getId()
    {
        return _id;
    }

    public String getName()
    {
        return _name;
    }

    public Module getModule()
    {
        return _module;
    }


    public String toString()
    {
        return _id;
    }


    static String makeCatalogName(OlapSchemaDescriptor d, Container c)
    {
        return "cn_" + d._id;
    }


    public OlapConnection getConnection(Container c, User u) throws SQLException
    {
        return ServerManager.getConnection(c,u,makeCatalogName(this,c));
    }


    public NamedList<Schema> getSchemas(OlapConnection connection, Container c, User u) throws SQLException
    {
        if (null == connection)
            return null;
        Catalog catalog = connection.getOlapDatabase().getCatalogs().get(makeCatalogName(this,c));
        if (null == catalog)
            return new EmptyNamedList();
        return catalog.getSchemas();
    }


    public Schema getSchema(OlapConnection connection, Container c, User u, String name) throws SQLException
    {
        return getSchemas(connection, c, u).get(name);
    }


    class EmptyNamedList<T extends Named> extends NamedListImpl<T>
    {
    }


    /* TODO: get file directly from resource! */
    final Object _fileLock = new Object();
    File tmpFile = null;

    File getFile() throws IOException
    {
        synchronized (_fileLock)
        {
            if (null == tmpFile)
            {
                tmpFile = File.createTempFile("olap", _resource.getName());
                tmpFile.deleteOnExit();
                try (
                    FileOutputStream out = new FileOutputStream(tmpFile);
                    InputStream in = _resource.getInputStream())
                {
                    IOUtils.copy(in, out);
                }
            }
        }
        return tmpFile;
    }


    @Override
    protected void finalize() throws Throwable
    {
        try
        {
            if (null != tmpFile)
                tmpFile.delete();
        }
        finally
        {
            super.finalize();
        }
    }
}

