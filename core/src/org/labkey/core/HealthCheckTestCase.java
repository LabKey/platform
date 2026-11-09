/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.core;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.admin.HealthCheck;
import org.labkey.api.admin.HealthCheckRegistry;
import org.labkey.api.data.DbScope;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HealthCheckTestCase extends Assert
{
    private static final String UNREACHABLE_DS = "healthCheckTestDataSource";
    private static final String SCOPE_LOADER_LOGGER = "org.labkey.api.data.DbScopeLoader";

    private static Map<String, Object> databaseDetails(String category)
    {
        HealthCheck.Result result = (HealthCheck.Result) HealthCheckRegistry.get().checkHealth(category).getDetails().get("database");
        return result.getDetails();
    }

    @Test
    public void testDefaultCoversOnlyLabKeyScope()
    {
        assertEquals(Set.of(DbScope.getLabKeyScope().getDatabaseName()), databaseDetails(HealthCheckRegistry.DEFAULT_CATEGORY).keySet());
    }

    @Test
    public void testExternalCoversOtherScopes()
    {
        Set<String> expected = new HashSet<>();
        DbScope.getDbScopes().stream()
            .filter(scope -> !scope.isLabKeyScope())
            .forEach(scope -> expected.add(scope.getDatabaseName()));
        expected.addAll(DbScope.getFailedDataSourceNames());
        assertEquals(expected, databaseDetails(HealthCheckRegistry.EXTERNAL_DATA_SOURCES_CATEGORY).keySet());
    }

    @Test
    public void testUnreachableExternalDataSource() throws Exception
    {
        // Core doesn't compile against tomcat-dbcp, but it's the only pool LabKeyDataSource accepts
        Class<?> poolClass = Class.forName("org.apache.tomcat.dbcp.dbcp2.BasicDataSource");
        DataSource ds = (DataSource) poolClass.getConstructor().newInstance();
        poolClass.getMethod("setDriverClassName", String.class).invoke(ds, "org.postgresql.Driver");
        // Port 1 refuses immediately, so the connection attempt fails fast instead of waiting out a timeout
        poolClass.getMethod("setUrl", String.class).invoke(ds, "jdbc:postgresql://127.0.0.1:1/healthchecktest");
        DbScope.addScope(UNREACHABLE_DS, new DbScope.LabKeyDataSource(ds, UNREACHABLE_DS));

        // The loader logs the expected connection failure as an error, which the test harness would report as a server error
        Level previousLevel = LogManager.getLogger(SCOPE_LOADER_LOGGER).getLevel();
        Configurator.setLevel(SCOPE_LOADER_LOGGER, Level.OFF);
        try
        {
            assertTrue(HealthCheckRegistry.get().checkHealth(HealthCheckRegistry.DEFAULT_CATEGORY).isHealthy());
            assertFalse("Default check connected to an external data source", DbScope.getFailedDataSourceNames().contains(UNREACHABLE_DS));

            assertFalse(HealthCheckRegistry.get().checkHealth(HealthCheckRegistry.EXTERNAL_DATA_SOURCES_CATEGORY).isHealthy());
            assertEquals(false, databaseDetails(HealthCheckRegistry.EXTERNAL_DATA_SOURCES_CATEGORY).get(UNREACHABLE_DS));

            assertFalse(HealthCheckRegistry.get().checkHealth(List.of("all")).isHealthy());
        }
        finally
        {
            Configurator.setLevel(SCOPE_LOADER_LOGGER, previousLevel);
            DbScope.removeScope(UNREACHABLE_DS);
        }
    }
}
