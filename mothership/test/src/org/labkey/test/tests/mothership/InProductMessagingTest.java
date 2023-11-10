package org.labkey.test.tests.mothership;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.categories.Daily;
import org.labkey.test.pages.mothership.EditUpgradeMessagePage;
import org.labkey.test.util.ExperimentalFeaturesHelper;
import org.labkey.test.util.TestUser;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


@Category({Daily.class})
public class InProductMessagingTest extends BaseWebDriverTest
{

    private static final TestUser TEST_AUTHOR = new TestUser("inproductmessageauthor@test.test");

    private static final String MARKETING_MESSAGE = "More features are available! Click <a href='https://www.labkey.com/products-services/labkey-server/'>here</a> to learn about our Premium Editions.";
    private static final String DEFAULT_MESSAGE = "Do more with LabKey Server. Click here to learn about our Premium Editions.";

    @Override
    protected void doCleanup(boolean afterTest)
    {
        _userHelper.deleteUsers(afterTest, TEST_AUTHOR);
    }

    @BeforeClass
    public static void setupProject()
    {
        InProductMessagingTest init = (InProductMessagingTest) getCurrentTest();

        init.doSetup();
    }

    @AfterClass
    static public void doAfter()
    {
        InProductMessagingTest after = (InProductMessagingTest) getCurrentTest();
        after.disableExpFeature();
    }

    private void disableExpFeature()
    {
        ExperimentalFeaturesHelper.disableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");
    }

    private void doSetup()
    {
        TEST_AUTHOR.create(this)
                .addPermission("Author", "home");
    }

    @Test
    public void testSetMarketingMessage()
    {
        ExperimentalFeaturesHelper.enableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");
        var editPage = EditUpgradeMessagePage.beginAt(this);
        editPage.setMarketingMessage(MARKETING_MESSAGE);
        editPage.save();
        refresh();
        var alertWarning = waitForAlertWarning();
        checker().withScreenshot("unexpected_banner_content")
                .wrapAssertion(()-> assertThat(alertWarning.getText())
                                .as("Expect a banner to be present with our specified text")
                                .contains("More features are available! Click here to learn about our Premium Editions."));
        dismissAlert(alertWarning);

        // sign out, then sign back in as Admin user
        signOut();
        signIn();
        var reappearingAlert = waitForAlertWarning();
        checker().withScreenshot("unexpected_banner_content_re-login")
                .wrapAssertion(()-> assertThat(reappearingAlert.getText())
                        .as("Expect a banner to appear after logging out/logging back in")
                        .contains("More features are available! Click here to learn about our Premium Editions."));

        // now toggle the exp feature off/on to reset the 24-hour clock
        ExperimentalFeaturesHelper.disableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");
        ExperimentalFeaturesHelper.enableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");

        // now clear the custom marketing message so other test methods can verify the default
        editPage = EditUpgradeMessagePage.beginAt(this);
        editPage.setMarketingMessage("");
        editPage.save();

        ExperimentalFeaturesHelper.disableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");
    }

    @Test
    public void testMarketingMessageAppearsForGuest() throws Exception
    {
        ExperimentalFeaturesHelper.enableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");

        goToHome();

        // now signout as admin, verify guest is shown the default banner
        signOut();
        refresh();

        var alertWarning = waitForAlertWarning();
        checker().withScreenshot("unexpected_banner_content")
                .wrapAssertion(()-> assertThat(alertWarning.getText())
                        .as("Expect a banner to be present with the default message")
                        .contains(DEFAULT_MESSAGE));
        dismissAlert(alertWarning);
        signIn();
        ExperimentalFeaturesHelper.disableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");
    }

    @Test
    public void testMarketingMessageAppearsForAuthor() throws Exception
    {
        ExperimentalFeaturesHelper.enableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");

        goToHome();
        waitForAlertWarning();
        TEST_AUTHOR.impersonate(true);
        refresh();
        var alertWarning = waitForAlertWarning();
        checker().withScreenshot("unexpected_banner_content")
                .wrapAssertion(()-> assertThat(alertWarning.getText())
                        .as("Expect a banner to be present with our specified text")
                        .contains(DEFAULT_MESSAGE));
        dismissAlert(alertWarning);
        TEST_AUTHOR.stopImpersonating();
        ExperimentalFeaturesHelper.disableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");
    }

    private WebElement waitForAlertWarning()
    {
        Locator alertLoc = Locator.tagWithClass("div", "alert-dismissable");
        WebDriverWrapper.waitFor(()-> {
            refresh();
            sleep(500);
            return alertLoc.existsIn(getDriver());
        }, "no alert appeared in time", 5_000);

        return alertLoc.findElement(getDriver());
    }

    private void dismissAlert(WebElement alert)
    {
        var closeLink = Locator.tagWithAttribute("a", "title", "dismiss").findElement(alert);
        closeLink.click();
        shortWait().until(ExpectedConditions.stalenessOf(alert));
    }

    @Override
    protected String getProjectName()
    {
        return "InProductMessagingTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList();
    }
}
