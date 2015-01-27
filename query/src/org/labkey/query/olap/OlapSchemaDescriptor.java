/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.query.OlapSchemaInfo;
import org.labkey.api.security.User;
import org.labkey.query.olap.rolap.RolapCubeDef;
import org.labkey.query.olap.rolap.RolapReader;
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
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.SQLException;
import java.util.List;

/**
 * This maps to a mondrian schema definition file.
 * It can be used to get an OlapConnection that makes this schema available.
 */
public abstract class OlapSchemaDescriptor
{
    public enum ImplStrategy {mondrian, rolapYourOwn}
    final static Logger _log = Logger.getLogger(OlapSchemaDescriptor.class);

    final Module _module;
    final String _id;
    final String _name;
    final String _queryTag;
    final ImplStrategy _strategy;
    final OlapSchemaInfo _olapSchemaInfo;

    protected OlapSchemaDescriptor(@NotNull String id, @NotNull Module module)
    {
        _id = id;
        _name = id.substring(id.indexOf("/")+1);
        _module = module;

        // See if the module has any extra schema information about Olap queries
        _olapSchemaInfo = module.getOlapSchemaInfo();
        _queryTag = (_olapSchemaInfo == null) ? "" : _olapSchemaInfo.getQueryTag();

        // TODO this is a horrible hack
        if (_name.equalsIgnoreCase("Metrics"))
            _strategy = ImplStrategy.mondrian;
        else
            _strategy = ImplStrategy.rolapYourOwn;
    }

    public ImplStrategy getStrategy()
    {
        return _strategy;
    }

    public boolean usesMondrian()
    {
        return _strategy == ImplStrategy.mondrian;
    }

    public boolean usesRolap()
    {
        return _strategy == ImplStrategy.rolapYourOwn;
    }

    static String makeCatalogName(OlapSchemaDescriptor d, Container c)
    {
        return "cn_" + d._id;
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

    @NotNull
    public Module getModule()
    {
        return _module;
    }

    @Nullable
    public Container getContainer()
    {
        return null;
    }

    /**
     * Schema descriptors can choose to expose themselves in a specific container
     */
    public boolean isExposed(Container container)
    {
        return true;
    }

    public String toString()
    {
        return _id;
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
            return new ModuleOlapSchemaDescriptor.EmptyNamedList();
        return catalog.getSchemas();
    }

    public boolean shouldWarmCube(Container c)
    {
        // Give the module a chance to respectfully decline the cube warming operation
        // without throwing an error.  This may happen in Argos, for example, if we are trying to
        // warm the cube in a Portal Selection container
        if (_olapSchemaInfo != null)
            return _olapSchemaInfo.shouldWarmCube(c);

        return true;
    }

    class EmptyNamedList<T extends Named> extends NamedListImpl<T>
    {
    }


    public Schema getSchema(OlapConnection connection, Container c, User u, String name) throws SQLException
    {
        return getSchemas(connection, c, u).get(name);
    }

    public abstract boolean isEditable();

    public abstract String getDefinition();


    List<RolapCubeDef> rolapCubes = null;

    public synchronized List<RolapCubeDef> getRolapCubeDefinitions() throws IOException
    {
        if (null == rolapCubes)
        {
            try (InputStream is = getInputStream(); Reader r = new InputStreamReader(is))
            {
                RolapReader rr = new RolapReader(r);
                rolapCubes = rr.getCubes();
            }
        }
        return rolapCubes;
    }


    @Nullable
    public RolapCubeDef getRolapCubeDefinitionByName(String name) throws IOException
    {
        List<RolapCubeDef> defs = getRolapCubeDefinitions();
        for (RolapCubeDef d : defs)
        {
            if (name.equalsIgnoreCase(d.getName()))
                return d;
        }
        return null;
    }


    protected abstract InputStream getInputStream() throws IOException;

    /* TODO: get file directly from resource! */
    final Object _fileLock = new Object();
    File tmpFile = null;

    File getFile() throws IOException
    {
        synchronized (_fileLock)
        {
            if (null == tmpFile)
            {
                tmpFile = File.createTempFile("olap", getName());
                tmpFile.deleteOnExit();
                try (
                    FileOutputStream out = new FileOutputStream(tmpFile);
                    InputStream in = getInputStream())
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
