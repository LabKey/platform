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
package org.labkey.test.tests.di;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.api.util.Pair;
import org.labkey.remoteapi.CommandException;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Data;
import org.labkey.test.categories.ETL;
import org.labkey.test.pages.dataintegration.ETLScheduler;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Split test cases from ETLTest as that class was timing out on completing all tests.
 * Tests for ETLs invoking pipeline tasks, queuing/requeuing jobs, batching to
 * multiple output files, and deleting rows are here
 */
@Category({DailyB.class, Data.class, ETL.class})
@BaseWebDriverTest.ClassTimeout(minutes = 18)
public class ETLTestB extends ETLAbstractTest
{
    private static final int DEFAULT_OUTPUT_ROWS = 3;

    @Nullable
    @Override
    protected String getProjectName()
    {
        return "ETLPipelineJobsTestProject";
    }

    @BeforeClass
    public static void setupProject()
    {
        ETLTestB init = (ETLTestB) getCurrentTest();
        init.doSetup();
    }

    @Override
    protected boolean isResetInPreTest()
    {
        return true;
    }

    private void tryAndRetry(String transformId, String expectedError, boolean normalErrorCount)
    {
        _etlHelper.runETLNoNav(transformId, true, false);
        _etlHelper.incrementExpectedErrorCount(normalErrorCount);
        refresh();
        assertTextPresent(expectedError);
        _etlHelper.clickRetryButton();
        _etlHelper.incrementExpectedErrorCount(normalErrorCount);
        refresh();
        assertTextPresent(expectedError, 2);
    }

    @Test
    public void testPipelineFileAnalysisTask() throws Exception
    {
        doSingleFilePipelineFileAnalysis("targetFile", null, DEFAULT_OUTPUT_ROWS);
    }

    /**
     * Test setting an override to default pipeline parameter value via ETL setting.
     * This etl passes -n 2 to the tail command, so the header row should be missing in the output
     *
     */
    @Test
    public void testPipelineTaskParameterOverride() throws Exception
    {
        doSingleFilePipelineFileAnalysis("targetFileParameterOverride", null, 2);
    }

    /**
     * Test queueing one etl from another. Uses pipeline file analysis as that's most relevant for sponsoring client.
     */
    @Test
    public void testQueueAnotherEtl() throws Exception
    {
        doSingleFilePipelineFileAnalysis("targetFileQueueTail", ETL_OUT, DEFAULT_OUTPUT_ROWS);
    }

    private void doSingleFilePipelineFileAnalysis(String etl, @Nullable String outputSubDir, int expectedOutputRows) throws Exception
    {
        insertSingleFileAnalysisSourceData();
        File dir = setupPipelineFileAnalysis(outputSubDir);
        String jobId = _etlHelper.runETL_API(etl).getJobId();
        validatePipelineFileAnalysis(dir, jobId, expectedOutputRows);
    }

    private void insertSingleFileAnalysisSourceData()
    {
        _etlHelper.insertSourceRow("2", "Subject 2", null);
        _etlHelper.insertSourceRow("3", "Subject 3", null);
    }

    /**
     * Test that jobs are serialized correctly so they can be requeued. Using "retry" as standin for requeue on server
     * restart, as it is the same mechanism. For each case, deliberately create an error condition (key violation, etc),
     * and then retry the job. If the same error occurs, the job requeued successfully. (Check the initial error now appears twice
     * in log.)
     */
    @Test
    public void testRequeueJobs() throws Exception
    {
        String FILTER_ERROR_MESSAGE;
        String SPROC_ERROR_MESSAGE = "Intentional SQL Exception From Inside Proc";
        if (WebTestHelper.getDatabaseType() == WebTestHelper.DatabaseType.PostgreSQL)
        {
            FILTER_ERROR_MESSAGE = "violates unique constraint";
        }
        else
        {
            FILTER_ERROR_MESSAGE = "Violation of UNIQUE KEY constraint";
        }

        _etlHelper.insertSourceRow("2", "Subject 2", "1042");

        // SelectAllFilter
        _etlHelper.runETL_API(APPEND_SELECT_ALL);
        tryAndRetry(APPEND_SELECT_ALL, FILTER_ERROR_MESSAGE, true);
        // 23833 Validate that a successful retry gives a status of COMPLETE
        _etlHelper.deleteAllRows(ETLHelper.ETL_TARGET);
        _etlHelper.clickRetryButton();
        assertEquals("Wrong transform status for retried ETL.", ETLHelper.COMPLETE, _diHelper.getTransformStatusByTransformId(_etlHelper.ensureFullIdString(APPEND_SELECT_ALL)));
        ETLScheduler scheduler = ETLScheduler.beginAt(this);
        assertEquals("Error status didn't clear on successful retry for " + APPEND_SELECT_ALL, "COMPLETE", scheduler.transform(APPEND_SELECT_ALL).getLastStatus());

        // ModifiedSinceFilter
        tryAndRetry(APPEND, FILTER_ERROR_MESSAGE, true);

        //RunFilter
        _etlHelper.insertTransferRow("1042", _etlHelper.getDate(), _etlHelper.getDate(), "new transfer", "added by test automation", "pending");
        tryAndRetry(TRANSFORM_BYRUNID, FILTER_ERROR_MESSAGE, true);

        //StoredProc
        tryAndRetry(TRANSFORM_BAD_THROW_ERROR_SP, SPROC_ERROR_MESSAGE, false);

        // Remote Source
        tryAndRetry("remoteInvalidDestinationSchemaName", "ERROR: Target schema not found: study_buddy", true);

        // TaskRef (serialization failure only occurred when taskref task had not yet started)
        tryAndRetry("appendAndTaskRefTask", FILTER_ERROR_MESSAGE, true);
    }

