package org.labkey.api.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbScope.LabKeyDataSourceProperties;
import org.labkey.api.data.dialect.SqlDialect.DataSourceProperties;
import org.labkey.api.settings.AppProps;

import javax.servlet.ServletException;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

class DbScopeLoader
{
    private static final Logger LOG = LogManager.getLogger(DbScopeLoader.class);

    // Marker object for scope that failed initial connection. For now, we try once and never again. We could easily
    // adjust to mark scopes that fail subsequent connection attempts and retry if enough time has passed since the last
    // failure. This would provide for more graceful handling of data sources that come and go.
    private static final DbScope BAD_SCOPE = new DbScope();

    private final DataSourceProperties _dsProps;
    private final String _dsName;
    private final String _displayName;
    private final DataSource _dataSource;
    private final LabKeyDataSourceProperties _labkeyProps;
    private final AtomicReference<DbScope> _dbScopeRef = new AtomicReference<>();

    // Stash some DataSource properties, but defer the initial connection until get() is called.
    DbScopeLoader(String dsName, DataSource dataSource, LabKeyDataSourceProperties props)
    {
        _dsProps = new DataSourceProperties(dsName, dataSource);
        _dsName = dsName;
        _displayName = null != props.getDisplayName() ? props.getDisplayName() : extractDisplayName(_dsName);
        _dataSource = dataSource;
        _labkeyProps = props;
    }

    private static String extractDisplayName(String dsName)
    {
        if (dsName.endsWith("DataSource"))
            return dsName.substring(0, dsName.length() - 10);
        else
            return dsName;
    }

    private final Object LOCK = new Object();

    @Nullable DbScope get()
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
                        scope.getSqlDialect().prepare(scope);
                    }
                    catch (SQLException | ServletException e)
                    {
                        // Always log, but callers determine if null DbScope is fatal or not
                        LOG.error("Cannot connect to DataSource \"" + _dsName + "\" defined in " + AppProps.getInstance().getWebappConfigurationFilename() + ". This DataSource will not be available during this server session.", e);
                        DbScope.addDataSourceFailure(_dsName, e);
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

    public DataSourceProperties getDsProps()
    {
        return _dsProps;
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
        return _dataSource;
    }

    public LabKeyDataSourceProperties getLabKeyProps()
    {
        return _labkeyProps;
    }
}
