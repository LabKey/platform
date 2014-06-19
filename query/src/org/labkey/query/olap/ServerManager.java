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

import com.drew.lang.annotations.NotNull;
import mondrian.olap.MondrianServer;
import mondrian.rolap.RolapConnection;
import mondrian.rolap.RolapConnectionProperties;
import mondrian.rolap.RolapHierarchy;
import mondrian.rolap.agg.AggregationKey;
import mondrian.server.RepositoryContentFinder;
import mondrian.server.StringRepositoryContentFinder;
import mondrian.spi.CatalogLocator;
import mondrian.spi.DataSourceChangeListener;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.concurrent.CountingSemaphore;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.security.User;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MemTrackerListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewServlet;
import org.olap4j.CellSet;
import org.olap4j.OlapConnection;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Dimension;
import org.olap4j.metadata.Schema;
import org.springframework.validation.BindException;

import javax.servlet.ServletContextEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

/**
 * User: matthew
 * Date: 10/31/13
 * Time: 8:12 AM

 [ ] try to have exactly one MondrianServer (start with one per container)
 [ ] figure out cube/catalog reload, or do we have to blow away the server(s)?
 [ ] investigate shutdown, ref counting?, mondrian 3.5
 [ ] what is change listener for?

*/

public class ServerManager
{
    private static final Logger _log = Logger.getLogger(ServerManager.class);

    private static final Map<String, ServerReferenceCount> _servers = new HashMap<>();
    private static final Object _serverLock = new Object();

    public static final ModuleResourceCache<OlapSchemaDescriptor> SCHEMA_DESCRIPTOR_CACHE = ModuleResourceCaches.create(new Path(OlapSchemaCacheHandler.DIR_NAME), "Olap cube defintions", new OlapSchemaCacheHandler());

