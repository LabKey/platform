/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
package org.labkey.test.tests.devtools;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.labkey.remoteapi.query.Sort;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.categories.Git;
import org.labkey.test.pages.core.login.LoginConfigRow;
import org.labkey.test.pages.core.login.LoginConfigurePage;
import org.labkey.test.params.devtools.SecondaryAuthenticationProvider;
import org.labkey.test.util.PasswordUtil;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category({Git.class})
@BaseWebDriverTest.ClassTimeout(minutes = 1)
public class SecondaryAuthenticationTest extends BaseWebDriverTest
{
    @Before
    public void preTest()
    {
        clearSecondaryConfigs();
    }

    @After
    public void afterTest()
    {
        clearSecondaryConfigs();
    }

    private void clearSecondaryConfigs()
    {
        LoginConfigurePage configurePage = LoginConfigurePage.beginAt(this);
        List<LoginConfigRow> configs = configurePage
                .getSecondaryConfigurations();

        for (LoginConfigRow row : configs)
        {
            row.clickDelete();
        }
    }

    @Test
    /* This test assumes that the Duo 2-Factor is Disabled */
    public void testSecondaryAuthentication()
    {
        Date currentDate = new Date(); //get today's date

        //Enable 'Test Secondary Authentication Provider'
        LoginConfigurePage configurePage = LoginConfigurePage.beginAt(this);
        configurePage
                .addSecondaryConfiguration(new SecondaryAuthenticationProvider())
                .setDescription("TestSecondary Configuration")
                .setEnabled(true)
                .clickApply();
        configurePage.clickSaveAndFinish();

        /** Test audit log* */
        //get all the rows that are greater than or equal to today's date
        SelectRowsResponse selectRowsResponse = getLatestAuditEntries();

        // get the one specific to TestSecondary Configuration changes
        Map<String, Object> row = selectRowsResponse.getRows().stream()
                .filter(a -> a.get("changes").toString().contains("TestSecondary Configuration"))
                .findFirst().orElse(null);
        assertNotNull("No event for TestSecondary Configuration exists today", row);

        String commentColVal = (String) row.get("Comment"); //get a value from Comment column
        Date auditDate = (Date)row.get("Created"); //get a value from Created ('Date' in the UI) column

        //compare time stamp of the audit log
        assertFalse("No audit entry for enabled Secondary Authentication", auditDate.before(DateUtils.truncate(currentDate, Calendar.SECOND)));

        //compare 'Comment' value of the last/latest audit log
        assertEquals("Latest audit log for Authentication provider is not as expected",
                "TestSecondary authentication configuration \"TestSecondary Configuration\" was created", commentColVal);

        //Sign Out
        signOut();

        currentDate= new Date();

        //URL before User Signs In
        String relativeURLBeforeSignIn = getCurrentRelativeURL();

            //Sign In - Primary Authentication before Secondary Authentication
            attemptSignIn(PasswordUtil.getUsername(), PasswordUtil.getPassword());

            /* Secondary Authentication */

            //'Sign In' link shouldn't be present
            waitForElementToDisappear(Locator.linkContainingText("Sign In"));

            //User should be still recognized as guest until secondary authentication is successful.
            assertTextPresent("Is " + PasswordUtil.getUsername() +" really you?");

            //Select Radio button No
            checkRadioButton(Locator.radioButtonByNameAndValue("valid", "0"));
            click(Locator.input("TestSecondary"));

            //should stay on Secondary Authentication page until user selects Yes radio
            assertTextPresent("Secondary Authentication");

            //Select radio Yes
            checkRadioButton(Locator.radioButtonByNameAndValue("valid", "1"));

            //Click on button 'TestSecondary'
            clickAndWait(Locator.input("TestSecondary"));

            //get current relative URL after Sign In
            String relativeURLAfterSignIn = StringUtils.stripEnd(getCurrentRelativeURL(), "?");

            //user should be redirected to the same URL they were on, before Sign In.
            assertEquals("After successful secondary authentication, user should be redirected to the same URL they were on before Sign In",
                    relativeURLBeforeSignIn, relativeURLAfterSignIn);

        //Disable 'Test Secondary Authentication Provider'
        configurePage = LoginConfigurePage.beginAt(this);
        configurePage
                .getSecondaryConfigurationRow("TestSecondary Configuration")
                .clickEdit(new SecondaryAuthenticationProvider())
                .setEnabled(false)
                .clickApply();
        configurePage.clickSaveAndFinish();

        selectRowsResponse = getLatestAuditEntries();
        row = selectRowsResponse.getRows().stream()
                .filter(a -> a.get("Comment").toString().contains("TestSecondary Configuration") &&
                        a.get("changes").toString().startsWith("enabled: true"))
                .findFirst().orElse(null);
        assertNotNull(row);

        commentColVal = (String) row.get("Comment"); //get a value from Comment column
        auditDate = (Date)row.get("Created"); //get a value from Created ('Date' in the UI) column
        String change = (String)row.get("Changes");

        //compare time stamp of the audit log
        assertFalse("No audit entry for disabled Secondary Authentication",
                auditDate.before(DateUtils.truncate(currentDate, Calendar.SECOND)));

        //compare 'Comment' value of the last/latest audit log
        assertTrue("Comment should be about the current configuration",
                commentColVal.startsWith("TestSecondary authentication configuration \"TestSecondary Configuration\""));
        assertTrue("Comment should say that the configuration was updated", commentColVal.endsWith("was updated"));
        assertTrue("Change should be [enabled: true » false], but was["+change+"]", change.startsWith("enabled: true"));
        assertTrue("Change should be [enabled: true » false], but was["+change+"]", change.endsWith("false"));

         // now remove the secondary auth configuration
        clearSecondaryConfigs();

        // validate the delete entry in the audit log after deleting
        selectRowsResponse = getLatestAuditEntries();
        row = selectRowsResponse.getRows().get(0);

        commentColVal = (String) row.get("Comment"); //get a value from Comment column
        change = (String)row.get("Changes");

        assertTrue(commentColVal.startsWith("TestSecondary authentication configuration \"TestSecondary Configuration\""));
        assertTrue(commentColVal.endsWith("was deleted"));
        assertEquals("Change should be 'deleted'", "deleted", change);
    }

    protected SelectRowsResponse getLatestAuditEntries()
    {
        Connection cn = createDefaultConnection(true);
        SelectRowsCommand selectCmd = new SelectRowsCommand("auditLog", "AuthenticationProviderConfiguration");
        selectCmd.setSorts(Arrays.asList(new Sort("Created", Sort.Direction.DESCENDING)));

        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        Date date = new Date();

        selectCmd.setFilters(Arrays.asList(new Filter("Date", dateFormat.format(date), Filter.Operator.DATE_GTE)));
        selectCmd.setColumns(Arrays.asList("*"));

        SelectRowsResponse selectResp = null;
        try
        {
            selectResp = selectCmd.execute(cn, "/");
        }
        catch (CommandException | IOException e)
        {
            throw new RuntimeException(e);
        }

        return selectResp;
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected String getProjectName()
    {
        return null;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList();
    }
}