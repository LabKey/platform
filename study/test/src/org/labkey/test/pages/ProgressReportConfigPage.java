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
