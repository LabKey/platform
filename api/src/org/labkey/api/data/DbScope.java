package org.labkey.api.data;

import org.apache.log4j.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.Reference;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * Class that wraps a datasource and is shared amongst instances of the
 * DbSchema that need to share the datasource.
 * Copyright (C) 2004 Fred Hutchinson Cancer Research Center. All Rights Reserved.
 * User: migra
 * Date: Nov 16, 2005
 * Time: 10:20:54 AM
 */
public class DbScope
{
    private static final ConnectionMap _initializedConnections = newConnectionMap();
    private static final Logger _log = Logger.getLogger(DbScope.class);

    private String dataSourceName;
    private DataSource dataSource;
    private SqlDialect _dialect;

    private ThreadLocal<Connection> threadConnection = new ThreadLocal<Connection>();
    private ThreadLocal<LinkedList<Runnable>> _transactedRemovals = new ThreadLocal<LinkedList<Runnable>>()
    {
        protected LinkedList<Runnable> initialValue()
        {
            return new LinkedList<Runnable>();
        }
    };


    protected DbScope()
    {
    }


    DbScope(String dsName) throws NamingException
    {
        InitialContext ctx = new InitialContext();
        Context envCtx = (Context) ctx.lookup("java:comp/env");
        dataSource = (DataSource) envCtx.lookup(dsName);
        dataSourceName = "java:comp/env/" + dsName;
    }


    public String getDataSourceName()
    {
        return dataSourceName;
    }


    public DataSource getDataSource()
    {
        return dataSource;
    }


/*    public void close() throws SQLException
    {
        ((BasicDataSource) dataSource).close();
        dataSource = null;
    } */


    public Connection beginTransaction() throws SQLException
    {
        if (null != threadConnection.get())
            throw new IllegalStateException("Existing transaction");

        Connection conn = _getConnection(null);
        conn.setAutoCommit(false);
        threadConnection.set(conn);
        return conn;
    }


    public void commitTransaction() throws SQLException
    {
        Connection conn = threadConnection.get();
        assert null != conn;
        conn.commit();
        conn.setAutoCommit(true);
        conn.close();
        threadConnection.remove();

        LinkedList<Runnable> removes = _transactedRemovals.get();
        while (!removes.isEmpty())
            removes.removeFirst().run();
    }


    public void rollbackTransaction()
    {
        Connection conn = threadConnection.get();
        assert null != conn;
        try
        {
            conn.rollback();
        }
        catch (SQLException x)
        {
            _log.error(x);
        }
        try
        {
            conn.setAutoCommit(true);
        }
        catch (SQLException x)
        {
            _log.error(x);
        }
        try
        {
            conn.close();
        }
        catch (SQLException x)
        {
            _log.error(x);
        }
        threadConnection.remove();
        _transactedRemovals.get().clear();
    }


    public boolean isTransactionActive()
    {
        return threadConnection.get() != null;
    }

    public Connection getConnection() throws SQLException
    {
        return getConnection(null);
    }

    public Connection getConnection(Logger log) throws SQLException
    {
        Connection conn = threadConnection.get();
        if (null == conn)
            conn = _getConnection(log);
        return conn;
    }


    static Class classDelegatingConnection = null;
    static Method methodGetInnermostDelegate = null;
    static
    {
        try
        {
            classDelegatingConnection = Class.forName("org.apache.tomcat.dbcp.dbcp.DelegatingConnection");
            methodGetInnermostDelegate = classDelegatingConnection.getMethod("getInnermostDelegate");
        }
        catch (Exception x)
        {
            _log.info("Could not find class DelegatingConnection", x);
        }
    }


    private static Connection getDelegate(Connection conn)
    {
        Connection delegate = null;
        if (null != classDelegatingConnection && null != methodGetInnermostDelegate && classDelegatingConnection.isAssignableFrom(conn.getClass()))
        {
            try
            {
                delegate = (Connection)methodGetInnermostDelegate.invoke(conn);
            }
            catch (Exception x)
            {
                _log.error("Unexpected error", x);
            }
        }
        if (null == delegate)
            delegate = conn;
        return delegate;
    }


    Integer spidUnknown = -1;

    protected Connection _getConnection(Logger log) throws SQLException
    {
        Connection conn = dataSource.getConnection();

        //
        // Handle one time per-connection setup
        // relies on pool implementation reusing same connection/wrapper instances
        //

        Connection delegate = getDelegate(conn);
        Integer spid = _initializedConnections.get(delegate);

        if (null == spid)
        {
            if (_dialect instanceof SqlDialectMicrosoftSQLServer)
            {
                Statement stmt = conn.createStatement();
                stmt.execute("SET ARITHABORT ON");
                stmt.close();
            }
            
            if (_dialect != null)
                spid = _dialect.getSPID(delegate);

            _initializedConnections.put(delegate, spid == null ? spidUnknown : spid);
        }

        return new ConnectionWrapper(conn, _dialect, spid, log);
    }


