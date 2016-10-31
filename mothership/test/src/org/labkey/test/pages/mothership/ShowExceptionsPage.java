/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.test.pages.mothership;

import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.mothership.MothershipHelper;
import org.openqa.selenium.WebDriver;

public class ShowExceptionsPage extends BaseMothershipPage<ShowExceptionsPage.ElementCache>
{
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
        return elementCache().exceptionSummary;
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends BaseMothershipPage.ElementCache
    {
        final ExceptionSummaryDataRegion exceptionSummary = new ExceptionSummaryDataRegion(getDriver());
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
            clickHeaderMenu("Assign To", true, assignTo);
        }
    }
}