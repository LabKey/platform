package org.labkey.test.tests.mothership;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.api.util.Pair;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyB;
import org.labkey.test.pages.issues.InsertPage;
import org.labkey.test.pages.mothership.EditUpgradeMessagePage;
import org.labkey.test.pages.mothership.ShowExceptionsPage;
import org.labkey.test.pages.mothership.ShowExceptionsPage.ExceptionSummaryDataRegion;
import org.labkey.test.pages.mothership.StackTraceDetailsPage;
import org.labkey.test.pages.test.TestActions;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.IssuesHelper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.labkey.test.util.Maps;
import org.labkey.test.util.PermissionsHelper.MemberType;
import org.labkey.test.util.TextSearcher;
import org.labkey.test.util.mothership.MothershipHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.labkey.test.pages.test.TestActions.*;
import static org.labkey.test.util.mothership.MothershipHelper.MOTHERSHIP_PROJECT;

@Category({DailyB.class})
public class MothershipTest extends BaseWebDriverTest
{
    private static final String ASSIGNEE = "assignee@mothership.test";
    private static final String NON_ASSIGNEE = "non_assignee@mothership.test";
    private static final String MOTHERSHIP_GROUP = "Mothership Test Group";
    private static final String ISSUES_PROJECT = "MothershipTest Issues";
    private static final String ISSUES_GROUP = "Issues Group";
    public static final String ISSUES_LIST = "MothershipIssues";

    private MothershipHelper _mothershipHelper;
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
        _userHelper.createUser(ASSIGNEE);
        _userHelper.createUser(NON_ASSIGNEE);
        permissionsHelper.createProjectGroup(MOTHERSHIP_GROUP, MOTHERSHIP_PROJECT);
        permissionsHelper.addMemberToRole(MOTHERSHIP_GROUP, "Editor", MemberType.group, MOTHERSHIP_PROJECT);
        permissionsHelper.addUserToProjGroup(ASSIGNEE, MOTHERSHIP_PROJECT, MOTHERSHIP_GROUP);
        permissionsHelper.addMemberToRole(NON_ASSIGNEE, "Project Admin", MemberType.user, MOTHERSHIP_PROJECT);

        EditUpgradeMessagePage configurePage = EditUpgradeMessagePage.beginAt(this);
        configurePage.createIssueURL().setValue(WebTestHelper.getContextPath() + "/" +
                WebTestHelper.buildRelativeUrl("issues", ISSUES_PROJECT, "insert", Maps.of("issueDefName", ISSUES_LIST)));
        configurePage.issuesContainer().setValue("/" + ISSUES_PROJECT);
        configurePage.save();

