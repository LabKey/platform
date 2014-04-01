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
package org.labkey.test.pages.DesignerController;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * org.labkey.study.controllers.designer.DesignerController#DesignerAction
 */
public class DesignerTester
{
    private BaseWebDriverTest _test;

    public DesignerTester(BaseWebDriverTest test)
    {
        _test = test;

        _test.waitForElement(Locator.css("input.gwt-TextBox"));
    }

    public void editDesign()
    {
        _test.clickButton("Edit");
        waitForEditReady();
    }

    private void waitForEditReady()
    {
        _test.waitForTextToDisappear("Loading", BaseWebDriverTest.WAIT_FOR_JAVASCRIPT);
        _test.waitForElement(Locator.gwtTextBoxByLabel("Protocol Name"), BaseWebDriverTest.WAIT_FOR_PAGE);
    }

    public void saveDesign()
    {
        _test.waitForElementToDisappear(Locator.tag("a").withText("Save").withClass("labkey-disabled-button"));

        int revisions =  _test.getElementCount(Locator.gwtListBoxByLabel("Revision").append("/option"));

        _test.clickButton("Save", 0);
        Locator successText = Locator.tagWithClass("div", "gwt-Label").withText("Revision " + (revisions + 1) + " saved successfully.");
        if (!_test.waitForElement(successText, 1000, false))
        {
            _test.clickButton("Save", 0); // GWT retry
        }
        _test.waitForElement(successText);
        _test.waitForElement(Locator.tag("a").withText("Save").withClass("labkey-disabled-button"));
    }

    public void finishEditing()
    {
        _test.clickButton("Finished");
    }

    public void setName(String name)
    {
        _test.setFormElement(Locator.gwtTextBoxByLabel("Protocol Name"), name);
    }

    public void setInvestigator(String investigator)
    {
        _test.setFormElement(Locator.gwtTextBoxByLabel("Investigator"), investigator);
    }

    public void setGrant(String grant)
    {
        _test.setFormElement(Locator.gwtTextBoxByLabel("Grant"), grant);
    }

    public void setSpecies(String species)
    {
        _test.setFormElement(Locator.gwtTextBoxByLabel("Species"), species);
    }

    public void setDescription(String description)
    {
        _test.fireEvent(Locator.xpath("//div[contains(text(), 'Click to edit description')]"), BaseWebDriverTest.SeleniumEvent.focus);
        _test.setFormElement(Locator.name("protocolDescription"), description);
    }

    public void addImmunogen(String name, String type, String doseAndUnits, String route)
    {
        int newRowNumber = _test.getElementCount(Locators.immunogenGridRow());

        setImmunogenName(newRowNumber, name);
        setImmunogenType(newRowNumber, type);
        setImmunogenDoseAndUnits(newRowNumber, doseAndUnits);
        setImmunogenRoute(newRowNumber, route);
    }

    public void setImmunogenName(int immunogenRowNumber, String name)
    {
        Locator immunogenNameField = Locators.immunogenGridRow().index(immunogenRowNumber - 1).append("/td[2]/input");
        _test.setFormElement(immunogenNameField, name);
        _test.fireEvent(immunogenNameField, BaseWebDriverTest.SeleniumEvent.change);
    }

    public void setImmunogenType(int immunogenRowNumber, String type)
    {
        Locator immunogenTypeField = Locators.immunogenGridRow().index(immunogenRowNumber - 1).append("/td[3]/select");
        _test.selectOptionByText(immunogenTypeField, type);
    }

    public void setImmunogenDoseAndUnits(int immunogenRowNumber, String doseAndUnits)
    {
        Locator immunogenRouteField = Locators.immunogenGridRow().index(immunogenRowNumber - 1).append("/td[4]/input");
        _test.setFormElement(immunogenRouteField, doseAndUnits);
        _test.fireEvent(immunogenRouteField, BaseWebDriverTest.SeleniumEvent.change);
    }

