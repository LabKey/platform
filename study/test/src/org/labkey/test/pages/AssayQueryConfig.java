/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.test.pages;

import org.labkey.test.Locator;
import org.labkey.test.util.Ext4Helper;
import org.openqa.selenium.WebDriver;

public class AssayQueryConfig extends LabKeyPage
{
    public AssayQueryConfig(WebDriver driver, int index)
    {
        super(driver);
        click(getEditLink(index));
    }

    private Locator getEditLink(int index)
    {
        return Locators.self.append(Locator.tagWithAttribute("span", "dataindex", String.valueOf(index)));
    }

    public void setFolderName(String name)
    {
        setFormElement(Locators.folderName, name);
    }

    public void setSchemaName(String name)
    {
        _ext4Helper.selectComboBoxItem("Schema name:", name);
        waitForElementToDisappear(Ext4Helper.Locators.mask().index(1));
    }

    public void setQueryName(String name)
    {
        _ext4Helper.selectComboBoxItem("Query name:", name);
        waitForElementToDisappear(Ext4Helper.Locators.mask().index(1));
    }

    public void save()
    {
        clickButton("Submit", 0);
    }

    public static class Locators
    {
        public static Locator.XPathLocator self = Locator.xpath("//table[contains(@class, 'assay-summary')]");
        public static Locator.XPathLocator dialog = Locator.xpath("//div[contains(@class, 'labkey-assay-config')]");
        public static Locator.XPathLocator folderName = dialog.append(Locator.input("Folder"));
        public static Locator.XPathLocator schemaName = dialog.append(Locator.input("Schema"));
        public static Locator.XPathLocator queryName = dialog.append(Locator.input("listTable"));
    }
}
