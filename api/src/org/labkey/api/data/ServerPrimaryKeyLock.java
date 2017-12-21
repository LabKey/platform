/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
import org.labkey.api.exceptions.OptimisticConflictException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * Created by matthew on 2/22/16.
 */
public class ServerPrimaryKeyLock implements DbScope.ServerLock
{
    final DbScope scope;
    final SQLFragment forUpdate;
    final boolean failIfRowNotFound;

    public ServerPrimaryKeyLock(boolean failIfRowNotFound, TableInfo t, Object... pkValues)
    {
        List<ColumnInfo> pkColumns = t.getPkColumns();
        // TODO core.containers doesn't have a declared PK???
        if (pkColumns.isEmpty() && "containers".equalsIgnoreCase(t.getName()))
            pkColumns = Arrays.asList(t.getColumn("entityid"));
        if (pkColumns.size() != pkValues.length)
            throw new IllegalStateException();

        forUpdate = new SQLFragment("SELECT * FROM " + t.getSelectName());
        scope = t.getSchema().getScope();
        this.failIfRowNotFound = failIfRowNotFound;

        if (scope.getSqlDialect().isSqlServer())
            forUpdate.append(" WITH (UPDLOCK)");
        forUpdate.append("\nWHERE ");
        String and = " ";
        for (ColumnInfo pkColumn : pkColumns)
        {
            forUpdate.append(and);
            forUpdate.append(pkColumn.getSelectName()).append("=?").add(pkValues[forUpdate.getParamsArray().length]);
        }
        if (scope.getSqlDialect().isPostgreSQL())
            forUpdate.append("\nFOR UPDATE");
    }

    @Override
    public void lock()
    {
        Map[] maps = new SqlSelector(scope, forUpdate).getArray(Map.class);
        if (failIfRowNotFound && 0 == maps.length)
            throw OptimisticConflictException.create(Table.ERROR_DELETED);
    }

    @Override
    public void lockInterruptibly()
    {
        lock();
    }

    @Override
    public boolean tryLock()
    {
        throw new UnsupportedOperationException("ServerRowLock does not support tryLock()");
    }

    @Override
    public boolean tryLock(long time, @NotNull TimeUnit unit)
    {
        throw new UnsupportedOperationException("ServerRowLock does not support tryLock()");
    }

    @Override
    public void unlock()
    {
        /* nope */
    }

    @NotNull
    @Override
    public Condition newCondition()
    {
        throw new UnsupportedOperationException("ServerRowLock does not support newCondition()");
    }
}
