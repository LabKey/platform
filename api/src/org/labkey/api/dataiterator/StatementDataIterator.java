/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.SwapQueue;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ExceptionFramework;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.ColumnMetaDataReader;
import org.labkey.api.data.dialect.JdbcHelper;
import org.labkey.api.data.dialect.PkMetaDataReader;
import org.labkey.api.data.dialect.SimpleSqlDialect;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.GUID;
import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;


public class StatementDataIterator extends AbstractDataIterator
{
    final SqlDialect _dialect;
    protected ParameterMapStatement[] _stmts;
    Triple[][] _bindings = null;

    ParameterMapStatement _currentStmt;
    Triple[] _currentBinding;

    // coordinate asynchronous statement execution
    private boolean _useAsynchronousExecute = false;
    SwapQueue<ParameterMapStatement> _queue = new SwapQueue<>();
    final Thread _foregroundThread;
    volatile Thread _asyncThread = null;
    AtomicReference<Exception> _backgroundException = new AtomicReference<>();

    protected DataIterator _data;
    int _currentRowNumber = -1;
    int _backgroundRowNumber = -1;
    protected EmbargoDataIterator _embargoDataIterator = null;

    // NOTE all columns are passed through to the source iterator, except for key columns
    ArrayList<ColumnInfo> _keyColumnInfo;
    ArrayList<Object> _keyValues;
    Integer _rowIdIndex = null;
    Integer _objectIdIndex = null;
    Integer _objectUriIndex = null;
    protected int _batchSize = -1;
    int _currentBatchSize = 0;
    int _currentTxSize = 0;
    int _txSize = -1;
    Logger _log = null;
    CPUTimer _elapsed = new CPUTimer("StatementDataIterator@" + System.identityHashCode(this) + ".elapsed");
    CPUTimer _execute = new CPUTimer("StatementDataIterator@" + System.identityHashCode(this) + ".execute()");

    public StatementDataIterator(SqlDialect dialect, DataIterator data, DataIteratorContext context, ParameterMapStatement... maps)
    {
        super(context);

        _dialect = dialect;
        _data = data;

        _stmts = maps;
        _foregroundThread = Thread.currentThread();

        _keyColumnInfo = new ArrayList<>(Collections.nCopies(data.getColumnCount()+1, null));
        _keyValues = new ArrayList<>(Collections.nCopies(data.getColumnCount()+1,null));
    }


    public void setUseAsynchronousExecute(boolean useAsynchronousExecute)
    {
        if (_dialect.allowAsynchronousExecute())
            _useAsynchronousExecute = useAsynchronousExecute && null == _rowIdIndex && null == _objectIdIndex && null == _objectUriIndex;
    }


