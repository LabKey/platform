/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
import org.labkey.test.categories.DailyB;
import org.labkey.test.pages.core.admin.CustomizeSitePage;
import org.labkey.test.pages.mothership.ShowInstallationDetailPage;
import org.labkey.test.pages.test.TestActions;
import org.labkey.test.util.mothership.MothershipHelper;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.labkey.test.util.mothership.MothershipHelper.MOTHERSHIP_PROJECT;

@Category({DailyB.class})
public class MothershipReportTest extends BaseWebDriverTest
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
    public void testTopLevelItems() throws Exception
    {
        // TODO: Test others

        _mothershipHelper.createUsageReport(MothershipHelper.ReportLevel.MEDIUM, true, null);
        ShowInstallationDetailPage installDetail = ShowInstallationDetailPage.beginAt(this);
        String distributionName = "localBuild";
        assertEquals("Incorrect distribution name", distributionName, installDetail.getDistributionName());
        assertNotNull("Usage reporting level is empty", StringUtils.trimToNull(installDetail.getInstallationValue("Usage Reporting Level")));
        assertNotNull("Exception reporting level is empty", StringUtils.trimToNull(installDetail.getInstallationValue("Exception Reporting Level")));
    }

    @Test
    public void testJsonMetrics() throws Exception
    {
        _mothershipHelper.createUsageReport(MothershipHelper.ReportLevel.MEDIUM, true, null);
        assertTextPresent("jsonMetrics",
                "modules",
                "CoreController", // in the module page hit counts
                "folderTypeCounts",
                "Collaboration", // a folder type guaranteed to exist, different from any module name
                "runCount", // targetedMS runs. TODO: this makes the test dependent on the TargetedMS module. replace this once a base build module registers usage metrics.
                "activeDayCount" // a LOW level metric, should also exist at MEDIUM
        );

        // TODO: Verify jsonMetrics persisted?
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
        _mothershipHelper.createUsageReport(MothershipHelper.ReportLevel.MEDIUM, true, forwardedFor);
        ShowInstallationDetailPage installDetail = ShowInstallationDetailPage.beginAt(this);
        assertEquals("Incorrect forwarded IP address", forwardedFor, installDetail.getServerIP());
    }

    @Test
    public void testServerHostName() throws Exception
    {
        log("Send test server host name from base server url");
        String hostName = "TEST_" + new URI(CustomizeSitePage.beginAt(this).getBaseServerUrl()).getHost();
        _mothershipHelper.createUsageReport(MothershipHelper.ReportLevel.MEDIUM, true, null);
        ShowInstallationDetailPage installDetail = ShowInstallationDetailPage.beginAt(this);
        assertEquals("Incorrect server host name", hostName, installDetail.getServerHostName());
    }

    private int triggerNpeAndGetCount()
    {
        return _mothershipHelper.getReportCount(_mothershipHelper.triggerException(TestActions.ExceptionActions.npe));
    }
    // TODO: Test output of each reporting level

    // TODO: test the View sample report buttons from Customize Site page?
}
