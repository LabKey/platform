/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
package org.labkey.authentication.openid;

import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.RedirectException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

public class OpenIdController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(OpenIdController.class);

    public OpenIdController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }


    @RequiresNoPermission
    public class RedirectAction extends SimpleViewAction<ReturnUrlForm>
    {
        @Override
        public ModelAndView getView(ReturnUrlForm returnUrlForm, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            URLHelper returnURL = returnUrlForm.getReturnURLHelper();

            // double embedding the returnURL seems to make this more fragile, so just remember it
            request.getSession().setAttribute(OpenIdController.class.getName() + "$returnURL", null==returnURL ? null : returnURL);

            // openid return_to
            URLHelper return_to = new ActionURL(ReturnAction.class,getContainer());
            request.getSession().setAttribute(GoogleOpenIdProvider.class.getName() + "$return_to", null==return_to ? null : return_to);

            String redirect = GoogleOpenIdProvider.getAuthenticationUrl(request, return_to);
            throw new RedirectException(redirect);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
   }


    @RequiresNoPermission
    public class ReturnAction extends SimpleViewAction<ReturnUrlForm>
    {
        @Override
        public ModelAndView getView(ReturnUrlForm returnUrlForm, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            URLHelper returnURL = (URLHelper)request.getSession().getAttribute(OpenIdController.class.getName() + "$returnURL");
            ActionURL login = PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL(returnURL);
            login.addParameters(getViewContext().getActionURL().getParameters());
            throw new RedirectException(login);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }
}