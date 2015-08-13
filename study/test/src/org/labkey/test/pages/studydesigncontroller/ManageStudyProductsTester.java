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

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;

/**
 * org.labkey.study.controllers.StudyDesignController#ManageStudyProductsAction
 */
public class ManageStudyProductsTester
{
    private BaseWebDriverTest _test;

    public ManageStudyProductsTester(BaseWebDriverTest test)
    {
        _test = test;
        _test.waitForElement(Locator.id("immunogens-grid"));
        _test._ext4Helper.waitForMaskToDisappear();
    }

    @LogMethod
    public void insertNewImmunogen(@LoggedParam String label, String type)
    {
        Locator.XPathLocator immunogensGrid = Locator.id("immunogens-grid");
        Locator.XPathLocator insertNewImmunogenButton = immunogensGrid.append(Ext4Helper.Locators.ext4Button("Insert New"));

        _test.click(insertNewImmunogenButton);
        _test.waitForElement(Ext4Helper.Locators.window("Insert Immunogen"));
        _test.setFormElement(Locator.name("Label"), label);
        _test._ext4Helper.selectComboBoxItem(Ext4Helper.Locators.formItemWithLabel("Type:"), Ext4Helper.TextMatchTechnique.CONTAINS, type);
        _test.clickButton("Submit", 0);
        _test._ext4Helper.waitForMaskToDisappear();
    }

    public void editAntigens(String immunogen)
    {
        int antigenColumnNumber = 3;
        _test.doubleClick(Locator.tag("tr").withPredicate(Locator.xpath("td[1]").withText(immunogen)).append("/td[" + antigenColumnNumber + "]"));
        _test.waitForElement(Ext4Helper.Locators.window("Edit HIV Antigens for " + immunogen));
    }

    @LogMethod
    public void insertNewAntigen(@LoggedParam String immunogen, String gene, String subType, String genBankId, String sequence)
    {
        if (genBankId != null || sequence != null)
            throw new IllegalArgumentException("genBankId and sequence are not yet supported");
        _test.click(Ext4Helper.Locators.window("Edit HIV Antigens for " + immunogen).append(Ext4Helper.Locators.ext4Button("Insert New")));
        if (gene != null) _test._ext4Helper.selectComboBoxItem(Locators.antigenComboBox("Gene"), Ext4Helper.TextMatchTechnique.CONTAINS, gene);
        if (subType != null) _test._ext4Helper.selectComboBoxItem(Locators.antigenComboBox("SubType"), Ext4Helper.TextMatchTechnique.CONTAINS, subType);
        _test.click(Ext4Helper.Locators.ext4Button("Update"));
    }

    public void submitAntigens(String immunogen)
    {
        _test.click(Ext4Helper.Locators.window("Edit HIV Antigens for " + immunogen).append(Ext4Helper.Locators.ext4Button("Submit")));
        _test._ext4Helper.waitForMaskToDisappear();
    }

    @LogMethod
    public void insertNewAdjuvant(@LoggedParam String label)
    {
        Locator.XPathLocator adjuvantGrid = Locator.id("adjuvants-grid");
        Locator.XPathLocator insertNewAdjuvantButton = adjuvantGrid.append(Ext4Helper.Locators.ext4Button("Insert New"));

        Locator.XPathLocator insertAdjuvantWindow = Ext4Helper.Locators.window("Insert Adjuvant");

        _test.click(insertNewAdjuvantButton);
        _test.waitForElement(insertAdjuvantWindow);
        _test.setFormElement(Locator.name("Label"), label);
        _test.waitAndClick(insertAdjuvantWindow.append(Ext4Helper.Locators.ext4ButtonEnabled("Submit")));
        _test._ext4Helper.waitForMaskToDisappear();
    }

    public static class Locators
    {
        public static Locator.XPathLocator antigenComboBox(String antigenField)
        {
            return Locator.tag("*").withClass("x4-form-item").withDescendant(Locator.tagWithName("input", antigenField)).notHidden();
        }
    }
}

