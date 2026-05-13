/*
 * Copyright (c) 2016-2026 LabKey Corporation
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
package org.labkey.test.tests;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.Git;
import org.labkey.test.pages.core.login.LoginConfigurePage;

import java.util.Collections;
import java.util.List;

@Category({Git.class})
public class LoginAttemptLimitTest extends BaseWebDriverTest
{
    private static final String TEST_USER  = "testuser@test.test";
    private static final String TEST_USER2 = "testuser2@test.test";

    @BeforeClass
    public static void doSetup() throws Exception
    {
        LoginAttemptLimitTest initTest = getCurrentTest();
        initTest.setupUsers();
    }

    private void setupUsers()
    {
        enableEmailRecorder();
        _userHelper.createUser(TEST_USER, true, true);
        _userHelper.createUser(TEST_USER2, true, true);

        goToEmailRecord();
        waitForTextWithRefresh(longWaitForPage, TEST_USER, TEST_USER2);

        setInitialPassword(TEST_USER);
        setInitialPassword(TEST_USER2);
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        LoginConfigurePage.beginAt(this)
            .setLoginAttemptEnabled(false)
            .clickSaveAndFinish();

        _userHelper.deleteUsers(afterTest, TEST_USER, TEST_USER2);
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Collections.singletonList("Core");
    }

    @Override
    protected String getProjectName()
    {
        return null;
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Test
    public void testLimit()
    {
        setupLoginAttemptLimit();
        log("Signing out");
        simpleSignOut();
        log("sign in fail number 1");
        signInShouldFail(TEST_USER, "foo", "The email address and password you entered did not match any accounts on file. Note: Passwords are case sensitive; make sure your Caps Lock is off.");
        simpleSignOut();
        log("sign in fail number 2");
        signInShouldFail(TEST_USER, "bar", "The email address and password you entered did not match any accounts on file. Note: Passwords are case sensitive; make sure your Caps Lock is off.");
        simpleSignOut();
        log("sign in fail number 3");
        signInShouldFail(TEST_USER, "pug", "The email address and password you entered did not match any accounts on file. Note: Passwords are case sensitive; make sure your Caps Lock is off.");
        simpleSignOut();
        log("sign in fail number 4, should be disabled now");
        attemptSignIn(TEST_USER, "dog");
        waitForText("Your login has been disabled. Please try again in 5 minutes.");
        // Re-authenticate as admin; waiting for the 5-minute reset to verify re-enable is impractically slow.
        signIn();
    }

    @Test
    public void testBehaviorWithFeatureOff()
    {
        LoginConfigurePage.beginAt(this)
            .setLoginAttemptEnabled(false)
            .clickSaveAndFinish();
        signOut();
        for (int i = 0; i < 10; i++)
        {
            signInShouldFail(TEST_USER2, "pug", "The email address and password you entered did not match any accounts on file. Note: Passwords are case sensitive; make sure your Caps Lock is off.");
            goToHome();
        }
    }

    private void setupLoginAttemptLimit()
    {
        LoginConfigurePage.beginAt(this)
            .setLoginAttemptEnabled(true)
            .setLoginAttemptLimit("3")
            .setLoginAttemptPeriod("30")
            .setLoginAttemptResetTime("5")
            .clickSaveAndFinish();
    }
}
