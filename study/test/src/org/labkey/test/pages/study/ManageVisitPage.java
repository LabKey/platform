package org.labkey.test.pages.study;

import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.pages.LabKeyPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

public class ManageVisitPage extends LabKeyPage<ManageVisitPage.ElementCache>
{
    // TODO refactor more of the Manage Visit page usages and page components

    public ManageVisitPage(WebDriver driver)
    {
        super(driver);
    }

    public static ManageVisitPage beginAt(WebDriverWrapper driver)
    {
        return beginAt(driver, driver.getCurrentContainerPath());
    }

    public static ManageVisitPage beginAt(WebDriverWrapper driver, String containerPath)
    {
        driver.beginAt(WebTestHelper.buildURL("study", containerPath, "manageVisits"));
        return new ManageVisitPage(driver.getDriver());
    }

    public int getVisitRowCount()
    {
        waitForElement(elementCache().visitsTableLoc);
        return elementCache().getVisitRows().size();
    }

    public boolean hasVisitForSequenceRange(String rangeStr)
    {
        return elementCache().getVisitsBySequenceRange(rangeStr).size() == 1;
    }

    public DeleteMultipleVisitsPage goToDeleteMultipleVisits()
    {
        clickAndWait(elementCache().deleteMultipleVisitsLoc);
        return new DeleteMultipleVisitsPage(getDriver());
    }

    public void goToEditVisit(String visitLabel)
    {
        clickAndWait(elementCache().getVisitEditLink(visitLabel));
        waitForElement(Locator.lkButton("Delete Visit"));
    }

    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends LabKeyPage.ElementCache
    {
        Locator.XPathLocator visitsTableLoc = Locator.id("visits");
        Locator.XPathLocator deleteMultipleVisitsLoc = Locator.linkWithText("Delete Multiple Visits");

        List<WebElement> getVisitRows()
        {
            return visitsTableLoc.append(Locator.tagWithClass("tr", "visit-row")).findElements(this);
        }

        List<WebElement> getVisitsBySequenceRange(String rangeStr)
        {
            return visitsTableLoc.append(Locator.tagWithClass("td", "visit-range-cell").withText(rangeStr)).findElements(this);
        }

        WebElement getVisitEditLink(String label)
        {
            Locator.XPathLocator loc = Locator.xpath("//tr[./td[text() = '" + label + "']]/td/a[text() = 'edit']");
            return visitsTableLoc.append(loc).findElement(this);
        }
    }
}