    public void releaseConnection(Connection conn) throws SQLException
    {
        Connection threadConn = threadConnection.get();
        assert null == threadConn || conn == threadConn; //Should release same conn we handed out

        if (null == threadConn)
            conn.close();
    }


    public void closeConnection()
    {
        Connection conn = threadConnection.get();
        if (conn != null)
        {
            try
            {
                conn.close();
            }
            catch (SQLException e)
            {
                _log.error("Failed to close connection", e);
            }
            threadConnection.remove();
            _transactedRemovals.remove();
        }
    }


    public List<DbSchema> getSchemas()
    {
        List<DbSchema> list = new ArrayList<DbSchema>();
        for (String name : DbSchema.getNames())
        {
            DbSchema schema = DbSchema.get(name);
            if (schema.getScope() == this)
                list.add(schema);
        }

        return list;
    }


    public void setSqlDialect(SqlDialect sqlDialect)
    {
        _dialect = sqlDialect;
    }


    public SqlDialect getSqlDialect()
    {
        return _dialect;
    }


    public void addCommitTask(Runnable task)
    {
        if (!isTransactionActive())
        {
            throw new IllegalStateException("Must be inside a transaction");
        }
        _transactedRemovals.get().add(task);
    }
    

    void addTransactedRemoval(final DatabaseCache cache, final String name, final boolean isPrefix)
    {
        addCommitTask(new Runnable() {
            public void run()
            {
                if (isPrefix)
                    cache.removeUsingPrefix(name);
                else
                    cache.remove(name);
            }
        });
    }

    void addTransactedRemoval(final DatabaseCache cache)
    {
        addCommitTask(new Runnable() {
            public void run()
            {
                cache.clear();
            }
        });
    }


    void addTransactedClear(final TableInfo tinfo)
    {
        addCommitTask(new Runnable() {
            public void run()
            {
                DbCache.clear(tinfo);
            }
        });
    }


    void addTransactedInvalidate(final TableInfo tinfo)
    {
        addCommitTask(new Runnable() {
            public void run()
            {
                DbCache.invalidateAll(tinfo);
            }
        });
    }


    interface ConnectionMap
    {
        Integer get(Connection c);
        Integer put(Connection c, Integer spid);
    }


    static ConnectionMap newConnectionMap()
    {
//        final HashMap<Connection,Integer> m = new HashMap<Connection,Integer>();
//        final HashMap<Connection,Integer> m = new WeakHashMap<Connection,Integer>();
        final _WeakestLinkMap<Connection,Integer> m = new _WeakestLinkMap<Connection,Integer>();
        return new ConnectionMap() {
            public synchronized Integer get(Connection c)
            {
                return m.get(c);
            }

            public synchronized Integer put(Connection c, Integer spid)
            {
                return m.put(c,spid);
            }
        };
    }


    /** weak identity hash map, could just subclass WeakHashMap but eq() is static (and not overridable) */
    private static class _WeakestLinkMap<K, V>
    {
        int _max = 1000;
        final ReferenceQueue<K> _q = new ReferenceQueue<K>();

        LinkedHashMap<_IdentityWrapper, V> _map = new LinkedHashMap<_IdentityWrapper, V>()
        {
            protected boolean removeEldestEntry(Map.Entry<_IdentityWrapper, V> eldest)
            {
                _purge();
                return size() > _max;
            }
        };

        private class _IdentityWrapper extends WeakReference<K>
        {
            int _hash;

            _IdentityWrapper(K o)
            {
                super(o, _q);
                _hash = System.identityHashCode(get());
            }

            public int hashCode()
            {
                return _hash;
            }

            public boolean equals(Object obj)
            {
                return obj instanceof Reference && get() == ((Reference)obj).get();
            }
        }

        _WeakestLinkMap()
        {
        }

        _WeakestLinkMap(int max)
        {
            _max = max;
        }

        public V put(K key, V value)
        {
            return _map.put(new _IdentityWrapper(key), value);
        }

        public V get(K key)
        {
            return _map.get(new _IdentityWrapper(key));
        }

        private void _purge()
        {
            _IdentityWrapper w;
            while (null != (w = (_IdentityWrapper)_q.poll()))
            {
                _map.remove(w);
            }
        }
    }

    public static void test()
    {
        _WeakestLinkMap<String,Integer> m = new _WeakestLinkMap<String,Integer>(10);
        //noinspection MismatchedQueryAndUpdateOfCollection
        Set<String> save = new HashSet<String>();
        
        for (int i=0 ; i<100000 ; i++)
        {
            if (i%1000 == 0) System.gc();
            String s = "" + i;
            if (i % 3 == 0)
                save.add(s);
            m.put(s,i);
        }
    }
}
