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

import java.util.List;

public class ManageAssaySchedulePage extends BaseManageVaccineDesignVisitPage
{
    public ManageAssaySchedulePage(BaseWebDriverTest test)
    {
        super(test);
        waitForElements(elements().studyVaccineDesignLoc, 2);
        waitForElements(elements().outerAddRowIconLoc, 1);
    }

    public void addNewAssayRow(String label, String description, int rowIndex)
    {
        clickOuterAddNewRow(elements().assaysLoc);

        setOuterTextFieldValue(elements().assaysLoc, "AssayName", label, rowIndex);
        if (description != null)
            setOuterTextFieldValue(elements().assaysLoc, "Description", description, rowIndex);
    }

    public void setBaseProperties(String lab, String sampleType, Integer sampleQuantity, String sampleUnits, int rowIndex)
    {
        if (lab != null)
            setOuterComboFieldValue(elements().assaysLoc, "Lab", lab, rowIndex);
        if (sampleType != null)
            setOuterComboFieldValue(elements().assaysLoc, "SampleType", sampleType, rowIndex);
        if (sampleQuantity != null)
            setOuterTextFieldValue(elements().assaysLoc, "SampleQuantity", sampleQuantity+"", rowIndex);
        if (sampleUnits != null)
            setOuterComboFieldValue(elements().assaysLoc, "SampleUnits", sampleUnits, rowIndex);
    }

    public void selectVisits(List<Visit> visits, int rowIndex)
    {
        for (Visit visit : visits)
            selectVisit(visit, rowIndex);
    }

    public void selectVisit(Visit visit, int rowIndex)
    {
        Locator.XPathLocator cellLoc = getOuterCellLoc(elements().assaysLoc, "VisitMap", rowIndex);
        cellLoc = cellLoc.withAttribute("data-filter-value", visit.getRowId().toString());
        _ext4Helper.checkCheckbox(cellLoc.append(elements().checkbox));
        sleep(500); // give the store a half second to update
    }

    public void setAssayPlan(String value)
    {
        setFormElement(elements().assayPlanLoc, value);
    }

    public void addAllExistingVisitColumns()
    {
        addAllExistingVisitColumns(elements().assaysLoc);
    }

    public void addExistingVisitColumn(String visitLabel)
    {
        addExistingVisitColumn(elements().assaysLoc, visitLabel);
    }

    public void addNewVisitColumn(String label, Integer rangeMin, Integer rangeMax)
    {
        addNewVisitColumn(elements().assaysLoc, label, rangeMin, rangeMax);
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
        Locator.XPathLocator assaysLoc = Locator.tagWithClass("div", "vaccine-design-assays");
        Locator.XPathLocator assayPlanLoc = Locator.tagWithName("textarea", "assayPlan");
        Locator.XPathLocator checkbox = Locator.tagWithClass("input", "x4-form-checkbox");
    }
}
