/*
 * Copyright (c) 2016 LabKey Corporation
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

import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.PostCommand;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.remoteapi.query.Sort;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebDriverWrapperImpl;
import org.labkey.test.util.APIUserHelper;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MothershipHelper
{
    public static final String ID_COLUMN = "ExceptionStackTraceId";
    public static final String MOTHERSHIP_PROJECT = "_mothership";

    WebDriverWrapper driver;

    public MothershipHelper(WebDriver driver)
    {
        this.driver = new WebDriverWrapperImpl(driver);
    }

    public int getLatestStackTraceId()
    {
        Connection connection = driver.createDefaultConnection(true);
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
        Connection connection = driver.createDefaultConnection(true);
        PostCommand command = new PostCommand("mothership", "updateStackTrace");
        Map<String, Object> params = new HashMap<>();
        params.put(ID_COLUMN, exceptionStackTraceId);
        if (bugNumber != null)
            params.put("bugNumber", bugNumber);
        if (comments != null)
            params.put("comments", comments);
        if (assignedToEmail != null)
        {
            String assignedToId = assignedToEmail.isEmpty() ? "" : new APIUserHelper(driver).getUserId(assignedToEmail).toString();
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
        driver.beginAt(relativeUrl);
    }
}
