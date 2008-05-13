/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.mothership;

import org.labkey.api.data.*;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * User: jeckels
 * Date: Apr 20, 2006
 */
public class MothershipManager
{
    private static final MothershipManager INSTANCE = new MothershipManager();
    private static final String SCHEMA_NAME = "mothership";
    private static final String UPGRADE_MESSAGE_PROPERTY_CATEGORY = "upgradeMessage";
    private static final String CURRENT_REVISION_PROP = "currentRevision";
    private static final String UPGRADE_MESSAGE_PROP = "upgradeMessage";
    private static final String CREATE_ISSUE_URL_PROP = "createIssueURL";
    private static final String ISSUES_CONTAINER_PROP = "issuesContainer";

    public static MothershipManager get()
    {
        return INSTANCE;
    }

    private MothershipManager() {}

    String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    /* package */
    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public void insertException(ExceptionStackTrace stackTrace, ExceptionReport report) throws SQLException
    {
        DbScope scope = getSchema().getScope();
        scope.beginTransaction();
        try
        {
            stackTrace.hashStackTrace();
            ExceptionStackTrace existingStackTrace = getExceptionStackTrace(stackTrace.getStackTraceHash(), stackTrace.getContainer());
            if (existingStackTrace != null)
            {
                stackTrace = existingStackTrace;
            }
            else
            {
                stackTrace = Table.insert(null, getTableInfoExceptionStackTrace(), stackTrace);
            }

            report.setExceptionStackTraceId(stackTrace.getExceptionStackTraceId());

            String url = report.getUrl();
            if (null != url && url.length() > 512)
                report.setURL(url.substring(0,500) + "...");

            String browser = report.getBrowser();
            if (null != browser && browser.length() > 100)
                report.setBrowser(browser.substring(0,90) + "...");

            Table.insert(null, getTableInfoExceptionReport(), report);
            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public ServerInstallation getServerInstallation(String serverGUID, String containerId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ServerInstallationGUID", serverGUID);
        filter.addCondition("Container", containerId);
        return Table.selectObject(getTableInfoServerInstallation(), filter, null, ServerInstallation.class);
    }

    public ServerSession getServerSession(String serverSessionGUID, String containerId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ServerSessionGUID", serverSessionGUID);
        filter.addCondition("Container", containerId);
        return Table.selectObject(getTableInfoServerSession(), filter, null, ServerSession.class);
    }

    public ExceptionStackTrace getExceptionStackTrace(String stackTraceHash, String containerId)
            throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("StackTraceHash", stackTraceHash);
        filter.addCondition("Container", containerId);
        return Table.selectObject(getTableInfoExceptionStackTrace(), filter, null, ExceptionStackTrace.class);
    }

    public ExceptionStackTrace getExceptionStackTrace(int exceptionStackTraceId, Container container) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ExceptionStackTraceId", exceptionStackTraceId);
        filter.addCondition("Container", container.getId());
        return Table.selectObject(getTableInfoExceptionStackTrace(), filter, null, ExceptionStackTrace.class);
    }

    public void deleteForContainer(Container c) throws SQLException
    {
        Object[] params = { c };
        Table.execute(getSchema(), "DELETE FROM " + getTableInfoExceptionReport() + " WHERE ExceptionStackTraceId IN (SELECT ExceptionStackTraceId FROM " + getTableInfoExceptionStackTrace() + " WHERE Container = ?)", params);
        Table.execute(getSchema(), "DELETE FROM " + getTableInfoExceptionStackTrace() + " WHERE Container = ?", params);
        Table.execute(getSchema(), "DELETE FROM " + getTableInfoServerSession() + " WHERE Container = ?", params);
        Table.execute(getSchema(), "DELETE FROM " + getTableInfoServerInstallation() + " WHERE Container = ?", params);
    }

