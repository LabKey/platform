/*
 * Copyright (c) 2014-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.test.pages.studydesigncontroller;

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.components.ext4.Window;
import org.labkey.test.util.Ext4Helper;

public class ManageTreatmentsPage extends BaseManageVaccineDesignPage
{
    public ManageTreatmentsPage(BaseWebDriverTest test)
    {
        super(test);
        waitForElements(elements().studyVaccineDesignLoc, 2);
        waitForElements(elements().outerAddRowIconLoc, 2);
    }

    public void addNewTreatmentRow(String label, String description, int rowIndex)
    {
        clickOuterAddNewRow(elements().treatmentsLoc);

        setOuterTextFieldValue(elements().treatmentsLoc, "Label", label, rowIndex);
        setOuterTextAreaValue(elements().treatmentsLoc, "Description", description, rowIndex);
    }

    public void addNewTreatmentImmunogenRow(String productLabel, String dose, String route, int outerRowIndex, int subgridRowIndex)
    {
        addNewTreatmentProductRow("Immunogen", productLabel, dose, route, outerRowIndex, subgridRowIndex);
    }

    public void addNewTreatmentAdjuvantRow(String productLabel, String dose, String route, int outerRowIndex, int subgridRowIndex)
    {
        addNewTreatmentProductRow("Adjuvant", productLabel, dose, route, outerRowIndex, subgridRowIndex);
    }

    public void addNewTreatmentProductRow(String role, String productLabel, String dose, String route, int outerRowIndex, int subgridRowIndex)
    {
        clickSubgridAddNewRow(elements().treatmentsLoc, role, outerRowIndex);

        setSubgridComboFieldValue(elements().treatmentsLoc, role, "ProductId", productLabel, outerRowIndex, subgridRowIndex);
        if (dose != null || route != null)
            setSubgridComboFieldValue(elements().treatmentsLoc, role, "DoseAndRoute", getDoseRouteComboLabel(dose, route), outerRowIndex, subgridRowIndex);
    }

    private String getDoseRouteComboLabel(String dose, String route)
    {
        return (dose != null ? dose : "") + " : " + (route != null ? route : "");
    }

    public void addNewCohortRow(String label, int subjectCount, int rowIndex)
    {
        clickOuterAddNewRow(elements().cohortsLoc);

        setOuterTextFieldValue(elements().cohortsLoc, "Label", label, rowIndex);
        setOuterTextFieldValue(elements().cohortsLoc, "SubjectCount", subjectCount+"", rowIndex);
    }

    public void addCohortTreatmentMapping(Visit visit, String treatmentLabel, int rowIndex)
    {
        Locator.XPathLocator cellLoc = getOuterCellLoc(elements().cohortsLoc, "VisitMap", rowIndex);
        cellLoc = cellLoc.withAttribute("data-filter-value", visit.getRowId().toString());
        setComboFieldValue(cellLoc, treatmentLabel);
    }

    public void addAllExistingVisitColumns()
    {
        addExistingVisitColumn("[Show All]");
    }

    public void addExistingVisitColumn(String visitLabel)
    {
        clickOuterAddNewVisit(elements().cohortsLoc);

        waitForElement(elements().existingVisitLoc);
        _ext4Helper.selectComboBoxItem(elements().existingVisitLoc, visitLabel);

        Window addVisitWindow = new Window("Add Visit", getDriver());
        addVisitWindow.clickButton("Select", 0);
        sleep(1000); // give the table a second to refresh
    }

    public void addNewVisitColumn(String label, Integer rangeMin, Integer rangeMax)
    {
        clickOuterAddNewVisit(elements().cohortsLoc);

        waitAndClick(elements().newVisitRadioLoc);
        setFormElement(elements().newVisitLabelLoc, label);
        if (rangeMin != null)
            setFormElement(elements().newVisitMinLoc, rangeMin.toString());
        if (rangeMax != null)
            setFormElement(elements().newVisitMaxLoc, rangeMax.toString());

        Window addVisitWindow = new Window("Add Visit", getDriver());
        addVisitWindow.clickButton("Submit", 0);
        sleep(1000); // give the table a second to refresh
    }

    public void save()
    {
        // TODO handle error case
        elements().saveButton.click();
    }

    protected Elements elements()
    {
        return new Elements();
    }

    protected class Elements extends BaseElements
    {
        Locator.XPathLocator treatmentsLoc = Locator.tagWithClass("div", "vaccine-design-treatments");
        Locator.XPathLocator cohortsLoc = Locator.tagWithClass("div", "vaccine-design-cohorts");
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
