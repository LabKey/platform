/*
 * Copyright (c) 2016-2018 LabKey Corporation
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
package org.labkey.test.pages.mothership;

import org.apache.commons.text.WordUtils;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.components.html.Input;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.mothership.MothershipHelper;
import org.openqa.selenium.NotFoundException;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.labkey.test.util.mothership.MothershipHelper.MOTHERSHIP_CONTROLLER;
import static org.labkey.test.util.mothership.MothershipHelper.MOTHERSHIP_PROJECT;
import static org.labkey.test.util.mothership.MothershipHelper.SERVER_INSTALLATION_ID_COLUMN;
import static org.labkey.test.util.mothership.MothershipHelper.SERVER_INSTALLATION_NAME_COLUMN;
import static org.labkey.test.util.mothership.MothershipHelper.SERVER_INSTALLATION_QUERY;

public class ShowInstallationDetailPage extends LabKeyPage<ShowInstallationDetailPage.ElementCache>
{
    public ShowInstallationDetailPage(WebDriver driver)
    {
        super(driver);
    }

    @Override
    protected void waitForPage()
    {
        waitFor(() -> elementCache().hostNameInput.getComponentElement().isDisplayed(),
                "Details page failed to load", 5_000);
    }

    public static ShowInstallationDetailPage beginAt(WebDriverWrapper driver, String hostName) throws IOException, CommandException
    {
        Integer serverInstallationId = new MothershipHelper(driver).getServerInstallationId(hostName);
        return beginAt(driver, serverInstallationId);
    }

    private static Integer getServerInstallationId(WebDriverWrapper driver, String hostName) throws IOException, CommandException
    {
        SelectRowsCommand selectRows = new SelectRowsCommand(MOTHERSHIP_CONTROLLER, SERVER_INSTALLATION_QUERY);
        selectRows.addFilter(new Filter(SERVER_INSTALLATION_NAME_COLUMN, hostName));
        SelectRowsResponse response = selectRows.execute(driver.createDefaultConnection(), MOTHERSHIP_PROJECT);
        if (response.getRows().size() != 1)
        {
            ShowInstallationsPage installationsPage = ShowInstallationsPage.beginAt(driver);
            List<String> hostNames = installationsPage.getInstallationGrid().getColumnDataAsText(SERVER_INSTALLATION_NAME_COLUMN);
            throw new NotFoundException(String.format("Unable to find server installation [%s]. Found: %s", hostName, hostNames));
        }
        return (Integer) response.getRows().get(0).get(SERVER_INSTALLATION_ID_COLUMN);
    }

    public static ShowInstallationDetailPage beginAt(WebDriverWrapper driver, int serverInstallationId)
    {
        driver.beginAt(WebTestHelper.buildURL(MOTHERSHIP_CONTROLLER, MOTHERSHIP_PROJECT, "showInstallationDetail",
                Map.of(SERVER_INSTALLATION_ID_COLUMN, serverInstallationId)));
        return new ShowInstallationDetailPage(driver.getDriver());
    }

    public String getServerIP()
    {
        return getSessionProperty("ServerIP");
    }

    public String getDistributionName()
    {
        return getSessionProperty("Distribution");
    }

    private String getSessionProperty(String ServerIP)
    {
        if (getSessionsGrid().getDataRowCount() == 0)
        {
            throw new IllegalStateException("No session info for server: " + getServerHostName());
        }
        return getSessionsGrid().getDataAsText(0, ServerIP);
    }

    public String getServerHostName()
    {
        return elementCache().hostNameInput.get();
    }

    public String getInstallationValue(String labelText)
    {
        return Locator.tagWithClassContaining("td", "lk-form-label")
                .withText(WordUtils.capitalize(labelText))
                .followingSibling("td")
                .findElement(getDriver()).getText();
    }

    public DataRegionTable getSessionsGrid()
    {
        return elementCache().sessionsGrid;
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends LabKeyPage<?>.ElementCache
    {
        private final Input hostNameInput = Input.Input(Locator.input("serverHostName"), getDriver())
                .findWhenNeeded(this);
        private final DataRegionTable sessionsGrid = new DataRegionTable.DataRegionFinder(getDriver())
                .withName("ServerSessions").findWhenNeeded(this);
    }
}
