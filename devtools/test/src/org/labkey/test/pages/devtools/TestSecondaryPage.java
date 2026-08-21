/*
 * Copyright (c) 2018-2026 LabKey Corporation
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
package org.labkey.test.pages.devtools;

import org.junit.Assert;
import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.components.html.RadioButton;
import org.labkey.test.pages.LabKeyPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Wraps 'testSecondary.jsp'
 */
public class TestSecondaryPage extends LabKeyPage<TestSecondaryPage.ElementCache>
{
    public TestSecondaryPage(WebDriver driver)
    {
        super(driver);
    }

    public void denyIdentity()
    {
        elementCache().noButton.check();
        clickAndWait(elementCache().submitButton);
        clearCache(); // Stays on same page
    }

    public void confirmIdentity()
    {
        elementCache().yesButton.check();
        clickAndWait(elementCache().submitButton);
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends LabKeyPage<ElementCache>.ElementCache
    {
        final RadioButton yesButton = RadioButton.RadioButton(Locator.radioButtonByNameAndValue("valid", "1")).findWhenNeeded(this);
        final RadioButton noButton = RadioButton.RadioButton(Locator.radioButtonByNameAndValue("valid", "0")).findWhenNeeded(this);
        final WebElement submitButton = Locator.name("TestSecondary").findWhenNeeded(this);
    }
}