    public void setImmunogenRoute(int immunogenRowNumber, String route)
    {
        Locator immunogenRouteField = Locators.immunogenGridRow().index(immunogenRowNumber - 1).append("/td[5]/select");
        _test.selectOptionByText(immunogenRouteField, route);
    }

    public void addAntigen(String immunogenName, String gene, String subType)
    {
        throw new RuntimeException("Not yet implemented");
    }

    public void addAdjuvant(String name, @Nullable String doseAndUnits, @Nullable String route)
    {
        int newRowNumber = _test.getElementCount(Locators.adjuvantGridRow());

        setAdjuvantName(newRowNumber, name);
        if (null != doseAndUnits)
            setAdjuvantDoseAndUnits(newRowNumber, doseAndUnits);
        if (null != route)
            setAdjuvantRoute(newRowNumber, route);
    }

    public void setAdjuvantName(int adjuvantRowNumber, String name)
    {
        Locator adjuvantNameField = Locators.adjuvantGridRow().index(adjuvantRowNumber - 1).append("/td[2]/input");
        _test.setFormElement(adjuvantNameField, name);
        _test.fireEvent(adjuvantNameField, BaseWebDriverTest.SeleniumEvent.change);
    }

    public void setAdjuvantDoseAndUnits(int adjuvantRowNumber, String doseAndUnits)
    {
        Locator adjuvantRouteField = Locators.adjuvantGridRow().index(adjuvantRowNumber - 1).append("/td[3]/input");
        _test.setFormElement(adjuvantRouteField, doseAndUnits);
        _test.fireEvent(adjuvantRouteField, BaseWebDriverTest.SeleniumEvent.change);
    }

    public void setAdjuvantRoute(int adjuvantRowNumber, String route)
    {
        Locator adjuvantRouteField = Locators.adjuvantGridRow().index(adjuvantRowNumber - 1).append("/td[4]/select");
        _test.selectOptionByText(adjuvantRouteField, route);
    }

    public void addImmunizationGroup(String name, @Nullable String count)
    {
        int newRowNumber = _test.getElementCount(Locators.immunizationGridRow());

        _test.click(Locator.tagWithText("div", "Add New"));
        if (!_test.waitForElement(Locator.id("DefineGroupDialog"), 1000, false))
        {
            _test.click(Locator.tagWithText("div", "Add New"));
        }
        _test.waitForElement(Locator.id("DefineGroupDialog"));
        _test.setFormElement(Locator.name("newName"), name);
        _test.clickButton("OK", 0);
        _test.waitForElementToDisappear(Locator.id("DefineGroupDialog"), BaseWebDriverTest.WAIT_FOR_JAVASCRIPT);
        _test.waitForElement(Locators.immunizationGridRow().index(newRowNumber - 1));

        if (null != count)
        {
            setImmunizationGroupCount(newRowNumber, count);
        }
    }

    public void setImmunizationGroupCount(int groupRowNumber, String count)
    {
        Locator immunizationGroupCountField = Locators.immunizationGridRow().index(groupRowNumber - 1).append("/td[3]/input");
        _test.setFormElementJS(immunizationGroupCountField, count);
        _test.fireEvent(immunizationGroupCountField, BaseWebDriverTest.SeleniumEvent.change);
    }

    public void addImmunizationTimepoint(String timepoint)
    {
        addImmunizationTimepoint(null, timepoint, null);
    }

    public void addImmunizationTimepoint(@Nullable String name, String timepoint, @Nullable String unit)
    {
        _test.click(Locator.xpath("//table[@id='ImmunizationGrid']//div[contains(text(), 'Add Timepoint')]"));
        if (null != name) _test.setFormElement(Locator.name("timepointName"), name);
        _test.setFormElement(Locator.name("timepointCount"), timepoint);
        if (null != unit) _test.selectOptionByText(Locator.name("timepointUnit"), unit);
        _test.click(Locator.tagWithText("button", "OK"));
    }

