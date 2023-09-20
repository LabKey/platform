package org.labkey.test.params.devtools;

import org.labkey.test.components.devtools.SecondaryAuthConfigureDialog;
import org.labkey.test.pages.core.login.LoginConfigRow;
import org.labkey.test.params.login.AuthenticationProvider;
import org.openqa.selenium.WebDriver;

public class SecondaryAuthenticationTOTPProvider extends AuthenticationProvider<SecondaryAuthConfigureDialog>
{
    @Override
    public String getProviderName()
    {
        return "TOTP 2 Factor";
    }

    @Override
    public String getProviderDescription()
    {
        return "Require two-factor authentication via Google Authenticator, Microsoft Authenticator, or another TOTP-enabled authenticator app";
    }

    @Override
    public SecondaryAuthConfigureDialog getEditDialog(LoginConfigRow row)
    {
        return new SecondaryAuthConfigureDialog(row);
    }

    @Override
    public SecondaryAuthConfigureDialog getNewDialog(WebDriver driver)
    {
        return new SecondaryAuthConfigureDialog(driver, this);
    }
}
