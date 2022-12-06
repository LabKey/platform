package org.labkey.api.data;

import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.arrays.IntegerArray;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.StatementWrapper;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * This is a wrapper over a Prepared statement that handles named (instead of positional) parameters.
 * Named parameters can be updated using clearParameters(), put(String,Object), putAll(Map) or select(Map).
 * Any unnamed parameters provided by SqlFragment are set once the constructor.
 */
public class ParameterMapStatement implements AutoCloseable
{
    SQLFragment _sqlf;
    PreparedStatement _stmt;
    boolean _selectRowId = false;
    Integer _selectObjectIdIndex = null;
    Integer _rowId;
    Integer _objectId;
    Integer _objectUriIndex = null;
    String _objectURI;
    protected CaseInsensitiveHashMap<Integer> _map;
    protected Parameter[] _parameters;
    final @NotNull DbScope _scope;
    Connection _conn;       // only used for copy()
    SqlDialect _dialect;
    boolean _closed = false;

    int batchCount = 0;

    private ExceptionFramework _exceptionFramework = ExceptionFramework.Spring;

    protected ParameterMapStatement()
    {
        //for testing subclasses (see NoopParameterMap)
        _scope = CoreSchema.getInstance().getScope();
    }

    public ParameterMapStatement(@NotNull DbScope scope, PreparedStatement stmt, Collection<Parameter> parameters)
    {
        this(scope, stmt, parameters, null);
    }

    public ParameterMapStatement(@NotNull DbScope scope, PreparedStatement stmt, Collection<Parameter> parameters, @Nullable Map<String, String> remap)
    {
        _scope = scope;
        init(stmt, parameters, remap);
    }

    public ParameterMapStatement copy()
    {
        if (null == _sqlf || null == _conn)
            throw new IllegalStateException("Copy can only be used on ParameterMap constructed with SQL");
        return new ParameterMapStatement(this);
    }

    protected ParameterMapStatement(ParameterMapStatement from)
    {
        _scope = from._scope;
        _sqlf = from._sqlf;
        _debugSql = from._debugSql;
        _conn = from._conn;
        _selectRowId = from._selectRowId;
        _selectObjectIdIndex = from._selectObjectIdIndex;
        _rowId = from._rowId;
        _objectId = from._objectId;
        _dialect = from._dialect;
        _map = from._map;
        _parameters = new Parameter[from._parameters.length];
        _exceptionFramework = from._exceptionFramework;
        try
        {
            _stmt = createStatement(_conn, _sqlf);
            for (int i = 0; i < from._parameters.length; i++)
                _parameters[i] = from._parameters[i].copy(_stmt);
        }
        catch (SQLException x)
        {
            throw _exceptionFramework.translate(_scope, "Copy statement", x);
        }
    }


