/*
 * Copyright (c) 2011-2015 LabKey Corporation
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
package org.labkey.api.etl;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.SwapQueue;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.util.CPUTimer;

import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;


class StatementDataIterator extends AbstractDataIterator
{
    Parameter.ParameterMap[] _stmts;
    Triple[][] _bindings = null;

    Parameter.ParameterMap _currentStmt;
    Triple[] _currentBinding;

    // coordinate asynchronous statement execution
    boolean _useAsynchronousExecute = false;
    SwapQueue<Parameter.ParameterMap> _queue = new SwapQueue<>();
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

        _keyColumnInfo = new ArrayList<>(Collections.nCopies(data.getColumnCount()+1,(ColumnInfo)null));
        _keyValues = new ArrayList<>(Collections.nCopies(data.getColumnCount()+1,null));
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
                    bindings.add(new Triple(i, to, mv));
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
        Triple(int from, Parameter to, Parameter mv)
        {
            this.fromIndex = from;
            this.to = to;
            this.mv = mv;
        }
        int fromIndex;
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

        boolean hasNextRow = false;

        try
        {
            hasNextRow = _data.next();

            if (hasNextRow)
            {
                _currentStmt.clearParameters();
                for (Triple binding : _currentBinding)
                {
                    Object value = _data.get(binding.fromIndex);
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

            // ETL's allow specifying more granular commits than the transaction wrapping the entire operation
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
            // table does not exists
            else if (SqlDialect.isObjectNotFoundException(x))
            {
                Table.OptimisticConflictException opt = Table.OptimisticConflictException.create(Table.ERROR_TABLEDELETED);
                getRowError().addGlobalError(opt);
                throw _errors;
            }
            throw new RuntimeSQLException(x);
        }
    }

    private void processBatch() throws SQLException
    {
        _currentBatchSize = 0;
        assert _execute.start();

        if (_batchSize == 1)
        {
            _currentStmt.execute();
        }
        else if (_useAsynchronousExecute && _stmts.length > 1 && _txSize==-1)
        {
            _currentStmt = _queue.swapFullForEmpty(_currentStmt);
            _currentBinding = (_currentStmt == _stmts[0] ? _bindings[0] : _bindings[1]);
        }
        else
        {
            _currentStmt.executeBatch();
        }
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
                executeStatmentsInBackground();
            }
            catch (Exception x)
            {
                _backgroundException.set(x);
            }
        }

        void executeStatmentsInBackground() throws BatchValidationException
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
                    if (StringUtils.startsWith(x.getSQLState(), "22") || RuntimeSQLException.isConstraintException(x))
                    {
                        getRowError().addGlobalError(x);
                        _context.checkShouldCancel();
                    }
                    throw new RuntimeSQLException(x);
                }
            }
        }
    }
}
