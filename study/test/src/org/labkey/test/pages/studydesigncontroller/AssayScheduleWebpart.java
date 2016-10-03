package org.labkey.test.pages.studydesigncontroller;

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.components.BodyWebPart;
import org.labkey.test.selenium.LazyWebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class AssayScheduleWebpart extends BodyWebPart<AssayScheduleWebpart.Elements>
{
    public AssayScheduleWebpart(WebDriver driver)
    {
        super(driver, "Assay Schedule");
    }

    public boolean isEmpty()
    {
        return assaysHasNoData();
    }

    public boolean assaysHasNoData()
    {
        elementCache().assaysTable.findElement(By.xpath(elementCache().tableOuterLoc.getLoc()));
        return getWrapper().isElementPresent(elementCache().assaysLoc.append(elementCache().tableOuterLoc).append(elementCache().emptyLoc));
    }

    public int getAssayRowCount()
    {
        return getWrapper().getElementCount(elementCache().assaysLoc.append(elementCache().tableRowLoc));
    }

    public String getAssayCellDisplayValue(String column, int rowIndex)
    {
        return getAssayCellDisplayValue(column, rowIndex, null);
    }

    public String getAssayCellDisplayValue(String column, int rowIndex, String dataFilterValue)
    {
        Locator.XPathLocator rowLoc = elementCache().assaysLoc.append(elementCache().tableRowLoc);
        Locator.XPathLocator cellLoc = rowLoc.append(elementCache().cellDisplayLoc.withAttribute("outer-index", rowIndex+"").withAttribute("data-index", column));
        if (dataFilterValue != null)
            cellLoc = cellLoc.withAttribute("data-filter-value", dataFilterValue);

        return cellLoc.findElement(getDriver()).getText();
    }

    public String getAssayPlan()
    {
        return elementCache().assayPlanLoc.findElement(getDriver()).getText();
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
        Locator.XPathLocator manageLoc = Locator.linkWithText("Manage Assay Schedule");
        Locator.XPathLocator assaysLoc = Locator.tagWithClass("div", "vaccine-design-assays");
        Locator.XPathLocator assayPlanLoc = Locator.tag("p").withAttribute("data-index", "AssayPlan");

        WebElement assaysTable = new LazyWebElement(assaysLoc.append(tableOuterLoc), getComponentElement()).withTimeout(wait);
        WebElement manageLink = new LazyWebElement(manageLoc, getComponentElement()).withTimeout(wait);
    }
}
