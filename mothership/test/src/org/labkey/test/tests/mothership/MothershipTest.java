package org.labkey.test.tests.mothership;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
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
import org.labkey.test.util.PermissionsHelper.MemberType;
import org.labkey.test.util.TextSearcher;
import org.labkey.test.util.mothership.MothershipHelper;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.labkey.test.util.mothership.MothershipHelper.MOTHERSHIP_PROJECT;

@Category({DailyB.class})
public class MothershipTest extends BaseWebDriverTest
{
    private static final String ASSIGNEE = "assignee@mothership.test";
    private static final String NON_ASSIGNEE = "non_assignee@mothership.test";
    private static final String MOTHERSHIP_GROUP = "Mothership Test Group";
    private static final String ISSUES_PROJECT = "MothershipTest Issues";
    private static final String ISSUES_GROUP = "Issues Group";

    private MothershipHelper _mothershipHelper;
    private ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
    private int _stackTraceId;

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
        configurePage.createIssueURL().setValue(WebTestHelper.getContextPath() + "/" + ISSUES_PROJECT + "/issues-insert.view?");
        configurePage.issuesContainer().setValue("/" + ISSUES_PROJECT);
        configurePage.save();

        _containerHelper.createProject(ISSUES_PROJECT, null);
        ApiPermissionsHelper permHelper = new ApiPermissionsHelper(this);
        permHelper.createProjectGroup(ISSUES_GROUP, ISSUES_PROJECT);
        permHelper.addUserToProjGroup(ASSIGNEE, ISSUES_PROJECT, ISSUES_GROUP);
        permHelper.addMemberToRole(ISSUES_GROUP, "Editor", MemberType.group, ISSUES_PROJECT);
    }

    @Before
    public void preTest() throws Exception
    {
        _mothershipHelper = new MothershipHelper(getDriver());
        triggerExceptions(TestActions.ExceptionActions.npe);
        _stackTraceId = _mothershipHelper.getLatestStackTraceId();
        _mothershipHelper.resetStackTrace(_stackTraceId);
    }

    @Test
    public void testCreateIssue() throws Exception
    {
        Integer highestIssueId = _mothershipHelper.getHighestIssueId();

        ShowExceptionsPage showExceptionsPage = ShowExceptionsPage.beginAt(this);
        ExceptionSummaryDataRegion exceptionSummary = showExceptionsPage.exceptionSummary();
        StackTraceDetailsPage detailsPage = exceptionSummary.clickStackTrace(_stackTraceId);
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
        Integer newIssueId = _mothershipHelper.getHighestIssueId();
        assertNotEquals("Didn't create a new issue.", highestIssueId, newIssueId);
        showExceptionsPage = new ShowExceptionsPage(getDriver());
        exceptionSummary = showExceptionsPage.exceptionSummary();
        detailsPage = exceptionSummary.clickStackTrace(_stackTraceId);
        Integer bugNumber = Integer.parseInt(detailsPage.bugNumber().getValue());
        assertEquals("Exception's related issue not set", newIssueId, bugNumber);
    }

    @Test
    public void testAssignException() throws Exception
    {
        ShowExceptionsPage showExceptionsPage = ShowExceptionsPage.beginAt(this);
        ExceptionSummaryDataRegion exceptionSummary = showExceptionsPage.exceptionSummary();
        exceptionSummary.uncheckAll();
        exceptionSummary.checkCheckboxByPrimaryKey(_stackTraceId);
        exceptionSummary.assignSelectedTo(displayNameFromEmail(ASSIGNEE));

        impersonate(ASSIGNEE);
        {
            showExceptionsPage = goToMothership().clickMyExceptions();
            exceptionSummary = showExceptionsPage.exceptionSummary();
            assertEquals("Should be only one issue assigned to user", 1, exceptionSummary.getDataRowCount());
            StackTraceDetailsPage detailsPage = exceptionSummary.clickStackTrace(_stackTraceId);
            assertElementPresent(Locator.linkWithText("#" + _stackTraceId));
            assertEquals(displayNameFromEmail(ASSIGNEE), detailsPage.assignedTo().getFirstSelectedOption().getText());
        }
        stopImpersonating();
    }

    @Test
    public void testCreateIssueForAssignedException() throws Exception
    {
        ShowExceptionsPage showExceptionsPage = ShowExceptionsPage.beginAt(this);
        ExceptionSummaryDataRegion exceptionSummary = showExceptionsPage.exceptionSummary();
        exceptionSummary.uncheckAll();
        exceptionSummary.checkCheckboxByPrimaryKey(_stackTraceId);
        exceptionSummary.assignSelectedTo(displayNameFromEmail(ASSIGNEE));

        StackTraceDetailsPage detailsPage = exceptionSummary.clickStackTrace(_stackTraceId);
        InsertPage insertPage = detailsPage.clickCreateIssue();
        assertEquals("Exception assignment != New issue assignment",
                displayNameFromEmail(ASSIGNEE), insertPage.assignedTo().getValue());
    }

    private ShowExceptionsPage goToMothership()
    {
        goToProjectHome(MOTHERSHIP_PROJECT);
        return new ShowExceptionsPage(getDriver());
    }

    protected void triggerExceptions(TestActions.ExceptionActions... actions)
    {
        checkErrors();
        for (TestActions.ExceptionActions action : actions)
        {
            action.triggerException();
        }
        checkExpectedErrors(2 * actions.length);
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