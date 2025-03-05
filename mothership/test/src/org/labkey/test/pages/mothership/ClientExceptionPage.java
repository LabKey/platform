package org.labkey.test.pages.mothership;

import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.util.mothership.MothershipHelper;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

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

    public ClientExceptionPage clickInlineScriptError() throws Exception
    {
        waitForNewTimestamp();
        elementCache().inlineScriptErrBtn.click();
        return this;
    }

    public ClientExceptionPage clickResourceScriptError() throws Exception
    {
        waitForNewTimestamp();
        elementCache().resourceScriptErrBtn.click();
        return this;
    }

    public ClientExceptionPage clickNestedScriptError() throws Exception
    {
        waitForNewTimestamp();
        elementCache().nestedScriptErrBtn.click();
        return this;
    }

    public ClientExceptionPage clickAsyncScriptError() throws Exception
    {
        waitForNewTimestamp();
        elementCache().asyncScriptErrBtn.click();
        return this;
    }


    private void waitForNewTimestamp() throws Exception
    {
        var stackTraces = mothershipHelper.getOrderedStackTraces();
        if (stackTraces.isEmpty())
            return;

        var lastTimestamp = (Date)stackTraces.get(0).get("LastReport");
        waitFor(()-> Instant.now().minus(Duration.ofSeconds(1)).isAfter(lastTimestamp.toInstant()), 2000);
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
