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

import org.labkey.test.TestFileUtils;
import org.labkey.test.Locator;
import org.labkey.test.components.html.Checkbox;
import org.labkey.test.pages.core.login.LoginConfigRow;
import org.labkey.test.pages.core.login.SsoAuthDialogBase;
import org.labkey.test.params.login.AuthenticationProvider;
import org.openqa.selenium.WebDriver;

import java.io.File;

public class TestSsoProvider extends AuthenticationProvider<TestSsoProvider.TestSsoConfigureDialog>
{
    public static final File THUMBNAIL_COOL = TestFileUtils.getSampleData("thumbnails/Super Cool R Report/Thumbnail.png");
    public static final File THUMBNAIL_UNCOOL = TestFileUtils.getSampleData("thumbnails/Want To Be Cool/Thumbnail.png");

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

        /**
         * Sets "Default to this TestSSO configuration", which redirects the login page straight to the TestSSO page
         * instead of requiring the user to click on a logo.
         */
        public TestSsoConfigureDialog setDefaultConfiguration(boolean isDefault)
        {
            elementCache().autoRedirectCheckbox.set(isDefault);
            return this;
        }

        /**
         * Sets "Skip Reauthentication", which exempts users who authenticated with this configuration from
         * reauthenticating when signing electronically.
         */
        public TestSsoConfigureDialog setSkipReauthentication(boolean skipReauthentication)
        {
            elementCache().skipReauthenticationCheckbox.set(skipReauthentication);
            return this;
        }

        @Override
        protected TestSsoConfigureDialog getThis()
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

        protected class ElementCache extends SsoAuthDialogBase<TestSsoConfigureDialog>.ElementCache
        {
            Checkbox autoRedirectCheckbox = new Checkbox(Locator.tagWithId("input", "autoRedirect").findWhenNeeded(this));
            Checkbox skipReauthenticationCheckbox = new Checkbox(Locator.tagWithId("input", "SkipReauthentication").findWhenNeeded(this));
        }
    }
}
