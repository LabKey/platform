/*
 * Copyright (c) 2014-2018 LabKey Corporation
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
package org.labkey.api.data;


import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.view.UnauthorizedException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tracks columns that should be logged when a query is executed as part of PHI access or similar.
 */
public class ColumnLogging
{
    // These fields are used to for generating error messages
    protected final String _originalSchemaName;
    protected final String _originalTableName;
    protected final FieldKey _originalColumnFieldKey;

    protected final UnauthorizedException _exception;
    protected final boolean _shouldLogName;
    protected final Set<FieldKey> _dataLoggingColumns;
    protected final Set<String> _loggingComments;
    protected final SelectQueryAuditProvider _selectQueryAuditProvider;


    public static ColumnLogging defaultLogging(ColumnInfo col)
    {
        return defaultLogging(col.getParentTable(), col.getFieldKey());
    }


    public static ColumnLogging defaultLogging(TableInfo parent, FieldKey col)
    {
        Objects.requireNonNull(parent);
        var tableName  = parent.getName();
        var schemaName = null != parent.getUserSchema() ? parent.getUserSchema().getName() : parent.getSchema().getName();
        return new ColumnLogging(schemaName, tableName, col,
                false, Set.of(), "", null);
    }

    public static ColumnLogging error(boolean shouldLogName, SelectQueryAuditProvider selectQueryAuditProvider, String message)
    {
        // Use UnauthorizedException for now to be consistent with getSelectSQL()
        return new ColumnLogging(shouldLogName, selectQueryAuditProvider, new UnauthorizedException(message));
    }


    public ColumnLogging remapFieldKeys(FieldKey baseFieldKey, Map<FieldKey,FieldKey> remap, @Nullable Set<String> remapWarnings)
    {
        if (null == baseFieldKey && (null == remap || remap.isEmpty()))
            return this;
        Set<FieldKey> dataLoggingColumns = new HashSet<>();
        for (FieldKey fk : _dataLoggingColumns)
        {
            var mapped = FieldKey.remap(fk, baseFieldKey, remap);
            if (null == mapped)
            {
                String msg = "Unable to find required logging column " + fk.getName() + " for table " + _originalTableName;
                if (null != remapWarnings)
                    remapWarnings.add(msg);
                return ColumnLogging.error(this._shouldLogName, this._selectQueryAuditProvider, msg);
            }
            dataLoggingColumns.add(mapped);
        }
        return new ColumnLogging(_originalSchemaName, _originalTableName, _originalColumnFieldKey,
                _shouldLogName, dataLoggingColumns, _loggingComments, _selectQueryAuditProvider);
    }


    // we don't usually want to change the original table, but if we do here you go.  See ListTable
    public ColumnLogging reparent(TableInfo table)
    {
        return new ColumnLogging(table.getUserSchema().getSchemaName(), table.getName(), _originalColumnFieldKey,
                _shouldLogName, _dataLoggingColumns, _loggingComments, _selectQueryAuditProvider);
    }


    public ColumnLogging(String schemaName, String tableName, FieldKey originalColumnFieldKey,
                         boolean shouldLogName, Set<FieldKey> dataLoggingColumns, String loggingComment, SelectQueryAuditProvider selectQueryAuditProvider)
    {
        this(schemaName, tableName, originalColumnFieldKey, shouldLogName, dataLoggingColumns, Set.of(loggingComment), selectQueryAuditProvider);
    }


    public ColumnLogging(String schemaName, String tableName, FieldKey originalColumnFieldKey,
                         boolean shouldLogName, Set<FieldKey> dataLoggingColumns, Set<String> loggingComments, SelectQueryAuditProvider selectQueryAuditProvider)
    {
        _originalSchemaName = schemaName;
        _originalTableName = tableName;
        _originalColumnFieldKey = originalColumnFieldKey;
        _shouldLogName = shouldLogName;
        _dataLoggingColumns = Collections.unmodifiableSet(dataLoggingColumns);
        _loggingComments = loggingComments;
        _selectQueryAuditProvider = selectQueryAuditProvider;
        _exception = null;
    }


    @Deprecated
    public ColumnLogging(boolean shouldLogName, FieldKey columnFieldKey, TableInfo parentTable, Set<FieldKey> dataLoggingColumns, String loggingComment, SelectQueryAuditProvider selectQueryAuditProvider)
    {
        this(parentTable.getUserSchema().getSchemaName(), parentTable.getName(), columnFieldKey,
                shouldLogName, dataLoggingColumns, loggingComment, selectQueryAuditProvider);
    }


    public ColumnLogging(boolean shouldLogName,  SelectQueryAuditProvider selectQueryAuditProvider, UnauthorizedException exception)
    {
        _originalSchemaName = null;
        _originalTableName = null;
        _originalColumnFieldKey = null;
        _shouldLogName = true;
        _dataLoggingColumns = Set.of();
        _loggingComments = Set.of();
        _selectQueryAuditProvider = selectQueryAuditProvider;
        _exception = exception;
    }

    /** If true, then this column's name should be logged when used in a query */
    public boolean shouldLogName()
    {
        return _shouldLogName;
    }

    /** Returns set of field keys for column's whose data should be logged when a query is logged; example is PatientId FK to MRN */
    public Set<FieldKey> getDataLoggingColumns()
    {
        // maks sure we aren't calling this w/o checking getException() first
        if (null != _exception)
            throw new IllegalStateException(_exception);
        return _dataLoggingColumns;
    }

    /** Returns comments to be added to the audit event */
    public Set<String> getLoggingComments()
    {
        return _loggingComments;
    }

    public FieldKey getOriginalColumnFieldKey()
    {
        return _originalColumnFieldKey;
    }

    public String getOriginalTableName()
    {
        return _originalTableName;
    }

    @Override
    public boolean equals(Object obj)
    {
        throw new UnsupportedOperationException();
    }


    /* This is an indirect way to test if two columns are the "same".  This functionality was being implemented using
     * compareTo().  That was not very sematically robust.  This is more consistent when looking at the results of
     * SQL queries, but changes the behavior of QuerySelectView.getRequiredDataLoggingColumns().
    public boolean isFromSameColumn(ColumnLogging o)
    {
        if (!_uniqueColumnKey.equals(_uniqueColumnKey))
            return false;
        if ((null==_columnScopeKey) != (null==o._columnScopeKey))
            return false;
        return _columnScopeKey==o._columnScopeKey || _columnScopeKey.equals(o._columnScopeKey);
    }
     */


    public SelectQueryAuditProvider getSelectQueryAuditProvider()
    {
        return _selectQueryAuditProvider;
    }


    /* We need a way for Query to report an error in generating a ColumnLogging object.  It does not know ahead of
     * time if column logging is enabled.  I don't particularly like this pattern.  It would be better to maybe
     * pass in a QueryLogging object into query compile?
     */
    public UnauthorizedException getException()
    {
        return _exception;
    }
}
