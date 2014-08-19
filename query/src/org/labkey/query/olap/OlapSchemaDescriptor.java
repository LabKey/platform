package org.labkey.query.olap;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.query.OlapSchemaInfo;
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
 * This maps to a mondrian schema definition file.
 * It can be used to get an OlapConnection that makes this schema available.
 */
public abstract class OlapSchemaDescriptor
{
    final static Logger _log = Logger.getLogger(OlapSchemaDescriptor.class);

    final Module _module;
    final String _id;
    final String _name;
    final String _queryTag;

    protected OlapSchemaDescriptor(@NotNull String id, @NotNull Module module)
    {
        _id = id;
        _name = id.substring(id.indexOf("/")+1);
        _module = module;

        // See if the module has any extra schema information about Olap queries.  Right now, we
        // only look for module-specific query tags used for auditing.
        OlapSchemaInfo olapSchemaInfo = module.getOlapSchemaInfo();
        _queryTag = (olapSchemaInfo == null) ? "" : olapSchemaInfo.getQueryTag();
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

    class EmptyNamedList<T extends Named> extends NamedListImpl<T>
    {
    }


    public Schema getSchema(OlapConnection connection, Container c, User u, String name) throws SQLException
    {
        return getSchemas(connection, c, u).get(name);
    }

    public abstract boolean isEditable();

    public abstract String getDefinition();

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
