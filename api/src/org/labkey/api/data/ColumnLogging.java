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


import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.FieldKey;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks columns that should be logged when a query is executed as part of PHI access or similar.
 */
public class ColumnLogging implements Comparable<ColumnLogging>
{
    private final String _originalSchemaName;
    private final String _originalTableName;
    private final FieldKey _originalColumnFieldKey;
    private final boolean _shouldLogName;
    private final Set<FieldKey> _dataLoggingColumns;
    private final String _loggingComment;
    private final SelectQueryAuditProvider _selectQueryAuditProvider;

    public static ColumnLogging defaultLogging(ColumnInfo col)
    {
        var parent = col.getParentTable();
        var tableName = null == parent ? "" : parent.getName();
        var schemaName = null == parent ? "" : parent.getPublicSchemaName();
        return new ColumnLogging(schemaName, tableName, col.getFieldKey(), false, Set.of(), "", null);
    }


    public ColumnLogging remapFieldKeys(FieldKey baseFieldKey, Map<FieldKey,FieldKey> remap)
    {
        if (null == baseFieldKey && (null == remap || remap.isEmpty()))
            return this;
        if (_dataLoggingColumns.isEmpty())
            return this;
        Set<FieldKey> dataLoggingColumns = new HashSet<>();
        for (FieldKey fk : _dataLoggingColumns)
            dataLoggingColumns.add(FieldKey.remap(fk, baseFieldKey, remap));
        return new ColumnLogging(_originalSchemaName, _originalTableName, _originalColumnFieldKey, _shouldLogName, dataLoggingColumns, _loggingComment, _selectQueryAuditProvider);
    }


    // we don't usually want to change the original table, but if we do here you go
    public ColumnLogging reparent(TableInfo table)
    {
        return new ColumnLogging(table.getSchema().getName(), table.getName(), _originalColumnFieldKey, _shouldLogName, _dataLoggingColumns, _loggingComment, _selectQueryAuditProvider);
    }


    public ColumnLogging(String schemaName, String tableName, FieldKey originalColumnFieldKey, boolean shouldLogName, Set<FieldKey> dataLoggingColumns, String loggingComment, SelectQueryAuditProvider selectQueryAuditProvider)
    {
        _originalSchemaName = schemaName;
        _originalTableName = tableName;
        _originalColumnFieldKey = originalColumnFieldKey;
        _shouldLogName = shouldLogName;
        _dataLoggingColumns = Collections.unmodifiableSet(dataLoggingColumns);
        _loggingComment = loggingComment;
        _selectQueryAuditProvider = selectQueryAuditProvider;
    }


    @Deprecated
    public ColumnLogging(boolean shouldLogName, FieldKey columnFieldKey, TableInfo parentTable, Set<FieldKey> dataLoggingColumns, String loggingComment, SelectQueryAuditProvider selectQueryAuditProvider)
    {
        this(parentTable.getSchema().getName(), parentTable.getName(), columnFieldKey, shouldLogName, dataLoggingColumns, loggingComment, selectQueryAuditProvider);
    }



    /** If true, then this column's name should be logged when used in a query */
    public boolean shouldLogName()
    {
        return _shouldLogName;
    }

    /** Returns set of field keys for column's whose data should be logged when a query is logged; example is PatientId FK to MRN */
    public Set<FieldKey> getDataLoggingColumns()
    {
        return _dataLoggingColumns;
    }

    /** Returns comment to be added to the audit event (typically only the first logged column is asked for this) */
    public String getLoggingComment()
    {
        return _loggingComment;
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
    public int compareTo(@NotNull ColumnLogging o)
    {
        int ret = this._originalSchemaName.compareTo(o._originalSchemaName);
        if (0 == ret)
            ret = this.getOriginalTableName().compareToIgnoreCase(o.getOriginalTableName());
        if (0 == ret)
            ret = this.getOriginalColumnFieldKey().compareTo(o.getOriginalColumnFieldKey());
        return ret;
    }

    public SelectQueryAuditProvider getSelectQueryAuditProvider()
    {
        return _selectQueryAuditProvider;
    }
}
