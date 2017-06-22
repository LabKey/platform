/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.core.authentication.test;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.PrimaryAuthenticationResult;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.User;
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

    public TestSecondaryController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    public static ActionURL getTestSecondaryURL(Container c)
    {
        return new ActionURL(TestSecondaryAction.class, c);
    }

    public static class TestSecondaryForm extends ReturnUrlForm
    {
        private boolean _valid;

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
        public ModelAndView getView(TestSecondaryForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!getUser().isGuest())
                return HttpView.redirect(AuthenticationManager.getAfterLoginURL(getContainer(), null, getUser()));

            PrimaryAuthenticationResult result = AuthenticationManager.getPrimaryAuthenticationResult(getViewContext().getSession());

            if (null == result || null == result.getUser())
                throw new NotFoundException("You must login before initiating secondary authentication");

            String email = result.getUser().getEmail();

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            getPageConfig().setIncludeLoginLink(false);

            return new JspView<>("/org/labkey/core/authentication/test/testSecondary.jsp", email, errors);
        }

        @Override
        public boolean handlePost(TestSecondaryForm form, BindException errors) throws Exception
        {
            if (form.isValid())
            {
                User user = AuthenticationManager.getPrimaryAuthenticationResult(getViewContext().getSession()).getUser();

                if (null != user)
                    AuthenticationManager.setSecondaryAuthenticationUser(getViewContext().getSession(), TestSecondaryProvider.class, user);

                return true;
            }

            return false;
        }

        @Override
        public URLHelper getSuccessURL(TestSecondaryForm form)
        {
            return AuthenticationManager.handleAuthentication(getViewContext().getRequest(), getContainer()).getRedirectURL();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }
}
