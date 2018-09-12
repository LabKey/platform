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
import org.labkey.test.categories.DailyB;
import org.labkey.test.categories.Data;
import org.labkey.test.categories.ETL;

/**
 * Split from ETLTest because of the verification of TransformSummary & TransformHistory. This makes the test sensitive to test order,
 * as previous TransformRun rows can interfere with the expected results here. Proper fix would be to delete pipeline jobs between tests,
 * but app bug prevents that from cascading to TransformRun.
 * TODO: Fold this back into ETL Test after 23854 & 23855 have been fixed.
 */
@Category({DailyB.class, Data.class, ETL.class})
@BaseWebDriverTest.ClassTimeout(minutes = 10)
public class ETLSimpleTransformTest extends ETLAbstractTest
{
    @Nullable
    @Override
    protected String getProjectName()
    {
        return "ETLSimpleTransformTestProject";
    }

    @BeforeClass
    public static void setupProject()
    {
        ETLSimpleTransformTest init = (ETLSimpleTransformTest) getCurrentTest();

        init.doSetup();
    }

    @Before
    public void preTest() throws Exception
    {
        goToProjectHome();
    }

    @Test
    public void testSimpleTransforms()
    {
        // TODO: There was a check here purporting to test container filter on transform summary, but it was a false negative. (it
        // erroneously assumed results from a previous ETL test would still be in the database). Removed it, but would be good
        // to get coverage... explicitly create another folder, run an ETL into it, then show that history doesn't show in the
        // original container.

        //append into empty target
        _etlHelper.insertSourceRow("0", "Subject 0", null);

        _etlHelper.runETL(APPEND);
        _etlHelper.addTransformResult(TRANSFORM_APPEND, "1", ETLHelper.COMPLETE, "1");
        _etlHelper.assertInTarget1("Subject 0");
        _etlHelper.verifyTransformSummary();
        _etlHelper.verifyTransformHistory(TRANSFORM_APPEND, TRANSFORM_APPEND_DESC);

        //append into populated target
        _etlHelper.insertSourceRow("1", "Subject 1", null);
        _etlHelper.runETL(APPEND);
        _etlHelper.addTransformResult(TRANSFORM_APPEND, "1", ETLHelper.COMPLETE, "1");
        _etlHelper.checkRun();
        _etlHelper.assertInTarget1("Subject 0", "Subject 1");

        // verify transform summary should only have the most recent entry (i.e., doesn't change)
        _etlHelper.verifyTransformSummary();
        _etlHelper.verifyTransformHistory(TRANSFORM_APPEND, TRANSFORM_APPEND_DESC);

        // rerun append and verify that no work is done
        _etlHelper.runETL_NoWork(TRANSFORM_APPEND);

        // verify only two pipeline jobs existed since the "no work" one should not
        // have fired off a pipeline job
        _etlHelper.checkRun();

        // verify transform summary should only have the most recent entry since
        // it should filter out "no work" rows
        _etlHelper.verifyTransformSummary();
        // summary should be where it was as well
        _etlHelper.verifyTransformHistory(TRANSFORM_APPEND, TRANSFORM_APPEND_DESC);

        _etlHelper.insertSourceRow("2", "Subject 2", null);
        //truncate into populated target
        _etlHelper.deleteSourceRow("0", "1");
        _etlHelper.runETL("truncate");
        // add a row for the 'truncate' etl - this should show up in our summary view
        _etlHelper.addTransformResult(TRANSFORM_TRUNCATE, "1", ETLHelper.COMPLETE, "1");
        _etlHelper.assertInTarget1("Subject 2");
        _etlHelper.assertNotInTarget1("Subject 0", "Subject 1");
        _etlHelper.verifyTransformSummary();
        _etlHelper.verifyTransformHistory(TRANSFORM_TRUNCATE, TRANSFORM_TRUNCATE_DESC);

        //identify by run into populated target
        _etlHelper.insertSourceRow("3", "Subject 3", "42");
        _etlHelper.insertTransferRow("42", _etlHelper.getDate(), _etlHelper.getDate(), "new transfer", "added by test automation", "pending");
        _etlHelper.runETL("appendIdByRun");
        _etlHelper.addTransformResult(TRANSFORM_BYRUNID, "1", ETLHelper.COMPLETE, "1");
        _etlHelper.assertInTarget1("Subject 2", "Subject 3");

        // verify the Last Status values and links to job logs
        goToModule("DataIntegration");
        click(_etlHelper.findLastStatusCell(TRANSFORM_BYRUNID, ETLHelper.COMPLETE, true));
        waitForText("transformrun = 42");
    }
}
