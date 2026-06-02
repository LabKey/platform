/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
