/*
 * Copyright (c) 2016-2018 LabKey Corporation
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
package org.labkey.test.tests.mothership;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.core.admin.CustomizeSitePage;
import org.labkey.test.pages.mothership.ShowInstallationDetailPage;
import org.labkey.test.pages.test.TestActions;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PostgresOnlyTest;
import org.labkey.test.util.mothership.MothershipHelper;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.labkey.test.TestProperties.isTestRunningOnTeamCity;
import static org.labkey.test.util.mothership.MothershipHelper.MOTHERSHIP_PROJECT;

@Category({Daily.class})
@BaseWebDriverTest.ClassTimeout(minutes = 4)
public class MothershipReportTest extends BaseWebDriverTest implements PostgresOnlyTest
{
    private MothershipHelper _mothershipHelper;

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return MOTHERSHIP_PROJECT;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("mothership");
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        // Don't delete the _mothership project
    }

    @Before
    public void preTest() throws Exception
    {
        _mothershipHelper = new MothershipHelper(this);
        // In case the testIgnoreInstallationExceptions() test case didn't reset this flag after itself.
        _mothershipHelper.setIgnoreExceptions(false);
    }

    @Test
    public void testTopLevelItems()
    {
        _mothershipHelper.createUsageReport(MothershipHelper.ReportLevel.ON, true, null);
        ShowInstallationDetailPage installDetail = ShowInstallationDetailPage.beginAt(this);
        String distributionName = isTestRunningOnTeamCity() ? "teamcity" : "localBuild";
        assertTextPresent(distributionName);
    }

    @Test
    public void testJsonMetrics()
    {
        _mothershipHelper.createUsageReport(MothershipHelper.ReportLevel.ON, true, null);
        assertTextPresent("jsonMetrics",
                "modules",
                "controllerHits", // Should have multiple sections for this across different modules
                "folderTypeCounts",
                "Collaboration", // a folder type guaranteed to exist, different from any module name
                "customViewCounts", // From Query module
                "activeDayCount" // a LOW level metric, should also exist at MEDIUM
        );

        // Self-report so that we have some metrics to verify
        String relativeUrl = "/mothership-selfReportMetrics.view";
        beginAt(relativeUrl);

        goToProjectHome("/_mothership");
        goToSchemaBrowser();
        var table = viewQueryData("mothership", "recentJsonMetricValues");
        assertTrue("Should have at least one row, but was " + table.getDataRowCount(), table.getDataRowCount() > 0);
        table.setFilter("DisplayKey", "Equals", "modules.Core.simpleMetricCounts.controllerHits.admin");
        table = new DataRegionTable("query", this);
        assertTrue("Should have at least one row, but was " + table.getDataRowCount(), table.getDataRowCount() > 0);
        table.clearAllFilters();
        table = new DataRegionTable("query", this);
        table.setFilter("DisplayKey", "Equals", "activeDayCount");
        assertTrue("Should have at least one row, but was " + table.getDataRowCount(), table.getDataRowCount() > 0);
    }

    @Test
    public void testIgnoreInstallationExceptions() throws Exception
    {
        int firstCount = triggerNpeAndGetCount();
        int secondCount = triggerNpeAndGetCount();
        // Verify the count incremented
        assertEquals("Second count did not increment correctly.", firstCount + 1, secondCount);
        _mothershipHelper.setIgnoreExceptions(true);
        int thirdCount = triggerNpeAndGetCount();
        assertEquals("Report count incremented; exception that should have been ignored was logged.", secondCount, thirdCount);
        _mothershipHelper.setIgnoreExceptions(false);
    }

    @Test
    public void testForwardedRequest()
    {
        log("Simulate receiving a report behind a load balancer");
        String forwardedFor = "172.217.5.68"; // The IP address for www.google.com, so unlikely to ever be the real test server IP address
        _mothershipHelper.createUsageReport(MothershipHelper.ReportLevel.ON, true, forwardedFor);
        ShowInstallationDetailPage installDetail = ShowInstallationDetailPage.beginAt(this);
        assertTextPresent(forwardedFor);
    }

    private int triggerNpeAndGetCount()
    {
        return _mothershipHelper.getReportCount(_mothershipHelper.triggerException(TestActions.ExceptionActions.npe));
    }

    // TODO: test the View sample report buttons from Customize Site page?
}
