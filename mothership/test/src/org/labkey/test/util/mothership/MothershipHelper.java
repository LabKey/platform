/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
package org.labkey.test.util.mothership;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.remoteapi.Command;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.PostCommand;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.remoteapi.query.Sort;
import org.labkey.remoteapi.query.UpdateRowsCommand;
import org.labkey.test.LabKeySiteWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.pages.core.admin.CustomizeSitePage;
import org.labkey.test.pages.core.admin.logger.ManagerPage;
import org.labkey.test.pages.mothership.ShowInstallationsPage;
import org.labkey.test.pages.test.TestActions;
import org.labkey.test.util.APIUserHelper;
import org.labkey.test.util.Log4jUtils;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.labkey.test.util.Maps;
import org.labkey.test.util.TestLogger;
import org.openqa.selenium.NotFoundException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WrapsDriver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MothershipHelper extends LabKeySiteWrapper
{
    public static final String ID_COLUMN = "ExceptionStackTraceId";
    public static final String SERVER_INSTALLATION_ID_COLUMN = "ServerInstallationId";
    public static final String SERVER_INSTALLATION_NAME_COLUMN = "ServerHostName";
    public static final String SERVER_INSTALLATION_QUERY = "ServerInstallation";
    public static final String MOTHERSHIP_PROJECT = "_mothership";
    public static final String MOTHERSHIP_CONTROLLER = "mothership";
    public static final String HOST_NAME = "localhost"; //org.labkey.api.util.MothershipReport.Target.local
    public static final String TEST_HOST_NAME = "TEST_localhost"; //org.labkey.api.util.MothershipReport.Target.test

    private boolean selfReportingEnabled;

    private final WrapsDriver _driver;

    public MothershipHelper(WrapsDriver driver)
    {
        _driver = driver;
        selfReportingEnabled = false;
    }

    @Override
    public WebDriver getWrappedDriver()
    {
        return _driver.getWrappedDriver();
    }

    public void enableDebugLoggers()
    {
        Log4jUtils.setLogLevel("org.labkey.mothership", ManagerPage.LoggingLevel.DEBUG);
        Log4jUtils.setLogLevel("org.labkey.api.util.MothershipReport", ManagerPage.LoggingLevel.DEBUG);
    }

    public Integer getServerInstallationId(String hostName) throws IOException, CommandException
    {
        Map<String, Object> installationInfo = getServerInstallationInfo(hostName);
        return (Integer) installationInfo.get(SERVER_INSTALLATION_ID_COLUMN);
    }

    public Date getLastPing(String hostName) throws IOException, CommandException
    {
        try
        {
            Map<String, Object> installationInfo = getServerInstallationInfo(hostName);
            return (Date) installationInfo.get("LastPing");
        }
        catch (NotFoundException ignore)
        {
            return null;
        }
    }

    public Map<String, Object> getLatestServerInfo() throws IOException, CommandException
    {
        return getServerInstallationInfo(null);
    }

    private Map<String, Object> getServerInstallationInfo(String hostName) throws IOException, CommandException
    {
        SelectRowsCommand selectRows = new SelectRowsCommand(MOTHERSHIP_CONTROLLER, SERVER_INSTALLATION_QUERY);
        if (hostName != null)
        {
            selectRows.addFilter(new Filter(SERVER_INSTALLATION_NAME_COLUMN, hostName));
        }
        selectRows.addSort("LastPing", Sort.Direction.DESCENDING);
        selectRows.setColumns(List.of("*"));
        selectRows.setMaxRows(1);
        SelectRowsResponse response = selectRows.execute(createDefaultConnection(), MOTHERSHIP_PROJECT);
        if (response.getRows().size() != 1)
        {
            ShowInstallationsPage installationsPage = ShowInstallationsPage.beginAt(this);
            if (hostName == null)
            {
                throw new NotFoundException("No mothership server info available.");
            }
            else
            {
                List<String> hostNames = installationsPage.getInstallationGrid().getColumnDataAsText(SERVER_INSTALLATION_NAME_COLUMN);
                throw new NotFoundException(String.format("Unable to find server installation [%s]. Found: %s", hostName, hostNames));
            }
        }
        return response.getRows().get(0);
    }

    public int getLatestStackTraceId()
    {
        Connection connection = createDefaultConnection();
        SelectRowsCommand command = new SelectRowsCommand("mothership", "ExceptionStackTrace");
        command.addSort("LastReport", Sort.Direction.DESCENDING);
        command.setMaxRows(1);
        try
        {
            SelectRowsResponse response = command.execute(connection, MOTHERSHIP_PROJECT);
            if (response.getRows().isEmpty())
                return 0;
            return (Integer) response.getRows().get(0).get(ID_COLUMN);
        }
        catch (IOException|CommandException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void resetStackTrace(int exceptionStackTraceId)
    {
        updateStackTrace(exceptionStackTraceId, "", "", "");
    }

    public void updateStackTrace(int exceptionStackTraceId, String bugNumber, String comments, String assignedToEmail)
    {
        Connection connection = createDefaultConnection();
        PostCommand command = new PostCommand("mothership", "updateStackTrace");
        Map<String, Object> params = new HashMap<>();
        params.put(ID_COLUMN, exceptionStackTraceId);
        if (bugNumber != null)
            params.put("bugNumber", bugNumber);
        if (comments != null)
            params.put("comments", comments);
        if (assignedToEmail != null)
        {
            String assignedToId = assignedToEmail.isEmpty() ? "" : new APIUserHelper(this).getUserId(assignedToEmail).toString();
            params.put("assignedTo", assignedToId);
        }
        command.setParameters(params);
        try
        {
            command.execute(connection, MOTHERSHIP_PROJECT);
        }
        catch (IOException|CommandException e)
        {
            throw new RuntimeException(e);
        }
    }

    public int getReportCount(int stackTraceId)
    {
        Connection connection = createDefaultConnection();
        SelectRowsCommand command = new SelectRowsCommand("mothership", "ExceptionStackTrace");
        command.addFilter(ID_COLUMN, stackTraceId, Filter.Operator.EQUAL);
        try
        {
            SelectRowsResponse response = command.execute(connection, MOTHERSHIP_PROJECT);
            return (int) response.getRows().get(0).get("instances");
        }
        catch (IOException|CommandException e)
        {
            throw new RuntimeException(e);
        }
    }

    public enum ReportLevel
    {
        NONE,
        ON
    }

    public void createUsageReport(ReportLevel level, boolean submit, String forwardedFor)
    {
        createMothershipReport("CheckForUpdates", level, submit, forwardedFor);
    }

    public void createExceptionReport(ReportLevel level, boolean submit)
    {
        createMothershipReport("ReportException", level, submit, null);
    }

    private void createMothershipReport(String type, ReportLevel level, boolean submit, @Nullable String forwardedFor)
    {
        String relativeUrl = getTestMothershipReportUrl(type, level, submit, forwardedFor);
        beginAt(relativeUrl);
    }

    public void submitMockUsageReport(String hostName, String serverGUID, String sessionGUID) throws IOException, CommandException
    {
        Map<String, Object> usageReportJson = getUsageReportJson();
        usageReportJson.put("serverHostName", hostName);
        usageReportJson.put("serverGUID", serverGUID);
        usageReportJson.put("serverSessionGUID", sessionGUID);
        submitUsageReport(usageReportJson);
    }

    public Map<String, Object> getUsageReportJson() throws IOException, CommandException
    {
        Command<CommandResponse> command = new Command<>("admin", "testMothershipReport");
        command.setParameters(getMothershipReportParams("CheckForUpdates", ReportLevel.ON, false, null));
        CommandResponse response = command.execute(createDefaultConnection(), "/");
        return response.getParsedData();
    }

    private void submitUsageReport(Map<String, Object> report) throws IOException
    {
        // 'jsonMetrics' is converted to a JSON object by 'testMothershipReport'. Needs to be a string to submit.
        report.computeIfPresent("jsonMetrics", (k, v) -> v.toString());
        String url = WebTestHelper.buildURL(MOTHERSHIP_CONTROLLER, MOTHERSHIP_PROJECT, "checkForUpdates");
        HttpContext context = WebTestHelper.getBasicHttpContext();
        HttpPost method;
        HttpResponse response = null;

        try (CloseableHttpClient httpClient = (CloseableHttpClient)WebTestHelper.getHttpClient())
        {
            method = new HttpPost(url);
            List<NameValuePair> args = new ArrayList<>();
            for (Map.Entry<String, Object> reportVal : report.entrySet())
            {
                args.add(new BasicNameValuePair(reportVal.getKey(), reportVal.getValue().toString()));
            }
            method.setEntity(new UrlEncodedFormEntity(args));
            response = httpClient.execute(method, context);
            int status = response.getStatusLine().getStatusCode();
            assertEquals("Report submitted status", HttpStatus.SC_OK, status);
            assertEquals("Success", response.getHeaders("MothershipStatus")[0].getValue());
        }
        finally
        {
            if (null != response)
                EntityUtils.consumeQuietly(response.getEntity());
        }
    }

    @NotNull
    public static String getTestMothershipReportUrl(String type, ReportLevel level, boolean submit, @Nullable String forwardedFor)
    {
        Map<String, Object> params = getMothershipReportParams(type, level, submit, forwardedFor);
        return WebTestHelper.buildURL("admin", "testMothershipReport", params);
    }

    @NotNull
    private static Map<String, Object> getMothershipReportParams(String type, ReportLevel level, boolean submit, @Nullable String forwardedFor)
    {
        Map<String, Object> params = new HashMap<>();
        params.put("type", type);
        params.put("level", level.toString());
        params.put("submit", submit);
        params.put("testMode", true);
        if (null != forwardedFor)
            params.put("forwardedFor", forwardedFor);
        return params;
    }

    @LogMethod
    public void setIgnoreExceptions(boolean ignore) throws IOException, CommandException
    {
        try
        {
            String serverName = executeScript("return LABKEY.serverName;", String.class);
            int installationId = getServerInstallationId(serverName);
            // Set the flag for the installation
            UpdateRowsCommand update = new UpdateRowsCommand("mothership", SERVER_INSTALLATION_QUERY);
            update.addRow(Maps.of(SERVER_INSTALLATION_ID_COLUMN, installationId, "IgnoreExceptions", ignore));
            update.execute(createDefaultConnection(), MOTHERSHIP_PROJECT);
        }
        catch (NotFoundException notFound)
        {
            TestLogger.log("No existing server installation record to update.");
            if (ignore)
            {
                throw new IllegalStateException("Attempting to set ignore exceptions true but no existing server installation record.", notFound);
            }
        }
    }

    public void ensureSelfReportingEnabled()
    {
        if (!selfReportingEnabled)
        {
            CustomizeSitePage.beginAt(this)
                    .setExceptionSelfReporting(true)
                    .save();
            selfReportingEnabled = true;
        }
    }

    public void disableExceptionReporting()
    {
        CustomizeSitePage.beginAt(this)
                .setExceptionReportingLevel(CustomizeSitePage.ReportingLevel.NONE)
                .setExceptionSelfReporting(false)
                .save();
        selfReportingEnabled = false;
    }

    public int triggerException(TestActions.ExceptionActions action)
    {
        return triggerExceptions(action).get(0);
    }

    public List<Integer> triggerExceptions(TestActions.ExceptionActions... actions)
    {
        List<Pair<TestActions.ExceptionActions, String>> actionsWithMessages = new ArrayList<>();
        for (TestActions.ExceptionActions action : actions)
        {
            actionsWithMessages.add(Pair.of(action, null));
        }
        return triggerExceptions(actionsWithMessages);
    }

    @LogMethod
    public List<Integer> triggerExceptions(@LoggedParam List<Pair<TestActions.ExceptionActions, String>> actionsWithMessages)
    {
        List<Integer> exceptionIds = new ArrayList<>();
        checkErrors();
        for (Pair<TestActions.ExceptionActions, String> action : actionsWithMessages)
        {
            action.getLeft().triggerException(action.getRight());
            sleep(100); // Wait for mothership to pick up exception
            exceptionIds.add(getLatestStackTraceId());
        }
        resetErrors();
        return exceptionIds;
    }
}
