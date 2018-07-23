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

package org.labkey.bigiron.sas;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.dialect.DatabaseNotSupportedException;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectFactory;

import java.util.Collection;
import java.util.Collections;

/*
* User: adam
* Date: Nov 26, 2010
* Time: 10:25:16 PM
*/
public class SasDialectFactory implements SqlDialectFactory
{
    private String getProductName()
    {
        return "SAS";
    }

    @Override
    public @Nullable SqlDialect createFromDriverClassName(String driverClassName)
    {
        return "com.sas.net.sharenet.ShareNetDriver".equals(driverClassName) ? new Sas91Dialect() : null;
    }

    // SAS/SHARE driver throws when invoking DatabaseMetaData database version methods, so use the jdbcDriverVersion to determine dialect version
    @Override
    public @Nullable SqlDialect createFromProductNameAndVersion(String dataBaseProductName, String databaseProductVersion, String jdbcDriverVersion, boolean logWarnings, boolean primaryDataSource) throws DatabaseNotSupportedException
    {
        if (!getProductName().equals(dataBaseProductName))
            return null;

        if (jdbcDriverVersion.startsWith("9.1"))
            return new Sas91Dialect();

        if (jdbcDriverVersion.startsWith("9.2"))
            return new Sas92Dialect();

        if (jdbcDriverVersion.startsWith("9.3"))
            return new Sas93Dialect();

        throw new DatabaseNotSupportedException(getProductName() + " version " + databaseProductVersion + " is not supported.");
    }

    @Override
    public Collection<? extends Class> getJUnitTests()
    {
        return Collections.emptyList();
    }

    @Override
    public Collection<? extends SqlDialect> getDialectsToTest()
    {
        return Collections.emptyList();
    }
}
