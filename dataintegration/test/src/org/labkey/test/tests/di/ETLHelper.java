/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.test.tests.di;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.di.RunTransformResponse;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.components.ext4.Window;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.di.DataIntegrationHelper;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ETLHelper
{
    static final String ETL_TEST_SCHEMA = "etltest";
    static final String ETL_SOURCE = "source";
    static final String ETL_TARGET = "target";
    private static final String ETL_TARGET_2 = "target2";
    private static final String ETL_DELETE = "delete";
    private static final String TRANSFER = "transfer";
    public static final String ERROR = "ERROR";
    public static final String COMPLETE = "COMPLETE";
    static final String DATAINTEGRATION_MODULE = "DataIntegration";
    public static final String DATAINTEGRATION_SCHEMA = "dataintegration";
    public static final String DATAINTEGRATION_ETLDEF = "etlDef";
    private static final String ETL_180_COLUMN_SOURCE = "x180column_source";
    private static final String ETL_180_COLUMN_TARGET = "x180column_target";
    private static final String TITLE_180_COLUMN_SOURCE = "x180ColumnSource";
    private static final String TITLE_180_COLUMN_TARGET = "x180ColumnTarget";
    private BaseWebDriverTest _test;

    private int _jobsComplete;
    private int _expectedErrors;

    private DataIntegrationHelper _diHelper;
    private String _projectName;

    //Internal counters

    // holds expected results for the TransformHistory table.  The transform history table
    // shows all the runs for a specific ETL type
    //
    private HashMap<String, ArrayList<String[]>> _transformHistories = new HashMap<>();

    //
    // holds expected results for the TransformSummary table.  This will show
    // one row for each different ETL type run.
    //
    private ArrayList<String []> _transformSummaries = new ArrayList<>();

    public ETLHelper(ETLAbstractTest test, String projectName)
    {
        _test = test;
        _diHelper = new DataIntegrationHelper("/" + projectName);
        _projectName = projectName;
    }

    public DataIntegrationHelper getDiHelper()
    {
        return _diHelper;
    }

    /**
     *  Increment the expected error count to the correct number of occurrences in the log file
     *  of the string "ERROR" which correspond to the individual error.
     *  <p/>
     *  This can depend on the source of an expected error, and at the moment all errors generate
     *  at least two occurrences anyway.
     *
     * @param twoErrors true when a given error generates two occurrences of the string "ERROR" in the log.
     */
    void incrementExpectedErrorCount(boolean twoErrors)
    {
        _expectedErrors++;
        if (twoErrors)
            _expectedErrors++;
    }

    void incrementExpectedErrorCount()
    {
        // ETL log files usually have two occurrences of the string "ERROR" for every error that occurs.
        incrementExpectedErrorCount(true);
    }

    int getExpectedErrorCount()
    {
        return _expectedErrors;
    }


    void incrementJobsCompleteCount()
    {
        _jobsComplete++;
    }

    void resetCounts()
    {
        _expectedErrors = 0;
        _jobsComplete = 0;
        _transformHistories.clear();
        _transformSummaries.clear();
    }

    //sets 'enabled' checkbox to checked state for given ETL on DataIntegration tab, assumes current tab selected is DataIntegration
    void enableScheduledRun(String transformName)
    {
        _test.checkCheckbox(Locator.xpath("//td[.='" + transformName + "']/../td/input[contains(@onchange, 'Enabled')]"));
    }

    void disableScheduledRun(String transformName)
    {
        _test.uncheckCheckbox(Locator.xpath("//td[.='" + transformName + "']/../td/input[contains(@onchange, 'Enabled')]"));
    }

    //sets 'verbose' checkbox to checked state for given ETL on DataIntegration tab, assumes current tab selected is DataIntegration
    protected void enableVerbose(String transformName)
    {
        _test.checkCheckbox(Locator.xpath("//td[.='" + transformName + "']/../td/input[contains(@onchange, 'Verbose')]"));
    }

    void doBasicSetup()
    {
        doBasicSetup(null);
    }

    public void doBasicSetup(@Nullable String folderType)
    {
        _test.log("running setup");
        _test._containerHelper.createProject(_projectName, folderType);
        _test._containerHelper.enableModules(Arrays.asList(DATAINTEGRATION_MODULE, "ETLtest"));
    }

    protected void doSetup()
    {
        doExtendedSetup(true);
    }

    void doExtendedSetup(boolean addAllWebparts)
    {
        doBasicSetup();
        PortalHelper portalHelper = new PortalHelper(_test);
        portalHelper.addQueryWebPart("Source", ETL_TEST_SCHEMA, ETL_SOURCE, null);
        portalHelper.addQueryWebPart("Target1", ETL_TEST_SCHEMA, ETL_TARGET, null);
        portalHelper.addQueryWebPart("Target2", ETL_TEST_SCHEMA, ETL_TARGET_2, null);

        if (!addAllWebparts)
            return;

        portalHelper.addQueryWebPart("Delete", ETL_TEST_SCHEMA, ETL_DELETE, null);
        portalHelper.addQueryWebPart("Transfers", ETL_TEST_SCHEMA, TRANSFER, null);
        portalHelper.addQueryWebPart("TransformRun", DATAINTEGRATION_SCHEMA, "TransformRun", null);
        portalHelper.addQueryWebPart("TransformHistory", DATAINTEGRATION_SCHEMA, "TransformHistory", null);
        portalHelper.addQueryWebPart("TransformSummary", DATAINTEGRATION_SCHEMA, "TransformSummary", null);
        portalHelper.addWebPart("Data Transform Jobs");
        // make sure the webpart has the 'scheduler' button on it
        _test.clickButton("Scheduler");
        _test.waitForText("View Processed Jobs");
        _test.goBack();
    }

    void do180columnSetup()
    {

        if (!haveDone180ColumnSetup())
        {
            _test.log("Adding query web parts for 180column source and target");
            PortalHelper portalHelper = new PortalHelper(_test);
            portalHelper.addQueryWebPart(TITLE_180_COLUMN_SOURCE, ETL_TEST_SCHEMA, ETL_180_COLUMN_SOURCE, null);
            portalHelper.addQueryWebPart(TITLE_180_COLUMN_TARGET, ETL_TEST_SCHEMA, ETL_180_COLUMN_TARGET, null);
        }
        else
        {
            _test.log("Skipping 180column setup as elements are already present.");
        }
    }

    private boolean haveDone180ColumnSetup()
    {
        _test.clickTab("Portal");
        return _test.isElementPresent(Locators.qwp180columnSource);
    }

    //
    // verify the following:
    // contains expected number of rows (container filter and "no work" filter is there)
    // contains expected number of columns
    // links to file logs work in history and summary UIs
    // links can be traversed from summary -> history -> detail for the given transform id
    //
    private void verifyLogFileLink(String status)
    {
        _test.click(Locator.linkContainingText(status));
        _test.waitForElement(Locator.tag("span").withClass("x4-window-header-text").containing(".etl.log"));
        _test.waitAndClick(Ext4Helper.Locators.ext4ButtonContainingText("Close"));
    }

    void verifyTransformSummary()
    {
        _test.goToProjectHome();
        gotoQueryWebPart("TransformSummary");
        TransformSummaryVerifier verifier = new TransformSummaryVerifier(_transformSummaries);
        verifier.verifyResults();
    }

    private void waitForTransformLink(String linkText, String goBackText, String... waitForTexts)
    {
        _test.log("clicking link with text " + linkText + "'");
        _test.clickAndWait(Locator.linkWithText(linkText));
        _test.waitForText(waitForTexts);
        _test.goBack();
        _test.waitForText(goBackText);
    }

    private void waitForTransformPage(String linkText, String title, String status)
    {
        _test.log("clicking link with text " + linkText + " and status " + status);
        if (_test.isElementPresent(Locator.xpath("//a[.='" + status + "']/../..//a[.='" + linkText + "']")))
        {
            _test.click(Locator.xpath("//a[.='" + status + "']/../..//a[.='" + linkText + "']"));
        }
        else
        {
            _test.click(Locator.xpath("//a[.='" + status + "']/../..//a/nobr[.='" + linkText + "']"));
        }
        // verify title
        _test.log("waiting for title text " + title);
        _test.waitForText(title);
        // wait for data in data region to appear
        _test.log("waiting for status text " + status);
        _test.waitForText(status);
    }

    void verifyTransformHistory(String transformId, String transformDesc)
    {
        verifyTransformHistory(transformId, transformDesc, COMPLETE);
    }

    private void verifyTransformHistory(String transformId, String transformDesc, String status)
    {
        waitForTransformPage(transformId, "Transform History - " + transformDesc, status);
        TransformHistoryVerifier verifier = new TransformHistoryVerifier(transformId, transformDesc,
                _transformHistories.get(transformId));
        verifier.verifyResults();
    }

    void addTransformResult(String transformId, String version, String status, String recordsAffected)
    {
        addTransformSummary(new String[]{transformId, version, null, status, recordsAffected, null, null});
        addTransformHistory(transformId, new String[]{transformId, version, null, status, recordsAffected, null, "Job Details", "Run Details", null});
    }

    // The summary table should only have one row per transform sorted by transform id so make sure
    // our expected results match that
    private void addTransformSummary(String[] newSummary)
    {
        String newTransformId = newSummary[0];
        int insertIdx;
        boolean replace = false;

        for (insertIdx = 0; insertIdx < _transformSummaries.size(); insertIdx++)
        {
            String transformId = _transformSummaries.get(insertIdx)[0];
            int cmp = transformId.compareToIgnoreCase(newTransformId);
            if (cmp >= 0)
            {
                replace = (cmp == 0);
                break;
            }
        }

        if (replace)
            _transformSummaries.remove(insertIdx);

        _transformSummaries.add(insertIdx, newSummary);
    }

    private void addTransformHistory(String transformName, String[] historyRow)
    {
        ArrayList<String[]> rows = null;

        if (_transformHistories.containsKey(transformName))
        {
            rows = _transformHistories.get(transformName);
        }

        if (null == rows)
        {
            rows = new ArrayList<>();
        }

        rows.add(0, historyRow);
        _transformHistories.put(transformName, rows);
    }

    void insertDatasetRow(String id, String name)
    {
        _test.log("inserting dataset row " + name);
        DataRegionTable.DataRegion(_test.getDriver()).waitFor().clickInsertNewRow();
        _test.waitForElement(Locator.name("quf_ParticipantId"));
        _test.setFormElement(Locator.name("quf_ParticipantId"), name);
        _test.setFormElement(Locator.name("quf_date"), getDate());
        _test.setFormElement(Locator.name("quf_id"), id);
        _test.setFormElement(Locator.name("quf_name"), name);
        _test.clickButton("Submit");
    }

    private void insertQueryRow(String id, String name, String RunId, String query)
    {
        insertQueryRow(id, name, RunId, query, null);
    }

    private void insertQueryRow(String id, String name, String RunId, String query, String subFolder)
    {
        _test.log("inserting " + query + " row " + name);
        if (null == subFolder)
            _test.clickTab("Portal");
        else
            _test.clickFolder(subFolder);
        _test.navigateToQuery(ETL_TEST_SCHEMA, query);
        DataRegionTable.DataRegion(_test.getDriver()).waitFor().clickInsertNewRow();
        _test.waitForElement(Locator.name("quf_id"));
        _test.setFormElement(Locator.name("quf_id"), id);
        _test.setFormElement(Locator.name("quf_name"), name);
        if (null != RunId)
        {
            _test.setFormElement(Locator.name("quf_transformrun"), RunId);
        }
        _test.clickButton("Submit");
        _test.log("returning to project home or folder");
        if (null == subFolder)
            _test.clickTab("Portal");
        else
            _test.clickFolder(subFolder);
    }

    void insertSourceRow(String id, String name, String runId, String subFolder)
    {
        insertQueryRow(id, name, runId, "source", subFolder);
    }

    void insertTarget2Row(String rowId, String id, String name , String runId)
    {
        String query = "target2";
        _test.log("inserting target2 row " + name);
        _test.waitAndClickAndWait(Locator.linkWithText(StringUtils.capitalize(query)));
        DataRegionTable.DataRegion(_test.getDriver()).waitFor().clickInsertNewRow();
        _test.waitForElement(Locator.name("quf_rowid"));
        _test.setFormElement(Locator.name("quf_rowid"), rowId);
        _test.setFormElement(Locator.name("quf_id"), id);
        _test.setFormElement(Locator.name("quf_name"), name);
        _test.setFormElement(Locator.name("quf_ditransformrunid"), runId);

        _test.clickButton("Submit");
        _test.log("returning to project home or folder");
    }

    void insertSourceRow(String id, String name, String runId)
    {
        insertQueryRow(id, name, runId, ETL_SOURCE);
    }

    void insertDeleteSourceRow(String id, String name, String runId)
    {
        insertQueryRow(id, name, runId, ETL_DELETE);
    }

    void insertTransferRow(String rowId, String transferStart, String transferComplete, String description, String log, String status)
    {
        _test.log("inserting transfer row rowid " + rowId);
        _test.goToProjectHome();
        _test.clickAndWait(Locator.xpath("//span[text()='Transfers']"));
        DataRegionTable.DataRegion(_test.getDriver()).waitFor().clickInsertNewRow();
        _test.waitForElement(Locator.name("quf_rowid"));
        _test.setFormElement(Locator.name("quf_rowid"), rowId);
        _test.setFormElement(Locator.name("quf_transferstart"), transferStart);
        _test.setFormElement(Locator.name("quf_transfercomplete"), transferComplete);
        _test.setFormElement(Locator.name("quf_description"), description);
        _test.setFormElement(Locator.name("quf_log"), log);
        _test.setFormElement(Locator.name("quf_status"), status);
        _test.clickButton("Submit");
        _test.log("returning to project home");
        _test.clickTab("Portal");
    }

    void editSourceRow0Name(String name)
    {
        _test.log("updating source row " + 0);
        _test.clickTab("Portal");
        _test.waitAndClickAndWait(Locator.linkWithText("Source"));
        DataRegionTable sourceTable = new DataRegionTable("query", _test.getDriver());
        _test.doAndWaitForPageToLoad(()-> sourceTable.updateLink(0).click(), WebDriverWrapper.WAIT_FOR_PAGE);
        _test.waitForElement(Locator.name("quf_name"));
        _test.setFormElement(Locator.name("quf_name"), name);
        _test.clickButton("Submit");
        assertInSource(name);
    }

    /**
     * Note most of the columns in the source and target table are hidden via the schema xml,
     * so if you're trying to verify fields in "field10" to "field179", you'll need to unhide.
     *
     */
    void insert180columnsRow(Map<Integer, String> fieldValues)
    {
        _test.log("inserting row to 180 column table");
        _test.clickTab("Portal");
        _test.waitAndClickAndWait(Locators.qwp180columnSource);
        DataRegionTable.DataRegion(_test.getDriver()).waitFor().clickInsertNewRow();
        _test.waitForElement(Locator.name("quf_field180"));
        fieldValues.forEach((key, value) -> {_test.setFormElement(Locator.name("quf_field" + key), value);});
        _test.clickButton("Submit");
        _test.clickTab("Portal");
    }

    /**
     * Note most of the columns in the source and target table are hidden via the schema xml,
     * so if you're trying to verify fields in "field10" to "field179", you'll need to unhide.
     *
     */
    void edit180columnsRow0(Map<Integer, String> fieldValues)
    {
        _test.log("updating row 0 in 180 column table");
        _test.clickTab("Portal");
        _test.waitAndClickAndWait(Locators.qwp180columnSource);
        DataRegionTable sourceTable = new DataRegionTable("query", _test.getDriver());
        _test.doAndWaitForPageToLoad(()-> sourceTable.updateLink(0).click(), WebDriverWrapper.WAIT_FOR_PAGE);
        _test.waitForElement(Locator.name("quf_field180"));
        fieldValues.forEach((key, value) -> {_test.setFormElement(Locator.name("quf_field" + key), value);});
        _test.clickButton("Submit");
        assertIn180ColumnSource(fieldValues.values().toArray(new String[fieldValues.size()]));
    }

    /**
     * Note most of the columns in the source and target table are hidden via the schema xml,
     * so if you're trying to verify fields in "field10" to "field179", you'll need to unhide.
     *
     */
    void assertIn180columnTarget(String... targets)
    {
        assertQueryWebPart(ETL_180_COLUMN_TARGET, TITLE_180_COLUMN_TARGET, true, targets);
    }

    /**
     * Note most of the columns in the source and target table are hidden via the schema xml,
     * so if you're trying to verify fields in "field10" to "field179", you'll need to unhide.
     *
     */
    void assertNotIn180columnTarget(String... targets)
    {
        assertQueryWebPart(ETL_180_COLUMN_TARGET, TITLE_180_COLUMN_TARGET, false, targets);
    }

    public void runETL(String transformId)
    {
        _runETL(transformId, true, false, false);
    }

    public void runETL_JobError(String transformId)
    {
        _runETL(transformId, true, false, true);
    }

    protected void runETL_CheckerError(String transformId)
    {
        _runETL(transformId, true, true, false);
    }

    void runETL_NoWork(String transformId)
    {
        _runETL(transformId, false, false, false);
    }

    private void _runETL(String transformId, boolean hasWork, boolean hasCheckerError, boolean expectExecutionError)
    {
        runETLNoNav(transformId, hasWork, hasCheckerError, true);

        if (hasWork)
            assertEquals("Wrong job status", expectExecutionError ? ERROR : COMPLETE, getEtlStatus());

        _test.log("returning to project home");
        _test.goToProjectHome();
    }

    void runETLNoNav(String transformId, boolean hasWork, boolean hasCheckerError)
    {
        runETLNoNav(transformId, hasWork, hasCheckerError, true);
    }

    void runETLNoNavNoWait(String transformId, boolean hasWork, boolean hasCheckerError)
    {
        runETLNoNav(transformId, hasWork, hasCheckerError, false);
    }

    private void runETLNoNav(String transformId, boolean hasWork, boolean hasCheckerError, boolean wait)
    {
        _test.log("running " + transformId + " job");
        _test.goToModule(DATAINTEGRATION_MODULE);

        if (hasWork && !hasCheckerError)
        {
            // pipeline job will run
            _test.waitAndClickAndWait(findRunNowButton(transformId));
            if (wait)
            {
                waitForEtl();
            }
            _jobsComplete++;
        }
        else
        {
            // pipeline job does not run
            _test.waitAndClick(findRunNowButton(transformId));
            new Window(hasCheckerError ? "Error" : "Success", _test.getDriver()).clickButton("OK", 0);
        }
    }

    void waitForEtl()
    {
        BaseWebDriverTest.waitFor(() -> {
                    String status = getEtlStatus();
                    if (ERROR.equals(status) || COMPLETE.equals(status))
                        return true;
                    _test.refresh();
                    return false;
                },
                "ETL did not finish", BaseWebDriverTest.WAIT_FOR_PAGE);
    }

    private String getEtlStatus()
    {
        return DataRegionTable.Locators.form("StatusFiles").append(
                Locator.byClass("lk-form-label").withText("Status:"))
                .followingSibling("td").findElement(_test.getDriver()).getText();
    }

    private Locator.XPathLocator findTransformConfigCell(String transformId, boolean isLink)
    {
        transformId = ensureFullIdString(transformId);
        Locator.XPathLocator baseCell = Locator.xpath("//tr[@transformid='" + transformId + "']/td");
        return (isLink) ? baseCell.child("a") : baseCell;
    }

    private Locator.XPathLocator findRunNowButton(String transformId)
    {
        return findTransformConfigCell(transformId, true).withDescendant(Locator.xpath("span")).withText("run now");
    }

    Locator.XPathLocator findLastStatusCell(String transformId, String status, boolean isLink)
    {
        return findTransformConfigCell(transformId, isLink).withText(status);
    }

    RunTransformResponse runETL_API(String transformId, boolean wait) throws Exception
    {
        _test.log("running " + transformId + " job");
        transformId = ensureFullIdString(transformId);
        return wait ? _diHelper.runTransformAndWait(transformId, 30000) : _diHelper.runTransform(transformId);
    }

    String ensureFullIdString(String transformId)
    {
        if (!StringUtils.startsWith(transformId, "{"))
        {
            transformId = "{ETLtest}/" + transformId;
        }
        return transformId;
    }

    void waitForStatus(String transformId, @NotNull String status, int msTimeout) throws IOException, CommandException
    {
        String currentStatus;
        long startTime = System.currentTimeMillis();
        do
        {
            currentStatus = _diHelper.getTransformStatusByTransformId(ensureFullIdString(transformId));
            if (status.equalsIgnoreCase(currentStatus))
                return;
            else
                _diHelper.sleep(500);
        }while(System.currentTimeMillis() - startTime < msTimeout);
        throw new TestTimeoutException("Timeout for ETL status. Current status = " + currentStatus + ". Exceeded " + msTimeout + "ms");
    }

    RunTransformResponse runETL_API(String transformId) throws Exception
    {
        return runETL_API(transformId, true);
    }

    public void runETL_API(String transformId, String expectedStatus) throws Exception
    {
        final String jobId = runETL_API(transformId).getJobId();
        assertEquals("ETL did not have expected status", expectedStatus, _diHelper.getTransformStatus(jobId).toUpperCase());
    }

    void clickRetryButton()
    {
        _test.clickAndWait(_test.waitForElementWithRefresh(Locator.lkButton("Retry"), BaseWebDriverTest.WAIT_FOR_PAGE));
    }

    @LogMethod
    void deleteSourceRow(@LoggedParam String... ids)
    {
        _test.goToProjectHome();
        _test.clickAndWait(Locator.xpath("//span[text()='Source']"));
        DataRegionTable query = new DataRegionTable("query", _test.getDriver());
        for (String id : ids)
        {
            query.checkCheckbox(query.getRowIndex("Id", id));
        }
        _test.doAndWaitForPageToLoad(() ->
        {
            query.clickHeaderButton("Delete");
            // eat the alert without spewing to the log file
            _test.acceptAlert();
        });
        _test.log("returning to project home");
        _test.clickTab("Portal");
    }

    void cleanupTestTables() throws Exception
    {
        deleteAllRows(ETL_SOURCE);
        deleteAllRows(ETL_TARGET);
        deleteAllRows(ETL_TARGET_2);
        deleteAllRows(ETL_DELETE);
        deleteAllRows(TRANSFER);
        deleteAllRows(ETL_180_COLUMN_SOURCE);
        deleteAllRows(ETL_180_COLUMN_TARGET);
    }

    void deleteAllRows(String tableName) throws Exception
    {
        _test.deleteAllRows(_projectName, ETL_TEST_SCHEMA, tableName);
    }

    void assertInSource(String... targets)
    {
        assertQueryWebPart(ETL_SOURCE, "Source", true, targets);
    }

    void assertIn180ColumnSource(String... targets)
    {
        assertQueryWebPart(ETL_180_COLUMN_SOURCE, TITLE_180_COLUMN_SOURCE, true, targets);
    }

    void assertInTarget1(String... targets)
    {
        assertQueryWebPart("target", "Target1", true, targets);
    }

    void assertInTarget2(String... targets)
    {
        assertQueryWebPart(ETL_TARGET_2, "Target2", true, targets);
    }

    void assertInTarget1_Api(String targetName) throws IOException, CommandException
    {
        SelectRowsResponse rsp = _diHelper.executeQuery("/" + _projectName, ETL_TEST_SCHEMA, "SELECT * FROM etltest.target WHERE name = '" + targetName +"'");
        assertTrue("Target name '" + targetName + "' not found", rsp.getRowCount().intValue() > 0) ;
    }

    void assertInTarget2_Api(String targetName) throws IOException, CommandException
    {
        assertTrue("Target name '" + targetName + "' not found", isInTarget2(targetName));
    }

    boolean isInTarget2(String targetName) throws IOException, CommandException
    {
        SelectRowsResponse rsp = _diHelper.executeQuery("/" + _projectName, ETL_TEST_SCHEMA, "SELECT * FROM etltest.target2 WHERE name = '" + targetName +"'");
        return rsp.getRowCount().intValue() > 0;
    }

    void assertNotInTarget1_Api(String targetName) throws IOException, CommandException
    {
        SelectRowsResponse rsp = _diHelper.executeQuery("/" + _projectName, ETL_TEST_SCHEMA, "SELECT * FROM etltest.target WHERE name = '" + targetName +"'");
        assertEquals("Target name '" + targetName + "' was present", 0, rsp.getRowCount().intValue());
    }

    void assertNotInTarget2_Api(String targetName) throws IOException, CommandException
    {
        SelectRowsResponse rsp = _diHelper.executeQuery("/" + _projectName, ETL_TEST_SCHEMA, "SELECT * FROM etltest.target2 WHERE name = '" + targetName +"'");
        assertFalse("Target name '" + targetName + "' was present", isInTarget2(targetName));
    }
    private void gotoQueryWebPart(String webpartName)
    {
        gotoQueryWebPart(webpartName, webpartName);
    }

    void gotoDataset(String name)
    {
        _test.clickTab("Study");
        _test.waitAndClickAndWait(Locator.linkContainingText("2 datasets"));
        _test.clickAndWait(Locator.linkContainingText(name));
    }

    void assertInDatasetTarget1(String... targets)
    {
        gotoDataset("ETL Target");
        _test.assertTextPresent(targets);
    }

    protected void assertNotInDatasetTarget1(String... targets)
    {
        gotoDataset("ETL Target");
        _test.assertTextNotPresent(targets);
    }

    private void gotoQueryWebPart(String webpartName, String queryName)
    {
        _test.clickTab("Portal");
        _test.waitAndClickAndWait(Locator.xpath("//span[text()='" + queryName + "']"));
        _test.waitForText(webpartName);
    }

    private void assertQueryWebPart(String webpartName, String queryName, boolean assertTextPresent, String... targets)
    {
        gotoQueryWebPart(webpartName, queryName);
        if (assertTextPresent)
            _test.assertTextPresent(targets);
        else
            _test.assertTextNotPresent(targets);
    }

    void assertNotInTarget1(String... targets)
    {
        assertQueryWebPart("target", "Target1", false, targets);
    }

    void assertNotInTarget2(String... targets)
    {
        assertQueryWebPart("target2", "Target2", false, targets);
    }

    protected void assertInLog(String... targets)
    {
        assertQueryWebPart("TransformRun", "TransformRun", true, targets);
    }

    void assertInEtlLogFile(String jobId, String... logStrings) throws Exception
    {
        // Promoted the guts of this method to DataIntegrationHelper
        _diHelper.assertInEtlLogFile(jobId, logStrings);
    }

    void checkRun()
    {
        checkRun(false);
    }

    void checkRun(boolean expectError)
    {
        _test.goToModule("Pipeline");
        _test.waitForPipelineJobsToComplete(_jobsComplete, "ETL Job", expectError);
    }

    protected String getDate()
    {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return format.format(Calendar.getInstance().getTime());
    }

    protected void verifyErrorLog(String transformName, List<String> errors)
    {
        _test.clickAndWait(Locator.xpath("//a[.='" + transformName + "']//..//..//a[.='ERROR']"));

        _test.assertTextPresent(errors);
    }

    void runETLandCheckErrors(String ETLName, boolean hasWork, boolean hasCheckerError, List<String> errors)
    {
        runETLNoNav(ETLName, hasWork, hasCheckerError);
        if (hasCheckerError)
            _test.goToProjectHome();
        else
        {
            _test.refresh(); // log webpart may not yet be present
            _test.waitAndClickAndWait(Locator.linkWithText("Show full log file"));
            _test.waitForElement(Locator.linkWithText("Show summary"));
        }
        _test.assertTextPresentCaseInsensitive(errors);
    }

    class BaseTransformVerifier
    {
        // table of results to verify
        String[] _columns;
        ArrayList<String[]> _data;

        BaseTransformVerifier(String[] columns, ArrayList<String[]> data)
        {
            _columns = columns;
            _data = data;
        }

        protected String getDataRegionName()
        {
            return "query";
        }

        //
        // use column names to verify data
        //
        void verifyResults()
        {
            DataRegionTable drt = new DataRegionTable(getDataRegionName(), _test.getDriver());
            assertEquals(_columns.length, drt.getColumnCount());
            assertEquals(_data.size(), drt.getDataRowCount());

            for (int row = 0; row < _data.size(); row++)
            {
                for (int col = 0; col < _columns.length; col++)
                {
                    //
                    // compare data order now using the expected column names
                    // note that this is a bit brittle as the data must be in the
                    // order of the columns
                    //
                    // if the "expected" data is null, then just verify that
                    // the actual data is non-null.  An expected value of "null"
                    // just means that it is not easily comparable (execution times, for example)
                    //
                    String actual = drt.getDataAsText(row, _columns[col]);
                    String expected = _data.get(row)[col];
                    if (null != expected)
                        assertTrue("Expected value " + expected + " in row " + String.valueOf(row + 1) + " column " + String.valueOf(col + 1) + " of DataRegion " + drt.getDataRegionName() + " but found " + actual, actual.equalsIgnoreCase(expected));
                }
            }
        }
    }

    private class TransformSummaryVerifier extends BaseTransformVerifier
    {
        TransformSummaryVerifier(ArrayList<String[]> data)
        {
            super(new String[]{
                    "Name",
                    "Version",
                    "Last Run",
                    "Last Status",
                    "Records Processed",
                    "Execution Time",
                    "Transform Run Log"
            }, data);
        }

        @Override
        protected void verifyResults()
        {
            super.verifyResults();

            DataRegionTable dataRegion = new DataRegionTable(getDataRegionName(), _test.getDriver());

            // just spot check the file log
            String status = dataRegion.getDataAsText(0, "Last Status");
            verifyLogFileLink(status);
        }
    }

    private class TransformHistoryVerifier extends BaseTransformVerifier
    {
        String _transformId;
        String _transformDesc;

        TransformHistoryVerifier(String transformId, String transformDesc, ArrayList<String[]> data)
        {
            super(new String[]{
                    "Name",
                    "Version",
                    "Date Run",
                    "Status",
                    "Records Processed",
                    "Execution Time",
                    "Job Info",
                    "Run Info",
                    "Transform Run Log"
            }, data);

            _transformId = transformId;
            _transformDesc = transformDesc;
        }

        @Override
        protected String getDataRegionName()
        {
            return _test.getAttribute(Locator.xpath("//*[starts-with(@lk-region-name, 'aqwp')]"), "lk-region-name");
        }

        @Override
        protected void verifyResults()
        {
            super.verifyResults();

            DataRegionTable dataRegion = new DataRegionTable(getDataRegionName(), _test.getDriver());

            // walk through all the history rows and verify the link to the file log works (just the first one)
            // and the links work for the transform details page, job details, and run details
            for (int row = 0; row < _data.size(); row ++)
            {
                String status = dataRegion.getDataAsText(row, "Status");
                if (0 == row)
                {
                    verifyLogFileLink(status);
                }
                String run = dataRegion.getDataAsText(row, "Name");
                verifyTransformDetails(run, status);
                _test.goBack();
                // wait for the grid to reload
                _test.waitForText("Run Details");

                // verify job link
                String job = dataRegion.getDataAsText(row, "Job Info");
                waitForTransformLink(job, "Run Details", "Pipeline Jobs", status);

                // verify experiment run link
                String exp = dataRegion.getDataAsText(row, "Run Info");
                waitForTransformLink(exp, "Run Details", "Run Details", run);
            }
        }

        void verifyTransformDetails(String run, String status)
        {
            waitForTransformPage(run, "Transform Details - " + _transformDesc, status);
            TransformDetailsVerifier verifier = new TransformDetailsVerifier(_transformId);
            verifier.verifyResults();
        }

    }

    // currently this has the same schema as the TransformRuns table
    private class TransformDetailsVerifier extends BaseTransformVerifier
    {
        String _transformId;
        TransformDetailsVerifier(String transformId)
        {

            super(new String[]{
                    "Transform Run Id",
                    "Container",
                    "Record Count",
                    "Transform Id",
                    "Transform Version",
                    "Status",
                    "Start Time",
                    "End Time",
                    "Created",
                    "Created By",
                    "Modified",
                    "Modified By",
                    "Job Id",
                    "Transform Run Log"}, null);

            _transformId = transformId;
        }

        @Override
        protected String getDataRegionName()
        {
            return _test.getAttribute(Locator.xpath("//*[starts-with(@lk-region-name, 'aqwp')]"), "lk-region-name");
        }

        // just verify that we have a single row with the transform id we expect
        @Override
        protected void verifyResults()
        {
            DataRegionTable drt = new DataRegionTable(getDataRegionName(), _test.getDriver());
            assertEquals("column mismatch for data region " + drt.getDataRegionName(), Arrays.asList(_columns), drt.getColumnLabels());
            assertEquals(1, drt.getDataRowCount());
            String actual = drt.getDataAsText(0, "Transform Id");
            assertTrue(_transformId.equalsIgnoreCase(actual));
        }
    }

    private static class Locators
    {
        static Locator qwp180columnSource = Locator.linkWithText(TITLE_180_COLUMN_SOURCE);
    }
}
