package org.labkey.test.pages.mothership;

import org.labkey.remoteapi.CommandException;
import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.util.mothership.MothershipHelper;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ClientExceptionPage extends LabKeyPage<ClientExceptionPage.ElementCache>
{
    private MothershipHelper mothershipHelper;

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
        var initialState = mothershipHelper.getOrderedStackTraces();
        elementCache().inlineScriptErrBtn.click();
        if (expectError)
            waitForNewTimestamp(initialState);
        return this;
    }

    public ClientExceptionPage clickResourceScriptError(boolean expectError) throws Exception
    {
        var initialState = mothershipHelper.getOrderedStackTraces();
        elementCache().resourceScriptErrBtn.click();
        if (expectError)
            waitForNewTimestamp(initialState);
        return this;
    }

    public ClientExceptionPage clickNestedScriptError(boolean expectError) throws Exception
    {
        var initialState = mothershipHelper.getOrderedStackTraces();
        elementCache().nestedScriptErrBtn.click();
        if (expectError)
            waitForNewTimestamp(initialState);
        return this;
    }

    public ClientExceptionPage clickAsyncScriptError(boolean expectError) throws Exception
    {
        var initialState = mothershipHelper.getOrderedStackTraces();
        elementCache().asyncScriptErrBtn.click();
        if (expectError)
            waitForNewTimestamp(initialState);
        return this;
    }

    private void waitForNewTimestamp(List<Map<String, Object>> initialState) throws IOException, CommandException
    {
        waitFor(()-> {
            try
            {
                sleep(250);
                var latestTraces =  mothershipHelper.getOrderedStackTraces();
                var lastTimestamp = (Date)initialState.get(0).get("LastReport");
                if (initialState.isEmpty())
                    return !latestTraces.isEmpty();
                else
                    return  !latestTraces.isEmpty() && ((Date)latestTraces.get(0).get("LastReport")).after(lastTimestamp);
            } catch (Exception e)
            {
                return false;
            }
        }, "No new report appeared in Mothership", 2000);
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    @Override
    protected ElementCache elementCache()
    {
        return (ElementCache) super.elementCache();
    }

    protected class ElementCache extends LabKeyPage<ElementCache>.ElementCache
    {
        final WebElement inlineScriptErrBtn = Locator.id("inline-script").findWhenNeeded(getDriver());
        final WebElement resourceScriptErrBtn = Locator.id("resource-script").findWhenNeeded(getDriver());
        final WebElement nestedScriptErrBtn = Locator.id("nested-script").findWhenNeeded(getDriver());
        final WebElement asyncScriptErrBtn = Locator.id("async-script").findWhenNeeded(getDriver());
    }
}
