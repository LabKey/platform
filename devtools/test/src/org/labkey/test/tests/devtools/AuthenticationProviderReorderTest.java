package org.labkey.test.tests.devtools;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.BootstrapLocators;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.Git;
import org.labkey.test.pages.core.login.LoginConfigurePage;
import org.labkey.test.params.devtools.TestSsoProvider;
import org.labkey.test.util.login.AuthenticationAPIUtils;
import org.openqa.selenium.WebElement;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@Category({Git.class})
public class AuthenticationProviderReorderTest extends BaseWebDriverTest
{
    private static final TestSsoProvider PROVIDER = new TestSsoProvider();
    private static final File THUMBNAIL_COOL = TestFileUtils.getSampleData("thumbnails/Super Cool R Report/Thumbnail.png");
    private static final File THUMBNAIL_UNCOOL = TestFileUtils.getSampleData("thumbnails/Want To Be Cool/Thumbnail.png");

    @Override
    protected void doCleanup(boolean afterTest)
    {
        AuthenticationAPIUtils.deleteConfigurations(PROVIDER.getProviderName(), createDefaultConnection());
    }

    @Test
    public void testReorderConfigurations()
    {
        String config_uncool = "TestSSO Uncool";
        String config_cool = "TestSSO Cool";

        log("Create SSO login configurations");
        LoginConfigurePage configurePage = LoginConfigurePage.beginAt(this);
        configurePage.addConfiguration(PROVIDER)
                .setDescription(config_uncool)
                .setLoginPageLogo(THUMBNAIL_UNCOOL)
                .setPageHeaderLogo(THUMBNAIL_UNCOOL)
                .clickApply();
        configurePage.addConfiguration(PROVIDER)
                .setDescription(config_cool)
                .setLoginPageLogo(THUMBNAIL_COOL)
                .setPageHeaderLogo(THUMBNAIL_COOL)
                .clickApply();

        signOut();
        assertSsoLinkOrder(config_uncool, config_cool);
        clickAndWait(Locator.linkWithText("Sign In"));
        assertSsoLinkOrder(config_uncool, config_cool);
        simpleSignIn();

        configurePage = LoginConfigurePage.beginAt(this);
        WebElement uncoolRow = Locator.byClass("domain-row-handle").findElement(configurePage.getPrimaryConfigurationRow(config_uncool));
        WebElement coolRow = Locator.byClass("domain-row-handle").findElement(configurePage.getPrimaryConfigurationRow(config_cool));
        dragAndDrop(uncoolRow, coolRow);
        BootstrapLocators.infoBanner.waitForElement(getDriver(), 2_000);
        configurePage.clickSaveAndFinish();

        signOut();
        assertSsoLinkOrder(config_cool, config_uncool);
        clickAndWait(Locator.linkWithText("Sign In"));
        assertSsoLinkOrder(config_cool, config_uncool);
        simpleSignIn();
    }

    private void assertSsoLinkOrder(String firstConfig, String secondConfig)
    {
        List<WebElement> ssoIcons = Locator.tagWithAttributeContaining("a", "href", "ssoRedirect").childTag("img").waitForElements(getDriver(), 2_000);
        List<String> ssoNames = ssoIcons.stream().map(el -> el.getAttribute("title")).map(s -> s.replace("Sign in using ", "")).toList();
        Assertions.assertThat(ssoNames).as("SSO link order").endsWith(firstConfig, secondConfig);
    }

    @Override
    protected String getProjectName()
    {
        return "AuthenticationProviderReorderTest Project";
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList();
    }
}