    public synchronized ServerSession updateServerSession(ServerSession session, ServerInstallation installation, Container container) throws SQLException
    {
        DbScope scope = getSchema().getScope();
        scope.beginTransaction();
        try
        {
            ServerInstallation existingInstallation = getServerInstallation(installation.getServerInstallationGUID(), container.getId());

            String hostName = null;
            try
            {
                hostName = InetAddress.getByName(installation.getServerIP()).getCanonicalHostName();
            }
            catch (UnknownHostException e)
            {
                // That's OK, not a big deal
            }

            if (existingInstallation == null)
            {
                installation.setContainer(container.getId());
                installation.setServerHostName(hostName);
                installation = Table.insert(null, getTableInfoServerInstallation(), installation);
            }
            else
            {
                existingInstallation.setLogoLink(getBestString(existingInstallation.getLogoLink(), installation.getLogoLink()));
                existingInstallation.setOrganizationName(getBestString(existingInstallation.getOrganizationName(), installation.getOrganizationName()));
                existingInstallation.setServerIP(installation.getServerIP());
                existingInstallation.setServerHostName(hostName);
                existingInstallation.setSystemDescription(getBestString(existingInstallation.getSystemDescription(), installation.getSystemDescription()));
                installation = Table.update(null, getTableInfoServerInstallation(), existingInstallation,  existingInstallation.getServerInstallationId(), null);
            }

            Date now = new Date();
            ServerSession existingSession = getServerSession(session.getServerSessionGUID(), container.getId());
            if (existingSession == null)
            {
                session.setEarliestKnownTime(now);
                session.setLastKnownTime(now);
                session.setServerInstallationId(installation.getServerInstallationId());
                session = Table.insert(null, getTableInfoServerSession(), session);
            }
            else
            {
                existingSession.setLastKnownTime(now);
                existingSession.setContainerCount(session.getContainerCount());
                existingSession.setProjectCount(session.getProjectCount());
                existingSession.setActiveUserCount(session.getActiveUserCount());
                existingSession.setUserCount(session.getUserCount());
                existingSession.setAdministratorEmail(session.getAdministratorEmail());
                existingSession.setEnterprisePipelineEnabled(session.isEnterprisePipelineEnabled());
                existingSession.setLdapEnabled(session.isLdapEnabled());

                session = Table.update(null, getTableInfoServerSession(), existingSession, existingSession.getServerSessionId(), null);
            }

            scope.commitTransaction();
            return session;
        }
        finally
        {
            scope.closeConnection();
        }
    }

    private String getBestString(String currentValue, String newValue)
    {
        if (newValue == null || newValue.equals(""))
        {
            return currentValue;
        }
        return newValue;
    }

    public TableInfo getTableInfoExceptionStackTrace()
    {
        return getSchema().getTable("ExceptionStackTrace");
    }

    public TableInfo getTableInfoExceptionReport()
    {
        return getSchema().getTable("ExceptionReport");
    }

    public TableInfo getTableInfoSoftwareRelease()
    {
        return getSchema().getTable("SoftwareRelease");
    }

    public TableInfo getTableInfoServerSession()
    {
        return getSchema().getTable("ServerSession");
    }

    public TableInfo getTableInfoServerInstallationWithSession()
    {
        return getSchema().getTable("ServerInstallationWithSession");
    }

    public TableInfo getTableInfoServerInstallation()
    {
        return getSchema().getTable("ServerInstallation");
    }

    public SqlDialect getDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoExceptionSummary()
    {
        return getSchema().getTable("ExceptionSummary");
    }

    public TableInfo getTableInfoExceptionReportSummary()
    {
        return getSchema().getTable("ExceptionReportSummary");
    }


    private PropertyManager.PropertyMap getWritableProperties(Container c) throws SQLException
    {
        return PropertyManager.getWritableProperties(0, c.getId(), UPGRADE_MESSAGE_PROPERTY_CATEGORY, true);
    }

    private Map getProperties(Container c) throws SQLException
    {
        return PropertyManager.getProperties(0, c.getId(), UPGRADE_MESSAGE_PROPERTY_CATEGORY, true);
    }

    public int getCurrentRevision(Container c) throws SQLException
    {
        Map props = getProperties(c);
        String rev = (String)props.get(CURRENT_REVISION_PROP);
        if (rev == null)
        {
            return 0;
        }
        return Integer.parseInt(rev);
    }