    /** throws RuntimeSQLException if getConnection) fails */
    public static ParameterMapStatement create(@NotNull DbScope scope, SQLFragment sql, Map<String, String> remap)
    {
        Connection conn;
        try
        {
             conn = scope.getConnection();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        return new ParameterMapStatement(scope, conn, sql, remap);
    }


    @Deprecated // use create(scope) or ParameterMap(scope,conn) to avoid SQLException
    public ParameterMapStatement(@NotNull DbScope scope, SQLFragment sql, Map<String, String> remap) throws SQLException
    {
        this(scope, scope.getConnection(), sql, remap);
    }


    /**
     *  sql bound to constants or Parameters, compute the index array for each named Parameter
     *
     *  Will throw RuntimeSQLException if createStatement fails.
     */
    public ParameterMapStatement(@NotNull DbScope scope, Connection conn, SQLFragment sql, Map<String, String> remap)
    {
        // TODO SQLFragment doesn't seem to actually handle CTE with named parameters, but we can "flatten" it
        _scope = scope;
        _sqlf = sql;
        _conn = conn;

        PreparedStatement stmt;
        try
        {
            stmt = createStatement(_conn, _sqlf);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        IdentityHashMap<Parameter, IntegerArray> paramMap = new IdentityHashMap<>();
        List<Object> paramList = _sqlf.getParams();
        List<Parameter> parameters = new ArrayList<>(paramList.size());

        for (int i = 0; i < paramList.size(); i++)
        {
            Object o = paramList.get(i);
            if (o instanceof Parameter)
            {
                Parameter p = (Parameter)o;
                if (!paramMap.containsKey(p))
                    paramMap.put(p, new IntegerArray());
                paramMap.get(p).add(i+1);
            }
        }

        for (Map.Entry<Parameter, IntegerArray> e : paramMap.entrySet())
        {
            e.getKey()._indexes = e.getValue().toArray(null);
            parameters.add(e.getKey());
        }

        init(stmt, parameters, remap);
    }


    // set constant parameters
    static PreparedStatement createStatement(Connection conn, SQLFragment sqlf) throws SQLException
    {
        PreparedStatement stmt;
        if (sqlf.getSQL().startsWith("{call"))
            stmt = conn.prepareCall(sqlf.getSQL());
        else
            stmt = conn.prepareStatement(sqlf.getSQL());
        var params = sqlf.getParams();
        for (int i = 0; i < params.size(); i++)
        {
            var value = params.get(i);
            // skip names parameters
            if (!(value instanceof Parameter))
                new Parameter(stmt, i + 1).setValue(value);
        }
        return stmt;
    }


    public void setExceptionFramework(ExceptionFramework f)
    {
        _exceptionFramework = f;
    }


    private void init(PreparedStatement stmt, Collection<Parameter> parameters, @Nullable Map<String, String> remap)
    {
        _dialect = _scope.getSqlDialect();
        _map = new CaseInsensitiveHashMap<>(parameters.size() * 2);
        _parameters = parameters.toArray(new Parameter[parameters.size()]);
        _stmt = stmt;

        for (int i=0 ; i<_parameters.length ; i++)
        {
            Parameter p = _parameters[i];
            if (null == p._name)
                throw new IllegalStateException();
            p._stmt = stmt;
            String name = p._name;
            if (null != remap && remap.containsKey(name))
                name = remap.get(name);
            if (_map.containsKey(name))
                throw new IllegalArgumentException("duplicate parameter name: " + name);
            _map.put(name, i);
            if (null != p._uri)
            {
                String uri = p._uri;
                if (null != remap && remap.containsKey(uri))
                    uri = remap.get(uri);
                if (_map.containsKey(uri))
                    throw new IllegalArgumentException("duplicate property uri: " + uri);
                _map.put(uri, i);
            }
        }
    }


    public void setSelectRowId(boolean selectRowId)
    {
        _selectRowId = selectRowId;
    }

    public void setObjectIdIndex(Integer i)
    {
        _selectObjectIdIndex = i;
    }

    public void setObjectUriIndex(Integer objectUriIndex)
    {
        _objectUriIndex = objectUriIndex;
    }

    public boolean hasReselectRowId()
    {
        return _selectRowId;
    }

    public boolean hasReselectObjectId()
    {
        return null != _objectId;
    }

    public int size()
    {
        return _map.size();
    }


    public boolean containsKey(String name)
    {
        return _map.containsKey(name);
    }


    public Parameter getParameter(String name)
    {
        Integer i = _map.get(name);
        return null==i ? null : _parameters[i];
    }


    public void executeBatch()
    {
        try
        {
            prepareParametersBeforeExecute();

            _objectId = null;
            _rowId = null;
            _stmt.executeBatch();
            batchCount = 0;
        }
        catch (SQLException x)
        {
            throw _exceptionFramework.translate(_scope, "Attempting to prepare a statement", x);
        }
    }


    public int execute()
    {
        if (batchCount > 0)
        {
            // this might be called in background thread so add an ERROR to the log
            var ex  = new IllegalStateException("Don't call execute() after calling addBatch()");
            LogHelper.getLogger(ParameterMapStatement.class, "ParameterMapStatement").error("Call executeBatch() instead", ex);
            throw ex;
        }

        prepareParametersBeforeExecute();

        ResultSet rs = null;
        _rowId = null;
        _objectId = null;

        try
        {
            if (_selectRowId || _selectObjectIdIndex != null || _objectUriIndex != null)
                rs = _dialect.executeWithResults(_stmt);
            else
                _stmt.execute();
            int rowcount = _stmt.getUpdateCount();

            Integer firstInt = null, secondInt = null;

            if (null != rs)
            {
                rs.next();
                if (_selectRowId || _selectObjectIdIndex != null)
                {
                    firstInt = rs.getInt(1);
                    if (rs.wasNull())
                        firstInt = null;
                    if (rs.getMetaData().getColumnCount() >= 2 && _selectObjectIdIndex != null)
                    {
                        secondInt = rs.getInt(2);
                        if (rs.wasNull())
                            secondInt = null;
                    }
                }

                if (_objectUriIndex != null)
                    _objectURI = rs.getString(_objectUriIndex);
            }

            if (null == _selectObjectIdIndex)
            {}
            else if (_selectObjectIdIndex == 2)
            {
                _objectId = secondInt;
            }
            else if (_selectObjectIdIndex == 1)
            {
                _objectId = firstInt;
                firstInt = secondInt;
            }

            if (_selectRowId)
                _rowId = firstInt;

            return rowcount;
        }
        catch (SQLException x)
        {
            throw _exceptionFramework.translate(_scope, "Attempting to prepare a statement", x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    public Integer getRowId()
    {
        return _rowId;
    }

    public Integer getObjectId()
    {
        return _objectId;
    }

    public String getObjectURI()
    {
        return _objectURI;
    }

    private void prepareParametersBeforeExecute()
    {
        try
        {
            for (Parameter p : _parameters)
            {
                if (!p._isSet)
                {
                    assert !p._constant;
                    if (!p._isNull)
                        p.setValue(null);
                }
            }
        }
        catch (RuntimeSQLException x)
        {
            throw _exceptionFramework.translate(_scope, "Attempting to prepare a statement", x);
        }
    }


    public void addBatch()
    {
        try
        {
            prepareParametersBeforeExecute();
            _stmt.addBatch();
            batchCount++;
        }
        catch (SQLException x)
        {
            throw _exceptionFramework.translate(_scope, "Attempting to prepare a statement", x);
        }
    }


    @Override
    public void close()
    {
        if (!_closed)
        {
            try
            {
                if (null != _stmt)
                {
                    _stmt.close();
                }
            }
            catch (SQLException e)
            {
                Parameter.LOG.warn("Failed to close backing statement during close operation", e);
                if (_stmt instanceof StatementWrapper)
                {
                    Throwable t = ((StatementWrapper) _stmt).getClosingStackTrace();
                    if (t != null)
                    {
                        Parameter.LOG.warn("Stack trace of the operation that previously closed the statement:", t);
                    }
                }
            }
            finally
            {
                afterClose();
                _closed = true;
            }
        }
        // Stop referring to the old values
        _parameters = new Parameter[0];
    }

    public boolean isClosed()
    {
        return _closed;
    }

//        public PreparedStatement getStatement()
//        {
//            return _stmt;
//        }

    public void clearParameters()
    {
        for (Parameter p : _parameters)
            if (!p._constant)
                p._isSet = false;
    }


    public void put(String name, Object value) throws ValidationException
    {
        try
        {
            Parameter p = getParameter(name);
            if (null == p)
                throw new IllegalArgumentException("parameter not found: " + name + ", available parameters are: " + _map.keySet());
            if (p._constant)
                throw new IllegalStateException("Can't set constant parameter: " + name);
            p.setValue(value);
        }
        catch (RuntimeSQLException e)
        {
            SQLException sqlx = e.getSQLException();
            SQLExceptionTranslator translator = new SQLErrorCodeSQLExceptionTranslator(_scope.getDataSource());
            DataAccessException translated = translator.translate("Message", null, sqlx);
            if (translated instanceof DataIntegrityViolationException)
                throw new ValidationException(sqlx.getMessage() == null ? translated.getMessage() : sqlx.getMessage());
            throw _exceptionFramework.translate(_scope, "Binding parameter", sqlx);
        }
    }


    public void putAll(Map<String,Object> values)
    {
        try
        {
            for (Map.Entry<String,Object> e : values.entrySet())
            {
                Parameter p = getParameter(e.getKey());
                if (null != p)
                    p.setValue(e.getValue());
            }
        }
        catch (RuntimeSQLException sqlx)
        {
            throw _exceptionFramework.translate(_scope, "Attempting to prepare a statement", sqlx);
        }
    }


    Runnable _onClose = null;
    boolean _runAfterTransaction = true;

    public void onClose(Runnable r)
    {
        if (null != _onClose)
            throw new IllegalStateException("only one onClose() callback supported");
        _onClose = r;
    }


    protected void afterClose()
    {
        if (null != _onClose)
        {
            if (_runAfterTransaction && _scope.isTransactionActive())
                _scope.addCommitTask(_onClose, DbScope.CommitTaskOption.POSTCOMMIT);
            else
                _onClose.run();
        }
        _onClose = null;
    }


    @Override
    protected void finalize() throws Throwable
    {
        super.finalize();
        assert null == _onClose;
        if (null != _onClose)
            _onClose.run();
    }


    String _debugSql;

    public String getDebugSql()
    {
        if (null != _debugSql)
            return _debugSql;
        if (_stmt instanceof StatementWrapper)
            return ((StatementWrapper)_stmt).getDebugSql();
        return null!=_sqlf ? _sqlf.toString() : null;
    }

    public void setDebugSql(String sql)
    {
        _debugSql = sql;
    }

    public DbScope getScope()
    {
        return _scope;
    }

    public Selector selector()
    {
        try
        {
            prepareParametersBeforeExecute();
            ResultSet rs = _stmt.executeQuery();
            return new ResultSetSelector(_scope, rs);
        }
        catch (SQLException e)
        {
            if (null == _debugSql && null != _sqlf)
                _debugSql = _sqlf.toDebugString();
            if (null != _debugSql)
            {
                Table.logException(new SQLFragment(_debugSql), _conn, e, Level.ERROR);
                ExceptionUtil.decorateException(e, ExceptionUtil.ExceptionInfo.DialectSQL, _debugSql, false);
            }

            throw ExceptionFramework.Spring.translate(_scope, "ParameterMap", e);
        }
    }

    public Selector selector(Map<String,Object> values)
    {
        putAll(values);
        return selector();
    }
}
