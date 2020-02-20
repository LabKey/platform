/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.module.Module;
import org.labkey.api.query.OlapSchemaInfo;
import org.labkey.api.security.User;
import org.labkey.query.olap.rolap.RolapCubeDef;
import org.labkey.query.olap.rolap.RolapReader;
import org.olap4j.OlapConnection;
import org.olap4j.impl.Olap4jUtil;
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
import java.util.Map;

/**
 * This maps to a mondrian schema definition file.
 * It can be used to get an OlapConnection that makes this schema available.
 */
public abstract class OlapSchemaDescriptor
{
    private final Module _module;
    private final String _id;
    private final String _name;
    private final String _queryTag;
    private final OlapSchemaInfo _olapSchemaInfo;

    protected OlapSchemaDescriptor(@NotNull String id, @NotNull Module module)
    {
        _id = id;
        _name = id.substring(id.indexOf("/")+1);
        _module = module;

        // See if the module has any extra schema information about Olap queries
        _olapSchemaInfo = module.getOlapSchemaInfo();
        _queryTag = (_olapSchemaInfo == null) ? "" : _olapSchemaInfo.getQueryTag();
    }


    /*
     * Populating the mondrian cubes is expensive, but most usages of olap in LabKey use the CountDistinct API
     * to enable using Mondrian in order to support MDX supply the "EnableMondrian" annotation
     */
    public boolean usesMondrian()
    {
        String s = getSchemaAnnotations().get("EnableMondrian");
        if (null == s)
            return false;
        return (boolean)JdbcType.BOOLEAN.convert(s);
    }

    /* In the future we could support an annotation for this, for now return true if useMondrian is true */
    public boolean allowExecuteMDX()
    {
        return usesMondrian();
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
            return Olap4jUtil.emptyNamedList();
        return catalog.getSchemas();
    }

    boolean shouldWarmCube(Container c)
    {
        // Give the module a chance to respectfully decline the cube warming operation
        // without throwing an error.
        if (_olapSchemaInfo != null)
            return _olapSchemaInfo.shouldWarmCube(c);

        return true;
    }

    public Schema getSchema(OlapConnection connection, Container c, User u, String name) throws SQLException
    {
        return getSchemas(connection, c, u).get(name);
    }

    public abstract boolean isEditable();

    public abstract String getDefinition();


    private List<RolapCubeDef> rolapCubes = null;
    private Map<String,String> annotations = null;
    private Boolean hasContainerColumn = null;

    private void _parse()
    {
        try (InputStream is = getInputStream(); Reader r = new InputStreamReader(is))
        {
            RolapReader rr = new RolapReader(r);
            rolapCubes = rr.getCubes();
            annotations = rr.getAnnotations();
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    private boolean isContainerLevel(RolapCubeDef.LevelDef l)
    {
        if (l.getName().equalsIgnoreCase("Container") || l.getName().equalsIgnoreCase("Folder"))
            return true;
        if (l.getKeyColumn().equalsIgnoreCase("Container") || l.getKeyColumn().equalsIgnoreCase("Folder"))
            return true;
        return false;
    }

    public synchronized boolean hasContainerColumn()
    {
        if (null == hasContainerColumn)
        {
            for (var def : getRolapCubeDefinitions())
            {
                for (var h : def.getHierarchies())
                {
                    for (var l : h.getLevels())
                    {
                        if (isContainerLevel(l))
                        {
                            hasContainerColumn = true;
                            return true;
                        }
                    }
                }
            }
            hasContainerColumn = false;
        }
        return hasContainerColumn;
    }


    public synchronized List<RolapCubeDef> getRolapCubeDefinitions()
    {
        if (null == rolapCubes)
            _parse();
        return rolapCubes;
    }

    public synchronized Map<String,String> getSchemaAnnotations()
    {
        if (null == annotations)
            _parse();
        return annotations;
    }


    @Nullable
    public RolapCubeDef getRolapCubeDefinitionByName(String name)
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

    /* Can't always get file directly from resource, so copy to known file system location */
    private final Object _fileLock = new Object();
    private File tmpFile = null;

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

    // TODO use ReferenceQueue to do cleanup instead of finalize()
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
