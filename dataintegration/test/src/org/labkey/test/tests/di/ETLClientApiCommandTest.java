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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.di.BaseTransformCommand;
import org.labkey.remoteapi.di.BaseTransformResponse;
import org.labkey.remoteapi.di.ResetTransformStateCommand;
import org.labkey.remoteapi.di.ResetTransformStateResponse;
import org.labkey.remoteapi.di.RunTransformCommand;
import org.labkey.remoteapi.di.RunTransformResponse;
import org.labkey.remoteapi.di.UpdateTransformConfigurationCommand;
import org.labkey.remoteapi.di.UpdateTransformConfigurationResponse;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Data;
import org.labkey.test.categories.ETL;
import org.labkey.test.util.Maps;
import org.labkey.test.util.PasswordUtil;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Keep these test cases separate from ETLTest as they only need a subset of the setup
 */
@Category({DailyB.class, Data.class, ETL.class})
@BaseWebDriverTest.ClassTimeout(minutes = 5)
public class ETLClientApiCommandTest extends ETLAbstractTest
{
    private static final String TRANSFORM_NOTFOUND = "{ETLtest}/notfound";

    @Override
    protected String getProjectName()
    {
        return "ETLClientApiCommandTestProject";
    }

    @BeforeClass
    public static void setupProject()
    {
        ETLClientApiCommandTest init = (ETLClientApiCommandTest) getCurrentTest();

        init.doSetup();
    }

    @Override
    protected void doSetup()
    {
        _etlHelper.doExtendedSetup(false);
    }

    @Test
    public void testSteps()
    {
        verifyClientApi();
    }

    private void verifyBaseTransformResponse(BaseTransformResponse response)
    {
        assertTrue("success should be true", response.getSuccess());
    }

    private void verifyUpdateTransformConfigurationResponse(UpdateTransformConfigurationResponse response, UpdateTransformConfigurationCommand command)
    {
        verifyBaseTransformResponse(response);
        // just verify this method doesn't choke
        response.getResult();
        // currently not filled in, verify the methods don't choke
        response.getLastChecked();
        response.getState();

        // verify the accessors now (which look at the returned JSON data anyway)
        assertTrue("response.enabled should equal command.enabled",
                command.getEnabled() == null || response.getEnabled() == command.getEnabled());
        assertTrue("response.verbose should equal command.verbose",
                command.getVerboseLogging() == null || response.getVerboseLogging() == command.getVerboseLogging());
        assertTrue("response.description should equal truncation description",
                response.getDescriptionId().equalsIgnoreCase(command.getTransformId()));
    }

    private void verifyResetTransformStateResponse(ResetTransformStateResponse response)
    {
        verifyBaseTransformResponse(response);
    }

    private void verifyRunTransformResponse(RunTransformResponse response, boolean noWork)
    {
        verifyBaseTransformResponse(response);

        String status = response.getStatus().toLowerCase();

        if (noWork)
        {
            assertEquals("Wrong response status", "no work", status);
            assertNull("expect no pipeline job", response.getPipelineURL());
            assertNull("expect no pipeline job", response.getJobId());
        }
        else
        {
            assertNotNull("job id should not be null", response.getJobId());
            assertNotEquals("status should not be error", "error", status);
            assertTrue("pipeline url should be valid\n" +
                    "Expected substring: " + getPipelineURLFragment(response.getJobId()) + "\n" +
                    "Actual: " + response.getPipelineURL(),
                    response.getPipelineURL().contains(getPipelineURLFragment(response.getJobId())));
        }
    }

    private String getPipelineURLFragment(String jobId)
    {
        return WebTestHelper.buildRelativeUrl("pipeline-status", getProjectName(), "details", Maps.of("rowId", jobId));
    }

    private void verifyClientApi()
    {
        //create a new connection, specifying base URL,
        //user email, and password
        Connection cn = new Connection(getBaseURL(), PasswordUtil.getUsername(), PasswordUtil.getPassword());

        // expect error with missing transform
        invokeCommand(new RunTransformCommand(TRANSFORM_NOTFOUND), cn, TRANSFORM_NOTFOUND);

        goToProjectHome();
        _etlHelper.insertSourceRow("0", "Subject 0", null);
        // this should succeed
        RunTransformResponse rtr = (RunTransformResponse) invokeCommand(new RunTransformCommand(TRANSFORM_APPEND), cn);
        _etlHelper.incrementJobsCompleteCount();
        verifyRunTransformResponse(rtr, false /*no work*/);
        // verify transform happened
        _etlHelper.assertInTarget1("Subject 0");

        // run again - but the checker should return no work
        rtr = (RunTransformResponse) invokeCommand(new RunTransformCommand(TRANSFORM_APPEND), cn);
        verifyRunTransformResponse(rtr, true);

        // expect error with missing transform
        invokeCommand(new ResetTransformStateCommand(TRANSFORM_NOTFOUND), cn, TRANSFORM_NOTFOUND);
        // now reset
        ResetTransformStateResponse rtsr = (ResetTransformStateResponse) invokeCommand(new ResetTransformStateCommand(TRANSFORM_APPEND), cn);
        verifyResetTransformStateResponse(rtsr);

        // rerun - we should run the transform but get an error in the pipeline - this is still a success though because
        // the transform was run.  The pipeline url will take you to the error status, however.
        rtr = (RunTransformResponse) invokeCommand(new RunTransformCommand(TRANSFORM_APPEND), cn);
        verifyRunTransformResponse(rtr, false);
        _etlHelper.incrementJobsCompleteCount();
        // verify we had two runs complete (one success, one error)
        _etlHelper.checkRun(true);
        _etlHelper.incrementExpectedErrorCount();

        // not found
        invokeCommand(new UpdateTransformConfigurationCommand(TRANSFORM_NOTFOUND), cn, TRANSFORM_NOTFOUND);

        // note that enabling will cause the transform to run immediately - so don't run one that injects
        // an error in the pipeline.  Truncate is safe because it wipes away whatever was in the target
        UpdateTransformConfigurationCommand utcc = new UpdateTransformConfigurationCommand(TRANSFORM_TRUNCATE);
        UpdateTransformConfigurationResponse utcr = (UpdateTransformConfigurationResponse) invokeCommand(utcc, cn);
        utcc.setEnabled(false);
        utcc.setVerboseLogging(false);
        verifyUpdateTransformConfigurationResponse(utcr, utcc);

        utcc.setEnabled(true);
        utcc.setVerboseLogging(true);
        utcr = (UpdateTransformConfigurationResponse) invokeCommand(utcc, cn);
        verifyUpdateTransformConfigurationResponse(utcr, utcc);
    }

    private CommandResponse invokeCommand(BaseTransformCommand cmd, Connection cn)
    {
        return invokeCommand(cmd, cn, null);
    }

    private CommandResponse invokeCommand(BaseTransformCommand cmd, Connection cn, String expectedError)
    {
        CommandResponse response = null;
        try
        {
            response = cmd.execute(cn, getProjectName());
        }
        catch (CommandException | IOException e)
        {
            if (null == expectedError)
                throw new RuntimeException(e);
            else
            {
                String s = e.getMessage();
                assertTrue("Did not receive expected error.\n" +
                        "Expected substring: " + expectedError + "\n" +
                        "Received: " + s,
                        s.contains(expectedError));
            }
        }

        return response;
    }
}