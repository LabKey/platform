package org.labkey.test.tests.mothership;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.pages.mothership.EditUpgradeMessagePage;
import org.labkey.test.util.ExperimentalFeaturesHelper;
import org.labkey.test.util.TestUser;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


@Category({})
public class InProductMessagingTest extends BaseWebDriverTest
{

    private static final TestUser GUEST_USER = new TestUser("inproductmessageguest@test.test");
    private static final TestUser GUEST_AUTHOR = new TestUser("inproductmessageauthor@test.test");

    private static final String MARKETING_MESSAGE = "More features are available! Click <a href='https://www.labkey.com/products-services/labkey-server/'>here</a> to learn about our Premium Editions.";

    @Override
    protected void doCleanup(boolean afterTest)
    {
        _userHelper.deleteUsers(afterTest, GUEST_USER, GUEST_AUTHOR);
    }

    @BeforeClass
    public static void setupProject()
    {
        InProductMessagingTest init = (InProductMessagingTest) getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        GUEST_USER.create(this);
        GUEST_AUTHOR.create(this)
                .addPermission("Author", "home");
    }


    @Test
    public void testSetMarketingMessage()
    {
        ExperimentalFeaturesHelper.enableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");
        var editPage = EditUpgradeMessagePage.beginAt(this);
        editPage.setMarketingMessage(MARKETING_MESSAGE);
        editPage.save();
        var alertWarning = waitForAlertWarning();
        checker().withScreenshot("unexpected_banner_content")
                .wrapAssertion(()-> assertThat(alertWarning.getText())
                                .as("Expect a banner to be present with our specified text")
                                .contains("More features are available! Click here to learn about our Premium Editions."));
        dismissAlert(alertWarning);
        ExperimentalFeaturesHelper.disableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");
    }

    @Test
    public void testMarketingMessageAppearsForGuest() throws Exception
    {
        ExperimentalFeaturesHelper.enableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");
        var editPage = EditUpgradeMessagePage.beginAt(this);
        editPage.setMarketingMessage(MARKETING_MESSAGE);
        editPage.save();
        goToHome();
        GUEST_USER.impersonate(true);
        refresh();
        var alertWarning = waitForAlertWarning();
        checker().withScreenshot("unexpected_banner_content")
                .wrapAssertion(()-> assertThat(alertWarning.getText())
                        .as("Expect a banner to be present with our specified text")
                        .contains("More features are available! Click here to learn about our Premium Editions."));
        dismissAlert(alertWarning);
        GUEST_USER.stopImpersonating();
        ExperimentalFeaturesHelper.disableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");
    }

    @Test
    public void testMarketingMessageAppearsForAuthor() throws Exception
    {
        ExperimentalFeaturesHelper.enableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");
        var editPage = EditUpgradeMessagePage.beginAt(this);
        editPage.setMarketingMessage(MARKETING_MESSAGE);
        editPage.save();
        goToHome();
        GUEST_AUTHOR.impersonate(true);
        refresh();
        var alertWarning = waitForAlertWarning();
        checker().withScreenshot("unexpected_banner_content")
                .wrapAssertion(()-> assertThat(alertWarning.getText())
                        .as("Expect a banner to be present with our specified text")
                        .contains("Many more features are available! Click here to learn about our Premium Editions."));
        dismissAlert(alertWarning);
        GUEST_AUTHOR.stopImpersonating();
        ExperimentalFeaturesHelper.disableExperimentalFeature(createDefaultConnection(), "localMarketingUpdates");
    }

    /*
        Might be worthwhile to migrate bannerhelper out of SM, or to make a general wrapper for banner-alerts
     */
    private WebElement waitForAlertWarning()
    {
        return Locator.tagWithClass("div", "alert-dismissable").waitForElement(getDriver(), 2000);
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
