/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
import java.util.Set;

/**
 * Tracks columns that should be logged when a query is executed as part of PHI access or similar.
 */
public class ColumnLogging implements Comparable<ColumnLogging>
{
    private final boolean _shouldLogName;
    private final Set<FieldKey> _dataLoggingColumns;
    private final String _loggingComment;
    private final FieldKey _originalColumnFieldKey;
    private String _originalTableName;
    private SelectQueryAuditProvider _selectQueryAuditProvider;

    public ColumnLogging(boolean shouldLogName, FieldKey columnFieldKey, TableInfo parentTable, Set<FieldKey> dataLoggingColumns, String loggingComment, SelectQueryAuditProvider selectQueryAuditProvider)
    {
        _shouldLogName = shouldLogName;
        _dataLoggingColumns = dataLoggingColumns;
        _loggingComment = loggingComment;
        _originalColumnFieldKey = columnFieldKey;
        _originalTableName = null != parentTable ? parentTable.getName() : "";
        _selectQueryAuditProvider = selectQueryAuditProvider;
    }

    public ColumnLogging(boolean shouldLogName, FieldKey columnFieldKey, TableInfo parentTable, Set<FieldKey> dataLoggingColumns, String loggingComment)
    {
        this(shouldLogName, columnFieldKey, parentTable, dataLoggingColumns, loggingComment, null);
    }

    public ColumnLogging(FieldKey columnFieldKey, TableInfo parentTable)
    {
        this(false, columnFieldKey, parentTable, Collections.emptySet(), "");
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

    public void setOriginalTableName(String originalTableName)
    {
        _originalTableName = originalTableName;
    }

    @Override
    public int compareTo(@NotNull ColumnLogging o)
    {
        int ret = this.getOriginalTableName().compareToIgnoreCase(o.getOriginalTableName());
        if (0 == ret)
            ret = this.getOriginalColumnFieldKey().compareTo(o.getOriginalColumnFieldKey());
        return ret;
    }

    public SelectQueryAuditProvider getSelectQueryAuditProvider()
    {
        return _selectQueryAuditProvider;
    }

}
