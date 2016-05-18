package org.labkey.test.pages.mothership;

import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.openqa.selenium.WebDriver;

public class StackTraceDetailsPage extends BaseMothershipPage
{
    private Elements _elements;

    public StackTraceDetailsPage(WebDriver driver)
    {
        super(driver);
    }

    public static StackTraceDetailsPage beginAt(WebDriverWrapper driver)
    {
        return beginAt(driver, driver.getCurrentContainerPath());
    }

    public static StackTraceDetailsPage beginAt(WebDriverWrapper driver, String containerPath)
    {
        driver.beginAt(WebTestHelper.buildURL("mothership", containerPath, "stackTraceDetails"));
        return new StackTraceDetailsPage(driver.getDriver());
    }

    protected Elements elements()
    {
        if (_elements == null)
            _elements = new Elements();
        return _elements;
    }

    private class Elements extends BaseMothershipPage.Elements
    {
    }
}
