/*
 * Copyright (c) 2016-2018 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.AuthenticationConfigurationCache;
import org.labkey.api.security.AuthenticationManager.AuthenticationConfigurationForm;
import org.labkey.api.security.AuthenticationManager.BaseSsoValidateAction;
import org.labkey.api.security.AuthenticationProvider.AuthenticationResponse;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.SSOConfigureAction;
import org.labkey.api.security.SSOConfigureAction.SSOConfigureForm;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by adam on 6/5/2016.
 */
public class TestSsoController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(TestSsoController.class);

    public TestSsoController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresNoPermission
    @AllowedDuringUpgrade
    public class TestSsoAction extends SimpleViewAction<AuthenticationConfigurationForm>
    {
        @Override
        public ModelAndView getView(AuthenticationConfigurationForm form, BindException errors)
        {
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            return new JspView<>("/org/labkey/devtools/authentication/testSso.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class TestSsoForm extends AuthenticationConfigurationForm
    {
        private String _email;

        public String getEmail()
        {
            return _email;
        }

        public void setEmail(String email)
        {
            _email = email;
        }
    }

    @AllowedDuringUpgrade
    @RequiresNoPermission
    public class ValidateAction extends BaseSsoValidateAction<TestSsoForm>
    {
        @Override
        public @NotNull AuthenticationResponse validateAuthentication(TestSsoForm form, BindException errors) throws Exception
        {
            TestSsoConfiguration configuration = AuthenticationConfigurationCache.getActiveConfiguration(TestSsoConfiguration.class, form.getConfiguration());

            if (null == configuration)
                throw new NotFoundException("Invalid TestSso configuration");

            return AuthenticationResponse.createSuccessResponse(configuration.getAuthenticationProvider(), new ValidEmail(form.getEmail()));
        }
    }

    public static class TestSsoConfigureForm extends SSOConfigureForm<TestSsoConfiguration>
    {
        public TestSsoConfigureForm()
        {
            setDescription("TestSSO Configuration");
        }

        @Override
        public String getProvider()
        {
            return TestSsoProvider.NAME;
        }
    }

    @AdminConsoleAction
    public class ConfigureAction extends SSOConfigureAction<TestSsoConfigureForm, TestSsoConfiguration>
    {
        @Override
        public ModelAndView getConfigureView(TestSsoConfigureForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/devtools/authentication/testSsoConfigure.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("authenticationModule");
            PageFlowUtil.urlProvider(LoginUrls.class).appendAuthenticationNavTrail(root).addChild("Configure " + TestSsoProvider.NAME + " Authentication");
            return root;
        }

        @Override
        protected void validateForm(TestSsoConfigureForm form, Errors errors)
        {
        }

        @Override
        public ActionURL getSuccessURL(TestSsoConfigureForm form)
        {
            return getConfigureURL(form.getRowId());  // Redirect to same action -- reload props from database
        }
    }

    public static ActionURL getConfigureURL(@Nullable Integer configuration)
    {
        ActionURL url = new ActionURL(ConfigureAction.class, ContainerManager.getRoot());

        if (null != configuration)
            url.addParameter("configuration", configuration);

        return url;
    }
}
