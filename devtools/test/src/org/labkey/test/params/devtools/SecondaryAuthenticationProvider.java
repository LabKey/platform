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
