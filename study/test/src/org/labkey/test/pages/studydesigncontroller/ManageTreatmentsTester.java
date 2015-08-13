/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;

/**
 * org.labkey.study.controllers.StudyDesignController#ManageTreatmentsAction
 */
public class ManageTreatmentsTester
{
    private BaseWebDriverTest _test;

    private static final Locator.XPathLocator editCohortWindow = Ext4Helper.Locators.window("Edit Cohort");

    public ManageTreatmentsTester(BaseWebDriverTest test)
    {
        _test = test;
        _test.waitForElement(Locator.id("treatments-grid"));
        _test._ext4Helper.waitForMaskToDisappear();
    }

    @LogMethod
    public void insertNewTreatment(@LoggedParam String label, String description, TreatmentComponent... components)
    {
        Locator.XPathLocator treatmentsGrid = Locator.id("treatments-grid");
        Locator.XPathLocator insertNewTreatmentButton = treatmentsGrid.append(Ext4Helper.Locators.ext4Button("Insert New"));

        _test.click(insertNewTreatmentButton);
        _test.waitForElement(Ext4Helper.Locators.window("Insert Treatment"));
        _test.setFormElement(Locator.name("Label"), label);
        _test.setFormElement(Locator.name("Description"), description);
        _test.clickButton("Next", 0);
        _test.waitForElement(Ext4Helper.Locators.window("Edit Treatment"));
        for (TreatmentComponent component : components)
        {
            Locator.XPathLocator componentRow = Locator.tag("table").withClass("x4-form-fieldcontainer")
                    .withPredicate(Locator.tagWithClass("label", "x4-form-cb-label").withText(component.getLabel()));
            _test._ext4Helper.checkCheckbox(component.getLabel());
            _test.setFormElement(componentRow.append(Locator.tagWithName("input", "Dose")), component.getDoseAndUnits());
            _test._ext4Helper.selectComboBoxItem(componentRow.append(Locator.tag("table").withClass("x4-box-item").withPredicate(Locator.tagWithName("input", "Route"))), Ext4Helper.TextMatchTechnique.CONTAINS, component.getRoute());
        }
        _test.clickButton("Submit", 0);
        _test._ext4Helper.waitForMaskToDisappear();
    }

    @LogMethod
    public void insertNewCohort(@LoggedParam String label, @Nullable Integer count, TreatmentVisit... treatmentVisits)
    {
        Locator.XPathLocator treatmentScheduleGrid = Locator.id("treatment-schedule-grid");
        Locator.XPathLocator insertNewCohortButton = treatmentScheduleGrid.append(Ext4Helper.Locators.ext4Button("Insert New"));

        _test.click(insertNewCohortButton);
        _test._extHelper.waitForExtDialog("Insert Cohort");
        _test.setFormElement(Locator.name("Label"), label);
        if (count != null) _test.setFormElement(Locator.name("SubjectCount"), count.toString());
        _test.clickButton("Next", 0);
        addTreatmentVisitMappingsToSelectedCohort(treatmentVisits);
        _test.waitForElement(Locators.treatmentScheduleCohortRow(label));
    }

    public void editCohort(String label)
    {
        Locator.XPathLocator groupRow = Locators.treatmentScheduleCohortRow(label);
        _test.doubleClick(groupRow);
        _test.waitForElement(editCohortWindow);
    }

    @LogMethod
    public void addTreatmentVisitMappingsToExistingCohort(@LoggedParam String cohortLabel, TreatmentVisit... treatmentVisits)
    {
        editCohort(cohortLabel);
        addTreatmentVisitMappingsToSelectedCohort(treatmentVisits);
    }

    @LogMethod
    public void removeVisitFromCohort(@LoggedParam String cohortLabel, String visitLabel)
    {
        Locator.XPathLocator removeVisitLink = editCohortWindow
                .append(Locators.fieldContainerWithLabel(visitLabel))
                .append(Locator.linkWithText("Remove"));

        editCohort(cohortLabel);
        _test.waitForElement(editCohortWindow);
        _test.click(removeVisitLink);
        _test.waitForElementToDisappear(removeVisitLink);
    }

