/*
 * Copyright (c) 2026 LabKey Corporation
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
import org.labkey.test.components.html.Input;
import org.labkey.test.pages.LabKeyPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Wraps testSso.jsp
 */
public class TestSsoPage extends LabKeyPage<TestSsoPage.ElementCache>
{
    public TestSsoPage(WebDriver driver)
    {
        super(driver);
    }

    public void authenticate(String email)
    {
        attemptToAuthenticate(email);
        Assert.assertEquals("Logged in as", email, getCurrentUser());
    }

    public void authenticateExpectingError(String email)
    {
        attemptToAuthenticate(email);
        Assert.assertEquals("Logged in as", "guest", getCurrentUserName());
    }

    public void attemptToAuthenticate(String email)
    {
        elementCache().emailInput.set(email);
        clickAndWait(elementCache().authenticateButton);
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends LabKeyPage<ElementCache>.ElementCache
    {
        final Input emailInput = Input.Input(Locator.name("email"), getDriver()).findWhenNeeded(this);
        // Might be "Authenticate" or "Reauthenticate"
        final WebElement authenticateButton = Locator.lkButton().withClass("primary").findWhenNeeded(this);
    }
}
