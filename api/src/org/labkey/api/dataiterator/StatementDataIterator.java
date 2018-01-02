/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.dataiterator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.SwapQueue;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.GUID;

import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;


public class StatementDataIterator extends AbstractDataIterator
{
    Parameter.ParameterMap[] _stmts;
    Triple[][] _bindings = null;

    Parameter.ParameterMap _currentStmt;
    Triple[] _currentBinding;

    // coordinate asynchronous statement execution
    boolean _useAsynchronousExecute = false;
    SwapQueue<Parameter.ParameterMap> _queue = new SwapQueue<>();
    final Thread _foregroundThread;
    Thread _asyncThread = null;
    AtomicReference<Exception> _backgroundException = new AtomicReference<>();

    DataIterator _data;


    // NOTE all columns are pass through to the source iterator, except for key columns
    ArrayList<ColumnInfo> _keyColumnInfo;
    ArrayList<Object> _keyValues;
    Integer _rowIdIndex = null;
    Integer _objectIdIndex = null;
    int _batchSize = -1;
    int _currentBatchSize = 0;
    int _currentTxSize = 0;
    int _txSize = -1;
    Logger _log = null;
    CPUTimer _elapsed = new CPUTimer("StatementDataIterator@" + System.identityHashCode(this) + ".elapsed");
    CPUTimer _execute = new CPUTimer("StatementDataIterator@" + System.identityHashCode(this) + ".execute()");

    protected StatementDataIterator(DataIterator data, @Nullable Parameter.ParameterMap map, DataIteratorContext context)
    {
        super(context);

        _data = data;

        _stmts = new Parameter.ParameterMap[] {map};
        _foregroundThread = Thread.currentThread();

        _keyColumnInfo = new ArrayList<>(Collections.nCopies(data.getColumnCount()+1,(ColumnInfo)null));
        _keyValues = new ArrayList<>(Collections.nCopies(data.getColumnCount()+1,null));
    }

    protected StatementDataIterator(DataIterator data, DataIteratorContext context, Parameter.ParameterMap... maps)
    {
        super(context);

        _data = data;

        _stmts = maps;
        _foregroundThread = Thread.currentThread();

        _keyColumnInfo = new ArrayList<>(Collections.nCopies(data.getColumnCount()+1,(ColumnInfo)null));
        _keyValues = new ArrayList<>(Collections.nCopies(data.getColumnCount()+1,null));
    }


    void setUseAsynchronousExecute(boolean b)
    {
        _useAsynchronousExecute = b && null == _rowIdIndex && null == _objectIdIndex;
    }


    // configure columns returned by statement, e.g. rowid
    // index > 0 to override an existing column
    // index == -1 to append
    void setRowIdColumn(int index, ColumnInfo col)
    {
        if (-1 == index)
        {
            _keyColumnInfo.add(null);
            _keyValues.add(null);
            index = _keyColumnInfo.size()-1;
        }
        _keyColumnInfo.set(index,col);
        _rowIdIndex = index;
        _useAsynchronousExecute = false;
    }


    void setObjectIdColumn(int index, ColumnInfo col)
    {
        if (-1 == index)
        {
            _keyColumnInfo.add(null);
            _keyValues.add(null);
            index = _keyColumnInfo.size()-1;
        }
        _keyColumnInfo.set(index,col);
        _objectIdIndex = index;
        _useAsynchronousExecute = false;
    }


