/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.authentication.oauth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyStore;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationProvider;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.CSRFUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.RedirectException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class OAuthController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(OAuthController.class);

    public OAuthController() throws Exception
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
            HttpServletResponse response = getViewContext().getResponse();
            URLHelper returnURL = returnUrlForm.getReturnURLHelper();

            // double embedding the returnURL seems to make this more fragile, so just remember it
            request.getSession().setAttribute(OAuthController.class.getName() + "$returnURL", null==returnURL ? null : returnURL);

            // openid return_to
            URLHelper return_to = new ActionURL(ReturnAction.class,getContainer());
            String csrf = CSRFUtil.getExpectedToken(request,response);
            String redirect = GoogleOAuthProvider.getAuthenticationUrl(csrf, return_to);
            throw new RedirectException(redirect);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
   }


    @RequiresNoPermission
    public class ReturnAction extends AuthenticationManager.BaseSsoValidateAction<ReturnUrlForm>
    {
        @NotNull
        @Override
        public String getProviderName()
        {
            return GoogleOAuthProvider.NAME;
        }

        @Nullable
        @Override
        public ValidEmail validate(ReturnUrlForm form) throws Exception
        {
            AuthenticationProvider.AuthenticationResponse auth = new GoogleOAuthProvider().authenticate(getViewContext().getRequest(), getViewContext().getResponse());
            if (auth.isAuthenticated())
                return auth.getValidEmail();
            return null;
        }
    }


    public static class ConfigureBean
    {
        String client_id;
        String client_secret;

        public void setClientId(String id)
        {
            this.client_id = id;
        }
        public void setClientSecret(String secret)
        {
            this.client_secret = secret;
        }
    }


    @RequiresSiteAdmin
    public class ConfigureAction extends FormViewAction<ConfigureBean>
    {
        @Override
        public void validateCommand(ConfigureBean target, Errors errors)
        {

        }

        @Override
        public ModelAndView getView(ConfigureBean form, boolean reshow, BindException errors) throws Exception
        {
            PropertyStore store = PropertyManager.getEncryptedStore();
            PropertyManager.PropertyMap map = store.getProperties(ContainerManager.getRoot(), GoogleOAuthProvider.class.getName());
            return new JspView(OAuthController.class, "configure.jsp", map);
        }

        @Override
        public boolean handlePost(ConfigureBean form, BindException errors) throws Exception
        {
            PropertyStore store = PropertyManager.getEncryptedStore();
            PropertyManager.PropertyMap map = store.getWritableProperties(ContainerManager.getRoot(),GoogleOAuthProvider.class.getName(),true);
            map.put("client_id", form.client_id);
            map.put("client_secret", form.client_secret);
            map.save();
            return true;
        }

        @Override
        public URLHelper getSuccessURL(ConfigureBean configureBean)
        {
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Authentication", PageFlowUtil.urlProvider(LoginUrls.class).getConfigureURL());
            root.addChild("Google+");
            return root;
        }
    }
}