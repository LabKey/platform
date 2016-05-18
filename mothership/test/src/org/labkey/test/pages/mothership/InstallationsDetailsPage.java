package org.labkey.test.pages.mothership;

import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.components.ComponentElements;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.selenium.LazyWebElement;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class InstallationsDetailsPage extends LabKeyPage
{
    private Elements _elements;

    public InstallationsDetailsPage(WebDriver driver)
    {
        super(driver);
    }

    public static InstallationsDetailsPage beginAt(WebDriverWrapper driver)
    {
        return beginAt(driver, driver.getCurrentContainerPath());
    }

    public static InstallationsDetailsPage beginAt(WebDriverWrapper driver, String containerPath)
    {
        driver.beginAt(WebTestHelper.buildURL("mothership", containerPath, "installationDetails"));
        return new InstallationsDetailsPage(driver.getDriver());
    }

    protected Elements elements()
    {
        if (_elements == null)
            _elements = new Elements();
        return _elements;
    }

    private class Elements extends ComponentElements
    {
        @Override
        protected SearchContext getContext()
        {
            return getDriver();
        }

        WebElement example = new LazyWebElement(Locator.css("button"), this);
    }
}