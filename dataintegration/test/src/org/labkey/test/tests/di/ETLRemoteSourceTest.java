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

import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestFileUtils;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Data;
import org.labkey.test.categories.ETL;
import org.labkey.test.util.RemoteConnectionHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * This should always be a separate class than the main ETLTest, as the test cases here rely on a study import.
 * Having that import as part of the setup for all the ETL tests adds substantial unnecessary overhead time.
 */
@Category({DailyB.class, Data.class, ETL.class})
@BaseWebDriverTest.ClassTimeout(minutes = 9)
public class ETLRemoteSourceTest extends ETLAbstractTest
{
    private static final String TRANSFORM_REMOTE = "{ETLtest}/remote";
    private static final String TRANSFORM_REMOTE_DESC = "Remote Test";
    private static final String TRANSFORM_REMOTE_CONNECTION = "EtlTest_RemoteConnection";
    private static final String TRANSFORM_REMOTE_BAD_DEST = "{ETLtest}/remoteInvalidDestinationSchemaName";
    private static final String TRANSFORM_REMOTE_NOTRUNC = "{ETLtest}/remote_noTruncate";
    private static final File TRANSFORM_REMOTE_STUDY = TestFileUtils.getSampleData( "dataintegration/ETLTestStudy.zip");

    @Nullable
    @Override
    protected String getProjectName()
    {
        return "ETLRemoteSourceTestProject";
    }

    @BeforeClass
    public static void setupProject()
    {
        ETLRemoteSourceTest init = (ETLRemoteSourceTest) getCurrentTest();

        init.doSetup();
    }

    @Override
    protected void doSetup()
    {
        super.doSetup();
        _containerHelper.enableModule("Study");
        //
        // import our study (used by the remote transform tests).  This study is a continuous time point
        // study to use the same type of ETL source and target that one of our customer uses.
        //
        clickTab("Study");
        importStudyFromZip(TRANSFORM_REMOTE_STUDY, true /*ignore query validation*/);
    }

    @Before
    public void preTest() throws Exception
    {
        goToProjectHome();
        deleteRemoteConnection();
        _etlHelper.cleanupTestTables();
    }

    private void createRemoteConnection()
    {
        RemoteConnectionHelper rconnHelper = new RemoteConnectionHelper(this);
        rconnHelper.createConnection(TRANSFORM_REMOTE_CONNECTION, WebTestHelper.getBaseURL(), getProjectName());
    }

    private void deleteRemoteConnection()
    {
        RemoteConnectionHelper rconnHelper = new RemoteConnectionHelper(this);
        rconnHelper.deleteConnection(TRANSFORM_REMOTE_CONNECTION);
    }

    //
    // test a "remote" ETL that transfers data into datasets (instead of just base tables)
    //
    @Test
    public void testRemoteTransform()
    {
        // bump our pipeline job count since we used the pipeline to import the study
        _etlHelper.incrementJobsCompleteCount();
        //
        // prepare our "remote" source dataset
        //
        _etlHelper.gotoDataset("ETL Source");
        for (int i = 1; i < 4; i++)
        {
            _etlHelper.insertDatasetRow(String.valueOf(i), "Subject " + String.valueOf(i));
        }

        _etlHelper.runETL_JobError(TRANSFORM_REMOTE);
        _etlHelper.incrementExpectedErrorCount();
        _etlHelper.checkRun(true /*expect error*/);

        createRemoteConnection();

        //
        // run the remote transform again.  At the end of this we should have one summary entry for the
        // most recently run transform and then two history entries (1 error, 1 complete)
        //
        _etlHelper.runETL(TRANSFORM_REMOTE);
        // note we do expect an error in the pipeline log from the above failure
        _etlHelper.checkRun(true /*expect error*/);
        _etlHelper.addTransformResult(TRANSFORM_REMOTE, "1", "COMPLETE", "3");
        log("Verify basic remote source transform worked");
        _etlHelper.assertInDatasetTarget1("Subject 1", "Subject 2");
        log("Verify named parameter override worked");
        _etlHelper.assertInDatasetTarget1("etlOverride");
        _etlHelper.verifyTransformSummary();
        _etlHelper.verifyTransformHistory(TRANSFORM_REMOTE, TRANSFORM_REMOTE_DESC);
    }

    @Test
    public void testTransformError()
    {
        List<String> errors = new ArrayList<>();
        //run remote etl without remote connection configured
        errors.add("ERROR: The remote connection EtlTest_RemoteConnection has not yet been setup in the remote connection manager.  You may configure a new remote connection through the schema browser.");
        errors.add("ERROR: Error running executeCopy");
        _etlHelper.runETLandCheckErrors(TRANSFORM_REMOTE, true, false, errors);
        _etlHelper.incrementExpectedErrorCount();
        errors.clear();

        // create our remote connection
        createRemoteConnection();
        errors.add("ERROR: Target schema not found: study_buddy");
        errors.add("Error running executeCopy");
        _etlHelper.runETLandCheckErrors(TRANSFORM_REMOTE_BAD_DEST, true, false, errors);
        _etlHelper.incrementExpectedErrorCount();
        errors.clear();

        //remote etl constraint violation
        _etlHelper.insertSourceRow("12", "Patient 12", "");
        _etlHelper.runETL(TRANSFORM_REMOTE_NOTRUNC);

        //since we just moved patient 12 to target, running the etl a second time should give us a constraint violation
        errors.add("AK_etltarget");
        errors.add("constraint");
        errors.add("ERROR: Error running executeCopy");
        errors.add("org.labkey.api.pipeline.PipelineJobException: Error running executeCopy");
        _etlHelper.runETLandCheckErrors(TRANSFORM_REMOTE_NOTRUNC, true, false, errors);
        _etlHelper.incrementExpectedErrorCount();
        errors.clear();
    }
}
