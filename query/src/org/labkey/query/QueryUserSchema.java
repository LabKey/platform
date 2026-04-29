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
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.query.query.CustomViewsTable;
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

    static public void register(final Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new QueryUserSchema(schema.getUser(), schema.getContainer());
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
            names.add(CUSTOM_VIEWS_TABLE_NAME);

        return names;
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        if (CUSTOM_VIEWS_TABLE_NAME.equalsIgnoreCase(name) && getContainer().hasPermission(getUser(), AdminPermission.class))
            return new CustomViewsTable(this, cf);

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
        public void testCustomViewsAdminOnlyAccess() throws Exception
        {
            LOG.info("Validate Query.CustomViews is admin only");

            var schema = QueryService.get().getUserSchema(User.getAdminServiceUser(), _container, SCHEMA_NAME);
            assertNotNull("Expected admin access to the " + CUSTOM_VIEWS_TABLE_NAME + " table", schema.getTable(CUSTOM_VIEWS_TABLE_NAME));

            schema = QueryService.get().getUserSchema(User.getSearchUser(), _container, SCHEMA_NAME);
            assertNull("Expected no reader access to the " + CUSTOM_VIEWS_TABLE_NAME + " table", schema.getTable(CUSTOM_VIEWS_TABLE_NAME));
        }

        @Test
        public void testCustomViewsApiAccess() throws Exception
        {
            var schema = QueryService.get().getUserSchema(_user, _container, SCHEMA_NAME);
            var table = schema.getTable(CUSTOM_VIEWS_TABLE_NAME);
            var qus = table.getUpdateService();
            assertNotNull("Expected update service for " + CUSTOM_VIEWS_TABLE_NAME, qus);

            BatchValidationException errors = new BatchValidationException();
            try
            {
                Map<String, Object> row = CaseInsensitiveHashMap.of(
                        "schemaName", "test",
                        "queryName", "query"
                );
                qus.insertRows(_user, _container, List.of(row), errors, null, null);
                assertTrue("Expected insert to error", errors.hasErrors());
            }
            catch (UnsupportedOperationException e)
            {
                // expected
            }

            try
            {
                Map<String, Object> row = CaseInsensitiveHashMap.of(
                        "customViewId", 1,
                        "schemaName", "test",
                        "queryName", "query"
                );
                qus.updateRows(_user, _container, List.of(row), null, errors, null, null);
                assertTrue("Expected update to error", errors.hasErrors());
            }
            catch (UnsupportedOperationException e)
            {
                // expected
            }
        }
    }
}
