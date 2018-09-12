/*
 * Copyright (c) 2013-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.di.RunTransformResponse;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locators;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Data;
import org.labkey.test.categories.ETL;
import org.labkey.test.pages.dataintegration.ETLScheduler;
import org.labkey.test.util.PortalHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.labkey.test.tests.di.ETLHelper.ETL_SOURCE;

@Category({DailyB.class, Data.class, ETL.class})
@BaseWebDriverTest.ClassTimeout(minutes = 25)
public class ETLTest extends ETLAbstractTest
{
    @Nullable
    @Override
    protected String getProjectName()
    {
        return "ETLTestProject";
    }

    @BeforeClass
    public static void setupProject()
    {
        ETLTest init = (ETLTest) getCurrentTest();
        init.doSetup();
    }

    @Override
    protected boolean isResetInPreTest()
    {
        return true;
    }

    @Test
    public void testRowversionIncremental()
    {
        // Test using a rowversion column as incremental filter
        final String PREFIX = "Subject By Rowversion ";
        _etlHelper.insertSourceRow("400", PREFIX + "1", null);
        _etlHelper.runETL(APPEND_WITH_ROWVERSION);
        _etlHelper.assertInTarget1(PREFIX + "1");
        _etlHelper.insertSourceRow("401", PREFIX + "2", null);
        // This will throw a constraint error if the filter doesn't work
        _etlHelper.runETL(APPEND_WITH_ROWVERSION);
        _etlHelper.assertInTarget1(PREFIX + "2");
    }

    @Test
    public void testMultipleTransactions() throws Exception // Migrated from ETLMultipleTransactionsTest
    {
        final String ETL = "{ETLtest}/multipleTransactions";
        String jobId = _etlHelper.runETL_API(ETL).getJobId();
        _etlHelper.incrementExpectedErrorCount();

        if (WebTestHelper.getDatabaseType().equals(WebTestHelper.DatabaseType.PostgreSQL))
        {
            // See issue 22213; we don't support specifying a transaction size on Postgres when source
            // and target are the same datasource. Make sure the error message happens.
            // Note: it will actually work for small datasets like that used in this test, but larger datasets will give
            // errors from pg and leave the target in a bad state.
            _etlHelper.assertInEtlLogFile(jobId, "not supported on Postgres");
        }
        else
        {
            _etlHelper.assertInEtlLogFile(jobId, "Target transactions will be committed every 2 rows", "Could not convert 'uhoh' for field rowid");
            goToProjectHome();
            _etlHelper.assertInTarget2("xact1 1", "xact1 2");
        }
    }

    @Test
    public void testErrors()
    {
        final String TRANSFORM_KEYCONSTRAINT_ERROR = "{ETLtest}/SimpleETLCausesKeyConstraintViolation";
        final String TRANSFORM_QUERY_ERROR = "{ETLtest}/SimpleETLqueryDoesNotExist";
        final String TRANSFORM_QUERY_ERROR_NAME = "Error Bad Source Query";
        final String TRANSFORM_NOCOL_ERROR = "{ETLtest}/SimpleETLCheckerErrorTimestampColumnNonexistent";
        final String TRANSFORM_BADCAST = "{ETLtest}/badCast";
        final String TRANSFORM_BADTABLE = "{ETLtest}/badTableName";

        final String NO_CHEESEBURGER_TABLE = "Could not find table: etltest.source_cheeseburger";

        goToProjectHome();
        clickTab(DATA_INTEGRATION_TAB);
        assertTextNotPresent("Should not have loaded invalid transform xml", "Error Missing Source");
        _etlHelper.insertSourceRow("0", "Subject 0", null);

        List<String> errors = new ArrayList<>();
        errors.add("AK_etltarget");
        errors.add("duplicate");
        errors.add("ERROR: Error running executeCopy");
        errors.add("org.labkey.api.pipeline.PipelineJobException: Error running executeCopy");
        _etlHelper.runETLandCheckErrors(TRANSFORM_KEYCONSTRAINT_ERROR, true, false, errors);
        errors.clear();

        errors.add(NO_CHEESEBURGER_TABLE);
        _etlHelper.runETLandCheckErrors(TRANSFORM_QUERY_ERROR, false, true, errors);
        // Verify we're showing error on the DataTransforms webpart
        clickTab(DATA_INTEGRATION_TAB);
        assertTextPresent(NO_CHEESEBURGER_TABLE);

        _etlHelper.runETLNoNav(TRANSFORM_NOCOL_ERROR, false, true);
        assertTextPresent("Column not found: source.monkeys");
        errors.clear();

        errors.add("contains value not castable");
        _etlHelper.runETLandCheckErrors(TRANSFORM_BADCAST, false, true, errors);
        errors.clear();

        errors.add("Table not found:");
        waitForElement(Locators.bodyPanel());
        _etlHelper.runETLandCheckErrors(TRANSFORM_BADTABLE, false, true, errors);
        errors.clear();

        clickTab(DATA_INTEGRATION_TAB);
        _etlHelper.enableScheduledRun(TRANSFORM_QUERY_ERROR_NAME);
        //schedule for job is 15 seconds
        sleep(15000);
        _etlHelper.disableScheduledRun(TRANSFORM_QUERY_ERROR_NAME);
        clickTab("Portal");
        Assert.assertTrue(countText("ConfigurationException: " + NO_CHEESEBURGER_TABLE) > 1);
        //no way of knowing error count due to scheduled job running unknown number of times
        pushLocation();
        resetErrors();
        popLocation();
    }

    @Test
    public void testStoredProcTransforms() throws Exception // Migrated from ETLStoredProcedureTest
    {
        final String TRANSFORM_NORMAL_OPERATION_SP = "{ETLtest}/SProcNormalOperation";
        final String TRANSFORM_BAD_NON_ZERO_RETURN_CODE_SP = "{ETLtest}/SProcBadNonZeroReturnCode";
        final String TRANSFORM_BAD_PROCEDURE_NAME_SP = "{ETLtest}/SProcBadProcedureName";
        final String TRANSFORM_PERSISTED_PARAMETER_SP = "{ETLtest}/SProcPersistedParameter";
        final String TRANSFORM_OVERRIDE_PERSISTED_PARAMETER_SP = "{ETLtest}/SProcOverridePersistedParameter";
        final String TRANSFORM_RUN_FILTER_SP = "{ETLtest}/SProcRunFilter";
        final String TRANSFORM_MODIFIED_FILTER_NO_SOURCE_SP = "{ETLtest}/SProcModifiedSinceNoSource";
        final String TRANSFORM_MODIFIED_FILTER_WITH_SOURCE_SP = "{ETLtest}/SProcModifiedSinceWithSource";
        final String TRANSFORM_BAD_MODIFIED_FILTER_WITH_BAD_SOURCE_SP = "{ETLtest}/SProcBadModifiedSinceWithBadSource";

        /*
        Test modes as mapped in the proc etlTest
        1	normal operation
        2	return code > 0
        3	raise error
        4	input/output parameter persistence
        5	override of persisted input/output parameter
        6	Run filter strategy, require @filterRunId
        7   Modified since filter strategy, no source, require @filterStartTimeStamp & @filterEndTimeStamp,
            populated from output of previous run
        8	Modified since filter strategy with source, require @filterStartTimeStamp & @filterEndTimeStamp
            populated from the filter strategy IncrementalStartTime & IncrementalEndTime
        */

        // All tests use the API
        RunTransformResponse rtr;

        // test mode 1, Normal operation
        rtr = _etlHelper.runETL_API(TRANSFORM_NORMAL_OPERATION_SP);
        assertEquals(ETLHelper.COMPLETE, _diHelper.getTransformStatus(rtr.getJobId()));
        _etlHelper.assertInEtlLogFile(rtr.getJobId(), "Test print statement logging", "Test returnMsg logging");

        // test mode 2, return code > 0, should be an error
        rtr = _etlHelper.runETL_API(TRANSFORM_BAD_NON_ZERO_RETURN_CODE_SP);
        assertEquals("ERROR", _diHelper.getTransformStatus(rtr.getJobId()));
        _etlHelper.incrementExpectedErrorCount();
        // That should have an error ERROR: Error: Sproc exited with return code 1

        // try to run a non-existent proc
        rtr = _etlHelper.runETL_API(TRANSFORM_BAD_PROCEDURE_NAME_SP);
        assertEquals("ERROR", _diHelper.getTransformStatus(rtr.getJobId()));
        _etlHelper.incrementExpectedErrorCount(false);
        // Error on missing procedure; over api that didn't create a job

        // test mode 3, raise an error inside the sproc
        rtr = _etlHelper.runETL_API(TRANSFORM_BAD_THROW_ERROR_SP);
        assertEquals("ERROR", _diHelper.getTransformStatus(rtr.getJobId()));
        _etlHelper.incrementExpectedErrorCount(false);

        // test mode 4, parameter persistence. Run twice. First time, the value of the in/out parameter supplied in the xml file
        // is changed in the sproc, which should be persisted into the transformConfiguration.state variable map.
        // The second time, the proc double checks the changed value was persisted and passed in; errors if not.
        _etlHelper.runETL_API(TRANSFORM_PERSISTED_PARAMETER_SP);
        rtr = _etlHelper.runETL_API(TRANSFORM_PERSISTED_PARAMETER_SP);
        assertEquals(ETLHelper.COMPLETE, _diHelper.getTransformStatus(rtr.getJobId()));
        // verify no error

        // test mode 5, override of persisted parameter. Run twice. First time, the value of the in/out parameter supplied in the xml file
        // is changed in the sproc, which should be persisted into the transformConfiguration.state variable map.
        // However, the parameter is set to override=true in the xml, so on the second run the persisted value should be ignored
        // and original value used instead. The proc verifies this and errors if value has changed.
        _etlHelper.runETL_API(TRANSFORM_OVERRIDE_PERSISTED_PARAMETER_SP);
        rtr = _etlHelper.runETL_API(TRANSFORM_OVERRIDE_PERSISTED_PARAMETER_SP);
        assertEquals(ETLHelper.COMPLETE, _diHelper.getTransformStatus(rtr.getJobId()));
        // verify no error

        // test mode 6, RunFilterStrategy specified in the xml. Require filterRunId input parameter to be populated
        _etlHelper.insertTransferRow("142", _etlHelper.getDate(), _etlHelper.getDate(), "new transfer", "added by test automation", "pending");
        rtr = _etlHelper.runETL_API(TRANSFORM_RUN_FILTER_SP);
        assertEquals(ETLHelper.COMPLETE, _diHelper.getTransformStatus(rtr.getJobId()));
        // run again, should be No Work. response status should be 'No Work'
        rtr = _etlHelper.runETL_API(TRANSFORM_RUN_FILTER_SP);
        assertEquals("No work", rtr.getStatus());
        // insert new transfer and run again to test filterRunId was persisted properly for next run
        _etlHelper.insertTransferRow("143", _etlHelper.getDate(), _etlHelper.getDate(), "new transfer", "added by test automation", "pending");
        rtr = _etlHelper.runETL_API(TRANSFORM_RUN_FILTER_SP);
        assertEquals(ETLHelper.COMPLETE, _diHelper.getTransformStatus(rtr.getJobId()));

        // test mode 7, ModifiedSinceFilterStrategy specified in the xml. Require filterStart & End timestamp input parameters to be populated
        rtr = _etlHelper.runETL_API(TRANSFORM_MODIFIED_FILTER_NO_SOURCE_SP);
        assertEquals(ETLHelper.COMPLETE, _diHelper.getTransformStatus(rtr.getJobId()));
        // run again to test filterStartTimeStamp & filterEndTimeStamp was persisted properly for next run
        rtr = _etlHelper.runETL_API(TRANSFORM_MODIFIED_FILTER_NO_SOURCE_SP);
        assertEquals(ETLHelper.COMPLETE, _diHelper.getTransformStatus(rtr.getJobId()));

        // Now test Modified where source is specified
        _etlHelper.insertSourceRow("111", "Sproc Subject 1", "142");
        rtr = _etlHelper.runETL_API(TRANSFORM_MODIFIED_FILTER_WITH_SOURCE_SP);
        assertEquals(ETLHelper.COMPLETE, _diHelper.getTransformStatus(rtr.getJobId()));
        // run again, should be no work
        rtr = _etlHelper.runETL_API(TRANSFORM_MODIFIED_FILTER_WITH_SOURCE_SP);
        assertEquals("No work", rtr.getStatus());
        // insert another source row, should have work
        _etlHelper.insertSourceRow("112", "Sproc Subject 2", "143");
        rtr = _etlHelper.runETL_API(TRANSFORM_MODIFIED_FILTER_WITH_SOURCE_SP);
        assertEquals(ETLHelper.COMPLETE, _diHelper.getTransformStatus(rtr.getJobId()));

        // Bad source name
        try
        {
            _etlHelper.runETL_API(TRANSFORM_BAD_MODIFIED_FILTER_WITH_BAD_SOURCE_SP);
        }
        catch (CommandException e)
        {
            assertTrue("Incorrect exception message on bad source: ", e.getMessage().startsWith("Could not find table"));
            assertEquals("ERROR", _diHelper.getTransformStatusByTransformId(TRANSFORM_BAD_MODIFIED_FILTER_WITH_BAD_SOURCE_SP));
            _etlHelper.incrementExpectedErrorCount(false);
        }

        //Stored proc result set ETL
        _etlHelper.runETL("SProcResultSet");
        _etlHelper.assertInTarget2("Sproc Subject 1", "Sproc Subject 2");
    }


    private final String INSERTED_ID1 = "id1";
    private final String INSERTED_NAME1 = "name1";

    @Test
    public void testColumnMapping()
    {
        // The id and name fields should be swapped by the mapping in the etl
        verifyColumnTransform("appendMappedColumns", INSERTED_NAME1, INSERTED_ID1);
    }

    @Test
    public void testColumnTransforms()
    {
        // A bit more complex. Name field is compound of id, name, and a constant set in the xml. Id field should be blank.
        String expectedName = INSERTED_ID1 + "_" + INSERTED_NAME1 + "_" + "aConstantValue";
        verifyColumnTransform("appendTransformedColumn", null, expectedName);
    }

    private void verifyColumnTransform(String etl, String expectedId, String expectedName)
    {
        _etlHelper.insertSourceRow(INSERTED_ID1, INSERTED_NAME1, null);
        _etlHelper.runETL(etl);
        Map<String, Object> result = executeSelectRowCommand("etltest", "target").getRows().get(0);
        assertEquals("Wrong transformed value for id field", expectedId, result.get("id"));
        assertEquals("Wrong transformed value for name field", expectedName, result.get("name"));
    }

    @Test
    public void testSaveStateMidJob() throws Exception
    {
        final String ETL = _etlHelper.ensureFullIdString("saveStateMidJobWithSleep");
        _etlHelper.runETL_API(ETL, false);
        _etlHelper.waitForStatus(ETL, "PENDING", 3000);
        String state = _diHelper.getTransformState(ETL);
        assertTrue("Midjob state not saved", state.contains("after"));
        assertFalse("Midjob state shouldn't have later step parameters", state.contains("setting1"));
        _etlHelper.waitForStatus(ETL, ETLHelper.COMPLETE, 12000);
        state = _diHelper.getTransformState(ETL);
        assertTrue("State not persisted after job", state.contains("after"));
        assertTrue("State not persisted after job", state.contains("\"setting1\":\"test\""));
    }

    /**
     *  Test persisting global in/out parameters, and chaining global input parameters.
     *
     */
    @Test
    public void testStoredProcGlobalParams() throws Exception
    {
        RunTransformResponse rtr = _etlHelper.runETL_API("SProcGlobalParameters");
        assertEquals("Wrong transform status from using stored proc global parameters.", ETLHelper.COMPLETE, _diHelper.getTransformStatus(rtr.getJobId()));
    }

    @Test
    public void testGatingStoredProc() throws Exception
    {
        final String SPROC_GATE = "SProcGate";
        RunTransformResponse rtr = _etlHelper.runETL_API(SPROC_GATE);
        assertEquals(ETLHelper.COMPLETE, _diHelper.getTransformStatus(rtr.getJobId()));
        rtr = _etlHelper.runETL_API(SPROC_GATE);
        assertEquals("Stored proc failed to gate job.", "NO WORK", rtr.getStatus().toUpperCase());

    }

    @Test
    public void testTruncateAndReset() throws Exception
    {
        _etlHelper.insertSourceRow("trunc111", "Truncate me 1", null);
        _etlHelper.insertSourceRow("trunc222", "Truncate me 2", null);
        _etlHelper.runETL_API(APPEND_SELECT_ALL);
        ETLScheduler scheduler = ETLScheduler.beginAt(this);
        scheduler.transform(APPEND_SELECT_ALL)
                .truncateAndReset()
                .confirmYes();

        _etlHelper.assertNotInTarget1("trunc111", "trunc222", "Truncate me");
    }

    @Test
    public void truncateWithoutDataTransfer() throws Exception
    {
        final String NAME = "row 1";
        _etlHelper.insertSourceRow("1", NAME, "1");
        _etlHelper.runETL_API(APPEND);
        _etlHelper.assertInTarget1(NAME);
        _etlHelper.runETL_API("truncateWithoutDataTransfer");
        _etlHelper.assertNotInTarget1(NAME);
    }

    @Test
    public void customContainerFilter() throws Exception
    {
        // This ETL xml uses a CurrentAndSubfolders containerFilter
        final String APPEND_CONTAINER_FILTER = "{ETLtest}/appendContainerFilter";
        final String MY_ROW = "own row";
        final String CHILD_FOLDER = "child";
        _etlHelper.insertSourceRow("1", MY_ROW, "1");
        _containerHelper.createSubfolder(getProjectName(), CHILD_FOLDER);
        clickFolder(CHILD_FOLDER);
        new PortalHelper(this).addQueryWebPart("Source", ETLHelper.ETL_TEST_SCHEMA, ETL_SOURCE, null);
        final String CHILD_ROW = "child row";
        _etlHelper.insertSourceRow("2", CHILD_ROW, "1", CHILD_FOLDER);
        _etlHelper.runETL_API(APPEND_CONTAINER_FILTER);
        goToProjectHome();
        log("Validating ETL respects containerFilter.");
        _etlHelper.assertInTarget1(MY_ROW);
        _etlHelper.assertInTarget1(CHILD_ROW);
        final String CHILD_ROW_2 = "child row 2";
        _etlHelper.insertSourceRow("3", CHILD_ROW_2, "1", CHILD_FOLDER);
        _etlHelper.runETL_API(APPEND_CONTAINER_FILTER);
        goToProjectHome();
        log("Validating modifiedSinceFilterStrategy is containerFilter aware.");
        _etlHelper.assertInTarget1(CHILD_ROW_2);
    }

    @Test
    public void testBasicMerge() throws Exception
    {
        final String PREFIX = "Subject for merge test";
        final String MERGE_ETL = "{ETLtest}/merge";
        _etlHelper.insertSourceRow("600", PREFIX + "1", null);
        _etlHelper.runETL_API(MERGE_ETL);
        // Check the ETL works at all
        _etlHelper.assertInTarget2(PREFIX + "1");

        Object target2created = executeSelectRowCommand("etltest", "target2").getRows().get(0).get("Created");
        Object sourceCreated = executeSelectRowCommand("etltest", "source").getRows().get(0).get("Created");
        assertEquals("Created field in target2 did not match source", sourceCreated, target2created);

        _etlHelper.insertSourceRow("610", PREFIX + "2", null);
        final String newNameForRow1 = "newNameForRow1";
        _etlHelper.editSourceRow0Name(newNameForRow1);
        _etlHelper.runETL_API(MERGE_ETL, "COMPLETE");

        // Check insert of new and update to existing
        _etlHelper.assertInTarget2(PREFIX + "2", newNameForRow1);
        // Check we really did UPDATE and not insert a new one
        _etlHelper.assertNotInTarget2(PREFIX + "1");
    }

    @Test
    public void testManyColumnMerge() throws Exception
    {
        // Mostly identical coverage as the basic case, but with > 100 columns.
        final String MERGE_ETL = "{ETLtest}/mergeManyColumns";
        final String firstField5 = "55555";
        final String secondField5 = "66666";
        final String modifiedField5 = "77777";
        final String field180val = "180180";

        _etlHelper.do180columnSetup();
        Map<Integer, String> rowMap = new HashMap<>();
        rowMap.put(5, firstField5);
        _etlHelper.insert180columnsRow(rowMap);
        _etlHelper.runETL_API(MERGE_ETL);
        _etlHelper.assertIn180columnTarget(firstField5);
        rowMap.put(5, secondField5);
        _etlHelper.insert180columnsRow(rowMap);
        rowMap.put(5, modifiedField5);
        rowMap.put(180, field180val);
        _etlHelper.edit180columnsRow0(rowMap);
        _etlHelper.runETL_API(MERGE_ETL, "COMPLETE");
        _etlHelper.assertIn180columnTarget(secondField5, modifiedField5, field180val);
        _etlHelper.assertNotIn180columnTarget(firstField5);
    }

    @Test
    public void testMergeWithAlternateKey() throws Exception
    {
        final String PREFIX = "Subject for AlternateKey test";
        final String MERGE_ETL = "{ETLtest}/mergeWithAlternateKey";
        final String ALT_KEY_VAL = "7777";
        _etlHelper.insertSourceRow(ALT_KEY_VAL, PREFIX + "1", null);
        _etlHelper.runETL_API(MERGE_ETL);
        // Check the ETL works at all
        _etlHelper.assertInTarget2(PREFIX + "1");

        // To verify we're merging matching on the alternate key, delete the row we just inserted, and add
        // another with the same alternate key value (set in the xml as the "id" column. If the merge works,
        // we'll update the existing target row instead of creating a new one.
        _etlHelper.deleteSourceRow(ALT_KEY_VAL);
        _etlHelper.insertSourceRow(ALT_KEY_VAL, PREFIX + "2", null);
        _etlHelper.runETL_API(MERGE_ETL);

        // Check update to existing
        _etlHelper.assertInTarget2(PREFIX + "2");
        // Check we really did UPDATE and not insert a new one
        _etlHelper.assertNotInTarget2(PREFIX + "1");
    }

    @Test
    public void testXmlDefinedConstants() throws Exception
    {
        _etlHelper.do180columnSetup();

        log("Verify name field added from xml when not present in source");
        Map<Integer, String> rowMap = new HashMap<>();
        rowMap.put(5, "55555");
        _etlHelper.insert180columnsRow(rowMap);
        _etlHelper.runETL_API("constantsNoMatchingSourceColumn");
        _etlHelper.assertInTarget1("addThisName");

        final String sourceName = "nameFromSource";

        log("Verify name from global xml level used instead of name from source");
        _etlHelper.insertSourceRow("10", sourceName, "47");
        _etlHelper.runETL_API("constantsGlobalOverride");
        _etlHelper.assertNotInTarget1(sourceName);
        _etlHelper.assertInTarget1("useGlobalName");

        _etlHelper.deleteAllRows(ETL_SOURCE);
        log("Verify name from step xml level is used instead of name from source or global level");
        _etlHelper.insertSourceRow("20", sourceName + 2, "47");
        _etlHelper.runETL_API("constantsStepOverride");
        _etlHelper.assertNotInTarget1(sourceName + 2);
        _etlHelper.assertInTarget1("useStepName");
    }

    @Test
    public void testTargetTransaction() throws Exception
    {
        // Added in response to issue 27848. Make sure the write to the target query really does get rolled back if there's an error
        // This is very difficult/impossible to test on Postgres. On SQL Server we take advantage of intentionally tripping against
        // a unique constraint violation on a multi-row insert. If there is a wrapping transaction that gets rolled back, no rows
        // are written. If there is no wrapping transaction or it gets committed, the non-violating row will still get written.
        // This will not happen on Postgres, no matter which order the rows are written, so this first test doesn't really
        // tell us anything.

        final String row1Name = "row1Name";
        final String row2Name = "row2Name";
        final String selectAllEtl = "appendSelectAll";
        log("Verify target transaction is rolled back on error");
        _etlHelper.insertSourceRow("12300", row1Name, "47");
        _etlHelper.runETL_API(selectAllEtl);
        _etlHelper.assertInTarget1(row1Name);
        // Add a second row
        _etlHelper.insertSourceRow("45600", row2Name, "47");
        // Run again - since we're selecting all rows, should get a unique constraint violation as we try to write another row with id=1
        _etlHelper.runETL_JobError(selectAllEtl);
        _etlHelper.incrementExpectedErrorCount();
        // if row2 is in the target, the transaction was committed instead of being rolled back
        _etlHelper.assertNotInTarget1(row2Name);

        // Now test the useTransaction=false setting on the destination in the etl xml
        // Note, this is a dangerous setting that is only recommended for advanced users,
        // This would never pass on Postgres with our current implementation, so include a test here for the opposite
        // case. If that ever starts failing, it's indicator our implementation or Postgres driver has changed
        // and there's other things we need to revisit about PG transactions.
        log("Verify write to target is not wrapped in transaction when useTransaction=false");
        _etlHelper.runETL_JobError(selectAllEtl + "NoTargetTx");
        _etlHelper.incrementExpectedErrorCount();

        if (WebTestHelper.getDatabaseType() == WebTestHelper.DatabaseType.MicrosoftSQLServer) // The real test, which only works on SQL Server
        {
            // There would still be the same error, but row2 should also be inserted.
            _etlHelper.assertInTarget1(row2Name);
        }
        else
        {
            // If this ever starts failing, it's indicator our implementation or Postgres driver has changed
            // and there's other things we need to revisit about PG transactions.
            _etlHelper.assertNotInTarget1(row2Name);
        }
    }

    @Test
    public void testWrappingTransactionOnDestination() throws Exception
    {
        final String firstSourceName = "nameFromSourceFirst";
        final String secondSourceName = "nameFromSourceSecond";
        log("Inserting the row in target2");
        _etlHelper.insertTarget2Row("10","10","targetName","47");

        log("Inserting the row in source");
        _etlHelper.insertSourceRow("1", firstSourceName, "48");
        _etlHelper.insertSourceRow("10", secondSourceName, "47");
        _etlHelper.runETL_API("SourceToTarget2");
        log("Incrementing the counter to match the errors in the log file 2+1");
        _etlHelper.incrementExpectedErrorCount();
        _etlHelper.incrementExpectedErrorCount(false);

        _etlHelper.assertNotInTarget2(firstSourceName);
        _etlHelper.assertInTarget2("targetName");
    }

    @Test
    public void testETLTransactWithSleep() throws Exception
    {
            _etlHelper.do180columnSetup();

            Map<Integer, String> rowMap = new HashMap<>();
            rowMap.put(5, "12345");
            _etlHelper.insert180columnsRow(rowMap);

            String sourceName = "nameFromSourceWithSleep";
            _etlHelper.insertSourceRow("10", sourceName, "47");

            try
            {
                _etlHelper.runETL_API("multipleAppendsWithSleepTransactSource", false);
            }
            catch(CommandException e)
            {
                if (WebTestHelper.getDatabaseType().equals(WebTestHelper.DatabaseType.MicrosoftSQLServer))
                {
                    _etlHelper.incrementExpectedErrorCount(false);
                    assertEquals("Excepted error not found","Transacting the source scope is only available on Postgres data sources.",e.getMessage());
                    return;
                }
                else
                    throw e;
            }
            sleep(1000);
            rowMap.clear();
            rowMap.put(6, "9898");
            _etlHelper.insert180columnsRow(rowMap);
            _etlHelper.waitForStatus("multipleAppendsWithSleepTransactSource", "COMPLETE", 20000);
            _etlHelper.assertIn180columnTarget("12345");
            _etlHelper.assertNotIn180columnTarget("9898");


    }
}
