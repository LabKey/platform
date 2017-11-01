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
package org.labkey.test.pages.mothership;

import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.components.html.Input;
import org.labkey.test.components.html.OptionSelect;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.pages.issues.InsertPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Maps;
import org.labkey.test.util.mothership.MothershipHelper;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.labkey.test.components.html.OptionSelect.OptionSelect;

public class StackTraceDetailsPage extends LabKeyPage<StackTraceDetailsPage.ElementCache>
{
    public StackTraceDetailsPage(WebDriver driver)
    {
        super(driver);
    }

    public static StackTraceDetailsPage beginAt(WebDriverWrapper driver, int exceptionStackTraceId)
    {
        return beginAt(driver, MothershipHelper.MOTHERSHIP_PROJECT, exceptionStackTraceId);
    }

    public static StackTraceDetailsPage beginAt(WebDriverWrapper driver, String containerPath, int exceptionStackTraceId)
    {
        driver.beginAt(WebTestHelper.buildURL("mothership", containerPath, "showStackTraceDetail", Maps.of(MothershipHelper.ID_COLUMN, String.valueOf(exceptionStackTraceId))));
        return new StackTraceDetailsPage(driver.getDriver());
    }

    public Input bugNumber()
    {
        return elementCache().bugNumberInput;
    }

    public Input comments()
    {
        return elementCache().commentsTextArea;
    }

    public OptionSelect assignedTo()
    {
        return elementCache().assignedToSelect;
    }

    public String getExceptionClass()
    {
        return getText(Locator.tagWithClass("td", "lk-form-label").withText("Stack Trace").followingSibling("td"))
                .split("\n")[0].trim();
    }

    public ShowExceptionsPage clickSave()
    {
        clickAndWait(elementCache().saveButton);
        return new ShowExceptionsPage(getDriver());
    }

    public InsertPage clickCreateIssue()
    {
        clickAndWait(elementCache().createIssueButton);
        return new InsertPage(getDriver());
    }

    public DataRegionTable getExceptionReports()
    {
        return elementCache().exceptionReportsDataRegion;
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends LabKeyPage.ElementCache
    {
        Input bugNumberInput = new Input(Locator.name("bugNumber").findWhenNeeded(this), getDriver());
        Input commentsTextArea = new Input(Locator.name("comments").findWhenNeeded(this), getDriver());
        OptionSelect assignedToSelect = OptionSelect(Locator.name("assignedTo")).findWhenNeeded(this);
        WebElement saveButton = Locator.lkButton("Save").findWhenNeeded(this);
        WebElement createIssueButton = Locator.lkButton("Create Issue").findWhenNeeded(this);
        DataRegionTable exceptionReportsDataRegion = new DataRegionTable("ExceptionReports", getDriver());
    }
}
