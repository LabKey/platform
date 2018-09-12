/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.di.RunTransformResponse;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Data;
import org.labkey.test.categories.ETL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Keeping this as a separate class b/c it only needs a subset of the main setup
 */
@Category({DailyB.class, Data.class, ETL.class})
@BaseWebDriverTest.ClassTimeout(minutes = 6)
public class ETLTaskRefTaskTest extends ETLAbstractTest
{
    private static final String PROJECT_NAME = "ETLTaskRefTaskProject";
    private static final String ETL = "{ETLtest}/TaskRefTask";
    public static final String LOG_MESSAGE = "Log from test task";
    private static final String TRANSFORM_SLEEP = "{ETLtest}/SleepTask";
    private static final String TRANSFORM_SLEEP_NAME = "SleepTask";
    private static final String TRANSFORM_SLEEP_ALLOW_MULTIPLE_QUEUE = "{ETLtest}/SleepTaskAllowDoubleQueue";
    private static final String TRANSFORM_SLEEP_ALLOW_MULTIPLE_QUEUE_NAME = "SleepTask Allow Multiple Queueing";

    @Nullable
    @Override
    protected String getProjectName()
    {
        return PROJECT_NAME;
    }

    @BeforeClass
    public static void setupProject()
    {
        ETLTaskRefTaskTest init = (ETLTaskRefTaskTest) getCurrentTest();

        init.doSetup();
    }

    @Override
    protected void doSetup()
    {
        _etlHelper.doBasicSetup();
    }

    @Before
    public void preTest() throws Exception
    {
        _etlHelper.resetCounts();
    }

    @Test
    public void testTaskRefTask() throws Exception
    {
        RunTransformResponse rtr = _etlHelper.runETL_API(ETL);
        assertEquals("COMPLETE", _diHelper.getTransformStatus(rtr.getJobId()));
        _etlHelper.assertInEtlLogFile(rtr.getJobId(), LOG_MESSAGE);

        // "setting1" in the xml has a value of "anything". TaskrefTestTask sets it to "test" to be persisted in TransformState
        assertTrue("Setting1 was not presisted with a value of 'test'.", _diHelper.getTransformState(ETL).contains("\"setting1\":\"test\""));
    }

    /**
    * Include this here b/c it only needs basic setup
    * By default, etls shouldn't be allowed to have multiple instances queued and waiting
    */
    @Test
    public void testBlockDoubleQueue() throws Exception
    {
        tryDoubleQueueing(TRANSFORM_SLEEP, TRANSFORM_SLEEP_NAME, false);
        // Check we're showing the correct status- the job that is sleeping should be in a RUNNING state.
        assertElementPresent(_etlHelper.findLastStatusCell(TRANSFORM_SLEEP, "RUNNING", true));
        // lastly, wait for the queued job to finish running
        clickTab("Pipeline");
        waitForRunningPipelineJobs(30000);
    }

    /**
     * For an etl explicitly configured to allow multiple queuing, verify that we can in fact have multiple instances in the queue
     */
    @Test
    public void testAllowDoubleQueue() throws Exception
    {
        tryDoubleQueueing(TRANSFORM_SLEEP_ALLOW_MULTIPLE_QUEUE, TRANSFORM_SLEEP_ALLOW_MULTIPLE_QUEUE_NAME, true);
        clickTab("Pipeline");
        waitForRunningPipelineJobs(120000);
    }

    private void tryDoubleQueueing(String etl, String etlName, boolean shouldDoubleQueue) throws Exception
    {
        goToProjectHome();
        clickTab("DataIntegration");
        int expectedJobCount = getJobCount() + (shouldDoubleQueue ? 5 : 2);
        _etlHelper.runETL_API(etl, false); // sleeps for 10 second during run
        sleep(50);
        // trigger a second run, it should go into the queued state
        RunTransformResponse rtr = _etlHelper.runETL_API(etl, false);
        assertEquals("Queued", rtr.getStatus());
        _etlHelper.enableScheduledRun(etlName); // this should try to queue one
        sleep(2000); // runs on a 1 sec schedule, so should be trying to queue additional instances.
        _etlHelper.disableScheduledRun(etlName); // immediately disable, so errors don't leave the ETL in the scheduler.
        int actualJobCount = getJobCount();
        // Test the quartz scheduler code path.
        // Should have 1 additional job for the first run, and one from the first queued job from enabling scheduled run. If there are more from the scheduled
        // jobs, we didn't block queueing.
        String errMsg = shouldDoubleQueue ? "Incorrect number of pipeline jobs- if actual < expected, we didn't allow double queuing the job"
                : "Incorrect number of pipeline jobs- if actual > expected, we didn't block double queuing the job";
        assertEquals(errMsg, expectedJobCount, actualJobCount);
        // Test the Run Now code path, which is shared by the API call.
        rtr = _etlHelper.runETL_API(TRANSFORM_SLEEP, false);
        String expectedStatus = shouldDoubleQueue ? "Queued" : "Not queuing job because ETL is already pending.";
        assertEquals("Incorrect status queuing job via API", expectedStatus, rtr.getStatus());
        refresh();
    }

    private int getJobCount() throws Exception
    {
        String query = "SELECT COUNT(*) as jobCount FROM pipeline.Job";
        SelectRowsResponse response = _diHelper.executeQuery("/" + getProjectName(), "pipeline", query);
        return Integer.parseInt(response.getRows().get(0).get("jobCount").toString());
    }
}
