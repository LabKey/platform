/*
 * Copyright (c) 2010 LabKey Corporation
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

import java.util.Collection;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 8:52:56 PM
*/
public abstract class SqlDialectFactory
{
    public abstract boolean claimsDriverClassName(String driverClassName);

    // Implementation should throw only if it's responsible for the specified database server but doesn't support the specified version
    public abstract boolean claimsProductNameAndVersion(String dataBaseProductName, int databaseMajorVersion, int databaseMinorVersion, String jdbcDriverVersion, boolean logWarnings) throws DatabaseNotSupportedException;

    public abstract SqlDialect create();

    // Note: Tests must be safe to invoke when LabKey Server can't connect to any datasources matching the dialect and
    // even when the JDBC driver isn't present.
    public abstract Collection<? extends Class> getJUnitTests();
}
