package org.labkey.test.pages.studydesigncontroller;

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.components.BodyWebPart;
import org.labkey.test.selenium.LazyWebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class VaccineDesignWebpart extends BodyWebPart<VaccineDesignWebpart.Elements>
{
    public VaccineDesignWebpart(WebDriver driver)
    {
        super(driver, "Vaccine Design");
    }

    public boolean isEmpty()
    {
        return immunogensHasNoData() && adjuvantsHasNoData();
    }

    public boolean immunogensHasNoData()
    {
        elementCache().immunogensTable.findElement(By.xpath(elementCache().tableOuterLoc.getLoc()));
        return getWrapper().isElementPresent(elementCache().immunogensLoc.append(elementCache().tableOuterLoc).append(elementCache().emptyLoc));
    }

    public int getImmunogenRowCount()
    {
        return getProductTableRowCount(elementCache().immunogensLoc);
    }

    public boolean adjuvantsHasNoData()
    {
        elementCache().adjuvantsTable.findElement(By.xpath(elementCache().tableOuterLoc.getLoc()));
        return getWrapper().isElementPresent(elementCache().adjuvantsLoc.append(elementCache().tableOuterLoc).append(elementCache().emptyLoc));
    }

    public int getAdjuvantRowCount()
    {
        return getProductTableRowCount(elementCache().adjuvantsLoc);
    }

    public int getProductTableRowCount(Locator.XPathLocator table)
    {
        return getWrapper().getElementCount(table.append(elementCache().tableRowLoc));
    }

    public int getImmunogenAntigenRowCount(int rowIndex)
    {
        Locator.XPathLocator cellLoc = elementCache().immunogensLoc.append(elementCache().cellDisplayLoc.withAttribute("outer-index", rowIndex+""));
        Locator.XPathLocator subgridTableLoc = cellLoc.append(Locator.tagWithClass("table", "subgrid-Antigens"));
        if (!getWrapper().isElementPresent(subgridTableLoc))
            return 0;
        else
            return getWrapper().getElementCount(subgridTableLoc.append(elementCache().subgridRowLoc));
    }

    public String getImmunogenAntigenRowCellDisplayValue(String column, int outerRowIndex, int subgridRowIndex)
    {
        Locator.XPathLocator cellLoc = elementCache().immunogensLoc.append(elementCache().cellDisplayLoc.withAttribute("outer-index", outerRowIndex+""));
        Locator.XPathLocator subgridTableLoc = cellLoc.append(Locator.tagWithClass("table", "subgrid-Antigens"));
        Locator.XPathLocator subgridCellLoc = subgridTableLoc.append(elementCache().cellDisplayLoc.withAttribute("data-index", column).withAttribute("subgrid-index", subgridRowIndex+""));
        return subgridCellLoc.findElement(getDriver()).getText();
    }

    public String getImmunogenCellDisplayValue(String column, int rowIndex)
    {
        return getCellDisplayValue(elementCache().immunogensLoc, column, rowIndex);
    }

    public String getAdjuvantCellDisplayValue(String column, int rowIndex)
    {
        return getCellDisplayValue(elementCache().adjuvantsLoc, column, rowIndex);
    }

    public String getCellDisplayValue(Locator.XPathLocator table, String column, int rowIndex)
    {
        Locator.XPathLocator cellLoc = table.append(elementCache().cellDisplayLoc.withAttribute("data-index", column).withAttribute("outer-index", rowIndex+""));
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
        Locator.XPathLocator subgridRowLoc = Locator.tagWithClass("tr", "subrow");
        Locator.XPathLocator cellDisplayLoc = Locator.tagWithClass("td", "cell-display");
        Locator.XPathLocator emptyLoc = Locator.tagWithClassContaining("td", "empty").withText("No data to show.");
        Locator.XPathLocator manageLoc = Locator.linkWithText("Manage Study Products");
        Locator.XPathLocator immunogensLoc = Locator.tagWithClass("div", "vaccine-design-immunogens");
        Locator.XPathLocator adjuvantsLoc = Locator.tagWithClass("div", "vaccine-design-adjuvants");

        WebElement immunogensTable = new LazyWebElement(immunogensLoc.append(tableOuterLoc), getComponentElement()).withTimeout(wait);
        WebElement adjuvantsTable = new LazyWebElement(adjuvantsLoc.append(tableOuterLoc), getComponentElement()).withTimeout(wait);
        WebElement manageLink = new LazyWebElement(manageLoc, getComponentElement()).withTimeout(wait);
    }
}
