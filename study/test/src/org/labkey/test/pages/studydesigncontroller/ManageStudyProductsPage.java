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

public class ManageStudyProductsPage extends BaseManageVaccineDesignPage
{
    public ManageStudyProductsPage(BaseWebDriverTest test)
    {
        super(test);
        waitForElements(elements().studyVaccineDesignLoc, 2);
        waitForElements(elements().outerAddRowIconLoc, 2);
    }

    public void addNewImmunogenRow(String label, String immunogenType, int rowIndex)
    {
        clickOuterAddNewRow(elements().immunogensLoc);

        setOuterTextFieldValue(elements().immunogensLoc, "Label", label, rowIndex);
        if (immunogenType != null)
            setOuterComboFieldValue(elements().immunogensLoc, "Type", immunogenType, rowIndex);
    }

    public void addNewImmunogenAntigen(String gene, String subtype, String genBankId, String sequence, int outerRowIndex, int subgridRowIndex)
    {
        clickSubgridAddNewRow(elements().immunogensLoc, "Antigens", outerRowIndex);

        if (gene != null)
            setSubgridComboFieldValue(elements().immunogensLoc, "Antigens", "Gene", gene, outerRowIndex, subgridRowIndex);
        if (subtype != null)
            setSubgridComboFieldValue(elements().immunogensLoc, "Antigens", "SubType", subtype, outerRowIndex, subgridRowIndex);
        if (genBankId != null)
            setSubgridTextFieldValue(elements().immunogensLoc, "Antigens", "GenBankId", genBankId, outerRowIndex, subgridRowIndex);
        if (sequence != null)
            setSubgridTextFieldValue(elements().immunogensLoc, "Antigens", "Sequence", sequence, outerRowIndex, subgridRowIndex);
    }

    public void addNewAdjuvantRow(String label, int rowIndex)
    {
        clickOuterAddNewRow(elements().adjuvantsLoc);

        setOuterTextFieldValue(elements().adjuvantsLoc, "Label", label, rowIndex);
    }

    public void addNewImmunogenDoseAndRoute(String dose, String route, int outerRowIndex, int subgridRowIndex)
    {
        addNewDoseAndRoute(elements().immunogensLoc, dose, route, outerRowIndex, subgridRowIndex);
    }

    public void addNewAdjuvantDoseAndRoute(String dose, String route, int outerRowIndex, int subgridRowIndex)
    {
        addNewDoseAndRoute(elements().adjuvantsLoc, dose, route, outerRowIndex, subgridRowIndex);
    }

    public void addNewDoseAndRoute(Locator.XPathLocator table, String dose, String route, int outerRowIndex, int subgridRowIndex)
    {
        clickSubgridAddNewRow(table, "DoseAndRoute", outerRowIndex);

        if (dose != null)
            setSubgridTextFieldValue(table, "DoseAndRoute", "Dose", dose, outerRowIndex, subgridRowIndex);
        if (route != null)
            setSubgridComboFieldValue(table, "DoseAndRoute", "Route", route, outerRowIndex, subgridRowIndex);
    }

    public void save()
    {
        // TODO handle error case
        elements().saveButton.click();
    }

    public void cancel()
    {
        elements().cancelButton.click();
    }

    protected Elements elements()
    {
        return new Elements();
    }

    protected class Elements extends BaseElements
    {
        Locator.XPathLocator immunogensLoc = Locator.tagWithClass("div", "vaccine-design-immunogens");
        Locator.XPathLocator adjuvantsLoc = Locator.tagWithClass("div", "vaccine-design-adjuvants");
    }
}

