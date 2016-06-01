package org.labkey.test.util.mothership;

import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.PostCommand;
import org.labkey.remoteapi.query.ContainerFilter;
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

    public int getHighestIssueId()
    {
        Connection connection = driver.createDefaultConnection(true);
        SelectRowsCommand command = new SelectRowsCommand("issues", "issues");
        command.addSort("IssueId", Sort.Direction.DESCENDING);
        command.setMaxRows(1);
        command.setContainerFilter(ContainerFilter.AllFolders);
        try
        {
            SelectRowsResponse response = command.execute(connection, "/");
            if (response.getRows().isEmpty())
                return 0;
            return (Integer) response.getRows().get(0).get("IssueId");
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
}
