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
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MemTrackerListener;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewServlet;
import org.labkey.query.controllers.OlapController;
import org.labkey.query.olap.metadata.CachedCube;
import org.labkey.query.olap.metadata.Olap4JCachedCubeFactory;
import org.labkey.query.olap.metadata.RolapCachedCubeFactory;
import org.labkey.query.olap.rolap.RolapCubeDef;
import org.labkey.query.persist.QueryManager;
import org.olap4j.CellSet;
import org.olap4j.OlapConnection;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Dimension;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Schema;
import org.springframework.validation.BindException;

import javax.servlet.ServletContextEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
    private static final Logger LOG = Logger.getLogger(ServerManager.class);

    private static final Map<String, ServerReferenceCount> SERVERS = new HashMap<>();
    private static final Object SERVERS_LOCK = new Object();

    private static final ModuleResourceCache<OlapSchemaDescriptor> MODULE_DESCRIPTOR_CACHE = ModuleResourceCaches.create(new Path(OlapSchemaCacheHandler.DIR_NAME), "Olap cube defintions (module)", new OlapSchemaCacheHandler());
    private static final BlockingStringKeyCache<OlapSchemaDescriptor> DB_DESCRIPTOR_CACHE = CacheManager.getBlockingStringKeyCache(1000, CacheManager.HOUR, "Olap cube definitions (db) ", new OlapCacheLoader());

    private static final BlockingStringKeyCache<Cube> CUBES = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.HOUR, "cube cache", null);

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
            public String getName()
            {
                return "OLAP server manager";
            }

            @Override
            public void shutdownPre(ServletContextEvent servletContextEvent)
            {
                SERVERS.clear();
            }

            @Override
            public void shutdownStarted(ServletContextEvent servletContextEvent)
            {
                SERVERS.clear();
            }
        });
    }


    static String getServerCacheKey(Container c)
    {
        return MondrianServer.class.getName() + "/" + c.getId();
    }

    private static class OlapCacheLoader implements CacheLoader<String, OlapSchemaDescriptor>
    {
        @Override
        public OlapSchemaDescriptor load(String key, @Nullable Object argument)
        {
            String[] parts = key.split("/", 2);
            if (parts.length != 2)
                throw new IllegalStateException("Unrecognized cache key format: " + key);

            String containerId = parts[0];
            String name = parts[1];

            Container c = ContainerManager.getForId(containerId);
            if (c == null)
                throw new IllegalStateException("Container not available: " + containerId);

            SimpleFilter filter = new SimpleFilter();
            // CONSIDER: cache by module as well?
            //filter.addCondition(FieldKey.fromParts("module"), module.getName());
            filter.addCondition(FieldKey.fromParts("container"), containerId);
            filter.addCondition(FieldKey.fromParts("name"), name);

            TableSelector s = new TableSelector(QueryManager.get().getTableInfoOlapDef(), filter, null);
            OlapDef def = s.getObject(OlapDef.class);
            if (def == null)
                return null;

            return new CustomOlapSchemaDescriptor(def);
        }
    }

    @Nullable
    public static OlapSchemaDescriptor getDescriptor(@NotNull Container c, @NotNull String schemaId)
    {
        // crack the schemaId into module and name parts
        ModuleResourceCache.CacheId id = OlapSchemaCacheHandler.parseOlapCacheKey(schemaId);

        // look for descriptor in database by container and name
        String olapDefCacheKey = c.getId() + "/" + id.getName();
        OlapSchemaDescriptor d = DB_DESCRIPTOR_CACHE.get(olapDefCacheKey);
        if (null != d && d.getModule() == id.getModule() && c.getActiveModules().contains(d.getModule()))
            return d;

        // look for descriptor in active modules
        d = MODULE_DESCRIPTOR_CACHE.getResource(schemaId);
        if (null != d && c.getActiveModules().contains(d.getModule()))
            return d;

        return null;
    }

    @NotNull
    public static List<OlapSchemaDescriptor> getDescriptors(@NotNull Container c)
    {
        List<OlapSchemaDescriptor> ret = new ArrayList<>();

        // look for descriptor in active modules
        for (OlapSchemaDescriptor osd : MODULE_DESCRIPTOR_CACHE.getResources(c))
        {
            if (osd.isExposed(c))
                ret.add(osd);
        }

        // TODO: add list of all olap descriptors in the container to the cache
        //List<OlapSchemaDescriptor> descriptors = DB_SCHEMA_DESCRIPTOR_CACHE.get(c.getId());
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        TableSelector s = new TableSelector(QueryManager.get().getTableInfoOlapDef(), new HashSet<>(Arrays.asList("name")), filter, null);
        for (String name : s.getArrayList(String.class))
        {
            // look for descriptor in database by container and name
            String olapDefCacheKey = c.getId() + "/" + name;
            OlapSchemaDescriptor d = DB_DESCRIPTOR_CACHE.get(olapDefCacheKey);
            if (d != null)
                ret.add(d);
        }

        return ret;
    }


    public static Cube getCachedCube(@NotNull OlapSchemaDescriptor sd, OlapConnection conn, final Container c, final User user, String schemaName, String cubeName, BindException errors)
            throws SQLException, IOException
    {
        if (sd.usesRolap())
            return getCachedCubeRolap(sd, c, user, cubeName, null);
        else
            return getCachedCubeMondrian(sd, conn, c, user, schemaName, cubeName, errors);
    }


    /* Note we pass in OlapConnection here, that's because the connection must stay open in order to use the cube
     * (Unless we create a cached cube)
     */
    private static Cube getCachedCubeMondrian(OlapSchemaDescriptor d, OlapConnection conn, final Container c, final User user, String schemaName, String cubeName, BindException errors)
        throws SQLException, IOException
    {
        Cube cube = null;
        if (StringUtils.isEmpty(cubeName))
        {
            errors.reject(ERROR_MSG, "cubeName parameter is required");
            return null;
        }

        for (Schema s : d.getSchemas(conn, c, user))
        {
            if (null != schemaName && !StringUtils.equalsIgnoreCase(schemaName, s.getName()))
                continue;
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
        Cube cachedCube = CUBES.get(cubeCacheKey,cube,new CacheLoader<String,Cube>()
        {
            @Override
            public Cube load(String key, @Nullable Object src)
            {
                try
                {
                    long start = System.currentTimeMillis();
                    Cube c = (new Olap4JCachedCubeFactory()).createCachedCube((Cube)src);
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

    public static Cube getCachedCubeRolap(OlapSchemaDescriptor d, final Container c, final User user, String cubeName, @Nullable final UserSchema schema)
            throws SQLException, IOException
    {
        RolapCubeDef rolap = d.getRolapCubeDefinitionByName(cubeName);

        if (null == rolap)
        {
            throw new IllegalArgumentException("Unable to find cube definiton for cubeName: " + cubeName);
        }

        String cubeCacheKey = c.getId() + rolap.getName();
        final SQLException ex[] = new SQLException[1];
        Cube cachedCube = CUBES.get(cubeCacheKey,rolap,new CacheLoader<String,Cube>()
        {
            @Override
            public Cube load(String key, @Nullable Object src)
            {
                try
                {
                    long start = System.currentTimeMillis();

                    QuerySchema startSchema = null!=schema ? schema : DefaultSchema.get(user, c).getSchema("core");
                    CachedCube cachedCube = new RolapCachedCubeFactory((RolapCubeDef) src, startSchema).createCachedCube();

                    long end = System.currentTimeMillis();
                    return cachedCube;
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

        synchronized (SERVERS_LOCK)
        {
            ServerReferenceCount ref = SERVERS.get(getServerCacheKey(c));
            MondrianServer s = null != ref ? ref.get() : null;
            if (null == s)
            {
                Collection<OlapSchemaDescriptor> descriptors = getDescriptors(c);

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
                    if ("junit".equals(d.getName()) && (!c.getParsedPath().equals(JunitUtil.getTestContainerPath())))
                        continue;
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
                LOG.debug(sb.toString());
                RepositoryContentFinder rcf = new StringRepositoryContentFinder(sb.toString());
                s = MondrianServer.createWithRepository(rcf, new _CatalogLocator(c));
                LOG.debug("Create new Mondrian server: " + c.getPath() + " " + s.toString());
//                MemTracker.getInstance().put(s);
                ref = new ServerReferenceCount(s, c);
                SERVERS.put(getServerCacheKey(c), ref);
            }
            return ref;
        }
    }


    public static String warmCube(User user, Container c, String schemaName, String configId, String cubeName)
    {
        String result;

        try
        {
            result = "Starting warm of " + cubeName + " in container " + c.getName();
            LOG.info(result);
            long start = System.currentTimeMillis();

            OlapSchemaDescriptor sd  = getDescriptor(c, configId);
            if (null == sd)
                return "Error: No cached descriptor found for " + configId + " in container " + c.getName();

            if (!sd.shouldWarmCube(c))
            {
                result = "Skipping warm of " + cubeName + " in container " + c.getName();
                LOG.info(result);
                return result;
            }

            User warmCubeUser;
            OlapConnection conn = null;

            if (sd.usesMondrian())
            {
                warmCubeUser = user;
                conn = sd.getConnection(c, warmCubeUser);
                if (null == conn)
                    return "Error: No olap connection for " + cubeName + " in container " + c.getName();
            }
            else
            {
                // OK: to leave OlapConnection null as it is not used in any configuration except Mondrian
                warmCubeUser = new LimitedUser(User.guest, new int[0], Collections.singleton(RoleManager.getRole(ReaderRole.class)), false);
                warmCubeUser.setPrincipalType(PrincipalType.SERVICE);
                warmCubeUser.setDisplayName("Warm OLAP Cache User");
                warmCubeUser.setEmail("warmolapcache@labkey.org");
                warmCubeUser.setEntityId(new GUID());
            }

            long s = System.currentTimeMillis();

            Cube cube = getCachedCube(sd, conn, c, warmCubeUser, schemaName, cubeName, getDummyBindException());

            if (null == cube)
                return "Error: No cached cube for " + cubeName + " in container " + c.getName();
            long e = System.currentTimeMillis();
            LOG.debug(DateUtil.formatDuration(e-s) + " CUBE DEFINITION");

            JSONArray jsonOnRows = new JSONArray();
            JSONObject jsonQuery = new JSONObject();
            jsonQuery.put("filter", new JSONArray());
            jsonQuery.put("showEmpty", false);
            jsonQuery.put("onRows", jsonOnRows);

            for (Dimension d : cube.getDimensions())
            {
                if (d.getDimensionType() == Dimension.Type.MEASURE)
                    continue;
                for (org.olap4j.metadata.Hierarchy h : d.getHierarchies())
                {
                    try
                    {
                        Level l = h.getLevels().get(h.getLevels().size()-1);

                        Map<String, Object> map = new HashMap<>();
                        map.put("hierarchy", h.getUniqueName());
                        map.put("members", "members");
                        jsonOnRows.put(0, map);
//                        ((JSONObject)jsonQuery.get("onRows")).put("level", l.getUniqueName());
//                        ((JSONObject)jsonQuery.get("onRows")).put("members", "members");

                        // TODO: what is the countDistinctLevel???
                        boolean isCountDistinctLevel = l.getUniqueName().equals("[Patient].[Patient]");
                        if (!isCountDistinctLevel)
                        {

                            if (ViewServlet.isShuttingDown())
                                return "warm cache stopped because of server shutdown";
                            s = System.currentTimeMillis();
                            execCountDistinct(c, null, sd, conn, cube, jsonQuery, getDummyBindException());
                            e = System.currentTimeMillis();
                            LOG.info(DateUtil.formatDuration(e - s) + " " + jsonQuery.toString());
                        }


                        // 20975: Ensure we touch specimen queries
                        if (l.getUniqueName().equals("[Specimen].[Specimen]") || h.getUniqueName().contains("[Specimen."))
                        {
                            jsonQuery.put("countDistinctLevel", "[Specimen].[Specimen]");

                            if (ViewServlet.isShuttingDown())
                                return "warm cache stopped because of server shutdown";

                            s = System.currentTimeMillis();
                            execCountDistinct(c, null, sd, conn, cube, jsonQuery, getDummyBindException());
                            e = System.currentTimeMillis();
                            LOG.info(DateUtil.formatDuration(e - s) + " " + jsonQuery.toString());

                            jsonQuery.remove("countDistinctLevel");
                        }
                    }
                    catch (Exception ignore)
                    {

                        LOG.warn("Error trying to warm the " + cubeName + " in container " + c.getName(), ignore);
                    }
                }
            }
            long end = System.currentTimeMillis();
            result = "Warming the " + cubeName + " in container " + c.getName() + " took: " + DateUtil.formatDuration(end - start);
            LOG.info(result);
        }
        catch(Exception e)
        {
            result = "Error trying to warm the " + cubeName + " in container " + c.getName();
            LOG.warn(result, e);
        }

        return result;
    }


    private static void execCountDistinct(Container c, User user, OlapSchemaDescriptor sd, OlapConnection conn,  Cube cube, JSONObject jsonQuery, BindException errors) throws Exception
    {
        QubeQuery qquery = new QubeQuery(cube);
        qquery.fromJson(jsonQuery, errors);

        if (errors.hasErrors())
            return;

        BitSetQueryImpl bitsetquery = new BitSetQueryImpl(c, user, sd, cube, conn, qquery, errors);
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

    public static class CacheListener implements org.labkey.api.cache.CacheListener
    {
        @Override
        public void clearCaches()
        {
            ServerManager.clearCaches();
        }
    }

    /**
     * Called from CacheListener to clear cube related caches.
     * Also called by CacheManager.clearAllKnownCaches() prior to the module specific caches being cleared.
     */
    public static void clearCaches()
    {
        synchronized (SERVERS_LOCK)
        {
            for (ServerReferenceCount ref : SERVERS.values())
                ref.decrement();
            SERVERS.clear();
            CUBES.clear();
            DB_DESCRIPTOR_CACHE.clear();
            BitSetQueryImpl.invalidateCache();
        }
    }

    public static void olapSchemaDescriptorChanged(OlapSchemaDescriptor d)
    {
        synchronized (SERVERS_LOCK)
        {
            for (ServerReferenceCount ref : SERVERS.values())
                ref.decrement();
            SERVERS.clear();
            CUBES.clear();
            if (d != null && d.getContainer() != null)
            {
                String olapDefCacheKey = d.getContainer().getId() + "/" + d.getName();
                DB_DESCRIPTOR_CACHE.remove(olapDefCacheKey);
            }
            BitSetQueryImpl.invalidateCache(d);
        }
    }


    public static void cubeDataChanged(Container c)
    {
        synchronized (SERVERS_LOCK)
        {
            ServerReferenceCount ref = SERVERS.remove(getServerCacheKey(c));
            if (null != ref)
                ref.decrement();
            CUBES.clear();
            DB_DESCRIPTOR_CACHE.removeUsingPrefix(c.getId());
            BitSetQueryImpl.invalidateCache(c);
        }
    }


    static void closeServer(MondrianServer s, @NotNull Container container)
    {
        LOG.debug("Shutdown Mondrian server: " + s.toString());

        try
        {
            Collection<OlapSchemaDescriptor> descriptors = getDescriptors(container);
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
            LOG.debug("Shutdown Mondrian server flush cache failed: " + s.toString());
            LOG.debug(x.getMessage());
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
        private final Container _container;

        public _CatalogLocator(Container c)
        {
            _container = c;
        }

        public String locate(String catalogPath)
        {
            try
            {
                OlapSchemaDescriptor d = getDescriptor(_container, catalogPath);
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
            LOG.debug("increment reference: " + counter.get() + " " + _server.toString());
        }

        @Override
        void decrement()
        {
            LOG.debug("decrement reference: " + (counter.get() - 1) + " " + _server.toString());
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
            switch (method.getName())
            {
                case "getGUID":
                    return _guid;
                case "shutdown":
                    _count.decrement();
                    return null;
                case "executeOlapQuery":
                    try (AutoCloseable permit = _semaphore.acquire())
                    {
                        return method.invoke(_inner,args);
                    }
                default:
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
            synchronized (SERVERS_LOCK)
            {
                for (ServerReferenceCount ref : SERVERS.values())
                {
                    MondrianServer s = ref.get();
                    if (null != s)
                        set.add(s);
                }
            }
        }
    }
}
