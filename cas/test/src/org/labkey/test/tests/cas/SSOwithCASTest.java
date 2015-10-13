/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.test.tests.cas;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.Locators;
import org.labkey.test.TestCredentials;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.DailyA;
import org.labkey.test.credentials.Login;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PasswordUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.labkey.test.WebTestHelper.getHttpGetResponse;

@Category({DailyA.class})
public class SSOwithCASTest extends BaseWebDriverTest
{
    private final File HEADER_LOGO_FILE = TestFileUtils.getSampleData("SSO/CAS/cas_small.png");
    private final File LOGIN_LOGO_FILE = TestFileUtils.getSampleData("SSO/CAS/cas_big.png");
    private static final String credentialKey = "CAS";
    private final String CAS_HOST;
    private final Login EXISTING_USER_LOGIN;
    private final Login NEW_USER_LOGIN;

    public SSOwithCASTest() throws IOException
    {
        super();
        CAS_HOST = TestCredentials.getServer(credentialKey).getHost();
        EXISTING_USER_LOGIN = TestCredentials.getServer(credentialKey).getLogins().get(0);
        NEW_USER_LOGIN = TestCredentials.getServer(credentialKey).getLogins().get(0);
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);

        deleteUsersIfPresent(EXISTING_USER_LOGIN.getEmail(), NEW_USER_LOGIN.getEmail());
    }

    @BeforeClass
    public static void setupProject()
    {
        SSOwithCASTest init = (SSOwithCASTest) getCurrentTest();

        init.doSetup();
    }

    private void doSetup()
    {
        configureCASServer();
    }

    @Before
    public void preTest()
    {
        enableCAS();
        casLogout();
    }

    @Test
    public void testNewUserSSO()
    {
        signOut();

        //Click on CAS link
        clickAndWait(Locator.linkWithHref("/labkey/login/ssoRedirect.view?provider=CAS"));

        casLogin(NEW_USER_LOGIN);

        assertTrue("Not on customize user page after new user CAS login", getDriver().getCurrentUrl().contains("/user/showUpdate.view"));
        assertEquals("Wrong email for new user.", NEW_USER_LOGIN.getEmail(), getText(Locator.css(".labkey-nav-page-header")));
        String displayName = getFormElement(Locator.name("quf_DisplayName"));
        assertEquals("Wrong display name for new user.", displayNameFromEmail(NEW_USER_LOGIN.getEmail()), displayName);

        ensureSignedInAsAdmin();
        deleteUsersIfPresent(NEW_USER_LOGIN.getEmail());
    }

    @Test
    public void testCASIcons()
    {
        //set logos
        beginAt("login/pickAuthLogo.view?provider=CAS");

        setFormElement(Locator.name("auth_header_logo_file"), HEADER_LOGO_FILE);
        setFormElement(Locator.name("auth_login_page_logo_file"), LOGIN_LOGO_FILE);
        clickButton("Save");

        //sign out
        signOut();

        //check for image on the header
        String imageHeader = getAttribute(Locator.tagWithAttribute("img", "alt", "Sign in using CAS"), "src");
        assertFalse("CAS image not found in the header", imageHeader.contains(HEADER_LOGO_FILE.getName()));

        //check for image on the SignIn page
        clickAndWait(Locators.signInButtonOrLink);//Go to Labkey Sign-in page
        String imageLogin = getAttribute(Locator.tagWithAttribute("img", "alt", "Sign in using CAS"), "src");
        assertFalse("CAS image not found in the header", imageLogin.contains(LOGIN_LOGO_FILE.getName()));

        signIn();

        //Delete logos
        beginAt("login/pickAuthLogo.view?provider=CAS");
        click(Locator.linkWithText("delete"));
        click(Locator.linkWithText("delete"));

        //Save
        clickButton("Save");

        verifyLogoDeletion();
    }

    @Test
    public void testSSOWithCASFromLoginPage()
    {
        testCAS(true);
    }

    @Test
    public void testSSOwithCASFromHeaderLink()
    {
        testCAS(false);
    }

    @Test
    public void testBogusTicket()
    {
        signOut();

        clickAndWait(Locators.signInButtonOrLink);//Go to Labkey Sign-in page

        beginAt("/cas/validate.view?&ticket=randomstringforbogusticket");

        assertTextPresent("Invalid ticket");
    }

    @Test
    public void testCASUnreachable()
    {
        //Configure CAS with "wrong" url
        beginAt("cas/configure.view?");
        setFormElement(Locator.name("serverUrl"), "www.labkey.org/cas"); //adding "/cas" in the end otherwise it doesn't allow me to save. Also, cannot save empty strings if configured previously.
        clickButton("Save");

        assertTextPresent("Enter a valid HTTP URL to your Apereo CAS server (e.g., https://test.org/cas)");

        setFormElement(Locator.name("serverUrl"), "https://www.labkey.org/cas");
        clickButton("Save");

        signOut();
        clickAndWait(Locator.linkWithHref("/labkey/login/ssoRedirect.view?provider=CAS"));
        assertEquals("invalid configured serverUrl, should get a 404 response", 404, getResponseCode()); //check for 404
        signIn();

        //configure CAS correctly for the other tests to run. Could add this part in Before method, but this is the only
        //test that requires re-configuring CAS Server correctly (since it is already configured once in BeforeClass method)
        configureCASServer();
    }

    private void testCAS(boolean loginPage)
    {
        createUser(EXISTING_USER_LOGIN.getEmail(), null);
        signOut();

        clickFolder("support");
        String relativeURLBeforeSignIn = getCurrentRelativeURL();

        if(loginPage)
            clickAndWait(Locators.signInButtonOrLink);//Go to Labkey Sign-in page

        //Click on CAS link
        clickAndWait(Locator.linkWithHref("/labkey/login/ssoRedirect.view?provider=CAS"));

        //CAS login page - Sign in using CAS
        casLogin(EXISTING_USER_LOGIN);

        if (getDriver().getCurrentUrl().contains("/user/showUpdate.view")) // Redirects to customize user on first login
            clickButton("Submit");

        //Should be re-directed the page user was previously on
        String relativeURLAfterSignIn = getCurrentRelativeURL();
        assertEquals("After successful SSO with CAS, user should be redirected to the same URL they were on before Sign In",
                relativeURLBeforeSignIn, relativeURLAfterSignIn);

        //User should be CAS user
        assertEquals("User should be signed in with CAS userId", displayNameFromEmail(EXISTING_USER_LOGIN.getEmail()), getDisplayName());

        //Sign out CAS user, should sign out from Labkey, but should remained Sign In into CAS.
        signOut();

        String relativeURLBeforeSignIn2 = getCurrentRelativeURL();

        if(loginPage)
            clickAndWait(Locators.signInButtonOrLink);//Go to Labkey Sign-in page

        //Click on CAS link
        clickAndWait(Locator.linkWithHref("/labkey/login/ssoRedirect.view?provider=CAS"));

        String relativeURLAfterSignIn2 = getCurrentRelativeURL();

        assertEquals("User should be redirected to the same URL they were previously on after Signing In via CAS link",
                relativeURLBeforeSignIn2, relativeURLAfterSignIn2);

        //User should be CAS user
        assertEquals("User should be still signed in with CAS userId", displayNameFromEmail(EXISTING_USER_LOGIN.getEmail()), getDisplayName());

        ensureSignedInAsAdmin();
        deleteUsersIfPresent(EXISTING_USER_LOGIN.getEmail());
    }

    @LogMethod(quiet = true)
    private void enableCAS()
    {
        try
        {
            assertEquals("Failed to enable SSO with CAS", 200, getHttpGetResponse(WebTestHelper.getBaseURL() + "/login/enable.view?provider=CAS", PasswordUtil.getUsername(), PasswordUtil.getPassword()));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to enable SSO with CAS", e);
        }
    }

    @LogMethod(quiet = true)
    private void disableCAS()
    {
        try
        {
            assertEquals("Failed to disable SSO with CAS", 200, getHttpGetResponse(WebTestHelper.getBaseURL() + "/login/disable.view?provider=CAS", PasswordUtil.getUsername(), PasswordUtil.getPassword()));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to disable SSO with CAS", e);
        }
    }

    private void configureCASServer()
    {
        beginAt("cas/configure.view?");
        setFormElement(Locator.name("serverUrl"), CAS_HOST);
        clickButton("Save");
    }

    private void casLogin(Login login)
    {
        setFormElement(Locator.input("username"), login.getUsername());
        setFormElement(Locator.input("password"), login.getPassword());
        clickAndWait(Locator.tagWithName("input", "submit"));
    }

    private void casLogout()
    {
        getDriver().navigate().to(CAS_HOST + "/logout");
    }

    private void verifyLogoDeletion()
    {
        String auth_header_logo_file = getFormElement(Locator.name("auth_header_logo_file"));
        assertEquals("Header logo not deleted.", "", auth_header_logo_file.trim());

        String auth_login_page_logo_file = getFormElement(Locator.name("auth_login_page_logo_file"));
        assertEquals("Login Page logo not deleted.", "", auth_login_page_logo_file.trim());
    }

    @After
    public void postTest()
    {
        disableCAS();
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
        return Arrays.asList("authentication");
    }
}