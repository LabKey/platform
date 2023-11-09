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
package org.labkey.test.pages.mothership;

import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.components.html.Input;
import org.labkey.test.util.mothership.MothershipHelper;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class EditUpgradeMessagePage extends BaseMothershipPage<EditUpgradeMessagePage.ElementCache>
{
    public EditUpgradeMessagePage(WebDriver driver)
    {
        super(driver);
    }

    public static EditUpgradeMessagePage beginAt(WebDriverWrapper driver)
    {
        return beginAt(driver, MothershipHelper.MOTHERSHIP_PROJECT);
    }

    public static EditUpgradeMessagePage beginAt(WebDriverWrapper driver, String containerPath)
    {
        driver.beginAt(WebTestHelper.buildURL("mothership", containerPath, "editUpgradeMessage"));
        return new EditUpgradeMessagePage(driver.getDriver());
    }

    public String getCurrentBuildDate()
    {
        return elementCache().currentBuildDateInput.get();
    }

    public EditUpgradeMessagePage setCurrentBuildDate(String buildDate)
    {
        elementCache().currentBuildDateInput.set(buildDate);
        return this;
    }

    /*
        gets the current upgrade message textarea's content
     */
    public String getMessage()
    {
        return elementCache().messageTextArea.get();
    }

    public EditUpgradeMessagePage setMessage(String message)
    {
        elementCache().messageTextArea.set(message);
        return this;
    }

    /*
        gets the current marketing message textarea's content
     */
    public String getMarketingMessage()
    {
        return elementCache().marketingMessageTextArea.get();
    }

    public EditUpgradeMessagePage setMarketingMessage(String marketingMessage)
    {
        elementCache().marketingMessageTextArea.setWithPaste(marketingMessage);
        return this;
    }

    public String getCreateIssueURL()
    {
        return elementCache().createIssueURLInput.get();
    }

    public EditUpgradeMessagePage setCreateIssueURL(String createIssueURL)
    {
        elementCache().createIssueURLInput.set(createIssueURL);
        return this;
    }

    public String getIssuesContainer()
    {
        return elementCache().issuesContainerInput.get();
    }

    public EditUpgradeMessagePage setIssuesContainer(String issuesContainerPath)
    {
        elementCache().issuesContainerInput.set(issuesContainerPath);
        return this;
    }

    public ShowExceptionsPage save()
    {
        clickAndWait(elementCache().saveButton);
        return new ShowExceptionsPage(getDriver());
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends BaseMothershipPage.ElementCache
    {
        Input currentBuildDateInput = new Input(Locator.name("currentBuildDate").findWhenNeeded(this), getDriver());
        Input messageTextArea = new Input(Locator.name("message").findWhenNeeded(this), getDriver());
        Input createIssueURLInput = new Input(Locator.name("createIssueURL").findWhenNeeded(this), getDriver());
        Input issuesContainerInput = new Input(Locator.name("issuesContainer").findWhenNeeded(this), getDriver());
        Input marketingMessageTextArea = new Input(Locator.name("marketingMessage").findWhenNeeded(this), getDriver());
        WebElement saveButton = Locator.lkButton("Save").findWhenNeeded( this);
    }
}
