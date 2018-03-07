/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.MothershipReport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static org.labkey.api.security.UserManager.USER_DISPLAY_NAME_COMPARATOR;

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
    private static final ReentrantLock INSERT_EXCEPTION_LOCK = new ReentrantLock();

    private static final Logger log = Logger.getLogger(MothershipManager.class);

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
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }

    public void insertException(ExceptionStackTrace stackTrace, ExceptionReport report)
    {
        // Synchronize to prevent two different threads from creating duplicate rows in the ExceptionStackTrace table
        try (DbScope.Transaction transaction = getSchema().getScope().ensureTransaction(INSERT_EXCEPTION_LOCK))
        {
            boolean isNew = false;
            stackTrace.hashStackTrace();
            ExceptionStackTrace existingStackTrace = getExceptionStackTrace(stackTrace.getStackTraceHash(), stackTrace.getContainer());
            if (existingStackTrace != null)
            {
                stackTrace = existingStackTrace;
            }
            else
            {
                stackTrace = Table.insert(null, getTableInfoExceptionStackTrace(), stackTrace);
                isNew = true;
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

            String errorCode = report.getErrorCode();
            if (null != errorCode && errorCode.length() > MothershipReport.ERROR_CODE_LENGTH)
            {
                report.setErrorCode(errorCode.substring(0, MothershipReport.ERROR_CODE_LENGTH - 1));
            }

            report = Table.insert(null, getTableInfoExceptionReport(), report);
            stackTrace.setInstances(stackTrace.getInstances() + 1);
            stackTrace.setLastReport(report.getCreated());
            if (isNew)
            {
                stackTrace.setFirstReport(report.getCreated());
            }
            Table.update(null, getTableInfoExceptionStackTrace(), stackTrace, stackTrace.getExceptionStackTraceId());

            transaction.commit();
        }
    }

    private static final Object ENSURE_SOFTWARE_RELEASE_LOCK = new Object();

    public SoftwareRelease ensureSoftwareRelease(Container container, Integer svnRevision, String svnURL, String description)
    {
        synchronized (ENSURE_SOFTWARE_RELEASE_LOCK)
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            if (svnRevision == null)
            {
                filter.addCondition(FieldKey.fromString("SVNRevision"), null, CompareType.ISBLANK);
            }
            else
            {
                filter.addCondition(FieldKey.fromString("SVNRevision"), svnRevision);
            }

            if (svnURL == null)
            {
                filter.addCondition(FieldKey.fromString("SVNURL"), null, CompareType.ISBLANK);
            }
            else
            {
                filter.addCondition(FieldKey.fromString("SVNURL"), svnURL);
            }

            if (description == null)
            {
                description = fabricateDescription(svnURL, svnRevision);
            }
            filter.addCondition(FieldKey.fromString("Description"), description);

            SoftwareRelease result = new TableSelector(getTableInfoSoftwareRelease(), filter, null).getObject(SoftwareRelease.class);
            if (result == null)
            {
                result = new SoftwareRelease();
                result.setSVNRevision(svnRevision);
                result.setSVNURL(svnURL);
                result.setContainer(container.getId());
                result.setDescription(description);
                result = Table.insert(null, getTableInfoSoftwareRelease(), result);
            }
            return result;
        }
    }

    private String fabricateDescription(String svnURL, Integer svnRevision)
    {
        String description = null;

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
                description = description != null ? description : "UnknownSVN";
            }
        }
        else
        {
            description = description != null ? description : "NotSVN";
        }

        return description;
    }

    public ServerInstallation getServerInstallation(String serverGUID, Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromString("ServerInstallationGUID"), serverGUID);
        return new TableSelector(getTableInfoServerInstallation(), filter, null).getObject(ServerInstallation.class);
    }

    public ServerSession getServerSession(String serverSessionGUID, Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromString("ServerSessionGUID"), serverSessionGUID);
        return new TableSelector(getTableInfoServerSession(), filter, null).getObject(ServerSession.class);
    }

    public ExceptionStackTrace getExceptionStackTrace(String stackTraceHash, String containerId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), containerId);
        filter.addCondition(FieldKey.fromString("StackTraceHash"), stackTraceHash);
        return new TableSelector(getTableInfoExceptionStackTrace(), filter, null).getObject(ExceptionStackTrace.class);
    }

    public ExceptionStackTrace getExceptionStackTrace(int exceptionStackTraceId, Container container)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromString("ExceptionStackTraceId"), exceptionStackTraceId);
        return new TableSelector(getTableInfoExceptionStackTrace(), filter, null).getObject(ExceptionStackTrace.class);
    }

    public void deleteForContainer(Container c)
    {
        SqlExecutor sqlExecutor = new SqlExecutor(getSchema());
        sqlExecutor.execute("DELETE FROM " + getTableInfoExceptionReport() + " WHERE ExceptionStackTraceId IN (SELECT ExceptionStackTraceId FROM " + getTableInfoExceptionStackTrace() + " WHERE Container = ?)", c);
        sqlExecutor.execute("DELETE FROM " + getTableInfoExceptionStackTrace() + " WHERE Container = ?", c);
        sqlExecutor.execute("DELETE FROM " + getTableInfoServerSession() + " WHERE Container = ?", c);
        sqlExecutor.execute("DELETE FROM " + getTableInfoServerInstallation() + " WHERE Container = ?", c);
        sqlExecutor.execute("DELETE FROM " + getTableInfoSoftwareRelease() + " WHERE Container = ?", c);
    }

    public void deleteForUser(User u)
    {
       SqlExecutor sqlExecutor = new SqlExecutor(getSchema());
       sqlExecutor.execute("UPDATE " + getTableInfoExceptionStackTrace() + " SET AssignedTo = NULL WHERE AssignedTo = ?", u.getUserId());
       sqlExecutor.execute("UPDATE " + getTableInfoExceptionStackTrace() + " SET ModifiedBy = NULL WHERE ModifiedBy = ?", u.getUserId());
    }

    public synchronized ServerSession updateServerSession(@Nullable String hostName, ServerSession session, ServerInstallation installation, Container container)
    {
        try (DbScope.Transaction transaction = getSchema().getScope().ensureTransaction())
        {
            ServerInstallation existingInstallation = getServerInstallation(installation.getServerInstallationGUID(), container);

            if (null == hostName || MothershipReport.BORING_HOSTNAMES.contains(hostName))
            {
                try
                {
                    hostName = InetAddress.getByName(installation.getServerIP()).getCanonicalHostName();
                }
                catch (UnknownHostException e)
                {
                    // That's OK, not a big deal
                }
            }
            else
            {
                hostName = StringUtils.left(hostName, 256);
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
                if (installation.getUsedInstaller())
                {
                    // The existing installation may have been an upgrade from an earlier version before we started recording usage of the installer
                    existingInstallation.setUsedInstaller(true);
                }
                installation = Table.update(null, getTableInfoServerInstallation(), existingInstallation,  existingInstallation.getServerInstallationId());
            }

            Date now = new Date();
            ServerSession existingSession = getServerSession(session.getServerSessionGUID(), container);
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
                existingSession.setDistribution(getBestString(existingSession.getDistribution(), session.getDistribution()));
                existingSession.setUsageReportingLevel(getBestString(existingSession.getUsageReportingLevel(), session.getUsageReportingLevel()));
                existingSession.setExceptionReportingLevel(getBestString(existingSession.getExceptionReportingLevel(), session.getExceptionReportingLevel()));
                existingSession.setJsonMetrics(getBestJson(existingSession.getJsonMetrics(), session.getJsonMetrics(), existingSession.getServerSessionGUID()));

                session = Table.update(null, getTableInfoServerSession(), existingSession, existingSession.getServerSessionId());
            }

            transaction.commit();
            return session;
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

    private String getBestJson(String currentValue, String newValue, String serverSessionGUID)
    {
        if (StringUtils.isEmpty(newValue))
        {
            return currentValue;
        }
        else if (StringUtils.isEmpty(currentValue))
        {
            // Verify the newValue as valid json; if it is, return it. Otherwise, return null.
            try
            {
                new ObjectMapper().readTree(newValue);
                return newValue;
            }
            catch (IOException e)
            {
                logJsonError(newValue, serverSessionGUID, e);
                return null;
            }
        }

        // Rather than overwrite the current json map, merge the new with the current.
        ObjectMapper mapper = new ObjectMapper();
        try
        {
            Map currentMap = mapper.readValue(currentValue, Map.class);
            ObjectReader updater = mapper.readerForUpdating(currentMap);
            Map merged = updater.readValue(newValue);
            return mapper.writeValueAsString(merged);
        }
        catch (IOException e)
        {
            logJsonError(newValue, serverSessionGUID, e);
            return currentValue;
        }
    }

    private void logJsonError(String newValue, String serverSessionGUID, Exception e)
    {
        log.error("Malformed json in mothership report from server session '"+serverSessionGUID + "': " + newValue, e);
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
        props.save();
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

    public void updateExceptionStackTrace(ExceptionStackTrace stackTrace, User user)
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

    public void insertSoftwareRelease(Container container, User user, SoftwareRelease bean)
    {
        bean.setContainer(container.getId());
        Table.insert(user, getTableInfoSoftwareRelease(), bean);
    }

    public void deleteSoftwareRelease(Container container, int i)
    {
        Filter filter = SimpleFilter.createContainerFilter(container).addCondition(FieldKey.fromParts("ReleaseId"), i);
        Table.delete(getTableInfoSoftwareRelease(), filter);
    }

    public SoftwareRelease updateSoftwareRelease(Container container, User user, SoftwareRelease bean)
    {
        bean.setContainer(container.getId());
        return Table.update(user, getTableInfoSoftwareRelease(), bean, bean.getSoftwareReleaseId());
    }

    public Collection<ServerInstallation> getServerInstallationsActiveOn(Calendar cal)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT si.* FROM ");
        sql.append(getTableInfoServerInstallation(), "si");
        sql.append(" WHERE si.serverinstallationid IN (SELECT serverinstallationid FROM ");
        sql.append(getTableInfoServerSession(), "ss");
        sql.append(" WHERE earliestknowntime <= ? AND lastknowntime >= ?)");
        sql.add(cal.getTime());
        sql.add(cal.getTime());

        return new SqlSelector(getSchema(), sql).getCollection(ServerInstallation.class);
    }

    public Collection<ServerInstallation> getServerInstallationsActiveBefore(Calendar cal)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT si.* FROM ");
        sql.append(getTableInfoServerInstallation(), "si");
        sql.append(" WHERE si.serverinstallationid IN (SELECT serverinstallationid FROM ");
        sql.append(getTableInfoServerSession(), "ss");
        sql.append(" WHERE earliestknowntime <= ?)");
        sql.add(cal.getTime());

        return new SqlSelector(getSchema(), sql).getCollection(ServerInstallation.class);
    }

    public ServerInstallation getServerInstallation(int id, Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromString("ServerInstallationId"), id);
        return new TableSelector(getTableInfoServerInstallation(), filter, null).getObject(ServerInstallation.class);
    }

    public List<User> getAssignedToList(Container container)
    {
        List<User> projectUsers = org.labkey.api.security.SecurityManager.getProjectUsers(container.getProject());
        List<User> list = new ArrayList<>();
        // Filter list to only show active users
        for (User user : projectUsers)
        {
            if (user.isActive())
            {
                list.add(user);
            }
        }
        list.sort(USER_DISPLAY_NAME_COMPARATOR);
        return list;
    }
}
