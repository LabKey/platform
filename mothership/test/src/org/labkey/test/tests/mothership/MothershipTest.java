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
package org.labkey.test.tests.mothership;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.api.util.Pair;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.Locators;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyB;
import org.labkey.test.components.BodyWebPart;
import org.labkey.test.pages.issues.InsertPage;
import org.labkey.test.pages.mothership.EditUpgradeMessagePage;
import org.labkey.test.pages.mothership.ReportsPage;
import org.labkey.test.pages.mothership.ShowExceptionsPage;
import org.labkey.test.pages.mothership.ShowExceptionsPage.ExceptionSummaryDataRegion;
import org.labkey.test.pages.mothership.StackTraceDetailsPage;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.IssuesHelper;
import org.labkey.test.util.Maps;
import org.labkey.test.util.PermissionsHelper.MemberType;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.TextSearcher;
import org.labkey.test.util.mothership.MothershipHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.labkey.test.pages.test.TestActions.ExceptionActions;
import static org.labkey.test.util.mothership.MothershipHelper.MOTHERSHIP_PROJECT;

@Category({DailyB.class})
public class MothershipTest extends BaseWebDriverTest
{
    private static final String ASSIGNEE = "assignee@mothership.test";
    private static final String NON_ASSIGNEE = "non_assignee@mothership.test";
    private static final String MOTHERSHIP_GROUP = "Mothership Test Group";
    private static final String ISSUES_PROJECT = "MothershipTest Issues";
    private static final String ISSUES_GROUP = "Issues Group";
    public static final String ISSUES_LIST = "mothershipissues";