    public void defineFirstUndefinedImmunization(List<String> immunogensAndAdjuvants)
    {
        _test.click(Locator.xpath("//div[contains(text(), '(none)')]"));
        _test.waitForElement(Locator.css("div.gwt-DialogBox"));

        for (String immunogenOrAdjuvant : immunogensAndAdjuvants)
        {
            _test.checkCheckbox(Locator.xpath(String.format("//label[text()='%s']/../input", immunogenOrAdjuvant)));
        }

        _test.click(Locator.tagWithText("button", "Done"));
        _test.waitForElementToDisappear(Locator.css("div.gwt-DialogBox"));
    }

    public void setAssayPlan(String description)
    {
        _test.fireEvent(Locator.xpath("//div[contains(text(), 'Click to type assay plan here')]"), BaseWebDriverTest.SeleniumEvent.focus);
        _test.setFormElement(Locator.name("assayPlan"), description);
    }

    public void addAssay(String assay, String lab)
    {
        int newRowNumber = _test.getElementCount(Locators.assayGridRow());

        Locator assayNameField = Locators.assayGridRow().index(newRowNumber - 1).append("/td[2]/select");
        _test.selectOptionByText(assayNameField, assay);

        setAssayLab(newRowNumber, lab);
    }

    public void setAssayLab(int assayRowNumber, String lab)
    {
        Locator assayLabField = Locators.assayGridRow().index(assayRowNumber - 1).append("/td[3]/select");
        _test.selectOptionByText(assayLabField, lab);
    }

    public void addAssayTimepoint(@Nullable String name, String timepoint, @Nullable String unit)
    {
        _test.click(Locator.xpath("//table[@id='AssayGrid']//div[contains(text(), 'Add Timepoint')]"));
        if (null != name) _test.setFormElement(Locator.name("timepointName"), name);
        _test.setFormElement(Locator.name("timepointCount"), timepoint);
        if (null != unit) _test.selectOptionByText(Locator.name("timepointUnit"), unit);
        _test.click(Locator.tagWithText("button", "OK"));
    }

    public void setAssayRequiredForTimepoint(String assayName, String timepointSubstring)
    {
        Locator.XPathLocator assayGridRow = Locators.assayGridRow().withDescendant(Locator.tagWithText("td", assayName));
        Locator.XPathLocator timepointColumnHeader = Locator.id("AssayGrid").append(Locator.tagWithAttribute("div", "title", "Click to modify or delete this timepoint."));
        int timepointIndex = -1;

        List<WebElement> timepointColumnHeaders = timepointColumnHeader.findElements(_test.getDriver());
        for (int i = 0; i < timepointColumnHeaders.size(); i++)
        {
            String timepointLabel = timepointColumnHeaders.get(i).getText();
            if (timepointLabel.contains(timepointSubstring))
            {
                timepointIndex = i;
                break;
            }
        }

        Assert.assertFalse("Could not find Assay timepoint: " + timepointSubstring, timepointIndex < 0);

        _test.checkCheckbox(assayGridRow.append(Locator.tagWithAttribute("input", "type", "checkbox")).index(timepointIndex));
    }

    public static class Locators
    {
        public static Locator.XPathLocator immunogenGridRow()
        {
            return Locator.xpath("//table[@id='ImmunogenGrid']/tbody/tr[./td[1][contains(@class, 'row-header')]]");
        }

        public static Locator.XPathLocator adjuvantGridRow()
        {
            return Locator.xpath("//table[@id='AdjuvantGrid']/tbody/tr[./td[1][contains(@class, 'row-header')]]");
        }

        public static Locator.XPathLocator immunizationGridRow()
        {
            return Locator.xpath("//table[@id='ImmunizationGrid']/tbody/tr[./td[1][contains(@class, 'row-header')]]");
        }

        public static Locator.XPathLocator assayGridRow()
        {
            return Locator.xpath("//table[@id='AssayGrid']/tbody/tr[./td[1][contains(@class, 'row-header')]]");
        }
    }
}
