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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.UnauthorizedException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tracks columns that should be logged when a query is executed as part of PHI access or similar.
 */
public class ColumnLogging implements Comparable<ColumnLogging>
{
    // These fields are used to for generating error messages
    protected final String _originalSchemaName;
    protected final String _originalTableName;
    protected final FieldKey _originalColumnFieldKey;

    protected final UnauthorizedException _exception;

    // QuerySelectView.getRequiredDataLoggingColumns() previously used ColumnLogging.equals() to detect
    // that a column in "allInvolvedColumns" is the same as a column used in a CustomView.
    //
    // I don't like this complexity, maybe we can update getRequiredDataLoggingColumns() to not rely on isFromSameColumn().
    // For now, we can improve how we identify that two ColumnLogging columns represent the same logical underlying column.
    // E.g. SELECT A.x, B.x FROM Table as A, Table as B     A.x and B.x are not the same even though they come from the same schema/table/column
    //      SELECT A.createdBy.UserId, B.createdBy.UserId   A.UserId and B.UserId are not the same even though they come from the same schema/table/column
//    @NotNull protected final String _uniqueColumnKey;
    @Nullable
//    protected final FieldKey _columnScopeKey;

    protected final boolean _shouldLogName;
    protected final Set<FieldKey> _dataLoggingColumns;
    protected final String _loggingComment;
    protected final SelectQueryAuditProvider _selectQueryAuditProvider;


    public static String makeUniqueKey(TableInfo table, FieldKey columnFieldKey)
    {
        UserSchema userSchema = table.getUserSchema();
        if (null != userSchema)
            return userSchema.getContainer().getId() + ":" + userSchema.getSchemaPath() + "/!/" + table.getName() + "/!/" + columnFieldKey;
        // I don't know that it makes sense to create ColumnLogging for SchemaTableInfo, but it's going to be a refactor to unwind this, see BaseColumnInfo
        DbSchema dbschema = table.getSchema();
        return  dbschema.getScope().getDataSourceName() + ":" + dbschema.getName() + "/!/" + table.getName() + "/!/" + columnFieldKey;
    }


    public static ColumnLogging defaultLogging(ColumnInfo col)
    {
        var parent = col.getParentTable();
        Objects.requireNonNull(parent);
        var tableName  = parent.getName();
        var schemaName = null != parent.getUserSchema() ? parent.getUserSchema().getName() : parent.getSchema().getName();
        var uniqueColumnKey = makeUniqueKey(parent, col.getFieldKey());
        return new ColumnLogging(schemaName, tableName, col.getFieldKey(),
//                uniqueColumnKey, null,
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
                _shouldLogName, dataLoggingColumns, _loggingComment, _selectQueryAuditProvider);
    }


    // we don't usually want to change the original table, but if we do here you go.  See ListTable
    public ColumnLogging reparent(TableInfo table)
    {
        String uniqueColumnKey = makeUniqueKey(table, _originalColumnFieldKey);
        return new ColumnLogging(table.getUserSchema().getSchemaName(), table.getName(), _originalColumnFieldKey,
//                uniqueColumnKey, _columnScopeKey,
                _shouldLogName, _dataLoggingColumns, _loggingComment, _selectQueryAuditProvider);
    }

    public ColumnLogging addScope(String scope)
    {
        return new ColumnLogging(_originalSchemaName, _originalTableName, _originalColumnFieldKey,
//                _uniqueColumnKey, new FieldKey(_columnScopeKey, scope),
                _shouldLogName, _dataLoggingColumns, _loggingComment, _selectQueryAuditProvider);
    }

    public ColumnLogging(String schemaName, String tableName, FieldKey originalColumnFieldKey,
//                         @NotNull String uniqueColumnKey, @Nullable FieldKey scope,
                         boolean shouldLogName, Set<FieldKey> dataLoggingColumns, String loggingComment, SelectQueryAuditProvider selectQueryAuditProvider)
    {
        _originalSchemaName = schemaName;
        _originalTableName = tableName;
        _originalColumnFieldKey = originalColumnFieldKey;
        _shouldLogName = shouldLogName;
        _dataLoggingColumns = Collections.unmodifiableSet(dataLoggingColumns);
        _loggingComment = loggingComment;
        _selectQueryAuditProvider = selectQueryAuditProvider;
//        _uniqueColumnKey = uniqueColumnKey;
//        _columnScopeKey = scope;
        _exception = null;
    }


    @Deprecated
    public ColumnLogging(boolean shouldLogName, FieldKey columnFieldKey, TableInfo parentTable, Set<FieldKey> dataLoggingColumns, String loggingComment, SelectQueryAuditProvider selectQueryAuditProvider)
    {
        this(parentTable.getUserSchema().getSchemaName(), parentTable.getName(), columnFieldKey,
//                makeUniqueKey(parentTable, columnFieldKey), null,
                shouldLogName, dataLoggingColumns, loggingComment, selectQueryAuditProvider);
    }


    public ColumnLogging(boolean shouldLogName,  SelectQueryAuditProvider selectQueryAuditProvider, UnauthorizedException exception)
    {
        _originalSchemaName = null;
        _originalTableName = null;
        _originalColumnFieldKey = null;
        _shouldLogName = true;
        _dataLoggingColumns = Set.of();
        _loggingComment = null;
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
        throw new UnsupportedOperationException();
//        return _uniqueColumnKey.compareTo(o._uniqueColumnKey);
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
     * time if column logging is enabled.  I don't particularly like this pattern.  I would be better to maybe
     * psas in a QueryLogging objet into query compile?
     */
    public UnauthorizedException getException()
    {
        return _exception;
    }
}