    void init()
    {
        _bindings = new Triple[_stmts.length][];

        for (int set=0 ; set<_stmts.length ; set++)
        {
            Parameter.ParameterMap stmt = _stmts[set];
            // map from source to target
            ArrayList<Triple> bindings = new ArrayList<>(stmt.size());
            // by name
            for (int i=1 ; i<=_data.getColumnCount() ; i++)
            {
                ColumnInfo col = _data.getColumnInfo(i);
                Parameter to = null;
                if (null != col.getPropertyURI())
                    to = stmt.getParameter(col.getPropertyURI());
                if (to == null)
                    to = stmt.getParameter(col.getName());
                if (null != to)
                {
                    FieldKey mvName = col.getMvColumnName();
                    Parameter mv = null==mvName ? null : stmt.getParameter(mvName.getName());
                    bindings.add(new Triple(_data.getSupplier(i), to, mv));
                }
            }
            _bindings[set] = bindings.toArray(new Triple[bindings.size()]);
        }
        _currentStmt = _stmts[0];
        _currentBinding = _bindings[0];

        if (_batchSize < 1 && null == _rowIdIndex && null == _objectIdIndex)
            _batchSize = Math.max(10, 10000/Math.max(2,_bindings.length));

        Integer contextTxSize = null;
        if (_context.getConfigParameters() != null)
        {
            contextTxSize = (Integer) _context.getConfigParameters().get(QueryUpdateService.ConfigParameters.TransactionSize);
            _log = (Logger)_context.getConfigParameters().get(QueryUpdateService.ConfigParameters.Logger); // may still be null
        }
        if (contextTxSize != null && contextTxSize > 1)
            _txSize = contextTxSize;

        if (_stmts.length > 1)
        {
            _asyncThread = new Thread(new _Runnable(_stmts[1]), "StatementDataIterator executor");
            _asyncThread.start();
        }
    }


    private static class Triple
    {
        Triple(Supplier from, Parameter to, Parameter mv)
        {
            this.from = from;
            this.to = to;
            this.mv = mv;
        }
        Supplier from;
        Parameter to;
        Parameter mv;
    }