    private String getStringProperty(Container c, String name) throws SQLException
    {
        Map props = getProperties(c);
        String message = (String)props.get(name);
        if (message == null)
        {
            return "";
        }
        return message;
    }

    public String getUpgradeMessage(Container c) throws SQLException
    {
        return getStringProperty(c, UPGRADE_MESSAGE_PROP);
    }

    private void saveProperty(Container c, String name, String value) throws SQLException
    {
        PropertyManager.PropertyMap props = getWritableProperties(c);
        props.put(name, value);
        PropertyManager.saveProperties(props);
    }

    public void setCurrentRevision(Container c, int revision) throws SQLException
    {
        saveProperty(c, CURRENT_REVISION_PROP, String.valueOf(revision));
    }

    public void setUpgradeMessage(Container c, String message) throws SQLException
    {
        saveProperty(c, UPGRADE_MESSAGE_PROP, message);
    }

    public String getCreateIssueURL(Container c) throws SQLException
    {
        return getStringProperty(c, CREATE_ISSUE_URL_PROP);
    }

    public void setCreateIssueURL(Container c, String url) throws SQLException
    {
        saveProperty(c, CREATE_ISSUE_URL_PROP, url);
    }

    public void updateExceptionStackTrace(ExceptionStackTrace stackTrace, User user) throws SQLException
    {
        Table.update(user, getTableInfoExceptionStackTrace(), stackTrace, stackTrace.getExceptionStackTraceId(), null);
    }

    public String getIssuesContainer(Container c) throws SQLException
    {
        return getStringProperty(c, ISSUES_CONTAINER_PROP);
    }

    public void setIssuesContainer(Container c, String container) throws SQLException
    {
        saveProperty(c, ISSUES_CONTAINER_PROP, container);
    }

    public void insertSoftwareRelease(Container container, User user, SoftwareRelease bean) throws SQLException
    {
        bean.setContainer(container.getId());
        Table.insert(user, getTableInfoSoftwareRelease(), bean);
    }

    public void deleteSoftwareRelease(Container container, int i) throws SQLException
    {
        Filter filter = new SimpleFilter("Container", container.getId()).addCondition("ReleaseId", i);
        Table.delete(getTableInfoSoftwareRelease(), filter);
    }

    public SoftwareRelease updateSoftwareRelease(Container container, User user, SoftwareRelease bean) throws SQLException
    {
        bean.setContainer(container.getId());
        return Table.update(user, getTableInfoSoftwareRelease(), bean, bean.getReleaseId(), null);
    }

    public ServerInstallation[] getServerInstallationsActiveOn(Calendar cal) throws SQLException
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT si.* FROM ");
        sql.append(getTableInfoServerInstallation());
        sql.append(" si WHERE si.serverinstallationid IN (SELECT serverinstallationid FROM ");
        sql.append(getTableInfoServerSession());
        sql.append(" WHERE earliestknowntime <= ? AND lastknowntime >= ?)");
        sql.add(cal.getTime());
        sql.add(cal.getTime());

        return Table.executeQuery(getSchema(), sql.toString(), sql.getParamsArray(), ServerInstallation.class);
    }

    public ServerInstallation[] getServerInstallationsActiveBefore(Calendar cal) throws SQLException
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT si.* FROM ");
        sql.append(getTableInfoServerInstallation());
        sql.append(" si WHERE si.serverinstallationid IN (SELECT serverinstallationid FROM ");
        sql.append(getTableInfoServerSession());
        sql.append(" WHERE earliestknowntime <= ?)");
        sql.add(cal.getTime());

        return Table.executeQuery(getSchema(), sql.toString(), sql.getParamsArray(), ServerInstallation.class);
    }

    public ServerInstallation getServerInstallation(int id, String containerId)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("ServerInstallationId", id);
            filter.addCondition("Container", containerId);
            return Table.selectObject(getTableInfoServerInstallation(), filter, null, ServerInstallation.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
}
