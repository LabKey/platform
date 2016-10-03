package org.labkey.test.pages.studydesigncontroller;

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.selenium.LazyWebElement;
import org.labkey.test.util.Ext4Helper;
import org.openqa.selenium.WebElement;

public class BaseManageVaccineDesignPage extends LabKeyPage
{
    public BaseManageVaccineDesignPage(BaseWebDriverTest test)
    {
        super(test);
        waitForElement(Locators.pageSignal("VaccineDesign_renderviewcomplete"));
    }

    protected void clickOuterAddNewRow(Locator.XPathLocator table)
    {
        waitAndClick(table.append(baseElements().outerAddRowIconLoc));
        sleep(1000); // give the table a second to refresh
    }

    protected void clickOuterAddNewVisit(Locator.XPathLocator table)
    {
        waitAndClick(table.append(baseElements().addVisitIconLoc));
        sleep(1000); // give the table a second to refresh
    }

    protected void clickSubgridAddNewRow(Locator.XPathLocator table, String column, int rowIndex)
    {
        Locator.XPathLocator subgridTable = getSubgridTableLoc(table, column, rowIndex);
        waitAndClick(subgridTable.append(baseElements().addRowIconLoc));
        sleep(1000); // give the table a second to refresh
    }

    protected void setOuterTextFieldValue(Locator.XPathLocator table, String column, String value, int rowIndex)
    {
        Locator.XPathLocator cellLoc = getOuterCellLoc(table, column, rowIndex);
        setTextFieldValue(cellLoc, column, value);
    }

    protected void setOuterTextAreaValue(Locator.XPathLocator table, String column, String value, int rowIndex)
    {
        Locator.XPathLocator cellLoc = getOuterCellLoc(table, column, rowIndex);
        setFormElement(cellLoc.append(Locator.tagWithName("textarea", column)), value);
        sleep(500); // give the store a half second to update

    }

    protected void setSubgridTextFieldValue(Locator.XPathLocator table, String outerColumn, String column, String value, int outerRowIndex, int subgridRowIndex)
    {
        Locator.XPathLocator cellLoc = getSubgridCellLoc(table, outerColumn, column, outerRowIndex, subgridRowIndex);
        setTextFieldValue(cellLoc, column, value);
    }

    protected void setTextFieldValue(Locator.XPathLocator cellLoc, String column, String value)
    {
        setFormElement(cellLoc.append(Locator.tagWithName("input", column)), value);
        sleep(500); // give the store a half second to update
    }

    protected void setOuterComboFieldValue(Locator.XPathLocator table, String column, String value, int rowIndex)
    {
        Locator.XPathLocator cellLoc = getOuterCellLoc(table, column, rowIndex);
        setComboFieldValue(cellLoc, value);
    }

    protected void setSubgridComboFieldValue(Locator.XPathLocator table, String outerColumn, String column, String value, int outerRowIndex, int subgridRowIndex)
    {
        Locator.XPathLocator cellLoc = getSubgridCellLoc(table, outerColumn, column, outerRowIndex, subgridRowIndex);
        setComboFieldValue(cellLoc, value);
    }

    protected void setComboFieldValue(Locator.XPathLocator cellLoc, String value)
    {
        _ext4Helper.selectComboBoxItem(cellLoc, Ext4Helper.TextMatchTechnique.STARTS_WITH, value);
        sleep(500); // give the store a half second to update
    }

    protected Locator.XPathLocator getOuterCellLoc(Locator.XPathLocator table, String column, int rowIndex)
    {
        Locator.XPathLocator tableLoc = table.append(baseElements().tableRowLoc);
        return tableLoc.append(baseElements().cellValueLoc.withAttribute("data-index", column).withAttribute("outer-index", rowIndex+""));
    }

    protected Locator.XPathLocator getSubgridCellLoc(Locator.XPathLocator table, String outerColumn, String column, int outerRowIndex, int subgridRowIndex)
    {
        Locator.XPathLocator subgridTableLoc = getSubgridTableLoc(table, outerColumn, outerRowIndex);
        return subgridTableLoc.append(baseElements().cellValueLoc.withAttribute("outer-data-index", outerColumn).withAttribute("data-index", column).withAttribute("subgrid-index", subgridRowIndex+""));
    }

    protected Locator.XPathLocator getSubgridTableLoc(Locator.XPathLocator table, String subgridName, int rowIndex)
    {
        Locator.XPathLocator tableLoc = table.append(baseElements().tableRowLoc);
        Locator.XPathLocator cellLoc = tableLoc.append(baseElements().cellDisplayLoc.withAttribute("outer-index", rowIndex+""));
        return cellLoc.append(Locator.tagWithClass("table", "subgrid-" + subgridName));
    }

    public void cancel()
    {
        baseElements().cancelButton.click();
    }

    protected BaseElements baseElements()
    {
        return new BaseElements();
    }

    protected class BaseElements
    {
        int wait = BaseWebDriverTest.WAIT_FOR_JAVASCRIPT;
        Locator.XPathLocator studyVaccineDesignLoc = Locator.tagWithClass("div", "study-vaccine-design");
        Locator.XPathLocator tableOuterLoc = Locator.tagWithClass("table", "outer");
        Locator.XPathLocator tableRowLoc = Locator.tagWithClass("tr", "row-outer");
        Locator.XPathLocator cellValueLoc = Locator.tagWithClass("td", "cell-value");
        Locator.XPathLocator cellDisplayLoc = Locator.tagWithClass("td", "cell-display");
        Locator.XPathLocator outerAddRowIconLoc = Locator.tagWithClass("i", "outer-add-new-row");
        Locator.XPathLocator addRowIconLoc = Locator.tagWithClass("i", "add-new-row");
        Locator.XPathLocator addVisitIconLoc = Locator.tagWithClass("i", "add-visit-column");

        WebElement saveButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("Save"), getWrappedDriver()).withTimeout(wait);
        WebElement cancelButton = new LazyWebElement(Ext4Helper.Locators.ext4Button("Cancel"), getWrappedDriver()).withTimeout(wait);
    }
}
