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

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.OrderWith;
import org.junit.runner.manipulation.Alphanumeric;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.core.admin.CustomizeSitePage;
import org.labkey.test.pages.mothership.ShowInstallationDetailPage;
import org.labkey.test.pages.test.TestActions;
import org.labkey.test.util.PostgresOnlyTest;
import org.labkey.test.util.mothership.MothershipHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.labkey.test.util.mothership.MothershipHelper.MOTHERSHIP_PROJECT;
import static org.labkey.test.util.mothership.MothershipHelper.SERVER_INSTALLATION_NAME_COLUMN;
import static org.labkey.test.util.mothership.MothershipHelper.TEST_HOST_NAME;

@Category({Daily.class})
@BaseWebDriverTest.ClassTimeout(minutes = 4) @OrderWith(Alphanumeric.class)
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
        _mothershipHelper.enableDebugLoggers();
    }

    @Test
    public void testTopLevelItems() throws Exception
    {
        _mothershipHelper.createUsageReport(MothershipHelper.ReportLevel.ON, true, null);
        ShowInstallationDetailPage installDetail = ShowInstallationDetailPage.beginAt(this, TEST_HOST_NAME);
        String distributionName = getDeployedDistributionName();
        Assert.assertEquals("Distribution name.", distributionName, installDetail.getDistributionName());
        checker().screenShotIfNewError("usage_report_items");
    }

    private String getDeployedDistributionName() throws IOException
    {
        File propsFile = new File(TestFileUtils.getDefaultWebAppRoot(), "WEB-INF/classes/distribution.properties");
        if (propsFile.exists())
        {
            // Deployed from distribution
            Properties props = new Properties();
            try (InputStream in = new FileInputStream(propsFile))
            {
                props.load(in);
            }
            return props.getProperty("name");
        }
        else if (propsFile.getParentFile().isDirectory())
        {
            // Local dev build
            return "localBuild";
        }
        else
        {
            // Couldn't find deploy directory. Might be a remote server; probably shouldn't run this test
            throw new IllegalStateException("Unable to determine expected distribution.");
        }
    }

    @Test
    public void testJsonMetrics() throws Exception
    {
        _mothershipHelper.createUsageReport(MothershipHelper.ReportLevel.ON, false, null);
        assertTextPresent("jsonMetrics",
                "modules",
                "controllerHits", // Should have multiple sections for this across different modules
                "folderTypeCounts",
                "Collaboration", // a folder type guaranteed to exist, different from any module name
                "customViewCounts", // From Query module
                "activeDayCount" // a LOW level metric, should also exist at MEDIUM
        );

        Date lastPing = _mothershipHelper.getLastPing("localhost");

        // These values only have second-level granularity, so wait 1.5 seconds to make sure that the old and
        // new timestamps are different enough for us to notice
        Thread.sleep(1500);

        // Self-report so that we have some metrics to verify
        beginAt(WebTestHelper.buildRelativeUrl("mothership", "selfReportMetrics"));
        assertTextPresent("success");
        Map<String, Object> latestServerInfo = _mothershipHelper.getLatestServerInfo();
        Assert.assertEquals("Self reported metrics were associated with the wrong host.", "localhost",
                latestServerInfo.get(SERVER_INSTALLATION_NAME_COLUMN));
        Date nextPing = (Date) latestServerInfo.get("LastPing");
        if (lastPing == null)
        {
            Assert.assertNotNull("No usage report found.", nextPing);
        }
        else
        {
            Assertions.assertThat(nextPing).as("Should be a new usage report.")
                    .isAfter(lastPing);
        }

        String sessionId = String.valueOf(latestServerInfo.get("MostRecentSession"));

        goToProjectHome("/_mothership");
        goToSchemaBrowser();
        var table = viewQueryData("mothership", "recentJsonMetricValues");
        table.setFilter("ServerSessionId", "Equals", sessionId);
        assertTrue("Should have at least one row, but was " + table.getDataRowCount(), table.getDataRowCount() > 0);
        table.setFilter("DisplayKey", "Contains", "modules.Core.simpleMetricCounts.controllerHits.");
        assertTrue("Should have at least one row, but was " + table.getDataRowCount(), table.getDataRowCount() > 0);
        table.setFilter("DisplayKey", "Equals", "activeDayCount");
        assertEquals("Should have one entry for activeDayCount", 1, table.getDataRowCount());
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
    public void testForwardedRequest() throws Exception
    {
        log("Simulate receiving a report behind a load balancer");
        String forwardedFor = "172.217.5.68"; // The IP address for www.google.com, so unlikely to ever be the real test server IP address
        _mothershipHelper.createUsageReport(MothershipHelper.ReportLevel.ON, true, forwardedFor);
        ShowInstallationDetailPage installDetail = ShowInstallationDetailPage.beginAt(this, TEST_HOST_NAME);
        Assert.assertEquals("Forwarded for", forwardedFor, installDetail.getServerIP());
    }

    @Test @Ignore ("Proof of concept for future testing.")
    public void testAPI() throws Exception
    {
        _mothershipHelper.submitMockUsageReport("fake_server.test", "testServerGUID", "sessionGUID_" + (Math.random() * 1_000_000));
    }

    private int triggerNpeAndGetCount()
    {
        return _mothershipHelper.getReportCount(_mothershipHelper.triggerException(TestActions.ExceptionActions.npe));
    }

    @Test
    public void testServerHostName() throws Exception
    {
        log("Send test server host name from base server url");
        _mothershipHelper.createUsageReport(MothershipHelper.ReportLevel.ON, true, null);

        String hostName = new URI(CustomizeSitePage.beginAt(this).getBaseServerUrl()).getHost();
        String hostName2 = "TEST_" + hostName;

        beginAt(WebTestHelper.buildURL("mothership", MothershipHelper.MOTHERSHIP_PROJECT, "showInstallations"));
        assertElementPresent(Locator.linkWithText(hostName));
        assertElementPresent(Locator.linkWithText(hostName2));
    }

}
