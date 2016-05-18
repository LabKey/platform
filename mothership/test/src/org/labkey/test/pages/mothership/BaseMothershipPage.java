package org.labkey.test.pages.mothership;

import org.labkey.test.Locator;
import org.labkey.test.components.ComponentElements;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.selenium.LazyWebElement;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public abstract class BaseMothershipPage extends LabKeyPage
{
    protected BaseMothershipPage(WebDriver driver)
    {
        super(driver);
    }

    public ShowExceptionsPage clickViewExceptions()
    {
        clickAndWait(elements().viewExceptionsLink);
        return new ShowExceptionsPage(getDriver());
    }

    public ShowInstallationsPage clickViewInstallations()
    {
        clickAndWait(elements().allInstallationsLink);
        return new ShowInstallationsPage(getDriver());
    }

    public EditUpgradeMessagePage clickConfigure()
    {
        clickAndWait(elements().configureLink);
        return new EditUpgradeMessagePage(getDriver());
    }

    public ShowReleasesPage clickShowReleases()
    {
        clickAndWait(elements().listReleasesLink);
        return new ShowReleasesPage(getDriver());
    }

    public ReportsPage clickReports()
    {
        clickAndWait(elements().reportsLink);
        return new ReportsPage(getDriver());
    }

    public ShowExceptionsPage clickMyExceptions()
    {
        clickAndWait(elements().myExceptionsLink);
        return new ShowExceptionsPage(getDriver());
    }

    protected abstract Elements elements();

    protected class Elements extends ComponentElements
    {
        @Override
        protected SearchContext getContext()
        {
            return getDriver();
        }

        WebElement viewExceptionsLink = new LazyWebElement(Locator.linkWithText("View Exceptions"), this);
        WebElement allInstallationsLink = new LazyWebElement(Locator.linkWithText("View All Installations"), this);
        WebElement configureLink = new LazyWebElement(Locator.linkWithText("Configure Mothership"), this);
        WebElement listReleasesLink = new LazyWebElement(Locator.linkWithText("List of Releases"), this);
        WebElement reportsLink = new LazyWebElement(Locator.linkWithText("Reports"), this);
        WebElement myExceptionsLink = new LazyWebElement(Locator.linkWithText("My Exceptions"), this);
    }
}