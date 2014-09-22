/*
 * Copyright (c) 2014 LabKey Corporation
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
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.Set;

/**
 * Created by davebradlee on 9/12/14.
 */
public class QueryLogging
{
    private Container _container;
    private User _user;
    private String _comment;
    private Set<ColumnLogging> _columnLoggings = Collections.emptySet();
    private Set<ColumnInfo> _dataLoggingColumns = Collections.emptySet();
    private Integer _queryId;
    private boolean _hasBeenValidated = false;
    private final boolean _readOnly;

    public QueryLogging()
    {
        _readOnly = false;
    }

    private QueryLogging(boolean validated)
    {
        _readOnly = true;
        _hasBeenValidated = validated;
    }

    public void setQueryLogging(User user, Container container, String comment, Set<ColumnLogging> columnLoggings,
                                Set<ColumnInfo> dataLoggingColumns)
    {
        if (_readOnly)
            throw new IllegalStateException("This QueryLogging instance is read-only.");
        _user = user;
        _container = container;
        _comment = comment;
        _columnLoggings = columnLoggings;
        _dataLoggingColumns = dataLoggingColumns;
        _hasBeenValidated = true;
    }

    public boolean isEmpty()
    {
        return _columnLoggings.isEmpty();
    }

    public boolean isReadOnly()
    {
        return _readOnly;
    }

    @Nullable
    public User getUser()
    {
        return _user;
    }

    @Nullable
    public Container getContainer()
    {
        return _container;
    }

    @Nullable
    public String getComment()
    {
        return _comment;
    }

    public Set<ColumnLogging> getColumnLoggings()
    {
        return _columnLoggings;
    }

    public Set<ColumnInfo> getDataLoggingColumns()
    {
        return _dataLoggingColumns;
    }

    public boolean hasBeenValidated()
    {
        return _hasBeenValidated;
    }

    @Nullable
    public Integer getQueryId()
    {
        return _queryId;
    }

    public void setQueryId(@Nullable Integer queryId)
    {
        if (_readOnly)
            throw new IllegalStateException("This QueryLogging instance is read-only.");
        _queryId = queryId;
    }

    private static final QueryLogging _emptyQueryLogging = new QueryLogging(false);
    private static final QueryLogging _noValidationNeededQueryLogging = new QueryLogging(true);

    public static QueryLogging emptyQueryLogging()
    {
        return _emptyQueryLogging;
    }

    public static QueryLogging noValidationNeededQueryLogging()
    {
        return _noValidationNeededQueryLogging;
    }
}
