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
import org.labkey.test.components.html.Input;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.selenium.LazyWebElement;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public abstract class BaseMothershipPage<EC extends BaseMothershipPage.ElementCache> extends LabKeyPage<EC>
{
    protected BaseMothershipPage(WebDriver driver)
    {
        super(driver);
    }

    public ShowExceptionsPage clickViewExceptions()
    {
        clickAndWait(elementCache().viewExceptionsLink);
        return new ShowExceptionsPage(getDriver());
    }

    public ShowInstallationsPage clickViewInstallations()
    {
        clickAndWait(elementCache().allInstallationsLink);
        return new ShowInstallationsPage(getDriver());
    }

    public EditUpgradeMessagePage clickConfigure()
    {
        clickAndWait(elementCache().configureLink);
        return new EditUpgradeMessagePage(getDriver());
    }

    public ShowReleasesPage clickShowReleases()
    {
        clickAndWait(elementCache().listReleasesLink);
        return new ShowReleasesPage(getDriver());
    }

    public ReportsPage clickReports()
    {
        clickAndWait(elementCache().reportsLink);
        return new ReportsPage(getDriver());
    }

    public ShowExceptionsPage clickMyExceptions()
    {
        clickAndWait(elementCache().myExceptionsLink);
        return new ShowExceptionsPage(getDriver());
    }

    public StackTraceDetailsPage searchForErrorCode(String errorCode)
    {
        elementCache().errorCodeInput.set(errorCode);
        clickAndWait(elementCache().jumpToErrorCodeButton);
        return new StackTraceDetailsPage(getDriver());
    }

    protected class ElementCache extends LabKeyPage.ElementCache
    {
        WebElement viewExceptionsLink = new LazyWebElement(Locator.linkWithText("View Exceptions"), this);
        WebElement allInstallationsLink = new LazyWebElement(Locator.linkWithText("View All Installations"), this);
        WebElement configureLink = new LazyWebElement(Locator.linkWithText("Configure Mothership"), this);
        WebElement listReleasesLink = new LazyWebElement(Locator.linkWithText("List of Releases"), this);
        WebElement reportsLink = new LazyWebElement(Locator.linkWithText("Reports"), this);
        WebElement myExceptionsLink = new LazyWebElement(Locator.linkWithText("My Exceptions"), this);
        Input errorCodeInput = Input.Input(Locator.input("errorCode"), getDriver()).findWhenNeeded(this);
        WebElement jumpToErrorCodeButton = Locator.tagWithName("form", "jumpToErrorCode").append(Locator.lkButton()).findWhenNeeded(this);
    }
}