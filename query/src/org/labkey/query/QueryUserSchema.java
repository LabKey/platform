/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
package org.labkey.query;

import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.query.query.CustomViewsTable;
import org.labkey.query.query.QueriesTable;
import org.labkey.query.query.QueryDbSchema;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.JunitUtil.deleteTestContainer;

public class QueryUserSchema extends UserSchema
{
    public static final String SCHEMA_NAME = "query";
    public static final String SCHEMA_DESCR = "Contains query related data.";

    public static final String CUSTOM_VIEWS_TABLE_NAME = "CustomViews";
    public static final String QUERIES_TABLE_NAME = "Queries";

    static public void register(final Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new QueryUserSchema(schema.getUser(), schema.getContainer());
            }

            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return schema.getContainer().hasPermission(schema.getUser(), AdminPermission.class);
            }
        });
    }

    public QueryUserSchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCR, user, container, QueryDbSchema.getInstance().getSchema());
    }

    @Override
    public Set<String> getTableNames()
    {
        Set<String> names = new HashSet<>();

        if (getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            names.add(CUSTOM_VIEWS_TABLE_NAME);
            names.add(QUERIES_TABLE_NAME);
        }

        return names;
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        if (getContainer().hasPermission(getUser(), AdminPermission.class))
        {
            if (CUSTOM_VIEWS_TABLE_NAME.equalsIgnoreCase(name))
                return new CustomViewsTable(this, cf);
            if (QUERIES_TABLE_NAME.equalsIgnoreCase(name))
                return new QueriesTable(this, cf);
        }

        return null;
    }

    public static class TestCase extends Assert
    {
        private static final Logger LOG = LogHelper.getLogger(QueryUserSchema.class, "Integration tests for the Query user schema");
        private static User _user;
        private static Container _container;

        @BeforeClass
        public static void setup() throws Exception
        {
            _container = JunitUtil.getTestContainer();
            _user = TestContext.get().getUser();
        }

        @AfterClass
        public static void cleanup()
        {
            deleteTestContainer();
            _container = null;
            _user = null;
        }

        @Test
        public void testCustomViewsAdminOnlyAccess()
        {
            LOG.info("Validate Query.CustomViews is admin only");

            var schema = QueryService.get().getUserSchema(User.getAdminServiceUser(), _container, SCHEMA_NAME);
            assertNotNull("Expected admin access to the " + CUSTOM_VIEWS_TABLE_NAME + " table", schema.getTable(CUSTOM_VIEWS_TABLE_NAME));

            schema = QueryService.get().getUserSchema(User.getSearchUser(), _container, SCHEMA_NAME);
            assertNull("Expected no reader access to the " + SCHEMA_NAME + " schema", schema);
        }

        @Test
        public void testCustomViewsApiAccess() throws Exception
        {
            var qus = ensureUpdateService(CUSTOM_VIEWS_TABLE_NAME);

            BatchValidationException errors = new BatchValidationException();
            Map<String, Object> row = CaseInsensitiveHashMap.of(
                    "schema", "test",
                    "queryName", "query",
                    "flags", 0
            );
            List<Map<String, Object>> views = qus.insertRows(_user, _container, List.of(row), errors, null, null);
            assertFalse("Unexpected error on insert", errors.hasErrors());

            Map<String, Object> newView = views.getFirst();
            newView.put("flags", 3);
            qus.updateRows(_user, _container, List.of(newView), null, errors, null, null);
            assertFalse("Unexpected error on update", errors.hasErrors());

            // finally, delete the custom view
            qus.deleteRows(_user, _container, List.of(newView), null, null);
        }

        @Test
        public void testQueriesAdminOnlyAccess()
        {
            LOG.info("Validate Query.Queries is admin only");

            var schema = QueryService.get().getUserSchema(User.getAdminServiceUser(), _container, SCHEMA_NAME);
            assertNotNull("Expected admin access to the " + QUERIES_TABLE_NAME + " table", schema.getTable(QUERIES_TABLE_NAME));

            // admin only access to the schema is tested in testCustomViewsAdminOnlyAccess
        }

        private QueryUpdateService ensureUpdateService(String tableName)
        {
            var schema = QueryService.get().getUserSchema(_user, _container, SCHEMA_NAME);
            var table = schema.getTable(tableName);
            var qus = table.getUpdateService();
            assertNotNull("Expected update service for " + tableName, qus);

            return qus;
        }

        @Test
        public void testQueriesApiAccess() throws Exception
        {
            var qus = ensureUpdateService(QUERIES_TABLE_NAME);

            try
            {
                BatchValidationException errors = new BatchValidationException();
                Map<String, Object> row = CaseInsensitiveHashMap.of(
                        "schema", "test",
                        "name", "custom query",
                        "sql", "SELECT * FROM test"
                );
                qus.insertRows(_user, _container, List.of(row), errors, null, null);
                fail("Insert should have thrown UnauthorizedException");
            }
            catch (UnauthorizedException e)
            {
                // expected
            }

            String customQueryName = "custom query";
            var queryDef = QueryService.get().createQueryDef(_user, _container, SchemaKey.fromParts("lists"), customQueryName);
            queryDef.setSql("SELECT * FROM lists");
            queryDef.save(_user, _container, false);

            queryDef = QueryService.get().getQueryDef(_user, _container, "lists", customQueryName);
            assertNotNull("Unable to retrieve a saved query def", queryDef);

            if (queryDef instanceof QueryDefinitionImpl queryImpl)
            {
                BatchValidationException errors = new BatchValidationException();
                Map<String, Object> row = CaseInsensitiveHashMap.of(
                        "queryDefId", queryImpl.getQueryDef().getQueryDefId(),
                        "name", "custom query",
                        "sql", "SELECT * FROM test"
                );

                try
                {
                    qus.updateRows(_user, _container, List.of(row), null, errors, null, null);
                    fail("Update should have thrown UnauthorizedException");
                }
                catch (UnauthorizedException e)
                {
                    // expected, delete the query
                    qus.deleteRows(_user, _container, List.of(row), null, null);
                }
            }
            else
                Assert.fail("Unexpected query def type: " + queryDef.getClass().getName());
        }
    }
}