    public static final BlockingStringKeyCache<Cube> _cubes = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.HOUR, "cube cache", null);

    private static final String DATA_SOURCE_NAME = "dsn_LABKEY";

    static
    {
        ContainerManager.addContainerListener(new ContainerManager.AbstractContainerListener()
        {
            @Override
            public void containerDeleted(Container c, User user)
            {
                cubeDataChanged(c);
            }

            @Override
            public void containerMoved(Container c, Container oldParent, User user)
            {
                cubeDataChanged(c);
            }
        });

        ContextListener.addShutdownListener(new ShutdownListener()
        {
            @Override
            public void shutdownPre(ServletContextEvent servletContextEvent)
            {
                _servers.clear();
            }

            @Override
            public void shutdownStarted(ServletContextEvent servletContextEvent)
            {
                _servers.clear();
            }
        });
    }


    static String getServerCacheKey(Container c)
    {
        return MondrianServer.class.getName() + "/" + c.getId();
    }


    @Nullable
    public static OlapSchemaDescriptor getDescriptor(@NotNull Container c, @NotNull String schemaId)
    {
        OlapSchemaDescriptor d = SCHEMA_DESCRIPTOR_CACHE.getResource(schemaId);
        if (null != d && c.getActiveModules().contains(d.getModule()))
            return d;
        return null;
    }


    /* Note we pass in OlapConnection here, that's because the connection must stay open in order to use the cube
     * (Unless we create a cached cube)
     */
    public static Cube getCachedCube(OlapSchemaDescriptor d, OlapConnection conn, Container c, User user, String schemaName, String cubeName, BindException errors)  throws SQLException
    {
        List<Schema> findSchemaList;
        if (StringUtils.isNotEmpty(schemaName))
        {
            Schema s = d.getSchema(conn, c, user, schemaName);
            if (null == s)
            {
                errors.reject(ERROR_MSG, "Schema not found: " + schemaName);
                return null;
            }
            findSchemaList = Collections.singletonList(s);
        }
        else
        {
            findSchemaList = d.getSchemas(conn, c, user);
        }

        Cube cube = null;
        if (StringUtils.isEmpty(cubeName))
        {
            errors.reject(ERROR_MSG, "cubeName parameter is required");
            return null;
        }

        for (Schema s : findSchemaList)
        {
            Cube findCube = s.getCubes().get(cubeName);
            if (null != findCube)
            {
                if (cube != null)
                {
                    errors.reject(ERROR_MSG, "Cube is ambigious, specify schemaName: " + cubeName);
                    return null;
                }
                cube = findCube;
            }
        }
        if (null == cube)
        {
            errors.reject(ERROR_MSG, "Cube not found: " + cubeName);
            return null;
        }

        String cubeCacheKey = c.getId() + "/" + cube.getSchema().getName() + "/" + cube.getUniqueName();
        final SQLException ex[] = new SQLException[1];
        Cube cachedCube = _cubes.get(cubeCacheKey,cube,new CacheLoader<String,Cube>()
        {
            @Override
            public Cube load(String key, @Nullable Object src)
            {
                try
                {
                    long start = System.currentTimeMillis();
                    Cube c = CachedCubeFactory.createCachedCube((Cube)src);
                    long end = System.currentTimeMillis();
                    return c;
                } catch (SQLException x)
                {
                    ex[0] = x;
                    return null;
                }
            }
        });
        if (null != ex[0])
            throw ex[0];
        return cachedCube;
    }


    /*
     * Start with one MondrianServer per container.  We'd like to get down to one MondrianServer.
     *
     * Need to detect changes to available catalogs in this container to close current server
     *
     * TODO: Investigate getting down to one MondrianServer instance
     */

    private static ServerReferenceCount getServer(Container c, User user) throws SQLException
    {
        ViewServlet.checkShuttingDown();

        synchronized (_serverLock)
        {
            ServerReferenceCount ref = _servers.get(getServerCacheKey(c));
            MondrianServer s = null != ref ? ref.get() : null;
            if (null == s)
            {
                Collection<OlapSchemaDescriptor> descriptors = SCHEMA_DESCRIPTOR_CACHE.getResources(c);

                StringBuilder sb = new StringBuilder();
                sb.append(
                        "<?xml version=\"1.0\"?>\n" +
                        "<DataSources>\n" +
                        "<DataSource>\n" +
                        "<DataSourceName>" + DATA_SOURCE_NAME + "</DataSourceName>\n" +
                        "<DataSourceDescription>" + PageFlowUtil.filter(c.getPath()) + "</DataSourceDescription>\n" +
                        "<URL></URL>\n" +
                        "<DataSourceInfo>" +
                        RolapConnectionProperties.Provider.name() + "=Mondrian;" +
                        RolapConnectionProperties.Jdbc.name() + "=" + getDatabaseConnectionString(c, user) +
//                        ";" + RolapConnectionProperties.DataSourceChangeListener.name() + "=" + _DataSourceChangeListener.class.getName() +
                        "</DataSourceInfo>\n" +
                        "<ProviderName>Mondrian</ProviderName>\n" +
                        "<ProviderType>MDP</ProviderType>\n" +
                        "<AuthenticationMode>Unauthenticated</AuthenticationMode>\n" +
                        "<Catalogs>\n");
                for (OlapSchemaDescriptor d : descriptors)
                {
                    sb.append(
                            "\n" +
                            "  <Catalog name=\"" + OlapSchemaDescriptor.makeCatalogName(d, c) + "\">\n" +
                            "  <Definition>" + d.getId() + "</Definition>\n" +
                            "  </Catalog>\n");
                }
                sb.append(
                        "\n</Catalogs>\n" +
                        "</DataSource>\n" +
                        "</DataSources>");
                _log.debug(sb.toString());
                RepositoryContentFinder rcf = new StringRepositoryContentFinder(sb.toString());
                s = MondrianServer.createWithRepository(rcf, new _CatalogLocator());
                _log.debug("Create new Mondrian server: " + c.getPath() + " " + s.toString());
//                MemTracker.getInstance().put(s);
                ref = new ServerReferenceCount(s, c);
                _servers.put(getServerCacheKey(c), ref);
            }
            return ref;
        }
    }

    public static void warmCube(User u, Container c, String schemaName, String configId, String cubeName)
    {
        try
        {
            OlapSchemaDescriptor sd  = getDescriptor(c, configId);
            if (null == sd)
                return;

            OlapConnection conn = sd.getConnection(c, u);
            if (null == conn)
                return;

            Cube cube = getCachedCube(sd, conn, c, u, schemaName, cubeName, getDummyBindException());
            if (null == cube)
                return;

            JSONArray jsonOnRows = new JSONArray();
            JSONObject jsonQuery = new JSONObject();
            jsonQuery.put("filter", new JSONArray());
            jsonQuery.put("showEmpty", false);
            jsonQuery.put("onRows", jsonOnRows);

            long start = System.currentTimeMillis();
            for (Dimension d : cube.getDimensions())
            {
                for (org.olap4j.metadata.Hierarchy h : d.getHierarchies())
                {
                    try
                    {
                        Map<String, Object> map = new HashMap<>();
                        map.put("hierarchy", h.getUniqueName());
                        map.put("members", "members");
                        jsonOnRows.put(0, map);

                        execCountDistinct(c, sd, conn, cube, jsonQuery, getDummyBindException());
                    }
                    catch (Exception ignore) {}
                }
            }
            long end = System.currentTimeMillis();
            _log.info("Warming the " + cubeName + " in container " + c.getName() + " took: " + DateUtil.formatDuration(end - start));
        }
        catch(Exception e)
        {
            _log.warn("Error trying to warm the " + cubeName + " in container " + c.getName(), e);
        }
    }

    private static void execCountDistinct(Container c, OlapSchemaDescriptor sd, OlapConnection conn,  Cube cube, JSONObject jsonQuery, BindException errors) throws Exception
    {
        QubeQuery qquery = new QubeQuery(cube);
        qquery.fromJson(jsonQuery, errors);

        if (errors.hasErrors())
            return;

        BitSetQueryImpl bitsetquery = new BitSetQueryImpl(c, sd, conn, qquery, errors);
        try(CellSet ignored = bitsetquery.executeQuery()){}
    }

    private static BindException getDummyBindException()
    {
        return new BindException(new Object(), "dummy");
    }

    public static OlapConnection getConnection(Container c, User u, String catalog) throws SQLException
    {
        ServerReferenceCount ref = ServerManager.getServer(c, u);
        if (null == ref || null == ref.get())
            return null;

        MondrianServer server = ref.get();
        OlapConnection olap = server.getConnection(DATA_SOURCE_NAME, catalog, null);
//        MemTracker.getInstance().put(olap);
        OlapConnection wrap = OlapConnectionProxy.wrap(olap, ref);
        MemTracker.getInstance().put(wrap);
        return wrap;
    }



    /*
     * This is to support XmlaServlet, don't hang on to this for more than one request.  If you need to hold on
     * longer we need a different interface
     */
    public static MondrianServer getMondrianServer(Container c, User u) throws SQLException
    {
        ServerReferenceCount ref = ServerManager.getServer(c, u);
        if (null == ref || null == ref.get())
            return null;

        MondrianServer server = ref.get();
        MondrianServer wrap = MondrianServerProxy.wrap(server, ref);
        MemTracker.getInstance().put(wrap);
        return wrap;
    }


    public static void olapSchemaDescriptorChanged(OlapSchemaDescriptor d)
    {
        synchronized (_serverLock)
        {
            for (ServerReferenceCount ref : _servers.values())
                ref.decrement();
            _servers.clear();
            _cubes.clear();
            BitSetQueryImpl.invalidateCache(d);
        }
    }


    public static void cubeDataChanged(Container c)
    {
        synchronized (_serverLock)
        {
            ServerReferenceCount ref = _servers.remove(getServerCacheKey(c));
            if (null != ref)
                ref.decrement();
            _cubes.clear();
            BitSetQueryImpl.invalidateCache(c);
        }
    }


    static void closeServer(MondrianServer s, @NotNull Container container)
    {
        _log.debug("Shutdown Mondrian server: " + s.toString());

        try
        {
            Collection<OlapSchemaDescriptor> descriptors = SCHEMA_DESCRIPTOR_CACHE.getResources(container);
            for (OlapSchemaDescriptor d : descriptors)
            {
                String catalogName = OlapSchemaDescriptor.makeCatalogName(d, container);
                OlapConnection c = s.getConnection(DATA_SOURCE_NAME, catalogName, null);
                RolapConnection r = c.unwrap(RolapConnection.class);
                r.getCacheControl(null).flushSchemaCache();
            }
        }
        catch (Exception x)
        {
            _log.debug("Shutdown Mondrian server flush cache failed: " + s.toString());
            _log.debug(x.getMessage());
        }

        s.shutdown();
        BitSetQueryImpl.invalidateCache(container);
    }


    @SuppressWarnings("UnusedParameters")
    static String getDatabaseConnectionString(Container c, User user)
    {
        //Currently all internal connections must be done with a limited service user
        return "jdbc:labkey:query:" + getDatabaseName(c) + ":container=" + c.getRowId() + ":schema=core";
    }


    static String getDatabaseName(Container c)
    {
        return "dn_" + c.getRowId();
    }


    static Map<_DataSourceChangeListener, Boolean> changeListeners = Collections.synchronizedMap(new WeakHashMap<_DataSourceChangeListener, Boolean>());

    public static class _DataSourceChangeListener implements DataSourceChangeListener
    {
        final Map<String,Boolean> ischanged = Collections.synchronizedMap(new HashMap<String, Boolean>());

        public _DataSourceChangeListener()
        {
            changeListeners.put(this,Boolean.TRUE);
        }

        @Override
        public boolean isHierarchyChanged(RolapHierarchy hierarchy)
        {
            Boolean waschanged = ischanged.put(hierarchy.getUniqueName(),Boolean.FALSE);
            return waschanged != Boolean.FALSE;
        }

        @Override
        public boolean isAggregationChanged(AggregationKey aggregation)
        {
            Boolean waschanged = ischanged.put(aggregation.toString(),Boolean.FALSE);
            return waschanged != Boolean.FALSE;
        }
    }


    private static class _CatalogLocator implements CatalogLocator
    {
        public String locate(String catalogPath)
        {
            try
            {
                // Need a way to get a URL or something to the resource
                OlapSchemaDescriptor d = SCHEMA_DESCRIPTOR_CACHE.getResource(catalogPath);
                if (null == d)
                    throw new IOException("catalog not found: " + catalogPath);
                File f = d.getFile();
                return f.getAbsolutePath();
            }
            catch (IOException x)
            {
                throw new UnexpectedException(x);
            }
        }
    }



    static abstract class ReferenceCount
    {
        AtomicInteger counter = new AtomicInteger(1);

        void increment()
        {
            counter.incrementAndGet();
        }

        void decrement()
        {
            int c = counter.decrementAndGet();
            if (0 == c)
                close();
        }

        abstract void close();
    }

    static class ServerReferenceCount extends ReferenceCount
    {
        MondrianServer _server;
        Container _container;

        ServerReferenceCount(@NotNull MondrianServer s, @NotNull Container c)
        {
            _server = s;
            _container = c;
        }

        MondrianServer get()
        {
            return (counter.get() > 0) ? _server : null;
        }

        @Override
        void increment()
        {
            super.increment();
            _log.debug("increment reference: " + counter.get() + " " + _server.toString());
        }

        @Override
        void decrement()
        {
            _log.debug("decrement reference: " + (counter.get()-1) + " " + _server.toString());
            super.decrement();
        }

        @Override
        void close()
        {
            closeServer(_server, _container);
            _server = null;
        }
    }


    public static class OlapConnectionProxy implements InvocationHandler
    {
        final OlapConnection _inner;
        final ReferenceCount _count;

        static OlapConnection wrap(OlapConnection conn, ReferenceCount ref)
        {
            OlapConnection wrapper = (OlapConnection) Proxy.newProxyInstance(
                    conn.getClass().getClassLoader(),
                    new Class[] {OlapConnection.class},
                    new OlapConnectionProxy(conn, ref));
            return wrapper;
        }

        OlapConnectionProxy(OlapConnection c, ReferenceCount ref)
        {
            _inner = c;
            _count = ref;
            _count.increment();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            try
            {
                return method.invoke(_inner,args);
            }
            finally
            {
                if ("close".equals(method.getName()))
                    _count.decrement();
            }
        }
    }


    public static class MondrianServerProxy implements InvocationHandler
    {
        final GUID _guid = new GUID();
        final MondrianServer _inner;
        final ReferenceCount _count;
        final CountingSemaphore _semaphore = new CountingSemaphore(4, true);

        static MondrianServer wrap(MondrianServer conn, ReferenceCount ref)
        {
            MondrianServer wrapper = (MondrianServer) Proxy.newProxyInstance(
                    conn.getClass().getClassLoader(),
                    new Class[] {MondrianServer.class}, // GUID.HasGuid.class},
                    new MondrianServerProxy(conn, ref));
            return wrapper;
        }

        MondrianServerProxy(MondrianServer c, ReferenceCount ref)
        {
            _inner = c;
            _count = ref;
            _count.increment();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            if ("getGUID".equals(method.getName()))
            {
                return _guid;
            }
            else if ("shutdown".equals(method.getName()))
            {
                _count.decrement();
                return null;
            }
            else if ("executeOlapQuery".equals(method.getName()))
            {
                try (AutoCloseable permit = _semaphore.acquire())
                {
                    return method.invoke(_inner,args);
                }
            }
            else
            {
                return method.invoke(_inner, args);
            }
        }
    }

    static
    {
        MemTracker.getInstance().register(new _MemTrackerListener());
    }

    public static class _MemTrackerListener implements MemTrackerListener
    {
        @Override
        public void beforeReport(Set<Object> set)
        {
            synchronized (_serverLock)
            {
                for (ServerReferenceCount ref : _servers.values())
                {
                    MondrianServer s = ref.get();
                    if (null != s)
                        set.add(s);
                }
            }
        }
    }
}