    private static MothershipHelper _mothershipHelper; // Static to remember site settings between tests
    private ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _containerHelper.deleteProject(ISSUES_PROJECT, false);
        // Don't delete mothership project
        _userHelper.deleteUsers(afterTest, ASSIGNEE, NON_ASSIGNEE);
        permissionsHelper.deleteGroup(MOTHERSHIP_GROUP, MOTHERSHIP_PROJECT, false);
    }

    @BeforeClass
    public static void setupProject()
    {
        MothershipTest init = (MothershipTest) getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        _mothershipHelper = new MothershipHelper(this);

        _userHelper.createUser(ASSIGNEE);
        _userHelper.createUser(NON_ASSIGNEE);
        permissionsHelper.createProjectGroup(MOTHERSHIP_GROUP, MOTHERSHIP_PROJECT);
        permissionsHelper.addMemberToRole(MOTHERSHIP_GROUP, "Editor", MemberType.group, MOTHERSHIP_PROJECT);
        permissionsHelper.addUserToProjGroup(ASSIGNEE, MOTHERSHIP_PROJECT, MOTHERSHIP_GROUP);
        permissionsHelper.addMemberToRole(NON_ASSIGNEE, "Project Admin", MemberType.user, MOTHERSHIP_PROJECT);

        EditUpgradeMessagePage configurePage = EditUpgradeMessagePage.beginAt(this);
        configurePage.createIssueURL().set(WebTestHelper.getContextPath() + "/" +
                WebTestHelper.buildRelativeUrl("issues", ISSUES_PROJECT, "insert", Maps.of("issueDefName", ISSUES_LIST)));
        configurePage.issuesContainer().set("/" + ISSUES_PROJECT);
        configurePage.save();

        _containerHelper.createProject(ISSUES_PROJECT, null);
        IssuesHelper helper = new IssuesHelper(this);
        helper.createNewIssuesList(ISSUES_LIST, _containerHelper);
        goToModule("Issues");
        helper.goToAdmin();
        helper.setIssueAssignmentList(null);
        clickButton("Save");

        ApiPermissionsHelper permHelper = new ApiPermissionsHelper(this);
        permHelper.createProjectGroup(ISSUES_GROUP, ISSUES_PROJECT);
        permHelper.addUserToProjGroup(ASSIGNEE, ISSUES_PROJECT, ISSUES_GROUP);
        permHelper.addMemberToRole(ISSUES_GROUP, "Editor", MemberType.group, ISSUES_PROJECT);
    }

    @Before
    public void preTest() throws Exception
    {
        _mothershipHelper.ensureSelfReportingEnabled();
        // In case the testIgnoreInstallationExceptions() test case didn't reset this flag after itself.
        _mothershipHelper.setIgnoreExceptions(false);
        goToMothership();
    }

    @Test
    public void testCreateIssue() throws Exception
    {
        IssuesHelper issuesHelper = new IssuesHelper(this);
        Integer highestIssueId = issuesHelper.getHighestIssueId(ISSUES_PROJECT, ISSUES_LIST);
        Integer stackTraceId = ensureUnassignedException();

        ShowExceptionsPage showExceptionsPage = ShowExceptionsPage.beginAt(this);
        ExceptionSummaryDataRegion exceptionSummary = showExceptionsPage.exceptionSummary();
        StackTraceDetailsPage detailsPage = exceptionSummary.clickStackTrace(stackTraceId);
        String stackDetailsUrl = getDriver().getCurrentUrl();
        InsertPage insertPage = detailsPage.clickCreateIssue();
        String expectedTitle = "NullPointerException in org.labkey.core.test.TestController$NpeAction.getView()";
        String issueTitle = insertPage.title().get();
        assertEquals("Wrong issue title", expectedTitle, issueTitle);
        String[] expectedComments = new String[] {
                "Created from crash report: " + stackDetailsUrl,
                "java.lang.NullPointerException",
                "TestController.java"};
        assertTextPresentInThisOrder(new TextSearcher(insertPage.comment().get()), expectedComments);
        assertEquals("New issue shouldn't be assigned by default", "", insertPage.assignedTo().get().trim());
        insertPage.assignedTo().set(_userHelper.getDisplayNameForEmail(ASSIGNEE));
        insertPage.save();
        Integer newIssueId = issuesHelper.getHighestIssueId(ISSUES_PROJECT, ISSUES_LIST);
        assertNotEquals("Didn't create a new issue.", highestIssueId, newIssueId);
        detailsPage = ShowExceptionsPage.beginAt(this)
                .exceptionSummary()
                .clickStackTrace(stackTraceId);
        assertEquals("Exception's related issue not set", newIssueId.toString(), detailsPage.bugNumber().get());
    }

    @Test
    public void testAssignException() throws Exception
    {
        Integer stackTraceId = ensureUnassignedException();

        ShowExceptionsPage showExceptionsPage = ShowExceptionsPage.beginAt(this);
        ExceptionSummaryDataRegion exceptionSummary = showExceptionsPage.exceptionSummary();
        exceptionSummary.uncheckAll();
        exceptionSummary.checkCheckboxByPrimaryKey(stackTraceId);
        exceptionSummary.assignSelectedTo(_userHelper.getDisplayNameForEmail(ASSIGNEE));

        impersonate(ASSIGNEE);
        {
            showExceptionsPage = goToMothership().clickMyExceptions();
            exceptionSummary = showExceptionsPage.exceptionSummary();
            assertEquals("Should be only one issue assigned to user", 1, exceptionSummary.getDataRowCount());
            StackTraceDetailsPage detailsPage = exceptionSummary.clickStackTrace(stackTraceId);
            assertElementPresent(Locator.linkWithText("#" + stackTraceId));
            assertEquals(_userHelper.getDisplayNameForEmail(ASSIGNEE), detailsPage.assignedTo().getFirstSelectedOption().getText());
        }
        stopImpersonating();
    }

    @Test
    public void testIgnoreExceptionFromDataRegion() throws Exception
    {
        Integer stackTraceId = ensureUnassignedException();

        ShowExceptionsPage showExceptionsPage = ShowExceptionsPage.beginAt(this);
        ExceptionSummaryDataRegion exceptionSummary = showExceptionsPage.exceptionSummary();
        exceptionSummary.uncheckAll();
        exceptionSummary.checkCheckboxByPrimaryKey(stackTraceId);
        exceptionSummary.ignoreSelected();

        StackTraceDetailsPage detailsPage = exceptionSummary.clickStackTrace(stackTraceId);
        assertEquals("Ignoring exception should set bugNumber", "-1", detailsPage.bugNumber().get());
    }

    @Test
    public void testCreateIssueForAssignedException() throws Exception
    {
        Integer stackTraceId = ensureUnassignedException();

        ShowExceptionsPage showExceptionsPage = ShowExceptionsPage.beginAt(this);
        ExceptionSummaryDataRegion exceptionSummary = showExceptionsPage.exceptionSummary();
        exceptionSummary.uncheckAll();
        exceptionSummary.checkCheckboxByPrimaryKey(stackTraceId);
        exceptionSummary.assignSelectedTo(_userHelper.getDisplayNameForEmail(ASSIGNEE));

        StackTraceDetailsPage detailsPage = exceptionSummary.clickStackTrace(stackTraceId);
        InsertPage insertPage = detailsPage.clickCreateIssue();
        assertEquals("Exception assignment != New issue assignment",
                _userHelper.getDisplayNameForEmail(ASSIGNEE), insertPage.assignedTo().get());
    }

    @Test
    public void testCombiningIdenticalExceptions() throws Exception
    {
        List<Integer> exceptionIds = _mothershipHelper.triggerExceptions(ExceptionActions.illegalState, ExceptionActions.illegalState);
        assertEquals("Should group identical exceptions", exceptionIds.get(0), exceptionIds.get(1));
    }

    @Test @Ignore("These don't actually get grouped")
    public void testCombiningSimilarExceptions() throws Exception
    {
        List<Pair<ExceptionActions, String>> actions = new ArrayList<>();
        actions.add(new Pair<>(ExceptionActions.multiException, "NPE"));
        actions.add(new Pair<>(ExceptionActions.multiException, "NPE2"));

        List<Integer> exceptionIds = _mothershipHelper.triggerExceptions(actions);
        assertEquals("Should group same exception type from same action", exceptionIds.get(0), exceptionIds.get(1));
    }

    @Test
    public void testNotCombiningDifferentExceptionTypes() throws Exception
    {
        List<Pair<ExceptionActions, String>> actions = new ArrayList<>();
        actions.add(new Pair<>(ExceptionActions.multiException, "NPE"));
        actions.add(new Pair<>(ExceptionActions.multiException, "ISE"));

        List<Integer> exceptionIds = _mothershipHelper.triggerExceptions(actions);
        assertNotEquals("Should not group different exception types", exceptionIds.get(0), exceptionIds.get(1));
    }

    @Test
    public void testNotCombiningFromDifferentActions() throws Exception
    {
        List<Integer> exceptionIds = _mothershipHelper.triggerExceptions(ExceptionActions.npeother, ExceptionActions.npe);
        assertNotEquals("Should not group exceptions from different actions", exceptionIds.get(0), exceptionIds.get(1));
    }

    @Test
    public void testReports() throws Exception
    {
        final ReportsPage reportsPage = ReportsPage.beginAt(this);
        final List<BodyWebPart> bodyWebParts = new PortalHelper(getDriver()).getBodyWebParts();

        List<String> expected = Arrays.asList("\"Unbugged\" Exceptions by Owner", "Unassigned Exceptions", "Installations");
        List<String> actual = new ArrayList<>();
        bodyWebParts.stream().forEachOrdered(wp -> actual.add(wp.getTitle()));

        // Very basic check.
        assertEquals("Wrong mothership reports", expected, actual);
        // TODO: Verify report contents
    }

    @Test
    public void testErrorCode() throws Exception
    {
        checkErrors();
        ExceptionActions exception = ExceptionActions.illegalState;
        exception.beginAt(this);
        String errorCode = getErrorCode();
        assertNotNull("Exception didn't produce an error code", errorCode);
        resetErrors();
        StackTraceDetailsPage stackTraceDetailsPage = ShowExceptionsPage.beginAt(this)
                .searchForErrorCode(errorCode);
        assertEquals("Searching for error code landed on incorrect page", "Exception Reports", getText(Locators.bodyTitle()));
        assertEquals("Searching for error code navigated to an incorrect stack trace", exception.getExceptionClass(), stackTraceDetailsPage.getExceptionClass());
    }

    @Test
    public void testNoErrorCodeWithReportingDisabled() throws Exception
    {
        _mothershipHelper.disableExceptionReporting();
        checkErrors();
        ExceptionActions.illegalState.beginAt(this);
        assertEquals("Shouldn't generate an error code when exception reporting is disabled", null, getErrorCode());
        resetErrors();
    }

    private String getErrorCode()
    {
        if (!isElementPresent(Locator.tagWithClass("table", "server-error")))
            fail("Expected to be on an error page");
        String error = Locators.labkeyError.findElement(getDriver()).getText();
        Pattern errorCodePattern = Pattern.compile(".*please refer to error code: ([^\\s]+)");
        Matcher matcher = errorCodePattern.matcher(error);
        if(matcher.find())
            return matcher.group(1);
        else
            return null;
    }

    private ShowExceptionsPage goToMothership()
    {
        return ShowExceptionsPage.beginAt(this);
    }

    protected int ensureUnassignedException()
    {
        int stackTraceId = _mothershipHelper.triggerException(ExceptionActions.npe);
        _mothershipHelper.resetStackTrace(stackTraceId);
        return stackTraceId;
    }

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
}