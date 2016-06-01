package org.labkey.test.pages.mothership;

import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.mothership.MothershipHelper;
import org.openqa.selenium.WebDriver;

public class ShowReleasesPage extends BaseMothershipPage
{
    private Elements _elements;

    public ShowReleasesPage(WebDriver driver)
    {
        super(driver);
    }

    public static ShowReleasesPage beginAt(WebDriverWrapper driver)
    {
        return beginAt(driver, MothershipHelper.MOTHERSHIP_PROJECT);
    }

    public static ShowReleasesPage beginAt(WebDriverWrapper driver, String containerPath)
    {
        driver.beginAt(WebTestHelper.buildURL("mothership", containerPath, "showReleases"));
        return new ShowReleasesPage(driver.getDriver());
    }

    protected Elements elements()
    {
        if (_elements == null)
            _elements = new Elements();
        return _elements;
    }

    private class Elements extends BaseMothershipPage.Elements
    {
        DataRegionTable dataRegion = new DataRegionTable("softwareReleases", getDriver());
    }
}
