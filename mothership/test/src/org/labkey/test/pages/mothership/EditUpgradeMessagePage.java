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
import org.labkey.test.selenium.LazyWebElement;
import org.labkey.test.util.mothership.MothershipHelper;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class EditUpgradeMessagePage extends BaseMothershipPage
{
    private Elements _elements;

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

    public Input currentRevision()
    {
        return elements().currentRevisionInput;
    }

    public Input message()
    {
        return elements().messageTextArea;
    }

    public Input createIssueURL()
    {
        return elements().createIssueURLInput;
    }

    public Input issuesContainer()
    {
        return elements().issuesContainerInput;
    }

    public ShowExceptionsPage save()
    {
        clickAndWait(elements().saveButton);
        return new ShowExceptionsPage(getDriver());
    }

    protected Elements elements()
    {
        if (_elements == null)
            _elements = new Elements();
        return _elements;
    }

    private class Elements extends BaseMothershipPage.Elements
    {
        Input currentRevisionInput = new Input(new LazyWebElement(Locator.name("currentRevision"), this), getDriver());
        Input messageTextArea = new Input(new LazyWebElement(Locator.name("message"), this), getDriver());
        Input createIssueURLInput = new Input(new LazyWebElement(Locator.name("createIssueURL"), this), getDriver());
        Input issuesContainerInput = new Input(new LazyWebElement(Locator.name("issuesContainer"), this), getDriver());
        WebElement saveButton = new LazyWebElement(Locator.lkButton("Save"), this);
    }
}
