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
import org.openqa.selenium.WebDriver;

public class ProgressReportConfigPage extends LabKeyPage
{
    public ProgressReportConfigPage(WebDriver driver)
    {
        super(driver);
        waitForElement(Locator.tagWithClass("table", "assay-summary"));
    }

    public void setReportName(String name)
    {
        setFormElement(Locators.reportName, name);
    }

    public void setDescription(String description)
    {
        setFormElement(Locators.reportDescription, description);
    }

    public void save()
    {
        clickButton("Save");
    }

    public void cancel()
    {
        clickButton("Cancel");
    }

    public static class Locators
    {
        public static Locator.XPathLocator self = Locator.xpath("//div[contains(@class, 'labkey-report-config')]");
        public static Locator.XPathLocator reportName = self.append(Locator.input("viewName"));
        public static Locator.XPathLocator reportDescription = self.append(Locator.textarea("description"));
        public static Locator.XPathLocator assayConfig = Locator.xpath("//table[contains(@class, 'assay-summary')]");
        public static Locator.XPathLocator editLink = assayConfig.append(Locator.tagWithClass("span", "fa-pencil"));
    }
}
