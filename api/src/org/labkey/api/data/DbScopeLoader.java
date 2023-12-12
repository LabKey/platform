package org.labkey.api.data;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbScope.LabKeyDataSource;
import org.labkey.api.data.dialect.SqlDialect.DataSourcePropertyReader;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.logging.LogHelper;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Holds a data source's configuration information, attempts to connect to the corresponding database when requested,
 * and holds the DbScope associated with each successfully connected data source. Currently, we attempt a connection
 * when the data source is first referenced... or if all data sources are requested (e.g., when showing the external
 * schema admin page or sending data source metrics to labkey.org). {@link #clearDbScope()} can be used to attempt a
 * re-connection to a data source that failed its previous connection attempt(s). This is used by the external schema
 * admin page, which provides a link to attempt re-connecting to all data sources that failed previous connection(s).
 * Note that there's not always a 1:1 correspondence between DbScope/DbScopeLoader and LabKeyDataSource because module
 * data sources that reference non-existent databases in dev mode will map to the primary data source. That's why
 * dsName is passed into this constructor instead of delegating to the LabKeyDataSource.
 */
class DbScopeLoader
{
    private static final Logger LOG = LogHelper.getLogger(DbScopeLoader.class, "DataSource connection problems");

    // Marker object for scope that has failed to connect
    private static final DbScope BAD_SCOPE = new DbScope();

    private final String _dsName;
    private final String _displayName;
    private final LabKeyDataSource _dataSource;
    private final AtomicReference<DbScope> _dbScopeRef = new AtomicReference<>();

    // Stash DataSource properties, but defer the initial connection until get() is called.
    DbScopeLoader(String dsName, LabKeyDataSource dataSource)
    {
        _dsName = dsName; // Doesn't always match dataSource.getDsName() (e.g., missing module data source case)
        _displayName = null != dataSource.getDisplayName() ? dataSource.getDisplayName() : extractDisplayName(_dsName);
        _dataSource = dataSource;
    }

    private static String extractDisplayName(String dsName)
    {
        if (dsName.endsWith("DataSource"))
            return dsName.substring(0, dsName.length() - 10);
        else
            return dsName;
    }

    void clearDbScope()
    {
        _dbScopeRef.set(null);
    }

    private final Object LOCK = new Object();

    static final Consumer<DbScope> NO_OP_CONSUMER = dbScope -> {};

    @Nullable DbScope get()
    {
        return get(NO_OP_CONSUMER);
    }

    @Nullable DbScope get(Consumer<DbScope> firstConnectionConsumer)
    {
        DbScope scope = _dbScopeRef.get();

        if (null == scope)
        {
            // Double-checked locking to prevent multiple threads making the initial connection
            synchronized (LOCK)
            {
                scope = _dbScopeRef.get();

                if (null == scope)
                {
                    try
                    {
                        scope = new DbScope(this);
                        firstConnectionConsumer.accept(scope); // Do this before prepare(), which could open connections
                        scope.getSqlDialect().prepare(scope);
                    }
                    catch (Throwable t)
                    {
                        // Always log, but callers determine if null DbScope is fatal or not
                        LOG.error("Cannot connect to DataSource \"" + getDsName() + "\" defined in " + AppProps.getInstance().getWebappConfigurationFilename() + ". This DataSource will not be available during this server session unless a successful retry is initiated from the schema administration page.", t);
                        DbScope.addDataSourceFailure(getDsName(), t);
                        scope = BAD_SCOPE;
                    }

                    boolean success = _dbScopeRef.compareAndSet(null, scope);
                    assert success : "Expected DbScope to be null!";
                }
            }
        }

        return scope != BAD_SCOPE ? scope : null;
    }

    // get() without attempting to create
    @Nullable DbScope getIfPresent()
    {
        DbScope scope = _dbScopeRef.get();
        return scope != BAD_SCOPE ? scope : null;
    }

    boolean isFailed()
    {
        return BAD_SCOPE == _dbScopeRef.get();
    }

    public DataSourcePropertyReader getDsProps()
    {
        return _dataSource.getDataSourcePropertyReader();
    }

    public String getDsName()
    {
        return _dsName;
    }

    public String getDisplayName()
    {
        return _displayName;
    }

    public DataSource getDataSource()
    {
        return _dataSource.getDataSource();
    }

    public LabKeyDataSource getLabKeyDataSource()
    {
        return _dataSource;
    }
}
