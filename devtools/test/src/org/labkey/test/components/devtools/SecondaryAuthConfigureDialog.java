/*
 * Copyright (c) 2020-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.test.components.devtools;

import org.labkey.test.pages.core.login.AuthDialogBase;
import org.labkey.test.pages.core.login.LoginConfigRow;
import org.labkey.test.params.devtools.SecondaryAuthenticationProvider;
import org.openqa.selenium.WebDriver;

public class SecondaryAuthConfigureDialog extends AuthDialogBase<SecondaryAuthConfigureDialog>
{

    public SecondaryAuthConfigureDialog(LoginConfigRow row)
    {
        super(row);
    }

    public SecondaryAuthConfigureDialog(WebDriver driver)
    {
        super(new SecondaryAuthenticationProvider(), driver);
    }


    @Override
    protected  SecondaryAuthConfigureDialog getThis()  // supports chaining/builder pattern from the base class
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
