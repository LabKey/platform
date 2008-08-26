/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.sql.SQLException;

/*
 * User: Dave
 * Date: Jun 9, 2008
 * Time: 4:35:37 PM
 */

/**
 * This interface should be implemented by any module that exposes a schema
 * and queries that can be updated by the HTTP-based APIs, or any other
 * code that works with rows as maps.
 */
public interface SchemaUpdateService
{
    /**
     * Returns the name of this schema.
     * @return The name of this schema.
     */
    public String getSchemaName();

    /**
     * Returns a QueryUpdateSerivce implementation for the given query name,
     * or null if the given query name is not updatable via this service.
     * @param queryName The name of the query
     * @param container The container in which the query should exist.
     * @param user The current user
     * @return A QueryUpdateService implementation for the query or null.
     */
    public QueryUpdateService getQueryUpdateService(String queryName, Container container, User user);

    /**
     * Begins a transaction in this schema. Implementations may
     * throw an exception if a transaction is already in progress
     * and the underlying data store does not support nested transactions.
     * Use {@link #isTransactionActive} to determine if a transaction has
     * already been started in this schema.
     * @throws SQLException Thrown if a transaction could not be started.
     */
    public void beginTransaction() throws SQLException;

    /**
     * Commits a transaction in this schema. If no transaction has been
     * started, the implementation may throw an exception.
     * Use {@link #isTransactionActive} to determine if a transaction has
     * already been started in this schema.
     * @throws SQLException Thrown if the transaction could not be committed.
     */
    public void commitTransaction() throws SQLException;

    /**
     * Rollsback a transaction in this schema. If no transaction has been
     * started, the implementation may throw a runtime exception.
     * Use {@link #isTransactionActive} to determine if a transaction has
     * already been started in this schema.
     */
    public void rollbackTransaction();

    /**
     * Returns true if changes to this schema are currently being transacted
     * for the current user/session.
     * @return true if in a transaction, false otherwise.
     */
    public boolean isTransactionActive();

    /**
     * Returns a schema suitable for use with ontology manager for the given query.
     * May return null if no such schema exists, or if ontology manager is not supported.
     * @param queryName The name of the query
     * @param container The container in which the query should exists
     * @param user The current user
     * @return A domain URI for ontology manager or null
     */
    @Nullable
    public String getDomainURI(String queryName, Container container, User user);

}