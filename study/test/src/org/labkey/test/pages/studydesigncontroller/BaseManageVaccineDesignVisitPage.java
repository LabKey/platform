package org.labkey.test.pages.studydesigncontroller;

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.components.ext4.Window;
import org.labkey.test.util.Ext4Helper;

public class BaseManageVaccineDesignVisitPage extends BaseManageVaccineDesignPage
{
    public BaseManageVaccineDesignVisitPage(BaseWebDriverTest test)
    {
        super(test);
    }

    public void addAllExistingVisitColumns(Locator.XPathLocator table)
    {
        addExistingVisitColumn(table, "[Show All]");
    }

    public void addExistingVisitColumn(Locator.XPathLocator table, String visitLabel)
    {
        clickOuterAddNewVisit(table);

        waitForElement(visitElements().existingVisitLoc);
        _ext4Helper.selectComboBoxItem(visitElements().existingVisitLoc, visitLabel);

        Window addVisitWindow = new Window("Add Visit", getDriver());
        addVisitWindow.clickButton("Select", 0);
        sleep(1000); // give the table a second to refresh
    }

    public void addNewVisitColumn(Locator.XPathLocator table, String label, Integer rangeMin, Integer rangeMax)
    {
        clickOuterAddNewVisit(table);

        waitAndClick(visitElements().newVisitRadioLoc);
        setFormElement(visitElements().newVisitLabelLoc, label);
        if (rangeMin != null)
            setFormElement(visitElements().newVisitMinLoc, rangeMin.toString());
        if (rangeMax != null)
            setFormElement(visitElements().newVisitMaxLoc, rangeMax.toString());

        Window addVisitWindow = new Window("Add Visit", getDriver());
        addVisitWindow.clickButton("Submit", 0);
        sleep(1000); // give the table a second to refresh
    }

    protected BaseVisitElements visitElements()
    {
        return new BaseVisitElements();
    }

    protected class BaseVisitElements extends BaseElements
    {
        Locator.XPathLocator existingVisitLoc = Locator.tagWithClass("table", "x4-field").withDescendant(Locator.tagWithName("input", "existingVisit"));
        Locator.XPathLocator newVisitRadioLoc = Ext4Helper.Locators.radiobutton(_test, "Create a new study visit:");
        Locator.XPathLocator newVisitLabelLoc = Locator.tagWithName("input", "newVisitLabel");
        Locator.XPathLocator newVisitMinLoc = Locator.tagWithName("input", "newVisitRangeMin");
        Locator.XPathLocator newVisitMaxLoc = Locator.tagWithName("input", "newVisitRangeMax");
    }

    public static class Visit
    {
        private Integer _rowId;
        private String _label;
        private Integer _rangeMin;
        private Integer _rangeMax;

        public Visit(String visit)
        {
            _label = visit;
        }

        public Visit(String visit, Integer rangeMin, Integer rangeMax)
        {
            _label = visit;
            _rangeMin = rangeMin;
            _rangeMax = rangeMax;
        }

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

        public String getLabel()
        {
            return _label;
        }

        public Integer getRangeMin()
        {
            return _rangeMin;
        }

        public Integer getRangeMax()
        {
            return _rangeMax;
        }
    }
}
