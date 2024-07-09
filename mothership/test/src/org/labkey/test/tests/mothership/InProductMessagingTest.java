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
import org.labkey.test.util.OptionalFeatureHelper;
import org.labkey.test.util.PostgresOnlyTest;
import org.labkey.test.util.TestUser;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


@Category({Daily.class})
public class InProductMessagingTest extends BaseWebDriverTest implements PostgresOnlyTest
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
        OptionalFeatureHelper.disableOptionalFeature(createDefaultConnection(), "localMarketingUpdates");
    }

    private void doSetup()
    {
        TEST_AUTHOR.create(this)
                .addPermission("Author", "home");
    }

    @Test
    public void testSetMarketingMessage()
    {
        // first, set the custom marketing message
        var editPage = EditUpgradeMessagePage.beginAt(this);
        editPage.setMarketingMessage(MARKETING_MESSAGE);
        editPage.save();

        // then, enable the exp feature and await the banner containing custom content
        OptionalFeatureHelper.enableOptionalFeature(createDefaultConnection(), "localMarketingUpdates");

        checker().withScreenshot("unexpected_banner_content")
                .awaiting(Duration.ofSeconds(10), ()-> assertThat(refreshAndAwaitAlert().getText())
                                .as("Expect a banner to be present with our specified text")
                                .contains("More features are available! Click here to learn about our Premium Editions."));
        dismissAlert(refreshAndAwaitAlert());

        // sign out, then sign back in as Admin user to verify that a new session re-shows the alert
        signOut();
        signIn();
        var reappearingAlert = refreshAndAwaitAlert();
        checker().withScreenshot("unexpected_banner_content_re-login")
                .wrapAssertion(()-> assertThat(reappearingAlert.getText())
                        .as("Expect a banner to appear after logging out/logging back in")
                        .contains("More features are available! Click here to learn about our Premium Editions."));

        // now clear the custom marketing message so other test methods can verify the default
        editPage = EditUpgradeMessagePage.beginAt(this);
        editPage.setMarketingMessage("");
        editPage.save();

        // don't leave an alert with the custom message lying around, other tests expect the default message
        dismissAlert(refreshAndAwaitAlert());

        OptionalFeatureHelper.disableOptionalFeature(createDefaultConnection(), "localMarketingUpdates");
    }

    @Test
    public void testMarketingMessageAppearsForGuest() throws Exception
    {
        OptionalFeatureHelper.enableOptionalFeature(createDefaultConnection(), "localMarketingUpdates");
        goToHome();

        // now signout as admin, verify guest is shown the default banner
        signOut();

        checker().withScreenshot("unexpected_banner_content")
                .awaiting(Duration.ofSeconds(10), ()-> assertThat(refreshAndAwaitAlert().getText())
                        .as("Expect a banner to be present with the default message")
                        .contains(DEFAULT_MESSAGE));
        dismissAlert(refreshAndAwaitAlert());   // don't leave an old alert around for the next test
        signIn();
        OptionalFeatureHelper.disableOptionalFeature(createDefaultConnection(), "localMarketingUpdates");
    }

    @Test
    public void testMarketingMessageAppearsForAuthor() throws Exception
    {
        OptionalFeatureHelper.enableOptionalFeature(createDefaultConnection(), "localMarketingUpdates");

        goToHome();
        /* give it some time for the exp feature task to process, sometimes the custom message will still be there */
        await().atMost(10, TimeUnit.SECONDS).until(()-> refreshAndAwaitAlert().getText().contains(DEFAULT_MESSAGE));

        TEST_AUTHOR.impersonate(true);

        checker().withScreenshot("unexpected_banner_content")
                .awaiting(Duration.ofSeconds(10), ()-> assertThat(refreshAndAwaitAlert().getText())
                        .as("Expect a banner to be present with our specified text")
                        .contains(DEFAULT_MESSAGE));
        dismissAlert(refreshAndAwaitAlert());
        TEST_AUTHOR.stopImpersonating();

        OptionalFeatureHelper.disableOptionalFeature(createDefaultConnection(), "localMarketingUpdates");
    }


    /*
        Refreshes the page every second until an alert-dismissable alert appears

        For context, the marketing message appears after enabling the exp feature. When the flag is enabled,
        several things have to happen on the server, so it can take a few seconds before the alert will be shown.
     */
    private WebElement refreshAndAwaitAlert()
    {
        Locator alertLoc = Locator.tagWithClass("div", "alert-dismissable");
        WebDriverWrapper.waitFor(()-> {
            refresh();
            sleep(1000);
            return alertLoc.existsIn(getDriver());
        }, "no alert appeared in time", WAIT_FOR_JAVASCRIPT);

        var alertEl = alertLoc.findElement(getDriver());
        log("found alert " + alertEl.getText());
        return alertEl;
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
