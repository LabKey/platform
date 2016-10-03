package org.labkey.test.pages.studydesigncontroller;

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.components.BodyWebPart;
import org.labkey.test.selenium.LazyWebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class ImmunizationScheduleWebpart extends BodyWebPart<ImmunizationScheduleWebpart.Elements>
{
    public ImmunizationScheduleWebpart(WebDriver driver)
    {
        super(driver, "Immunization Schedule");
    }

    public boolean isEmpty()
    {
        return cohortsHasNoData();
    }

    public boolean cohortsHasNoData()
    {
        elementCache().cohortsTable.findElement(By.xpath(elementCache().tableOuterLoc.getLoc()));
        return getWrapper().isElementPresent(elementCache().cohortsLoc.append(elementCache().tableOuterLoc).append(elementCache().emptyLoc));
    }

    public int getCohortRowCount()
    {
        return getWrapper().getElementCount(elementCache().cohortsLoc.append(elementCache().tableRowLoc));
    }

    public String getCohortCellDisplayValue(String column, int rowIndex)
    {
        Locator.XPathLocator rowLoc = elementCache().cohortsLoc.append(elementCache().tableRowLoc.withAttribute("outer-index", rowIndex+""));
        Locator.XPathLocator cellLoc = rowLoc.append(elementCache().cellDisplayLoc.withAttribute("data-index", column));
        return cellLoc.findElement(getDriver()).getText();
    }

    public void manage()
    {
        elementCache().manageLink.click();
    }

    @Override
    protected Elements newElementCache()
    {
        return new Elements();
    }

    public class Elements extends BodyWebPart.Elements
    {
        int wait = BaseWebDriverTest.WAIT_FOR_JAVASCRIPT;
        Locator.XPathLocator tableOuterLoc = Locator.tagWithClass("table", "outer");
        Locator.XPathLocator tableRowLoc = Locator.tagWithClass("tr", "row-outer");
        Locator.XPathLocator cellDisplayLoc = Locator.tagWithClass("td", "cell-display");
        Locator.XPathLocator emptyLoc = Locator.tagWithClassContaining("td", "empty").withText("No data to show.");
        Locator.XPathLocator manageLoc = Locator.linkWithText("Manage Treatments");
        Locator.XPathLocator cohortsLoc = Locator.tagWithClass("div", "immunization-schedule-cohorts");

        WebElement cohortsTable = new LazyWebElement(cohortsLoc.append(tableOuterLoc), getComponentElement()).withTimeout(wait);
        WebElement manageLink = new LazyWebElement(manageLoc, getComponentElement()).withTimeout(wait);
    }
}
