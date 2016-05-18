package org.labkey.test.pages.mothership;

import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.openqa.selenium.WebDriver;

public class EditUpgradeMessagePage extends BaseMothershipPage
{
    private Elements _elements;

    public EditUpgradeMessagePage(WebDriver driver)
    {
        super(driver);
        _elements = new Elements();
    }

    public static EditUpgradeMessagePage beginAt(WebDriverWrapper driver)
    {
        return beginAt(driver, driver.getCurrentContainerPath());
    }

    public static EditUpgradeMessagePage beginAt(WebDriverWrapper driver, String containerPath)
    {
        driver.beginAt(WebTestHelper.buildURL("mothership", containerPath, "editUpgradeMessage"));
        return new EditUpgradeMessagePage(driver.getDriver());
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
