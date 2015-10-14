/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.data;

import com.drew.lang.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.queryprofiler.DatabaseQueryListener;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.view.HttpView;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 10/8/2015
 *
 * Listener to log sql queries against dbscopes configured for such. Ignores explicit sql queries used to retrieve database metadata.
 */
public class ScopeQueryLoggingProfilerListener implements DatabaseQueryListener
{
    private final ThreadLocal<Boolean> _reentrancyPreventer = new ThreadLocal<>();

    @Override
    public ContainerContext getEnvironment()
    {
        if (HttpView.hasCurrentView())
        {
            return HttpView.currentContext();
        }
        return ContainerManager.getRoot();
    }

    @Override
    public boolean matches(@Nullable DbScope scope)
    {
        return null != scope && scope.isLogQueries();
    }

    @Override
    public boolean matches(QueryLogging queryLogging)
    {
        return !queryLogging.isMetadataQuery();
    }

    @Override
    public void queryInvoked(DbScope scope, String sql, User user, Container container, @Nullable Object environment, QueryLogging queryLogging)
    {
        if (_reentrancyPreventer.get() != null)
        {
            throw new IllegalStateException("Already in the middle of logging!");
        }

        try
        {
            _reentrancyPreventer.set(Boolean.TRUE);
            QueryLoggingAuditTypeEvent event = new QueryLoggingAuditTypeEvent();
            event.setContainer(null == container ? ContainerManager.getRoot().getId() : container.getId());
            event.setSQL(sql);

            AuditLogService.get().addEvent(user, event);
            if (!queryLogging.isEmpty())
            {
                queryLogging.setSelectQueryAuditEvent(new SelectQueryAuditEvent(queryLogging));
                queryLogging.setQueryId(event.getRowId());
            }
        }
        finally
        {
            _reentrancyPreventer.remove();
        }
    }

    public static class TestCase extends Assert
    {
        @Test  // This doesn't test anything yet, just shows how to get & use a dbscope that will log queries
        public void testLogging() throws Exception
        {
            DbScope loggingScope = makeLoggingScope();

            Map<String, Object> myMap = new SqlSelector(loggingScope, "SELECT 1").getMap();
            // Verify sql is logged as expected.
            // Another test would be that we're not logging MetadataSqlSelector queries against the loggingScope
        }

        protected DbScope makeLoggingScope() throws ServletException, SQLException
        {
            DbScope.DataSourceProperties dsp = new DbScope.DataSourceProperties();
            dsp.setLogQueries(true);
            return new DbScope("testLoggingScope", DbScope.getLabKeyScope().getDataSource(), dsp);
        }
    }
}
