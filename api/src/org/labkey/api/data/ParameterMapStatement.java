package org.labkey.api.data;

import org.apache.log4j.Level;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.arrays.IntegerArray;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.StatementWrapper;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ResultSetUtil;
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
    protected CaseInsensitiveHashMap<Integer> _map;
    protected Parameter[] _parameters;
    DbScope _scope;
    Connection _conn;       // only used for copy()
    SqlDialect _dialect;
    boolean _closed = false;

    protected ParameterMapStatement()
    {
        //for testing subclasses (see NoopParameterMap)
    }

    public ParameterMapStatement(DbScope scope, PreparedStatement stmt, Collection<Parameter> parameters)
    {
        this(scope, stmt, parameters, null);
    }


    public ParameterMapStatement(DbScope scope, PreparedStatement stmt, Collection<Parameter> parameters, @Nullable Map<String, String> remap)
    {
        init(scope, stmt, parameters, remap);
    }


    public ParameterMapStatement copy() throws SQLException
    {
        if (null == _sqlf || null == _conn)
            throw new IllegalStateException("Copy can only be used on ParameterMap constructed with SQL");
        return new ParameterMapStatement(this);
    }


    protected ParameterMapStatement(ParameterMapStatement from) throws SQLException
    {
        _sqlf = from._sqlf;
        _debugSql = from._debugSql;
        _scope = from._scope;
        _conn = from._conn;
        _stmt = createStatement(_conn, _sqlf);
        _selectRowId = from._selectRowId;
        _selectObjectIdIndex = from._selectObjectIdIndex;
        _rowId = from._rowId;
        _objectId = from._objectId;
        _dialect = from._dialect;
        _map = from._map;
        _parameters = new Parameter[from._parameters.length];
        for (int i=0 ; i<from._parameters.length ; i++)
            _parameters[i] = from._parameters[i].copy(_stmt);
    }


    public ParameterMapStatement(DbScope scope, SQLFragment sql, Map<String, String> remap) throws SQLException
    {
        this(scope, scope.getConnection(), sql, remap);
    }


    /**
     *  sql bound to constants or Parameters, compute the index array for each named Parameter
     */
    public ParameterMapStatement(DbScope scope, Connection conn, SQLFragment sql, Map<String, String> remap) throws SQLException
    {
        // TODO SQLFragment doesn't seem to actually handle CTE with named parameters, but we can "flatten" it
        _sqlf = sql; // new SQLFragment(sql.getSQL(), sql.getParams());
        _conn = conn;
        PreparedStatement stmt = createStatement(_conn, _sqlf);

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

        init(scope, stmt, parameters, remap);
    }


    // set constant parameters
    static PreparedStatement createStatement(Connection conn, SQLFragment sqlf) throws SQLException
    {
        PreparedStatement stmt;
        if (sqlf.getSQL().startsWith("{call"))
            stmt = conn.prepareCall(sqlf.getSQL());
        else
            stmt= conn.prepareStatement(sqlf.getSQL());
        var params = sqlf.getParams();
        for (int i=0 ; i<params.size() ; i++)
        {
            var value = params.get(i);
            // skip names parameters
            if (!(value instanceof Parameter))
                new Parameter(stmt, i+1).setValue(value);
        }
        return stmt;
    }


    private void init(DbScope scope, PreparedStatement stmt, Collection<Parameter> parameters, @Nullable Map<String, String> remap)
    {
        _scope = scope;
        _dialect = scope.getSqlDialect();
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


    public void executeBatch() throws SQLException
    {
        prepareParametersBeforeExecute();

        _objectId = null;
        _rowId = null;
        _stmt.executeBatch();
    }


    public boolean execute() throws SQLException
    {
        prepareParametersBeforeExecute();

        ResultSet rs = null;
        _rowId = null;
        _objectId = null;

        try
        {
            if (_selectRowId || _selectObjectIdIndex != null)
                rs = _dialect.executeWithResults(_stmt);
            else
                _stmt.execute();

            Integer firstInt = null, secondInt = null;

            if (null != rs)
            {
                rs.next();
                firstInt = rs.getInt(1);
                if (rs.wasNull())
                    firstInt = null;
                if (rs.getMetaData().getColumnCount() >= 2)
                {
                    secondInt = rs.getInt(2);
                    if (rs.wasNull())
                        secondInt = null;
                }
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
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

        return true;
    }


    public Integer getRowId()
    {
        return _rowId;
    }

    public Integer getObjectId()
    {
        return _objectId;
    }


    private void prepareParametersBeforeExecute() throws SQLException
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


    public void addBatch() throws SQLException
    {
        prepareParametersBeforeExecute();
        _stmt.addBatch();
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
        catch (SQLException sqlx)
        {
            SQLExceptionTranslator translator = new SQLErrorCodeSQLExceptionTranslator(ExperimentService.get().getSchema().getScope().getDataSource());
            DataAccessException translated = translator.translate("Message", null, sqlx);
            if (translated instanceof DataIntegrityViolationException)
            {
                throw new ValidationException(sqlx.getMessage() == null ? translated.getMessage() : sqlx.getMessage());
            }
            throw new RuntimeSQLException(sqlx);
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
        catch (SQLException sqlx)
        {
            throw new RuntimeSQLException(sqlx);
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
