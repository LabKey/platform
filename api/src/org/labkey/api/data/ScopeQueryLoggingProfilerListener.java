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
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.data.queryprofiler.DatabaseQueryListener;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.view.HttpView;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

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
    public String getEnvironment()
    {
        return "hello"; // Nothing special is about the context is needed here; user and container are already passed in the call to query invoked.
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
        @Test
        public void testNormalQueryLogging() throws Exception
        {
            final String SQL = "SELECT 1";
            Date now = new Date();
            new SqlSelector(makeLoggingScope(), SQL).getMap();
            assertTrue("SQL wasn't logged", isSqlLogged(SQL, now));
        }

        @Test
        public void testMetadataQueryLogging() throws Exception
        {
            // The sql is arbitrary. The use of MetadataSqlSelector flags it as a metadata retrieving query
            final String METADATA_SQL = "SELECT 'metadata'";
            Date now = new Date();
            new MetadataSqlSelector(makeLoggingScope(), METADATA_SQL).getMap();
            assertFalse("Metadata SQL was logged", isSqlLogged(METADATA_SQL, now));
        }

        private boolean isSqlLogged(String sql, Date start)
        {
            Container c = HttpView.currentView().getViewContext().getContainer();
            User u = HttpView.currentView().getViewContext().getUser();
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("created"), start, CompareType.GT);
            filter.addCondition(FieldKey.fromParts("SQL"), sql);
            List<AuditTypeEvent> events = AuditLogService.get().getAuditEvents(c, u, QueryLoggingAuditTypeProvider.EVENT_NAME, filter, null);

            return !events.isEmpty();
        }

        private DbScope makeLoggingScope() throws ServletException, SQLException
        {
            DbScope.DataSourceProperties dsp = new DbScope.DataSourceProperties();
            dsp.setLogQueries(true);
            return new DbScope("testLoggingScope", DbScope.getLabKeyScope().getDataSource(), dsp);
        }
    }
}
