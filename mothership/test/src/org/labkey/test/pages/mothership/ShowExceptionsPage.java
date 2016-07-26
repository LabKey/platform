package org.labkey.test.pages.mothership;

import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.mothership.MothershipHelper;
import org.openqa.selenium.WebDriver;

public class ShowExceptionsPage extends BaseMothershipPage
{
    private Elements _elements;

    public ShowExceptionsPage(WebDriver driver)
    {
        super(driver);
    }

    public static ShowExceptionsPage beginAt(WebDriverWrapper driver)
    {
        return beginAt(driver, MothershipHelper.MOTHERSHIP_PROJECT);
    }

    public static ShowExceptionsPage beginAt(WebDriverWrapper driver, String containerPath)
    {
        driver.beginAt(WebTestHelper.buildURL("mothership", containerPath, "showExceptions"));
        return new ShowExceptionsPage(driver.getDriver());
    }

    public ExceptionSummaryDataRegion exceptionSummary()
    {
        return elements().exceptionSummary;
    }

    protected Elements elements()
    {
        if (_elements == null)
            _elements = new Elements();
        return _elements;
    }

    private class Elements extends BaseMothershipPage.Elements
    {
        ExceptionSummaryDataRegion exceptionSummary = new ExceptionSummaryDataRegion(getDriver());
    }

    public class ExceptionSummaryDataRegion extends DataRegionTable
    {
        public ExceptionSummaryDataRegion(WebDriver driver)
        {
            super("ExceptionSummary", driver);
        }

        public StackTraceDetailsPage clickStackTrace(int stackTraceId)
        {
            clickAndWait(Locator.linkWithHref("exceptionStackTraceId=" + stackTraceId).findElement(getComponentElement()));
            return new StackTraceDetailsPage(getWrapper().getDriver());
        }

        public void ignoreSelected()
        {
            assignSelectedTo("Ignore");
        }

        public void assignSelectedTo(String assignTo)
        {
            clickHeaderButton("Assign To", true, assignTo);
        }
    }
}