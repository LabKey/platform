/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.security.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
    private static final Object INSERT_EXCEPTION_LOCK = new Object();

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
        // Synchronize to prevent two different threads from creating duplicate rows in the ExceptionStackTrace table
        synchronized (INSERT_EXCEPTION_LOCK)
        {
            DbScope scope = getSchema().getScope();
            scope.ensureTransaction();
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
                    report.setURL(url.substring(0, 506) + "...");

                String referrerURL = report.getReferrerURL();
                if (null != referrerURL && referrerURL.length() > 512)
                    report.setReferrerURL(referrerURL.substring(0, 506) + "...");

                String browser = report.getBrowser();
                if (null != browser && browser.length() > 100)
                    report.setBrowser(browser.substring(0,90) + "...");

                String exceptionMessage = report.getExceptionMessage();
                if (null != exceptionMessage && exceptionMessage.length() > 1000)
                    report.setExceptionMessage(exceptionMessage.substring(0,990) + "...");

                String actionName = report.getPageflowAction();
                if (null != actionName && actionName.length() > 40)
                {
                    report.setPageflowAction(actionName.substring(0, 39));
                }

                String controllerName = report.getPageflowName();
                if (null != controllerName && controllerName.length() > 30)
                {
                    report.setPageflowName(controllerName.substring(0, 29));
                }

                Table.insert(null, getTableInfoExceptionReport(), report);
                scope.commitTransaction();
            }
            finally
            {
                scope.closeConnection();
            }
        }
    }

    private static final Object ENSURE_SOFTWARE_RELEASE_LOCK = new Object();

    public SoftwareRelease ensureSoftwareRelease(Container container, Integer svnRevision, String svnURL)
    {
        synchronized (ENSURE_SOFTWARE_RELEASE_LOCK)
        {
            try
            {
                SimpleFilter filter = new SimpleFilter();
                if (svnRevision == null)
                {
                    filter.addCondition("SVNRevision", null, CompareType.ISBLANK);
                }
                else
                {
                    filter.addCondition("SVNRevision", svnRevision);
                }

                if (svnURL == null)
                {
                    filter.addCondition("SVNURL", null, CompareType.ISBLANK);
                }
                else
                {
                    filter.addCondition("SVNURL", svnURL);
                }
                filter.addCondition("Container", container.getId());
                SoftwareRelease result = Table.selectObject(getTableInfoSoftwareRelease(), filter, null, SoftwareRelease.class);
                if (result == null)
                {
                    result = new SoftwareRelease();
                    result.setSVNRevision(svnRevision);
                    result.setSVNURL(svnURL);
                    result.setContainer(container.getId());
                    String description;
                    if (svnURL != null)
                    {
                        if (svnURL.startsWith("https://hedgehog.fhcrc.org/tor/stedi/"))
                        {
                            description = svnURL.substring("https://hedgehog.fhcrc.org/tor/stedi/".length());
                            if (description.endsWith("/server"))
                            {
                                description = description.substring(0, description.length() - "/server".length());
                            }
                            if (description.startsWith("branches/"))
                            {
                                description = description.substring("branches/".length());
                            }
                            if (svnRevision != null)
                            {
                                description = description + " - " + svnRevision;
                            }
                        }
                        else
                        {
                            description = "UnknownSVN";
                        }
                    }
                    else
                    {
                        description = "NotSVN";
                    }
                    result.setDescription(description);
                    result = Table.insert(null, getTableInfoSoftwareRelease(), result);
                }
                return result;

            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
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
        Table.execute(getSchema(), "DELETE FROM " + getTableInfoSoftwareRelease() + " WHERE Container = ?", params);
    }

    public synchronized ServerSession updateServerSession(ServerSession session, ServerInstallation installation, Container container) throws SQLException
    {
        DbScope scope = getSchema().getScope();
        scope.ensureTransaction();
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
                installation = Table.update(null, getTableInfoServerInstallation(), existingInstallation,  existingInstallation.getServerInstallationId());
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
                existingSession.setContainerCount(getBestInteger(existingSession.getContainerCount(), session.getContainerCount()));
                existingSession.setProjectCount(getBestInteger(existingSession.getProjectCount(), session.getProjectCount()));
                existingSession.setActiveUserCount(getBestInteger(existingSession.getActiveUserCount(), session.getActiveUserCount()));
                existingSession.setUserCount(getBestInteger(existingSession.getUserCount(), session.getUserCount()));
                existingSession.setAdministratorEmail(getBestString(existingSession.getAdministratorEmail(), session.getAdministratorEmail()));
                existingSession.setEnterprisePipelineEnabled(getBestBoolean(existingSession.isEnterprisePipelineEnabled(), session.isEnterprisePipelineEnabled()));

                session = Table.update(null, getTableInfoServerSession(), existingSession, existingSession.getServerSessionId());
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

    private Integer getBestInteger(Integer currentValue, Integer newValue)
    {
        if (newValue == null)
        {
            return currentValue;
        }
        return newValue;
    }

    private Boolean getBestBoolean(Boolean currentValue, Boolean newValue)
    {
        if (newValue == null)
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

    public TableInfo getTableInfoServerInstallation()
    {
        return getSchema().getTable("ServerInstallation");
    }

    public SqlDialect getDialect()
    {
        return getSchema().getSqlDialect();
    }

    private PropertyManager.PropertyMap getWritableProperties(Container c)
    {
        return PropertyManager.getWritableProperties(c, UPGRADE_MESSAGE_PROPERTY_CATEGORY, true);
    }

    private @NotNull Map<String, String> getProperties(Container c)
    {
        return PropertyManager.getProperties(c, UPGRADE_MESSAGE_PROPERTY_CATEGORY);
    }

    public int getCurrentRevision(Container c)
    {
        Map<String, String> props = getProperties(c);
        String rev = props.get(CURRENT_REVISION_PROP);
        if (rev == null)
        {
            return 0;
        }
        return Integer.parseInt(rev);
    }

    private String getStringProperty(Container c, String name)
    {
        Map<String, String> props = getProperties(c);
        String message = props.get(name);
        if (message == null)
        {
            return "";
        }
        return message;
    }

    public String getUpgradeMessage(Container c)
    {
        return getStringProperty(c, UPGRADE_MESSAGE_PROP);
    }

    private void saveProperty(Container c, String name, String value)
    {
        PropertyManager.PropertyMap props = getWritableProperties(c);
        props.put(name, value);
        PropertyManager.saveProperties(props);
    }

    public void setCurrentRevision(Container c, int revision)
    {
        saveProperty(c, CURRENT_REVISION_PROP, String.valueOf(revision));
    }

    public void setUpgradeMessage(Container c, String message)
    {
        saveProperty(c, UPGRADE_MESSAGE_PROP, message);
    }

    public String getCreateIssueURL(Container c)
    {
        return getStringProperty(c, CREATE_ISSUE_URL_PROP);
    }

    public void setCreateIssueURL(Container c, String url)
    {
        saveProperty(c, CREATE_ISSUE_URL_PROP, url);
    }

    public void updateExceptionStackTrace(ExceptionStackTrace stackTrace, User user) throws SQLException
    {
        Table.update(user, getTableInfoExceptionStackTrace(), stackTrace, stackTrace.getExceptionStackTraceId());
    }

    public String getIssuesContainer(Container c)
    {
        return getStringProperty(c, ISSUES_CONTAINER_PROP);
    }

    public void setIssuesContainer(Container c, String container)
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
        return Table.update(user, getTableInfoSoftwareRelease(), bean, bean.getSoftwareReleaseId());
    }

    public ServerInstallation[] getServerInstallationsActiveOn(Calendar cal) throws SQLException
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT si.* FROM ");
        sql.append(getTableInfoServerInstallation(), "si");
        sql.append(" WHERE si.serverinstallationid IN (SELECT serverinstallationid FROM ");
        sql.append(getTableInfoServerSession(), "ss");
        sql.append(" WHERE earliestknowntime <= ? AND lastknowntime >= ?)");
        sql.add(cal.getTime());
        sql.add(cal.getTime());

        return Table.executeQuery(getSchema(), sql, ServerInstallation.class);
    }

    public ServerInstallation[] getServerInstallationsActiveBefore(Calendar cal) throws SQLException
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT si.* FROM ");
        sql.append(getTableInfoServerInstallation(), "si");
        sql.append(" WHERE si.serverinstallationid IN (SELECT serverinstallationid FROM ");
        sql.append(getTableInfoServerSession());
        sql.append(" WHERE earliestknowntime <= ?)");
        sql.add(cal.getTime());

        return Table.executeQuery(getSchema(), sql, ServerInstallation.class);
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

    public List<User> getAssignedToList(Container container)
    {
        List<User> projectUsers = org.labkey.api.security.SecurityManager.getProjectUsers(container.getProject());
        List<User> list = new ArrayList<User>();
        // Filter list to only show active users
        for (User user : projectUsers)
        {
            if (user.isActive())
            {
                list.add(user);
            }
        }
        Collections.sort(list, new UserDisplayNameComparator());
        return list;
    }
}
