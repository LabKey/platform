package org.labkey.test.util.mothership;

import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.remoteapi.query.Sort;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebDriverWrapperImpl;
import org.openqa.selenium.WebDriver;

import java.io.IOException;

public class MothershipHelper
{
    private static final String idColumn = "ExceptionStackTraceId";
    public static final String projectName = "_mothership";

    WebDriverWrapper driver;

    public MothershipHelper(WebDriver driver)
    {
        this.driver = new WebDriverWrapperImpl(driver);
    }

    public int getLatestStackTraceId()
    {
        Connection connection = driver.createDefaultConnection(true);
        SelectRowsCommand command = new SelectRowsCommand("mothership", "ExceptionStackTrace");
        command.addSort(idColumn, Sort.Direction.DESCENDING);
        command.setMaxRows(1);
        try
        {
            SelectRowsResponse response = command.execute(connection, projectName);
            if (response.getRows().isEmpty())
                return 0;
            return (Integer) response.getRows().get(0).get(idColumn);
        }
        catch (IOException|CommandException e)
        {
            throw new RuntimeException(e);
        }
    }
}
