/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.devtools.authentication;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.security.AuthenticationConfigurationCache;
import org.labkey.api.security.AuthenticationManager.AuthenticationConfigurationForm;
import org.labkey.api.security.AuthenticationManager.BaseSsoValidateAction;
import org.labkey.api.security.AuthenticationProvider.AuthenticationResponse;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SsoSaveConfigurationAction;
import org.labkey.api.security.SsoSaveConfigurationAction.SsoSaveConfigurationForm;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

public class TestSsoController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(TestSsoController.class);

    public TestSsoController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresNoPermission
    @AllowedDuringUpgrade
    @IgnoresTermsOfUse
    public static class TestSsoAction extends SimpleViewAction<TestSsoForm>
    {
        @Override
        public ModelAndView getView(TestSsoForm form, BindException errors)
        {
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            return new JspView<>("/org/labkey/devtools/authentication/testSso.jsp", form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class TestSsoForm extends AuthenticationConfigurationForm
    {
        private String _email;
        private boolean _reauth;

        public String getEmail()
        {
            return _email;
        }

        @SuppressWarnings("unused")
        public void setEmail(String email)
        {
            _email = email;
        }

        public boolean isReauth()
        {
            return _reauth;
        }

        @SuppressWarnings("unused")
        public void setReauth(boolean reauth)
        {
            _reauth = reauth;
        }
    }

    @AllowedDuringUpgrade
    @RequiresNoPermission
    @IgnoresTermsOfUse
    // NOTE: Always invoked in the root, so no need for @IgnoresForbiddenProjectCheck
    public static class ValidateAction extends BaseSsoValidateAction<TestSsoForm>
    {
        @Override
        public @NotNull AuthenticationResponse validateAuthentication(TestSsoForm form, BindException errors) throws Exception
        {
            TestSsoConfiguration configuration = AuthenticationConfigurationCache.getActiveConfiguration(TestSsoConfiguration.class, form.getConfiguration());

            if (null == configuration)
                throw new NotFoundException("Invalid TestSso configuration");

            return AuthenticationResponse.success(configuration, new ValidEmail(form.getEmail())).setReauth(form.isReauth());
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class TestSsoSaveConfigurationAction extends SsoSaveConfigurationAction<TestSsoSaveConfigurationForm, TestSsoConfiguration>
    {
        @Override
        public void validate(TestSsoSaveConfigurationForm form, Errors errors)
        {
        }
    }

    public static class TestSsoSaveConfigurationForm extends SsoSaveConfigurationForm
    {
        @Override
        public String getProvider()
        {
            return TestSsoProvider.NAME;
        }

        @Override
        public @NotNull Map<String, Object> getPropertyMap()
        {
            return null != _domain ? Map.of("domain", _domain) : Map.of();
        }
    }
}
