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

package org.labkey.bigiron.mssql;

import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectFactory;
import org.labkey.api.util.VersionNumber;

import java.util.Collection;
import java.util.Collections;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 10:01:13 PM
*/
public class MicrosoftSqlServer2005DialectFactory extends SqlDialectFactory
{
    // JTDS driver always gets mapped to base SQL Server dialect, not this one
    @Override
    public boolean claimsDriverClassName(String driverClassName)
    {
        return false;
    }

    @Override
    public boolean claimsProductNameAndVersion(String dataBaseProductName, VersionNumber databaseProductVersion, String jdbcDriverVersion, boolean logWarnings)
    {
        return dataBaseProductName.equals("Microsoft SQL Server") && (databaseProductVersion.getVersionInt() >= 90);
    }

    @Override
    public SqlDialect create()
    {
        return new MicrosoftSqlServer2005Dialect();
    }

    @Override
    public Collection<? extends Class> getJUnitTests()
    {
        return Collections.emptyList();
    }
}
