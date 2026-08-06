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
 * The devtools TestSSO provider's stand-in for an identity provider: type an email address to "authenticate" (or, when
 * the browser was sent here to reauthenticate, "reauthenticate") as that user. No password required.
 */
public class TestSsoPage extends LabKeyPage<TestSsoPage.ElementCache>
{
    public TestSsoPage(WebDriver driver)
    {
        super(driver);
    }

    /**
     * Navigates to the sign-in page, which redirects to the TestSSO page when a TestSSO configuration has
     * "Default to this TestSSO configuration" enabled.
     */
    public static TestSsoPage beginAtLogin(WebDriverWrapper webDriverWrapper)
    {
        webDriverWrapper.beginAt(WebTestHelper.buildURL("login", "login"));
        return new TestSsoPage(webDriverWrapper.getDriver());
    }

    @Override
    protected void waitForPage()
    {
        waitFor(() -> Locators.emailInput.existsIn(getDriver()), "TestSSO page did not load in time.", WAIT_FOR_PAGE);
    }

    /**
     * The form's label, which identifies whether the browser was sent here to authenticate or to reauthenticate.
     */
    public String getLabel()
    {
        return elementCache().label.getText();
    }

    public TestSsoPage setEmail(String email)
    {
        elementCache().emailInput.set(email);
        return this;
    }

    /**
     * Authenticates as the given user, which leaves the browser wherever LabKey sends the user after signing in.
     */
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
        setEmail(email);
        clickAndWait(elementCache().authenticateButton);
        clearCache();
    }

    /**
     * Reauthenticates as the given user, which returns the browser to the page that requested reauthentication. Only
     * available when the browser arrived here through a reauthentication redirect.
     */
    public void reauthenticate(String email)
    {
        setEmail(email);
        clickAndWait(elementCache().reauthenticateButton);
        clearCache();
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends LabKeyPage<ElementCache>.ElementCache
    {
        final WebElement label = Locator.tagWithClass("label", "control-label").findWhenNeeded(this);
        final Input emailInput = new Input(Locators.emailInput.findWhenNeeded(this), getDriver());
        final WebElement authenticateButton = Locator.lkButton("Authenticate").findWhenNeeded(this);
        final WebElement reauthenticateButton = Locator.lkButton("Reauthenticate").findWhenNeeded(this);
    }

    private static class Locators
    {
        static final Locator emailInput = Locator.name("email");
    }
}
