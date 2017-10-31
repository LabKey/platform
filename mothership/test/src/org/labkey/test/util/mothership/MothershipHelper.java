/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

import org.labkey.api.util.Pair;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.PostCommand;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.remoteapi.query.Sort;
import org.labkey.remoteapi.query.UpdateRowsCommand;
import org.labkey.test.LabKeySiteWrapper;
import org.labkey.test.Locator;
import org.labkey.test.pages.core.admin.CustomizeSitePage;
import org.labkey.test.pages.test.TestActions;
import org.labkey.test.util.APIUserHelper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.labkey.test.util.Maps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.labkey.test.WebDriverWrapper.sleep;

public class MothershipHelper
{
    public static final String ID_COLUMN = "ExceptionStackTraceId";
    public static final String SERVER_INSTALLATION_ID_COLUMN = "ServerInstallationId";
    public static final String SERVER_INSTALLATION_QUERY = "ServerInstallations";
    public static final String MOTHERSHIP_PROJECT = "_mothership";

    private boolean selfReportingEnabled;

    private final LabKeySiteWrapper test;

    public MothershipHelper(LabKeySiteWrapper test)
    {
        this.test = test;
        selfReportingEnabled = false;
    }

    public int getLatestStackTraceId()
    {
        Connection connection = test.createDefaultConnection(true);
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
        Connection connection = test.createDefaultConnection(true);
        PostCommand command = new PostCommand("mothership", "updateStackTrace");
        Map<String, Object> params = new HashMap<>();
        params.put(ID_COLUMN, exceptionStackTraceId);
        if (bugNumber != null)
            params.put("bugNumber", bugNumber);
        if (comments != null)
            params.put("comments", comments);
        if (assignedToEmail != null)
        {
            String assignedToId = assignedToEmail.isEmpty() ? "" : new APIUserHelper(test).getUserId(assignedToEmail).toString();
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
        Connection connection = test.createDefaultConnection(true);
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
        LOW,
        MEDIUM,
        HIGH
    }

    public void createUsageReport(ReportLevel level, boolean submit)
    {
        createMothershipReport("CheckForUpdates", level, submit);
    }

    public void createExceptionReport(ReportLevel level, boolean submit)
    {
        createMothershipReport("ReportException", level, submit);
    }

    private void createMothershipReport(String type, ReportLevel level, boolean submit)
    {
        String relativeUrl = "/admin-testMothershipReport.view?" + "type=" + type +
                "&level=" + level.toString() +
                "&submit=" + submit;
        test.beginAt(relativeUrl);
    }

    @LogMethod
    public void setIgnoreExceptions(boolean ignore) throws IOException, CommandException
    {
        // Find the current server GUID
        test.goToAdmin();
        String serverGUID = test.getText(Locator.tagWithText("td", "Server GUID").followingSibling("td"));

        Connection connection = test.createDefaultConnection(true);
        // Find the corresponding serverInstallationId
        SelectRowsCommand select = new SelectRowsCommand("mothership", SERVER_INSTALLATION_QUERY);
        select.setColumns(Collections.singletonList(SERVER_INSTALLATION_ID_COLUMN));
        select.addFilter("ServerInstallationGUID", serverGUID, Filter.Operator.EQUAL);
        SelectRowsResponse response = select.execute(connection, MOTHERSHIP_PROJECT);
        if (!response.getRows().isEmpty())
        {
            int installationId = (int) response.getRows().get(0).get(SERVER_INSTALLATION_ID_COLUMN);
            // Set the flag for the installation
            UpdateRowsCommand update = new UpdateRowsCommand("mothership", SERVER_INSTALLATION_QUERY);
            update.addRow(Maps.of(SERVER_INSTALLATION_ID_COLUMN, installationId, "IgnoreExceptions", ignore));
            update.execute(connection, MOTHERSHIP_PROJECT);
        }
        else
        {
            test.log("No existing server installation record to update.");
            assertFalse("Attempting to set ignore exceptions true but no existing server installation record.", ignore);
        }
    }

    public void ensureSelfReportingEnabled()
    {
        if (!selfReportingEnabled)
        {
            CustomizeSitePage.beginAt(test)
                    .setExceptionSelfReporting(true)
                    .save();
            selfReportingEnabled = true;
        }
    }

    public void disableExceptionReporting()
    {
        CustomizeSitePage.beginAt(test)
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
            actionsWithMessages.add(new Pair<>(action, null));
        }
        return triggerExceptions(actionsWithMessages);
    }

    @LogMethod
    public List<Integer> triggerExceptions(@LoggedParam List<Pair<TestActions.ExceptionActions, String>> actionsWithMessages)
    {
        List<Integer> exceptionIds = new ArrayList<>();
        test.checkErrors();
        for (Pair<TestActions.ExceptionActions, String> action : actionsWithMessages)
        {
            action.first.triggerException(action.second);
            sleep(100); // Wait for mothership to pick up exception
            exceptionIds.add(getLatestStackTraceId());
        }
        test.resetErrors();
        return exceptionIds;
    }
}