    /*
     * EmbargoDataIterator and a StatementDataIterator can be paired such that the EmbargoDataIterator
     * does not 'release' rows until the statement that operates on that row (e.g. inserts it) has been
     * executed.
     *
     * This is different than the normal flow of control where 'later' data iterators only call 'earlier' data iterators.
     * In this case the StatementDataIterator is passing some internal state information forward to to the EmbargoDataIterator
     * This is actually fine, since it's the DataIteratorBuilder's job to set up a correct pipeline.
     */
    public void setEmbargoDataIterator(EmbargoDataIterator cache)
    {
        _embargoDataIterator = cache;
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

    public void setObjectUriColumn(int index, ColumnInfo col)
    {
        if (-1 == index)
        {
            _keyColumnInfo.add(null);
            _keyValues.add(null);
            index = _keyColumnInfo.size()-1;
        }
        _keyColumnInfo.set(index,col);
        _objectUriIndex = index;
        _useAsynchronousExecute = false;
    }


    void init()
    {
        _bindings = new Triple[_stmts.length][];

        for (int set=0 ; set<_stmts.length ; set++)
        {
            ParameterMapStatement stmt = _stmts[set];
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
                if (to == null)
                    to = stmt.getParameter(col.getAlias());
                if (null != to)
                {
                    FieldKey mvName = col.getMvColumnName();
                    bindings.add(new Triple(_data.getSupplier(i), to,
                                            (null != mvName ? getMvParameter(stmt, mvName) : null)));
                }
            }
            _bindings[set] = bindings.toArray(new Triple[bindings.size()]);
        }
        _currentStmt = _stmts[0];
        _currentBinding = _bindings[0];

        if (_batchSize < 1 && null == _rowIdIndex && null == _objectIdIndex && null == _objectUriIndex)
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

    public void setBatchSize(int batchSize)
    {
        _batchSize = batchSize;
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

    @Nullable
    protected Parameter getMvParameter(@NotNull ParameterMapStatement stmt, @NotNull FieldKey mvFieldKey)
    {
        return stmt.getParameter(mvFieldKey.getName());
    }

    private boolean _firstNext = true;

    protected void onFirst()
    {
        init();
        for (var stmt : _stmts)
            stmt.setExceptionFramework(ExceptionFramework.JDBC);
    }

    protected void afterExecute(ParameterMapStatement stmt, int batchSize, int rowNumber)
    {
        if (null != _embargoDataIterator)
            _embargoDataIterator.setReleasedRowNumber(rowNumber);
    }

    @Override
    public boolean supportsGetExistingRecord()
    {
        return _data.supportsGetExistingRecord();
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        boolean ret = false;
        try
        {
            if (_firstNext)
            {
                _firstNext = false;
                onFirst();
                assert _elapsed.start();
            }

            log("<next>"); // Note: First opportunity for this logging, since onFirst() initializes the logger
            ret = _next();
        }
        finally
        {
            if (!ret)
            {
                log("<close() on _queue>");
                joinBackgroundThread();
                if (null != _embargoDataIterator)
                    _embargoDataIterator.setReleasedRowNumber(Integer.MAX_VALUE);
                checkBackgroundException();
            }
            log("</next>");
        }
        return ret;
    }

    void joinBackgroundThread()
    {
        log("<close() on _queue>");
        _queue.close();
        log("</close() on _queue>");
        log("<join() on _asyncThread>");
        Thread bgThread;
        while (null != (bgThread = _asyncThread))
        {
            try
            {
                Thread.interrupted(); // clear interrupted status
                bgThread.join();
                break;
            }
            catch (InterruptedException x)
            {
                log("join() was interrupted!", x);
            }
        }
        log("</join() on _asyncThread>");
    }

    private boolean _next() throws BatchValidationException
    {
        try
        {
            boolean hasNextRow = _data.next();

            if (hasNextRow)
            {
                int n = (Integer)_data.get(0);
                assert( n > _currentRowNumber);
                _currentRowNumber = n;

                log("<clear and set parameters on " + _currentStmt + ">");
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
                log("</clear and set parameters on " + _currentStmt + ">");

                checkShouldCancel();

                if (_batchSize > 1)
                    _currentStmt.addBatch();
                _currentBatchSize++;
                _currentTxSize++;
            }
            else if (_errors.getExtraContext() != null)
            {
                _errors.getExtraContext().put("hasNextRow", false);
            }

            if ((_currentBatchSize == _batchSize || !hasNextRow) && _currentBatchSize > 0)
            {
                processBatch(_currentBatchSize, _currentRowNumber);
            }

            // This allows specifying more granular commits than the transaction wrapping the entire operation
            if (_currentTxSize == _txSize && _currentStmt.getScope().isTransactionActive() && hasNextRow)
            {
                _currentTxSize = 0;
                if (_currentBatchSize > 0) // flush the statement batch buffer
                    processBatch(_currentBatchSize, _currentRowNumber);
                if (_log != null)
                    _log.info("Committing " + Integer.toString(_txSize) + " rows");
                _currentStmt.getScope().getCurrentTransaction().commitAndKeepConnection();
            }

            if (null != _rowIdIndex)
                _keyValues.set(_rowIdIndex, _currentStmt.getRowId());
            if (null != _objectIdIndex)
                _keyValues.set(_objectIdIndex, _currentStmt.getObjectId());
            if (null != _objectUriIndex)
                _keyValues.set(_objectUriIndex, _currentStmt.getObjectURI());

            checkBackgroundException();
            return hasNextRow;
        }
        catch (RuntimeSQLException rtsqlx)
        {
            SQLException sqlx = rtsqlx.getSQLException();
            if (sqlx instanceof BatchUpdateException && null != sqlx.getNextException())
                sqlx = sqlx.getNextException();
            if (StringUtils.startsWith(sqlx.getSQLState(), "22") || RuntimeSQLException.isConstraintException(sqlx))
            {
                getRowError().addGlobalError(sqlx);
//              see bug21719
//              Sometimes (always?) Postgres leaves the connection unusable after a constraint exception, so we can't continue even if we want to
                throw _errors;
            }
            // table does not exist
            else if (SqlDialect.isObjectNotFoundException(sqlx))
            {
                OptimisticConflictException opt = OptimisticConflictException.create(Table.ERROR_TABLEDELETED);
                getRowError().addGlobalError(opt);
                throw _errors;
            }
            throw new RuntimeSQLException(sqlx);
        }
        catch (DataAccessException x)
        {
            assert false : "Should not be here";
            throw x;
        }
    }


    private void processBatch(int batchSize, int rowNumber) throws BatchValidationException
    {
        assert _execute.start();

        if (_batchSize == 1)
        {
            assert batchSize == 1;
            /* use .execute() for handling keys */
            log("<execute() on " + _currentStmt + ">");
            _currentStmt.execute();
            afterExecute(_currentStmt, batchSize, rowNumber);
            log("</execute() on " + _currentStmt + ">");
        }
        else if (_useAsynchronousExecute && _stmts.length > 1 && _txSize==-1)
        {
            while (true)
            {
                try
                {
                    log("<swap() - old: " + _currentStmt + ">");
                    int previousBackgroundRowNumber = _backgroundRowNumber;
                    _backgroundRowNumber = rowNumber;
                    _currentStmt = _queue.swapFullForEmpty(_currentStmt);
                    if (previousBackgroundRowNumber >= 0)
                    {
                        // TODO remember background batchsize if afterExecute() impl needs it
                        afterExecute(_currentStmt, -1, rowNumber);
                    }
                    if (previousBackgroundRowNumber >= 0 && null != _embargoDataIterator)
                        _embargoDataIterator.setReleasedRowNumber(previousBackgroundRowNumber);
                    log("</swap() - new: " + _currentStmt + ">");
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
            log("<executeBatch() on " + _currentStmt + ">");
            _currentStmt.executeBatch();
            afterExecute(_currentStmt, batchSize, rowNumber);
            log("</executeBatch() on " + _currentStmt + ">");
        }

        _currentBatchSize = 0;
        _currentBinding = (_currentStmt == _stmts[0] ? _bindings[0] : _bindings[1]);

        assert _execute.stop();
    }

    private void log(String message)
    {
        if (null != _log)
            _log.trace(message);
    }

    private void log(String message, Exception e)
    {
        if (null != _log)
            _log.trace(message, e);
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
        try
        {
            log("<close()>");
            log("<close() on _data>");
            _data.close();
            log("</close() on _data>");
            joinBackgroundThread();
            for (ParameterMapStatement stmt : _stmts)
            {
                if (stmt != null)
                {
                    log("<close() on " + stmt + ">");
                    stmt.close();
                    log("</close() on " + stmt + ">");
                }
            }
            _stmts = new ParameterMapStatement[0];
        }
        finally
        {
            log("</close()>");
        }
    }


    class _Runnable implements Runnable
    {
        private final ParameterMapStatement _firstEmpty;

        _Runnable(@NotNull ParameterMapStatement empty)
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
                log("executeStatementsInBackground() threw", x);
                _backgroundException.set(x);
                _foregroundThread.interrupt();
            }
            finally
            {
                _asyncThread = null;
            }
        }

        private void executeStatementsInBackground() throws BatchValidationException, InterruptedException
        {
            ParameterMapStatement m = _firstEmpty;

            while (null != (m = _queue.swapEmptyForFull(m)))
            {
                try
                {
                    log("<executeBatch() on " + m + ">");
                    m.executeBatch();
                    log("</executeBatch() on " + m + ">");
                }
                catch (RuntimeSQLException rtsqlx)
                {
                    // for backward compatibility use the underlying SQLException (could update tests, etc)
                    SQLException sqlx = rtsqlx.getSQLException();
                    if (sqlx instanceof BatchUpdateException && null != sqlx.getNextException())
                        sqlx = sqlx.getNextException();
                    // NOTE some constraint exceptions are recoverable (especially on sql server), but treat all sql exceptions as fatal
                    //noinspection ThrowableResultOfMethodCallIgnored
                    getRowError().addGlobalError(sqlx);
                    throw _context.getErrors();
                }
                catch (DataAccessException x)
                {
                    assert false : "Should not be here";
                    throw x;
                }
            }
            assert _queue.isClosed();
            log("exit background thread");
        }
    }


    @Override
    public void debugLogInfo(StringBuilder sb)
    {
        super.debugLogInfo(sb);
        if (null != _data)
            _data.debugLogInfo(sb);
    }





    public static class TestCase extends Assert
    {
        private DataIterator getSource(DataIteratorContext context, int rowlimit)
        {
            return new DummyDataIterator(context, rowlimit);
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
                ParameterMapStatement pm1 = new ParameterMapStatement(tempdb.getScope(), conn, updateX, null);

                SQLFragment updateY = new SQLFragment("UPDATE " + tableName + " SET Y=Y+?");
                updateY.add(new Parameter("I", JdbcType.INTEGER));
                ParameterMapStatement pm2 = new ParameterMapStatement(tempdb.getScope(), conn, updateY, null);

                DataIteratorContext context = new DataIteratorContext();
                DataIterator source = getSource(context, rowCount);
                StatementDataIterator sdi = new StatementDataIterator(scope.getSqlDialect(), source, context, pm1, pm2)
                {
                    @Override
                    void init()
                    {
                        super.init();
                        _batchSize = batchSize;
                    }
                };
                sdi.setUseAsynchronousExecute(true);

                new Pump(sdi,context).run();

                Map<String,Object> result = new SqlSelector(tempdb,"SELECT X, Y FROM " + tableName).getMap();
                assertNotNull(result.get("X"));
                assertNotNull(result.get("Y"));
                int x = ((Number)result.get("X")).intValue();
                int y = ((Number)result.get("Y")).intValue();
                assertEquals(rowCount, x+y);
                if (scope.getSqlDialect().allowAsynchronousExecute())
                {
                    if (0 == rowCount % (2 * batchSize))
                        assertEquals(x, y);
                    assertTrue(Math.abs(x - y) <= batchSize);
                }
            }
            finally
            {
                if (null != conn)
                    scope.releaseConnection(conn);
                new SqlExecutor(tempdb).execute("DROP TABLE " + tableName);
            }
        }


        private static final class NoopParameterMapStatement extends ParameterMapStatement
        {
            private final AtomicInteger _errorWhen;

            NoopParameterMapStatement(AtomicInteger errorWhen)
            {
                super();
                _errorWhen = errorWhen;
                _map = new CaseInsensitiveHashMap<>();
                _parameters = new Parameter[1];
                _parameters[0] = new Parameter("I",JdbcType.INTEGER);
                _map.put(_parameters[0].getName(),0);
            }

            @Override
            public void executeBatch()
            {
                if (0 == _errorWhen.getAndDecrement())
                    throw new RuntimeSQLException(new SQLException("boom"));
            }

            @Override
            public int execute()
            {
                if (0 == _errorWhen.getAndDecrement())
                    throw new RuntimeSQLException(new SQLException("boom"));
                return 1;
            }

            @Override
            public void addBatch()
            {
            }

            @Override
            public void clearParameters()
            {
            }

            @Override
            public void put(String name, Object value)
            {
            }

            @Override
            public void putAll(Map<String, Object> values)
            {
            }
        }

        @Test
        public void testException()
        {
            _testException(1);
            _testException(2);
            _testException(3);
        }

        static class MockDialect extends SimpleSqlDialect
        {
            @Override
            public String getProductName()
            {
                return null;
            }

            @Override
            public String concatenate(String... args)
            {
                return null;
            }

            @Override
            public SQLFragment concatenate(SQLFragment... args)
            {
                return null;
            }

            @Override
            public JdbcHelper getJdbcHelper()
            {
                return null;
            }

            @Override
            public boolean allowSortOnSubqueryWithoutLimit()
            {
                return false;
            }

            @Override
            public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, TableInfo table)
            {
                return null;
            }

            @Override
            public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
            {
                return null;
            }

            @NotNull
            @Override
            protected Set<String> getReservedWords()
            {
                return Set.of();
            }

            @Override
            public boolean allowAsynchronousExecute()
            {
                return true;
            }

            @Override
            public boolean supportsOffset()
            {
                return false;
            }
        }


        public void _testException(int when)
        {
            AtomicInteger intWhen = new AtomicInteger(when);
            ParameterMapStatement pm1 = new NoopParameterMapStatement(intWhen);
            ParameterMapStatement pm2 = new NoopParameterMapStatement(intWhen);

            DataIteratorContext context = new DataIteratorContext();
            DataIterator source = getSource(context, 100);
            StatementDataIterator sdi = new StatementDataIterator(new MockDialect(), source, context, pm1, pm2)
            {
                @Override
                void init()
                {
                    super.init();
                    _batchSize = 10;
                }
            };
            sdi.setUseAsynchronousExecute(true);

            new Pump(sdi,context).run();
            assertTrue(pm1.isClosed());
            assertTrue(pm2.isClosed());
            assertEquals(1, context.getErrors().getRowErrors().size());
        }

        @Test
        public void testEmbargoDI() throws BatchValidationException
        {
            DataIteratorContext context = new DataIteratorContext();
            DummyDataIterator generator = new DummyDataIterator(context, 23);
            EmbargoDataIterator edi = new EmbargoDataIterator(context, generator, null, null);
            generator._embargoDataIterator = edi;
            int count = 0;
            while (edi.next())
            {
                assertEquals(count, edi.get(0));
                count++;
            }
            assertEquals(count, generator.totalRows);
        }
    }

    static class DummyDataIterator extends AbstractDataIterator
    {
        public final int totalRows;
        public final int rowsPerBatch = 5;
        int currentRow = -1;
        public EmbargoDataIterator _embargoDataIterator;

        DummyDataIterator(DataIteratorContext context, int limit)
        {
            super(context);
            totalRows = limit;
        }

        @Override
        public int getColumnCount()
        {
            return 1;
        }

        @Override
        public ColumnInfo getColumnInfo(int i)
        {
            if (i==0)
                return new BaseColumnInfo("rownumber", JdbcType.INTEGER );
            else if (i==1)
                return new BaseColumnInfo("I", JdbcType.INTEGER );
            else
                return new BaseColumnInfo("col"+i, JdbcType.INTEGER );
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            currentRow++;
            if (currentRow == totalRows || (currentRow % rowsPerBatch == (rowsPerBatch-1)))
            {
                if (null != _embargoDataIterator)
                    _embargoDataIterator.setReleasedRowNumber(currentRow);
            }
            return currentRow < totalRows;
        }

        @Override
        public Object get(int i)
        {
            if (i==0)
                return currentRow;
            return i;
        }

        @Override
        public void close() throws IOException
        {
        }
    }
}
