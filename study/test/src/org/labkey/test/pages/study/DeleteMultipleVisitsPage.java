package org.labkey.test.pages.study;

import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.pages.LabKeyPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class DeleteMultipleVisitsPage extends LabKeyPage<DeleteMultipleVisitsPage.ElementCache>
{
    public DeleteMultipleVisitsPage(WebDriver driver)
    {
        super(driver);
    }

    public static DeleteMultipleVisitsPage beginAt(WebDriverWrapper driver)
    {
        return beginAt(driver, driver.getCurrentContainerPath());
    }

    public static DeleteMultipleVisitsPage beginAt(WebDriverWrapper driver, String containerPath)
    {
        driver.beginAt(WebTestHelper.buildURL("study", containerPath, "bulkDeleteVisits"));
        return new DeleteMultipleVisitsPage(driver.getDriver());
    }

    public void selectVisitForDeletion(String label)
    {
        click(elementCache().getVisitCheckbox(label));
    }

    public ManageVisitPage clickDeleteSelected()
    {
        doAndWaitForPageToLoad(() ->
        {
            click(elementCache().deleteSelectedBtn);
            assertAlert("Are you sure you want to delete the selected visit and all related dataset/specimen data? This action cannot be undone.");
        });
        return new ManageVisitPage(getDriver());
    }

    public String getErrorMessage()
    {
        return elementCache().errorMsg.getText();
    }

    public int getVisitDatasetRowCount(String visitLabel)
    {
        return Integer.parseInt(elementCache().getVisitDatasetCount(visitLabel).getText());
    }

    public int getVisitSpecimenRowCount(String visitLabel)
    {
        return Integer.parseInt(elementCache().getVisitSpecimenCount(visitLabel).getText());
    }

    public ManageVisitPage clickCancel()
    {
        clickAndWait(elementCache().cancelBtn);
        return new ManageVisitPage(getDriver());
    }

    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends LabKeyPage.ElementCache
    {
        Locator.XPathLocator visitsTableLoc = Locator.tagWithClass("table", "labkey-data-region");

        WebElement errorMsg = Locator.tagWithClass("div", "labkey-error").findWhenNeeded(this);
        WebElement deleteSelectedBtn = Locator.lkButton("Delete Selected").findWhenNeeded(this);
        WebElement cancelBtn = Locator.lkButton("Cancel").findWhenNeeded(this);

        WebElement getVisitCheckbox(String label)
        {
            Locator.XPathLocator loc = Locator.xpath("//tr[./td[@class = 'visit-label']/a[text() = '" + label + "']]/td/input[@type = 'checkbox']");
            return visitsTableLoc.append(loc).findElement(this);
        }

        WebElement getVisitDatasetCount(String label)
        {
            Locator.XPathLocator loc = Locator.xpath("//tr[./td[@class = 'visit-label']/a[text() = '" + label + "']]/td[@class = 'visit-dataset-count']");
            return visitsTableLoc.append(loc).findElement(this);
        }

        WebElement getVisitSpecimenCount(String label)
        {
            Locator.XPathLocator loc = Locator.xpath("//tr[./td[@class = 'visit-label']/a[text() = '" + label + "']]/td[@class = 'visit-specimen-count']");
            return visitsTableLoc.append(loc).findElement(this);
        }
    }
}