package org.labkey.test.components.devtools;

import org.labkey.test.pages.core.login.AuthDialogBase;
import org.labkey.test.pages.core.login.LoginConfigRow;
import org.labkey.test.params.login.AuthenticationProvider;
import org.openqa.selenium.WebDriver;

public class SecondaryAuthConfigureDialog extends AuthDialogBase<SecondaryAuthConfigureDialog>
{

    public SecondaryAuthConfigureDialog(LoginConfigRow row)
    {
        super(row);
    }

    public SecondaryAuthConfigureDialog(WebDriver driver, AuthenticationProvider authenticationProvider)
    {
        super(authenticationProvider, driver);
    }

    @Override
    protected SecondaryAuthConfigureDialog getThis()  // supports chaining/builder pattern from the base class
    {
        return this;
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    @Override
    protected ElementCache elementCache()
    {
        return (ElementCache) super.elementCache();
    }


    protected class ElementCache extends AuthDialogBase.ElementCache
    {

    }

}
