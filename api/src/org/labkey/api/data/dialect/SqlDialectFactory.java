/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

package org.labkey.api.data.dialect;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/*
* User: adam
* Date: Nov 26, 2010
*/
public interface SqlDialectFactory
{
    @Nullable SqlDialect createFromDriverClassName(String driverClassName);

    /**
     * Returns null if this factory is not responsible for the specified database server.  Otherwise, if the version is
     * supported, returns the matching implementation; if the version is not supported, throws DatabaseNotSupportedException.
     * @param primaryDataSource whether the data source is the primary LabKey Server database, or an external/secondary database
     */
    @Nullable SqlDialect createFromProductNameAndVersion(String dataBaseProductName, String databaseProductVersion, String jdbcDriverVersion, boolean logWarnings, boolean primaryDataSource) throws DatabaseNotSupportedException;

    // These tests must be safe to invoke when LabKey Server can't connect to any data sources matching the dialect and
    // even when the JDBC driver isn't present.
    Collection<? extends Class> getJUnitTests();

    // Caller must invoke initialize() on the dialects.
    Collection<? extends SqlDialect> getDialectsToTest();

    // Factories can override if they want the ability to replace their standard TableResolver
    default void setTableResolver(TableResolver tableResolver)
    {
    }
}
