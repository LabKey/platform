/*
 * Copyright (c) 2016-2019 LabKey Corporation
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

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.security.AuthenticationConfigurationCache;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.PrimaryAuthenticationResult;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SaveConfigurationAction;
import org.labkey.api.security.SaveConfigurationForm;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: adam
 * Date: 3/27/2015
 * Time: 5:40 PM
 */
public class TestSecondaryController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(TestSecondaryController.class);

    public TestSecondaryController()
    {
        setActionResolver(_actionResolver);
    }

    public static ActionURL getTestSecondaryURL(Container c, int rowId)
    {
        ActionURL url = new ActionURL(TestSecondaryAction.class, c);
        url.addParameter("configuration", rowId);

        return url;
    }

    public static class TestSecondaryForm extends ReturnUrlForm
    {
        private String _email = null;
        private int _configuration = -1;
        private boolean _valid;

        public String getEmail()
        {
            return _email;
        }

        public void setEmail(String email)
        {
            _email = email;
        }

        public int getConfiguration()
        {
            return _configuration;
        }

        @SuppressWarnings("unused")
        public void setConfiguration(int configuration)
        {
            _configuration = configuration;
        }

        public boolean isValid()
        {
            return _valid;
        }

        @SuppressWarnings("unused")
        public void setValid(boolean valid)
        {
            _valid = valid;
        }
    }

    @RequiresNoPermission
    @AllowedDuringUpgrade
    public class TestSecondaryAction extends FormViewAction<TestSecondaryForm>
    {
        @Override
        public void validateCommand(TestSecondaryForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(TestSecondaryForm form, boolean reshow, BindException errors)
        {
            if (!getUser().isGuest())
                return HttpView.redirect(AuthenticationManager.getAfterLoginURL(getContainer(), null, getUser()));

            PrimaryAuthenticationResult result = AuthenticationManager.getPrimaryAuthenticationResult(getViewContext().getSession());

            if (null == result || null == result.getUser())
                throw new NotFoundException("You must login before initiating secondary authentication");

            form.setEmail(result.getUser().getEmail());

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            getPageConfig().setIncludeLoginLink(false);
            getPageConfig().setIncludeSearch(false);

            return new JspView<>("/org/labkey/devtools/authentication/testSecondary.jsp", form, errors);
        }

        @Override
        public boolean handlePost(TestSecondaryForm form, BindException errors)
        {
            if (form.isValid())
            {
                PrimaryAuthenticationResult result = AuthenticationManager.getPrimaryAuthenticationResult(getViewContext().getSession());

                if (null != result)
                {
                    User user = result.getUser();

                    if (null != user)
                    {
                        TestSecondaryConfiguration configuration = AuthenticationConfigurationCache.getActiveConfiguration(TestSecondaryConfiguration.class, form.getConfiguration());

                        if (null != configuration)
                            AuthenticationManager.setSecondaryAuthenticationUser(getViewContext().getSession(), configuration.getRowId(), user);
                    }

                    return true;
                }
            }

            return false;
        }

        @Override
        public URLHelper getSuccessURL(TestSecondaryForm form)
        {
            return AuthenticationManager.handleAuthentication(getViewContext().getRequest(), getContainer()).getRedirectURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class TestSecondarySaveConfigurationAction extends SaveConfigurationAction<TestSecondarySaveConfigurationForm, TestSecondaryConfiguration>
    {
        @Override
        public void validate(TestSecondarySaveConfigurationForm form, Errors errors)
        {
        }
    }

    public static class TestSecondarySaveConfigurationForm extends SaveConfigurationForm
    {
        @Override
        public String getProvider()
        {
            return TestSecondaryProvider.NAME;
        }
    }
}