    @Override
    public int getColumnCount()
    {
        return _keyColumnInfo.size()-1;
    }


    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        if (null != _keyColumnInfo.get(i))
            return _keyColumnInfo.get(i);
        return _data.getColumnInfo(i);
    }

    private boolean _firstNext = true;

    protected void onFirst()
    {
        init();
    }



    @Override
    public boolean next() throws BatchValidationException
    {
        boolean ret = false;
        try
        {
            ret = _next();
        }
        finally
        {
            if (ret == false)
            {
                _queue.close();
                if (null != _asyncThread)
                {
                    try {_asyncThread.join();} catch (InterruptedException x) {}
                    _asyncThread = null;
                }
                checkBackgroundException();
            }
        }
        return ret;
    }


    private boolean _next() throws BatchValidationException
    {
        if (_firstNext)
        {
            _firstNext = false;
            onFirst();
            assert _elapsed.start();
        }

        try
        {
            boolean hasNextRow = _data.next();

            if (hasNextRow)
            {
                _currentStmt.clearParameters();
                for (Triple binding : _currentBinding)
                {
                    Object value = binding.from.get();
                    if (null == value)
                        continue;
                    if (value instanceof MvFieldWrapper)
                    {
                        if (null != binding.mv)
                            binding.mv.setValue(((MvFieldWrapper) value).getMvIndicator());
                        binding.to.setValue(((MvFieldWrapper) value).getValue());
                    }
                    else
                    {
                        binding.to.setValue(value);
                    }
                }

                checkShouldCancel();

                if (_batchSize > 1)
                    _currentStmt.addBatch();
                _currentBatchSize++;
                _currentTxSize++;
            }
            else if (_errors.getExtraContext() != null)
                _errors.getExtraContext().put("hasNextRow", false);

            if (_currentBatchSize == _batchSize || !hasNextRow && _currentBatchSize > 0)
            {
                processBatch();
            }

            // This allows specifying more granular commits than the transaction wrapping the entire operation
            if (_currentTxSize == _txSize && _currentStmt.getScope().isTransactionActive() && hasNextRow)
            {
                _currentTxSize = 0;
                if (_currentBatchSize > 0) // flush the statement batch buffer
                    processBatch();
                if (_log != null)
                    _log.info("Committing " + Integer.toString(_txSize) + " rows");
                _currentStmt.getScope().getCurrentTransaction().commitAndKeepConnection();
            }

            if (null != _rowIdIndex)
                _keyValues.set(_rowIdIndex, _currentStmt.getRowId());
            if (null != _objectIdIndex)
                _keyValues.set(_objectIdIndex, _currentStmt.getObjectId());

            checkBackgroundException();
            return hasNextRow;
        }
        catch (SQLException x)
        {
            if (x instanceof BatchUpdateException && null != x.getNextException())
                x = x.getNextException();
            if (StringUtils.startsWith(x.getSQLState(), "22") || RuntimeSQLException.isConstraintException(x))
            {
                getRowError().addGlobalError(x);
//              see bug21719
//              Sometimes (always?) Postgres leaves the connection unusable after a constraint exception, so we can't continue even if we want to
                throw _errors;
            }
            // table does not exist
            else if (SqlDialect.isObjectNotFoundException(x))
            {
                OptimisticConflictException opt = OptimisticConflictException.create(Table.ERROR_TABLEDELETED);
                getRowError().addGlobalError(opt);
                throw _errors;
            }
            throw new RuntimeSQLException(x);
        }
    }

    private void processBatch() throws SQLException, BatchValidationException
    {
        assert _execute.start();

        if (_batchSize == 1)
        {
            /* use .execute() for handling keys */
            _currentStmt.execute();
        }
        else if (_useAsynchronousExecute && _stmts.length > 1 && _txSize==-1)
        {
            while (true)
            {
                try
                {
                    _currentStmt = _queue.swapFullForEmpty(_currentStmt);
                    break;
                }
                catch (InterruptedException x)
                {
                    checkBackgroundException();
                }
            }
        }
        else
        {
            _currentStmt.executeBatch();
        }

        _currentBatchSize = 0;
        _currentBinding = (_currentStmt == _stmts[0] ? _bindings[0] : _bindings[1]);

        assert _execute.stop();
    }


    private void checkBackgroundException() throws BatchValidationException
    {
        Exception bkg = _backgroundException.getAndSet(null);
        if (null != bkg)
        {
            if (bkg instanceof RuntimeException)
                throw (RuntimeException)bkg;
            if (bkg instanceof BatchValidationException)
                throw (BatchValidationException)bkg;
            throw new RuntimeException(bkg);
        }
    }


    @Override
    public Object get(int i)
    {
        if (null != _keyColumnInfo.get(i))
            return _keyValues.get(i);
        return _data.get(i);
    }


    @Override
    public void close() throws IOException
    {
        _data.close();
        _queue.close();
        SQLException sqlx = null;
        if (_asyncThread != null)
        {
            try {_asyncThread.join();}catch(InterruptedException x){}
            _asyncThread = null;
        }
        for (Parameter.ParameterMap stmt : _stmts)
        {
            if (stmt != null)
            {
                try
                {
                    stmt.close();
                }
                catch (SQLException x)
                {
                    sqlx = x;
                }
            }
        }
        _stmts = new Parameter.ParameterMap[0];
        if (null != sqlx)
            throw new RuntimeSQLException(sqlx);
    }



    class _Runnable implements Runnable
    {
        Parameter.ParameterMap _firstEmpty;

        _Runnable(@NotNull Parameter.ParameterMap empty)
        {
            _firstEmpty = empty;
        }

        @Override
        public void run()
        {
            try
            {
                executeStatementsInBackground();
            }
            catch (Exception x)
            {
                _backgroundException.set(x);
                _foregroundThread.interrupt();
            }
        }

        void executeStatementsInBackground() throws BatchValidationException, InterruptedException
        {
            Parameter.ParameterMap m = _firstEmpty;

            while (null != (m = _queue.swapEmptyForFull(m)))
            {
                try
                {
                    m.executeBatch();
                }
                catch (SQLException x)
                {
                    if (x instanceof BatchUpdateException && null != x.getNextException())
                        x = x.getNextException();
                    // NOTE some constraint exceptions are recoverable (especially on sql server), but treat all sql exceptions as fatal
                    //noinspection ThrowableResultOfMethodCallIgnored
                    getRowError().addGlobalError(x);
                    throw _context.getErrors();
                }
            }
            assert _queue.isClosed();
        }
    }


    @Override
    public void debugLogInfo(StringBuilder sb)
    {
        sb.append(this.getClass().getName() + "\n");
        if (null != _data)
            _data.debugLogInfo(sb);
    }


    public static class TestCase extends Assert
    {
        DataIterator getSource(DataIteratorContext context, final int rowlimit)
        {
            return new DataIterator()
            {
                int row=0;

                @Override
                public String getDebugName()
                {
                    return null;
                }

                @Override
                public int getColumnCount()
                {
                    return 1;
                }

                @Override
                public ColumnInfo getColumnInfo(int i)
                {
                    return i==0 ?
                        new ColumnInfo("rownumber",JdbcType.INTEGER) :
                        new ColumnInfo("I",JdbcType.INTEGER);
                }

                @Override
                public boolean isConstant(int i)
                {
                    return false;
                }

                @Override
                public Object getConstantValue(int i)
                {
                    return null;
                }

                @Override
                public boolean next() throws BatchValidationException
                {
                    return row++ < rowlimit;
                }

                @Override
                public Object get(int i)
                {
                    return 0==i ? row : i;
                }

                @Override
                public void close() throws IOException
                {

                }
            };
        }


        @Test
        public void testAsyncStatements() throws Exception
        {
            _testAsyncStatements(0,2);
            _testAsyncStatements(1,2);
            _testAsyncStatements(2,2);
            _testAsyncStatements(3,2);
            _testAsyncStatements(4,2);
            _testAsyncStatements(5,2);

            _testAsyncStatements(1000,5);
            _testAsyncStatements(499,7);
        }


        void _testAsyncStatements(int rowCount, final int batchSize) throws Exception
        {
            DbSchema tempdb = DbSchema.getTemp();
            String tableName = tempdb.getName() + ".junit" + GUID.makeHash();
            DbScope scope = tempdb.getScope();
            Connection conn = null;

            try
            {
                conn = scope.getConnection();
                new SqlExecutor(tempdb).execute("CREATE TABLE " + tableName + " (X INT, Y INT)");
                new SqlExecutor(tempdb).execute("INSERT INTO " + tableName + " (X,Y) VALUES (0,0)");


                SQLFragment updateX = new SQLFragment("UPDATE " + tableName + " SET X=X+?");
                updateX.add(new Parameter("I", JdbcType.INTEGER));
                Parameter.ParameterMap pm1 = new Parameter.ParameterMap(tempdb.getScope(), conn, updateX, null);

                SQLFragment updateY = new SQLFragment("UPDATE " + tableName + " SET Y=Y+?");
                updateY.add(new Parameter("I", JdbcType.INTEGER));
                Parameter.ParameterMap pm2 = new Parameter.ParameterMap(tempdb.getScope(), conn, updateY, null);

                DataIteratorContext context = new DataIteratorContext();
                DataIterator source = getSource(context, rowCount);
                StatementDataIterator sdi = new StatementDataIterator(source, context, pm1, pm2)
                {
                    @Override
                    void init()
                    {
                        super.init();
                        _batchSize = batchSize;
                    }
                };
                sdi._useAsynchronousExecute = true;

                new Pump(sdi,context).run();

                Map<String,Object> result = new SqlSelector(tempdb,"SELECT X, Y FROM " + tableName).getMap();
                assertNotNull(result.get("X"));
                assertNotNull(result.get("Y"));
                int x = ((Number)result.get("X")).intValue();
                int y = ((Number)result.get("Y")).intValue();
                assertEquals(rowCount, x+y);
                if (0 == rowCount % (2*batchSize))
                    assertEquals(x,y);
                assertTrue(Math.abs(x-y) <= batchSize);
            }
            finally
            {
                if (null != conn)
                    scope.releaseConnection(conn);
                new SqlExecutor(tempdb).execute("DROP TABLE " + tableName);
            }
        }
    }
}
