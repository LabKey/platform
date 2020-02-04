package org.labkey.test.params.devtools;


import org.labkey.test.components.devtools.SecondaryAuthConfigureDialog;
import org.labkey.test.pages.core.login.LoginConfigRow;
import org.labkey.test.params.login.AuthenticationProvider;
import org.openqa.selenium.WebDriver;

public class SecondaryAuthenticationProvider extends AuthenticationProvider<SecondaryAuthConfigureDialog>
{
    @Override
    public String getProviderName()
    {
        return "TestSecondary";
    }

    @Override
    public String getProviderDescription()
    {
        return "Adds a trivial, insecure secondary authentication requirement (for test purposes only)";
    }

    @Override
    public SecondaryAuthConfigureDialog getEditDialog(LoginConfigRow row)
    {
        return new SecondaryAuthConfigureDialog(row);
    }

    @Override
    public SecondaryAuthConfigureDialog getNewDialog(WebDriver driver)
    {
        return new SecondaryAuthConfigureDialog(driver);
    }
}
