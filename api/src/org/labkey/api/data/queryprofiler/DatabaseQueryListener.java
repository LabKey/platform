/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.api.data.queryprofiler;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.QueryLogging;
import org.labkey.api.security.User;

/**
 * Gets invoked when a query that matches its registered substring is run against the underlying database.
 *
 * User: jeckels
 * Date: 2/13/14
 */
public interface DatabaseQueryListener<T>
{
    /**
     * @return whether this listener cares about the query based on sql string
     */
    default boolean matches(String sql)
    {
        return true;
    }

    /**
     * @return whether this listener cares about the query based on QueryLogging properties
     */
    default boolean matches(@Nullable QueryLogging queryLogging)
    {
        return true;
    }

    /**
     * @return whether this listener cares about the query based on Dbscope properties
     */
    default boolean matches(DbScope scope)
    {
        return true;
    }

    /**
     * @return whether this listener cares about the query based on dbscope properties, sql string, and queryLogging properties
     */
    default boolean matches(@Nullable DbScope scope, String sql, @Nullable QueryLogging queryLogging)
    {
        return matches(scope) && matches(sql) && matches(queryLogging);
    }

    /** Called when a matching query is run against the database. */
    void queryInvoked(DbScope scope, String sql, User user, Container container, @Nullable T environment, QueryLogging queryLogging);

    /** @return a custom context, which will be provided if and when the queryInvoked() method is called. This will be called
     * from the originating thread (not an asychronous thread that might actually be running the query), so it can
     * gather information from ThreadLocals if needed */
    @Nullable
    T getEnvironment();
}