    @LogMethod
    private void addTreatmentVisitMappingsToSelectedCohort(TreatmentVisit... treatmentVisits)
    {
        _test.waitForElement(editCohortWindow);
        
        for (TreatmentVisit treatmentVisit : treatmentVisits)
        {
            Locator.XPathLocator treatmentComboForVisit = editCohortWindow
                    .append(Locators.fieldContainerWithLabel(treatmentVisit.getVisit().getLabel()))
                    .append(Ext4Helper.Locators.formItem());

            if (!_test.isElementPresent(treatmentComboForVisit))
                addVisitToSchedule(treatmentVisit.getVisit(), treatmentVisit.isNewVisit());

            _test.waitForElement(treatmentComboForVisit);
            _test._ext4Helper.selectComboBoxItem(treatmentComboForVisit, Ext4Helper.TextMatchTechnique.CONTAINS, treatmentVisit.getTreatment());
        }

        _test.click(editCohortWindow.append(Ext4Helper.Locators.ext4Button("Submit")));
        _test.waitForElementToDisappear(editCohortWindow);
    }

    private void addVisitToSchedule(Visit visit, boolean isNew)
    {
        Locator.XPathLocator addVisitWindow = Ext4Helper.Locators.window("Add Visit");
        Locator.XPathLocator existingVisitRadio = Locator.tag("inpug").withClass("x4-form-radio")
                .withPredicate(Locator.xpath("following-sibling::label").withText("Select an existing study visit:"));
        String newVisitRadioLabel = "Create a new study visit:";
        Locator.XPathLocator newVisitLabel = Locator.tagWithName("input", "newVisitLabel");
        Locator.XPathLocator newVisitRangeMin = Locator.tagWithName("input", "newVisitRangeMin");
        Locator.XPathLocator newVisitRangeMax = Locator.tagWithName("input", "newVisitRangeMax");

        _test.click(Locator.linkWithText("Add Visit"));
        _test.waitForElement(addVisitWindow);

        if (isNew)
        {
            _test.click(Ext4Helper.Locators.radiobutton(_test, newVisitRadioLabel));
            _test.setFormElement(newVisitLabel, visit.getLabel());
            _test.setFormElement(newVisitRangeMin, visit.getRangeMin().toString());
            _test.setFormElement(newVisitRangeMax, visit.getRangeMax().toString());
            _test.fireEvent(newVisitRangeMax, BaseWebDriverTest.SeleniumEvent.blur);
            _test.click(addVisitWindow.append(Ext4Helper.Locators.ext4Button("Submit")));
        }
        else
        {
            _test._ext4Helper.selectComboBoxItem(Ext4Helper.Locators.formItemWithInputNamed("existingVisit"), Ext4Helper.TextMatchTechnique.CONTAINS, visit.getLabel());
            _test.click(addVisitWindow.append(Ext4Helper.Locators.ext4Button("Select")));
        }

        _test.waitForElementToDisappear(addVisitWindow);
    }

    public static class TreatmentComponent
    {
        private String _label;
        private String _doseAndUnits;
        private String _route;

        public TreatmentComponent(String label, String doseAndUnits, String route)
        {
            _label = label;
            _doseAndUnits = doseAndUnits;
            _route = route;
        }

        public String getLabel()
        {
            return _label;
        }

        public String getDoseAndUnits()
        {
            return _doseAndUnits;
        }

        public String getRoute()
        {
            return _route;
        }
    }

    public static class TreatmentVisit
    {
        private String _treatment;
        private Visit _visit;
        private boolean _isNewVisit;

        public TreatmentVisit(String treatment, Visit visit, boolean isNewVisit)
        {
            _treatment = treatment;
            _visit = visit;
            _isNewVisit = isNewVisit;
        }

        public String getTreatment()
        {
            return _treatment;
        }

        public Visit getVisit()
        {
            return _visit;
        }

        public boolean isNewVisit()
        {
            return _isNewVisit;
        }
    }

    public static class Visit
    {
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

    public static class Locators
    {
        public static Locator.XPathLocator antigenComboBox(String antigenField)
        {
            return Locator.tag("*").withClass("x4-form-item").withDescendant(Locator.tagWithName("input", antigenField)).notHidden();
        }

        private static Locator.XPathLocator fieldContainerWithLabel(String label)
        {
            return Locator.tagWithClass("table", "x4-form-fieldcontainer")
                    .withPredicate(Locator.tagWithText("label", label + ":"));
        }

        public static Locator.XPathLocator treatmentScheduleCohortRow(String label)
        {
            return Locator.id("treatment-schedule-grid").append(Locator.tagWithAttribute("tr", "role", "row").append("/td[1]").withText(label));
        }
    }
}
