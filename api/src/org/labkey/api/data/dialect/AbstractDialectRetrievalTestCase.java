/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.ConfigurationException;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

public abstract class AbstractDialectRetrievalTestCase extends Assert
{
    @Test
    public abstract void testDialectRetrieval();

    protected void good(String databaseName, double beginVersion, double endVersion, String jdbcDriverVersion, String jdbcConnectionUrl, String driverName, Class<? extends SqlDialect> expectedDialectClass)
    {
        testRange(databaseName, beginVersion, endVersion, jdbcDriverVersion, jdbcConnectionUrl, driverName, expectedDialectClass, null);
    }

    protected void badProductName(String databaseName, double beginVersion, double endVersion, String jdbcDriverVersion, String jdbcConnectionUrl)
    {
        testRange(databaseName, beginVersion, endVersion, jdbcDriverVersion, jdbcConnectionUrl, null, null, SqlDialectNotSupportedException.class);
    }

    protected void badVersion(String databaseName, double beginVersion, double endVersion, String jdbcDriverVersion, String jdbcConnectionUrl)
    {
        testRange(databaseName, beginVersion, endVersion, jdbcDriverVersion, jdbcConnectionUrl, null, null, DatabaseNotSupportedException.class);
    }

    protected void testRange(String databaseName, double beginVersion, double endVersion, String jdbcDriverVersion, String jdbcConnectionUrl, String driverName, @Nullable Class<? extends SqlDialect> expectedDialectClass, @Nullable Class<? extends ConfigurationException> expectedExceptionClass)
    {
        int begin = (int)Math.round(beginVersion * 10);
        int end = (int)Math.round(endVersion * 10);

        for (int i = begin; i < end; i++)
        {
            int majorVersion = i / 10;
            int minorVersion = i % 10;

            String description = databaseName + " version " + majorVersion + "." + minorVersion;

            try
            {
                SqlDialect dialect = SqlDialectManager.getFromMetaData(getMockedMetadata(databaseName, majorVersion + "." + minorVersion, jdbcDriverVersion, jdbcConnectionUrl, driverName), false, false);
                assertNotNull(description + " returned " + dialect.getClass().getSimpleName() + "; expected failure", expectedDialectClass);
                assertEquals(description, expectedDialectClass, dialect.getClass());
            }
            catch (Exception e)
            {
                assertNull(description + " failed; expected success", expectedDialectClass);
                assertEquals(description, expectedExceptionClass, e.getClass());
            }
        }
    }

    protected DatabaseMetaData getMockedMetadata(String databaseProductName, String databaseProductVersion, String jdbcDriverVersion, String jdbcConnectionUrl, String driverName) throws SQLException
    {
        Mockery mocker = new Mockery();
        final DatabaseMetaData md = mocker.mock(DatabaseMetaData.class);
        mocker.checking(new Expectations()
        {
            {
                allowing(md).getURL();
                will(returnValue(jdbcConnectionUrl));

                allowing(md).getDatabaseProductName();
                will(returnValue(databaseProductName));

                allowing(md).getDatabaseProductVersion();
                will(returnValue(databaseProductVersion));

                allowing(md).getDriverVersion();
                will(returnValue(jdbcDriverVersion));

                allowing(md).getDriverName();
                will(returnValue(driverName));
            }
        });
        return md;
    }
}
