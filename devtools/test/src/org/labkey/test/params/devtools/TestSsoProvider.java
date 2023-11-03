package org.labkey.test.params.devtools;

import org.labkey.test.pages.core.login.LoginConfigRow;
import org.labkey.test.pages.core.login.SsoAuthDialogBase;
import org.labkey.test.params.login.AuthenticationProvider;
import org.openqa.selenium.WebDriver;

public class TestSsoProvider extends AuthenticationProvider<TestSsoProvider.TestSsoConfigureDialog>
{
    @Override
    public String getProviderName()
    {
        return "TestSSO";
    }

    @Override
    public String getProviderDescription()
    {
        return "A trivial, insecure SSO authentication provider (for test purposes only)";
    }

    @Override
    public TestSsoConfigureDialog getEditDialog(LoginConfigRow row)
    {
        return new TestSsoConfigureDialog(row);
    }

    @Override
    public TestSsoConfigureDialog getNewDialog(WebDriver driver)
    {
        return new TestSsoConfigureDialog(driver);
    }

    public class TestSsoConfigureDialog extends SsoAuthDialogBase<TestSsoConfigureDialog>
    {
        public TestSsoConfigureDialog(LoginConfigRow row)
        {
            super(row);
        }

        public TestSsoConfigureDialog(WebDriver driver)
        {
            super(TestSsoProvider.this, driver);
        }

        @Override
        protected TestSsoConfigureDialog getThis()
        {
            return this;
        }

    }
}
