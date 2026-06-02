/*
 * Copyright (c) 2025-2026 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.util.mothership.MothershipHelper;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.Date;
import java.util.Map;

public class ClientExceptionPage extends LabKeyPage<ClientExceptionPage.ElementCache>
{
    private final MothershipHelper mothershipHelper;

    public ClientExceptionPage(WebDriver driver)
    {
        super(driver);
        mothershipHelper = new MothershipHelper(this);
    }

    public static ClientExceptionPage beginAt(WebDriverWrapper webDriverWrapper)
    {
        return beginAt(webDriverWrapper, webDriverWrapper.getCurrentContainerPath());
    }

    public static ClientExceptionPage beginAt(WebDriverWrapper webDriverWrapper, String containerPath)
    {
        webDriverWrapper.beginAt(WebTestHelper.buildURL("mothership", containerPath, "clientException"));
        return new ClientExceptionPage(webDriverWrapper.getDriver());
    }

    @Override
    protected void waitForPage()
    {
        waitFor(()-> Locator.id("inline-script").isDisplayed(getDriver()),
                "the page did not become enabled", WAIT_FOR_JAVASCRIPT);
    }

    public ClientExceptionPage clickInlineScriptError(boolean expectError) throws Exception
    {
        var initialState = mothershipHelper.getLatestStackTrace();
        elementCache().inlineScriptErrBtn.click();
        if (expectError)
            waitForNewTimestamp(initialState);
        return this;
    }

    public ClientExceptionPage clickResourceScriptError(boolean expectError) throws Exception
    {
        var initialState = mothershipHelper.getLatestStackTrace();
        elementCache().resourceScriptErrBtn.click();
        if (expectError)
            waitForNewTimestamp(initialState);
        return this;
    }

    public ClientExceptionPage clickNestedScriptError(boolean expectError) throws Exception
    {
        var initialState = mothershipHelper.getLatestStackTrace();
        elementCache().nestedScriptErrBtn.click();
        if (expectError)
            waitForNewTimestamp(initialState);
        return this;
    }

    public ClientExceptionPage clickAsyncScriptError(boolean expectError) throws Exception
    {
        var initialState = mothershipHelper.getLatestStackTrace();
        elementCache().asyncScriptErrBtn.click();
        if (expectError)
            waitForNewTimestamp(initialState);
        return this;
    }

    private void waitForNewTimestamp(@Nullable Map<String, Object> initialState) throws Exception
    {
        waitFor(() -> {
            try
            {
                sleep(500); // half a second between iterations to avoid spamming the server
                var latestStackTrace = mothershipHelper.getLatestStackTrace();
                if (null == initialState)
                    return latestStackTrace != null;
                else
                    return latestStackTrace != null && ((Date) latestStackTrace.get("LastReport"))
                            .after((Date) initialState.get("LastReport"));
            }
            catch (Exception e)
            {
                return false;
            }
        }, "No new report appeared in Mothership", 4000);
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }


    protected class ElementCache extends LabKeyPage<ElementCache>.ElementCache
    {
        final WebElement inlineScriptErrBtn = Locator.id("inline-script").findWhenNeeded(getDriver());
        final WebElement resourceScriptErrBtn = Locator.id("resource-script").findWhenNeeded(getDriver());
        final WebElement nestedScriptErrBtn = Locator.id("nested-script").findWhenNeeded(getDriver());
        final WebElement asyncScriptErrBtn = Locator.id("async-script").findWhenNeeded(getDriver());
    }
}