    /**
     * Test requeuing an etl job with a pipeline task in it. Doing this one a little differently than other requeue tests.
     * Cancel the job while it's running, and then on retry verify the job ran to completion.
     *
     */
    @Test
    public void testRetryCancelledPipelineTask() throws IOException, CommandException
    {
        final String TARGET_FILE_WITH_SLEEP = _etlHelper.ensureFullIdString("targetFileWithSleep");
        insertSingleFileAnalysisSourceData();
        File dir = setupPipelineFileAnalysis(ETL_OUT);
        _etlHelper.runETLNoNavNoWait(TARGET_FILE_WITH_SLEEP, true, false);
        refresh();
        clickButton("Cancel");
        // 23829 Validate run status is really CANCELLED
        _etlHelper.waitForStatus(TARGET_FILE_WITH_SLEEP, "CANCELLED", 10000);
        clickTab(DATA_INTEGRATION_TAB);
        ETLScheduler scheduler = new ETLScheduler(this);
        assertEquals("Wrong status for " + TARGET_FILE_WITH_SLEEP, "CANCELLED", scheduler.transform(TARGET_FILE_WITH_SLEEP).getLastStatus());
        scheduler.transform(TARGET_FILE_WITH_SLEEP).clickLastStatus();
        _etlHelper.clickRetryButton();
        _etlHelper.waitForEtl();
        scheduler = ETLScheduler.beginAt(this);
        assertEquals("Wrong status for " + TARGET_FILE_WITH_SLEEP, "COMPLETE", scheduler.transform(TARGET_FILE_WITH_SLEEP).getLastStatus());
        scheduler.transform(TARGET_FILE_WITH_SLEEP).clickLastStatus();
        String jobId = _diHelper.getTransformRunFieldByTransformId(TARGET_FILE_WITH_SLEEP, "jobId");
        validatePipelineFileAnalysis(dir, jobId, DEFAULT_OUTPUT_ROWS);
    }

    @Test
    public void testBatchingToMultipleFiles() throws Exception
    {
        final String BATCH_FILES = "targetBatchedFiles";
        insertMultipleFilesSourceData();
        File dir = setupPipelineFileAnalysis(ETL_OUT);
        String jobId = _etlHelper.runETL_API(BATCH_FILES).getJobId();

        log("Validating output file count and content");
        Pair<List<String[]>, List<String[]>> fileRows = readFile(dir, jobId, 1, false);
        validateFileRow(fileRows.first, 1, "row 1");
        validateFileRow(fileRows.first, 2, "row 2");
        fileRows = readFile(dir, jobId, 2, false);
        validateFileRow(fileRows.first, 1, "row 3");
        validateFileRow(fileRows.first, 2, "row 4");
        fileRows = readFile(dir, jobId, 3, false);
        validateFileRow(fileRows.first, 1, "row 5");
    }

    @Test
    public void testBatchingMultipleFilesByBatchColumn() throws Exception
    {
        final String BATCH_FILES_WITH_BATCH_COLUMN = "targetBatchedFilesWithBatchColumn";
        insertMultipleFilesSourceData();
        File dir = setupPipelineFileAnalysis(ETL_OUT);
        String jobId = _etlHelper.runETL_API(BATCH_FILES_WITH_BATCH_COLUMN).getJobId();

        log("Validating output file count and content");
        Pair<List<String[]>, List<String[]>> fileRows = readFile(dir, jobId, 1, false);
        validateFileRow(fileRows.first, 1, "row 1");
        validateFileRow(fileRows.first, 2, "row 2");
        validateFileRow(fileRows.first, 3, "row 3");
        validateFileRow(fileRows.first, 4, "row 4");
        fileRows = readFile(dir, jobId, 2, false);
        validateFileRow(fileRows.first, 1, "row 5");
    }

