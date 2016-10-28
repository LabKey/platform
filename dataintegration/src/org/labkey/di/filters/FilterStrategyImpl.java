/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.di.filters;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.CopyConfig;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.util.ConfigurationException;
import org.labkey.di.VariableMap;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.steps.StepMeta;

import java.util.List;

/**
 * User: tgaluhn
 * Date: 3/5/2015
 */
public abstract class FilterStrategyImpl implements FilterStrategy
{
    final TransformJobContext _context;
    final CopyConfig _config;
    final DeletedRowsSource _deletedRowsSource;
    TableInfo _deletedRowsTinfo;
    String _deletedRowsKeyCol;
    String _targetDeletionKeyCol;
    boolean _isInit = false;

    public FilterStrategyImpl(StepMeta stepMeta, TransformJobContext context, DeletedRowsSource deletedRowsSource)
    {
        if (!(stepMeta instanceof CopyConfig))
            throw new ConfigurationException(this.getClass().getName() + " is not compatible with " + stepMeta.getClass().getName());
        _config = (CopyConfig)stepMeta;
        _context = context;
        _deletedRowsSource = deletedRowsSource;
    }

    @Override
    public DeletedRowsSource getDeletedRowsSource()
    {
        return _deletedRowsSource;
    }

    protected void init()
    {
        if (_isInit)
            return;
        initMainFilter();
        initDeletedRowsFilter();
        _isInit = true;
    }

    protected void initMainFilter() {}

    protected void initDeletedRowsFilter()
    {
        if (null != _deletedRowsSource)
        {
            QuerySchema sourceSchema = DefaultSchema.get(_context.getUser(), _context.getContainer(), _deletedRowsSource.getSchemaName());
            if (null == sourceSchema)
                throw new ConfigurationException("Schema for deleted rows query not found: " + _deletedRowsSource.getSchemaName());

            _deletedRowsTinfo = sourceSchema.getTable(_deletedRowsSource.getQueryName());
            if (null == _deletedRowsTinfo)
                throw new ConfigurationException("Query for deleted rows not found: " + _deletedRowsSource.getQueryName());

            if (_deletedRowsSource.getDeletedSourceKeyColumnName() == null) // use the PK
            {
                List<String> delSrcPkCols = _deletedRowsTinfo.getPkColumnNames();
                if (delSrcPkCols.size() != 1)
                {
                    throw new ConfigurationException("Deleted rows query must either have exactly one primary key column, or the match column should be specified in the xml.");
                }
                _deletedRowsKeyCol = delSrcPkCols.get(0);
            }
            else
            {
                ColumnInfo deletedRowsCol = _deletedRowsTinfo.getColumn(FieldKey.fromParts(_deletedRowsSource.getDeletedSourceKeyColumnName()));
                if (null == deletedRowsCol)
                    throw new ConfigurationException("Match key for deleted rows not found: " + _deletedRowsSource.getQueryName() + "." + _deletedRowsSource.getDeletedSourceKeyColumnName());
                _deletedRowsKeyCol = deletedRowsCol.getColumnName();
            }
        }
    }

    @Override
    public TableInfo getDeletedRowsTinfo()
    {
        return _deletedRowsTinfo;
    }

    @Override
    public String getDeletedRowsKeyCol()
    {
        return _deletedRowsKeyCol;
    }

    @Override
    public String getTargetDeletionKeyCol()
    {
        return _targetDeletionKeyCol;
    }

    @Override
    public void setTargetDeletionKeyCol(String col)
    {
        _targetDeletionKeyCol = col;
    }

    @Override
    public SimpleFilter getFilter(VariableMap variables)
    {
        return getFilter(variables, false);
    }

    @Override
    public SimpleFilter getFilter(VariableMap variables, boolean deleting)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLogMessage(@Nullable String filterValue)
    {
        return this.getClass().getSimpleName() + ": " + (null == filterValue ? "no filter" : filterValue);
    }
}