        _containerHelper.createProject(ISSUES_PROJECT, null);
        new IssuesHelper(getDriver()).createNewIssuesList(ISSUES_LIST, _containerHelper);
        ApiPermissionsHelper permHelper = new ApiPermissionsHelper(this);
        permHelper.createProjectGroup(ISSUES_GROUP, ISSUES_PROJECT);
        permHelper.addUserToProjGroup(ASSIGNEE, ISSUES_PROJECT, ISSUES_GROUP);
        permHelper.addMemberToRole(ISSUES_GROUP, "Editor", MemberType.group, ISSUES_PROJECT);
    }

    @Before
    public void preTest() throws Exception
    {
        _mothershipHelper = new MothershipHelper(getDriver());
        goToMothership();
    }

    @Test
    public void testCreateIssue() throws Exception
    {
        IssuesHelper issuesHelper = new IssuesHelper(getDriver());
        Integer highestIssueId = issuesHelper.getHighestIssueId(ISSUES_PROJECT, ISSUES_LIST);
        Integer stackTraceId = ensureUnasignedException();

        ShowExceptionsPage showExceptionsPage = ShowExceptionsPage.beginAt(this);
        ExceptionSummaryDataRegion exceptionSummary = showExceptionsPage.exceptionSummary();
        StackTraceDetailsPage detailsPage = exceptionSummary.clickStackTrace(stackTraceId);
        String stackDetailsUrl = getDriver().getCurrentUrl();
        InsertPage insertPage = detailsPage.clickCreateIssue();
        String expectedTitle = "NullPointerException in org.labkey.core.test.TestController$NpeAction.getView()";
        String issueTitle = insertPage.title().getValue();
        assertEquals("Wrong issue title", expectedTitle, issueTitle);
        String[] expectedComments = new String[] {
                "Created from crash report: " + stackDetailsUrl,
                "java.lang.NullPointerException",
                "TestController.java"};
        assertTextPresentInThisOrder(new TextSearcher(() -> insertPage.comment().getValue()), expectedComments);
        assertEquals("New issue shouldn't be assigned by default", "", insertPage.assignedTo().getValue().trim());
        insertPage.assignedTo().setValue(displayNameFromEmail(ASSIGNEE));
        insertPage.save();
        Integer newIssueId = issuesHelper.getHighestIssueId(ISSUES_PROJECT, ISSUES_LIST);
        assertNotEquals("Didn't create a new issue.", highestIssueId, newIssueId);
        showExceptionsPage = new ShowExceptionsPage(getDriver());
        exceptionSummary = showExceptionsPage.exceptionSummary();
        detailsPage = exceptionSummary.clickStackTrace(stackTraceId);
        Integer bugNumber = Integer.parseInt(detailsPage.bugNumber().getValue());
        assertEquals("Exception's related issue not set", newIssueId, bugNumber);
    }

    @Test
    public void testAssignException() throws Exception
    {
        Integer stackTraceId = ensureUnasignedException();

        ShowExceptionsPage showExceptionsPage = ShowExceptionsPage.beginAt(this);
        ExceptionSummaryDataRegion exceptionSummary = showExceptionsPage.exceptionSummary();
        exceptionSummary.uncheckAll();
        exceptionSummary.checkCheckboxByPrimaryKey(stackTraceId);
        exceptionSummary.assignSelectedTo(displayNameFromEmail(ASSIGNEE));

        impersonate(ASSIGNEE);
        {
            showExceptionsPage = goToMothership().clickMyExceptions();
            exceptionSummary = showExceptionsPage.exceptionSummary();
            assertEquals("Should be only one issue assigned to user", 1, exceptionSummary.getDataRowCount());
            StackTraceDetailsPage detailsPage = exceptionSummary.clickStackTrace(stackTraceId);
            assertElementPresent(Locator.linkWithText("#" + stackTraceId));
            assertEquals(displayNameFromEmail(ASSIGNEE), detailsPage.assignedTo().getFirstSelectedOption().getText());
        }
        stopImpersonating();
    }

    @Test
    public void testIgnoreExceptionFromDataRegion() throws Exception
    {
        Integer stackTraceId = ensureUnasignedException();

        ShowExceptionsPage showExceptionsPage = ShowExceptionsPage.beginAt(this);
        ExceptionSummaryDataRegion exceptionSummary = showExceptionsPage.exceptionSummary();
        exceptionSummary.uncheckAll();
        exceptionSummary.checkCheckboxByPrimaryKey(stackTraceId);
        exceptionSummary.ignoreSelected();

        StackTraceDetailsPage detailsPage = exceptionSummary.clickStackTrace(stackTraceId);
        assertEquals("Ignoring exception should set bugNumber", "-1", detailsPage.bugNumber().getValue());
    }

    @Test
    public void testCreateIssueForAssignedException() throws Exception
    {
        Integer stackTraceId = ensureUnasignedException();

        ShowExceptionsPage showExceptionsPage = ShowExceptionsPage.beginAt(this);
        ExceptionSummaryDataRegion exceptionSummary = showExceptionsPage.exceptionSummary();
        exceptionSummary.uncheckAll();
        exceptionSummary.checkCheckboxByPrimaryKey(stackTraceId);
        exceptionSummary.assignSelectedTo(displayNameFromEmail(ASSIGNEE));

        StackTraceDetailsPage detailsPage = exceptionSummary.clickStackTrace(stackTraceId);
        InsertPage insertPage = detailsPage.clickCreateIssue();
        assertEquals("Exception assignment != New issue assignment",
                displayNameFromEmail(ASSIGNEE), insertPage.assignedTo().getValue());
    }

    @Test
    public void testCombiningIdenticalExceptions() throws Exception
    {
        List<Integer> exceptionIds = triggerExceptions(ExceptionActions.illegalState, ExceptionActions.illegalState);
        assertEquals("Should group identical exceptions", exceptionIds.get(0), exceptionIds.get(1));
    }

    @Test @Ignore("These don't actually get grouped")
    public void testCombiningSimilarExceptions() throws Exception
    {
        List<Pair<ExceptionActions, String>> actions = new ArrayList<>();
        actions.add(new Pair<>(ExceptionActions.multiException, "NPE"));
        actions.add(new Pair<>(ExceptionActions.multiException, "NPE2"));

        List<Integer> exceptionIds = triggerExceptions(actions);
        assertEquals("Should group same exception type from same action", exceptionIds.get(0), exceptionIds.get(1));
    }

    @Test
    public void testNotCombiningDifferentExceptionTypes() throws Exception
    {
        List<Pair<ExceptionActions, String>> actions = new ArrayList<>();
        actions.add(new Pair<>(ExceptionActions.multiException, "NPE"));
        actions.add(new Pair<>(ExceptionActions.multiException, "ISE"));

        List<Integer> exceptionIds = triggerExceptions(actions);
        assertNotEquals("Should not group different exception types", exceptionIds.get(0), exceptionIds.get(1));
    }

    @Test
    public void testNotCombiningFromDifferentActions() throws Exception
    {
        List<Integer> exceptionIds = triggerExceptions(ExceptionActions.npeother, ExceptionActions.npe);
        assertNotEquals("Should not group exceptions from different actions", exceptionIds.get(0), exceptionIds.get(1));
    }

    private ShowExceptionsPage goToMothership()
    {
        clickProject(MOTHERSHIP_PROJECT);
        return new ShowExceptionsPage(getDriver());
    }

    protected int ensureUnasignedException()
    {
        int stackTraceId = triggerException(ExceptionActions.npe);
        _mothershipHelper.resetStackTrace(stackTraceId);
        return stackTraceId;
    }

    protected int triggerException(ExceptionActions action)
    {
        return triggerExceptions(action).get(0);
    }

    protected List<Integer> triggerExceptions(ExceptionActions... actions)
    {
        List<Pair<ExceptionActions, String>> actionsWithMessages = new ArrayList<>();
        for (ExceptionActions action : actions)
        {
            actionsWithMessages.add(new Pair<>(action, null));
        }
        return triggerExceptions(actionsWithMessages);
    }

    @LogMethod
    protected List<Integer> triggerExceptions(@LoggedParam List<Pair<TestActions.ExceptionActions, String>> actionsWithMessages)
    {
        List<Integer> exceptionIds = new ArrayList<>();
        checkErrors();
        for (Pair<TestActions.ExceptionActions, String> action : actionsWithMessages)
        {
            action.first.triggerException(action.second);
            sleep(100); // Wait for mothership to pick up exception
            exceptionIds.add(_mothershipHelper.getLatestStackTraceId());
        }
        resetErrors();
        return exceptionIds;
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
        return Arrays.asList("mothership");
    }
}