    /**
     * Output to multiple files, and queue a pipeline job (tail the file) for each.
     * Verifies that we implicitly allowing of multiple queuing of etl's that start
     * with an external pipeline task as their first step.
     *
     */
    @Test
    public void testBatchingMultipleFilesQueuingMultipleTails() throws Exception
    {
        final String BATCH_FILES_QUEUE_TAIL = "targetBatchedFilesQueueTail";
        insertMultipleFilesSourceData();
        File dir = setupPipelineFileAnalysis(ETL_OUT);
        String jobId = _etlHelper.runETL_API(BATCH_FILES_QUEUE_TAIL).getJobId();

        // Just validate the final output files, the testBatchingMultipleFilesByBatchColumn test case already validated the intermediate files
        log("Validating queued pipeline jobs output file count and content");
        Pair<List<String[]>, List<String[]>> fileRows = readFile(dir, jobId, 1, true);
        validateFileRow(fileRows.second, 1, "row 1");
        validateFileRow(fileRows.second, 2, "row 2");
        fileRows = readFile(dir, jobId, 2, true);
        validateFileRow(fileRows.second, 1, "row 3");
        validateFileRow(fileRows.second, 2, "row 4");
        fileRows = readFile(dir, jobId, 3, true);
        validateFileRow(fileRows.second, 1, "row 5");
    }

    private void insertMultipleFilesSourceData()
    {
        _etlHelper.insertSourceRow("1", "row 1", "1");
        _etlHelper.insertSourceRow("2", "row 2", "1");
        _etlHelper.insertSourceRow("3", "row 3", "2");
        _etlHelper.insertSourceRow("4", "row 4", "2");
        _etlHelper.insertSourceRow("5", "row 5", "3");
    }

    @Test
    public void testDeleteRows() throws Exception
    {

        final String PREFIX = "Subject For Delete Test ";
        _etlHelper.insertSourceRow("500", PREFIX + "1", null);
        _etlHelper.insertSourceRow("501", PREFIX + "2", null);
        _etlHelper.runETL(APPEND_WITH_ROWVERSION);
        _etlHelper.assertInTarget1(PREFIX + "1", PREFIX + "2");
        // Add one of them to the deleted rows source query
        _etlHelper.insertDeleteSourceRow("500", PREFIX + "1", null);
        // Filter column for the append is a rowversion; for the delete is a datetime
        _etlHelper.runETL(APPEND_WITH_ROWVERSION);
        _etlHelper.assertNotInTarget1(PREFIX + "1");
        _etlHelper.assertInTarget1(PREFIX + "2");

        // Now flip which column is which datatype. Filter column for append is a datetime, delete is a rowversion
        _etlHelper.cleanupTestTables();
        _etlHelper.insertSourceRow("502", PREFIX + "3", null);
        _etlHelper.insertSourceRow("503", PREFIX + "4", null);
        _etlHelper.runETL(APPEND);
        _etlHelper.assertInTarget1(PREFIX + "3", PREFIX + "4");
        // Add one of them to the deleted rows source query
        _etlHelper.insertDeleteSourceRow("503", PREFIX + "4", null);
        _etlHelper.runETL(APPEND);
        _etlHelper.assertNotInTarget1(PREFIX + "4");
        _etlHelper.assertInTarget1(PREFIX + "3");
    }

    /**
     * Verify that the job time validation of all ETL steps happens before queuing the job.
     */
    @Test
    public void testPreflightChecks() throws Exception
    {
        final String failJobValidation = "failJobValidation";
        try
        {
            _etlHelper.runETL_API(failJobValidation, false);
        }
        catch (CommandException ex)
        {
         // this is supposed to happen
        }
        String transformRunLog = _diHelper.getTransformRunFieldByTransformId(_etlHelper.ensureFullIdString(failJobValidation), "transformRunLog");
        // String "doesNotExist" comes from validating a step with a well formed, but non-existing, transformId. "malformed" is from a badly formed
        // tranformId; ModuleResourceCache would have thrown an IllegalStateException, which should be caught and reported here.
        final String doesNotExist = "doesNotExist";
        assertTrue("Transform run log did not contain expected string '" + doesNotExist + "': " + transformRunLog, StringUtils.contains(transformRunLog, doesNotExist));
        final String malformed = "***malformed***";
        assertTrue("Transform run log did not contain expected string '" + malformed + "': " + transformRunLog, StringUtils.contains(transformRunLog, malformed));
    }
}
