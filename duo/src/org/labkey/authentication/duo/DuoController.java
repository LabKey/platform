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
package org.labkey.authentication.duo;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationProviderConfigAuditTypeProvider;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;

/**
 * User: tgaluhn
 * Date: 3/6/2015
 */
public class DuoController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(DuoController.class);

    public DuoController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    public static ActionURL getConfigureURL()
    {
        return new ActionURL(ConfigureAction.class, ContainerManager.getRoot());
    }

    @AdminConsoleAction
    @CSRF
    public class ConfigureAction extends FormViewAction<Config>
    {
        @Override
        public ModelAndView getView(Config form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/authentication/duo/configure.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("configureDuoTwoFactor");
            PageFlowUtil.urlProvider(LoginUrls.class).appendAuthenticationNavTrail(root).addChild("Configure Duo 2 Factor Authentication");
            return root;
        }

        @Override
        public void validateCommand(Config target, Errors errors)
        {
            if(StringUtils.isBlank(target.getIntegrationKey()))
                errors.reject(ERROR_MSG, "Integration Key cannot be blank.");
            if(StringUtils.isBlank(target.getSecretKey()))
                errors.reject(ERROR_MSG, "Secret Key cannot be blank.");
            if(StringUtils.isBlank(target.getApiHostname()))
                errors.reject(ERROR_MSG, "API Hostname cannot be blank.");
        }

        @Override
        public boolean handlePost(Config config, BindException errors) throws Exception
        {
            List<String> dirtyProps = new ArrayList<>();
            if (!config.getApiHostname().equalsIgnoreCase(DuoManager.getAPIHostname()))
                dirtyProps.add(DuoManager.Key.APIHostname.toString());
            if (!config.getIntegrationKey().equalsIgnoreCase(DuoManager.getIntegrationKey()))
                dirtyProps.add(DuoManager.Key.IntegrationKey.toString());
            if (!config.getSecretKey().equalsIgnoreCase(DuoManager.getSecretKey()))
                dirtyProps.add(DuoManager.Key.SecretKey.toString());

            if (!dirtyProps.isEmpty())
            {
                DuoManager.saveProperties(config);
                StringBuilder sb = new StringBuilder();
                for (String prop : dirtyProps)
                {
                    if (sb.length() > 0)
                        sb.append(", ");
                    sb.append(prop);
                }
                AuthenticationProviderConfigAuditTypeProvider.AuthProviderConfigAuditEvent event = new AuthenticationProviderConfigAuditTypeProvider.AuthProviderConfigAuditEvent(
                        ContainerManager.getRoot().getId(), DuoProvider.NAME + " provider configuration was changed.");
                event.setChanges(sb.toString());
                AuditLogService.get().addEvent(getUser(), event);
            }

            return true;
        }

        @Override
        public ActionURL getSuccessURL(Config config)
        {
            return getConfigureURL();  // Redirect to same action -- reload props from database
        }
    }

    public static class Config extends ReturnUrlForm
    {
        private String integrationKey = DuoManager.getIntegrationKey();
        private String secretKey = DuoManager.getSecretKey();
        private String applicationKey = DuoManager.getApplicationKey();//Application key and Application Secret key (as sometimes used in Duo docs) are synonymous.
        private String apiHostname = DuoManager.getAPIHostname();

        public String getSecretKey()
        {
            return secretKey;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setSecretKey(String secretKey)
        {
            this.secretKey = secretKey;
        }

        public String getIntegrationKey()
        {
            return integrationKey;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setIntegrationKey(String integrationKey)
        {
            this.integrationKey = integrationKey;
        }

        public String getApiHostname()
        {
            return apiHostname;
        }

        public String getApplicationKey()
        {
            return applicationKey;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setApplicationKey(String applicationKey)
        {
            this.applicationKey = applicationKey;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setApiHostname(String apiHostname)
        {
            this.apiHostname = apiHostname;
        }

    }


    public static class DuoForm
    {
        private String sig_request;
        private String sig_response;
        private boolean test = false;

        @SuppressWarnings("UnusedDeclaration")
        public boolean isStatus()
        {
            return status;
        }

        public void setStatus(boolean status)
        {
            this.status = status;
        }

        private boolean status = false; //Duo success or failure flag

        @SuppressWarnings("UnusedDeclaration")
        public String getSig_request()
        {
            return sig_request;
        }

        public void setSig_request(String sig_request)
        {
            this.sig_request = sig_request;
        }

        public String getSig_response()
        {
            return sig_response;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setSig_response(String sig_response)
        {
            this.sig_response = sig_response;
        }

        public boolean isTest()
        {
            return test;
        }

        public void setTest(boolean test)
        {
            this.test = test;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static ActionURL getValidateURL(Container c)
    {
        return new ActionURL(ValidateAction.class, c);
    }

    @AllowedDuringUpgrade
    @RequiresNoPermission
    public class ValidateAction extends FormViewAction<DuoForm>
    {
        private static final String DUO_2_FACTOR_AUTHENTICATION = "Duo 2-Factor Authentication";

        @Override
        public void validateCommand(DuoForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(DuoForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!getUser().isGuest())
                HttpView.redirect(AuthenticationManager.getAfterLoginURL(getContainer(), null, getUser()));

            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            getPageConfig().setIncludeLoginLink(false);
            getPageConfig().setHelpTopic(new HelpTopic("duoTwoFactor"));
            return new JspView<>("/org/labkey/authentication/duo/duoEntry.jsp", form, errors);
        }

        @Override
        public boolean handlePost(DuoForm form, BindException errors) throws Exception
        {
            String returnedUser = DuoManager.verifySignedResponse(form.getSig_response(), form.isTest(), errors);

            User duoUser = UserManager.getUser(Integer.parseInt(returnedUser));
            User primaryAuthUser = AuthenticationManager.getPrimaryAuthenticationUser(getViewContext().getSession());

            boolean success = (duoUser != null && duoUser.equals(primaryAuthUser));

            // TODO: More detailed error checking and messages - see line 293, is that sufficient?

            if (success)
            {
                AuthenticationManager.setSecondaryAuthenticationUser(getViewContext().getSession(), DuoProvider.class, duoUser);
                UserManager.addAuditEvent(duoUser, ContainerManager.getRoot(), duoUser, DUO_2_FACTOR_AUTHENTICATION + " successful for user: " + duoUser.getEmail());
            }
            else
            {
                String message = DUO_2_FACTOR_AUTHENTICATION + " failed for user: " + primaryAuthUser.getEmail();
                UserManager.addAuditEvent(primaryAuthUser, ContainerManager.getRoot(), primaryAuthUser, message);
                errors.reject(DUO_2_FACTOR_AUTHENTICATION, message);
            }

            return success;
        }

        @Override
        public URLHelper getSuccessURL(DuoForm form)
        {
            return AuthenticationManager.handleAuthentication(getViewContext().getRequest(), getContainer()).getRedirectURL();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class TestDuoAction extends FormViewAction<DuoForm>
    {
        public void validateCommand(DuoForm target, Errors errors)
        {
        }

        public ModelAndView getView(DuoForm form, boolean reshow, BindException errors) throws Exception
        {
            form.setTest(true);
            form.setSig_request(DuoManager.generateSignedRequest(getUser()));
            return new JspView<>("/org/labkey/authentication/duo/duoEntry.jsp", form, errors);
        }

        public boolean handlePost(DuoForm form, BindException errors) throws Exception
        {
            return false;
        }

        @Override
        public URLHelper getSuccessURL(DuoForm duoForm)
        {
            return null;   // Always reshow form
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresNoPermission
    public class TestDuoResultAction extends FormViewAction<DuoForm>
    {
        public void validateCommand(DuoForm target, Errors errors)
        {
        }

        public ModelAndView getView(DuoForm form, boolean reshow, BindException errors) throws Exception
        {
            String sig_response = form.getSig_response();
            int userId = Integer.valueOf(DuoManager.verifySignedResponse(sig_response, true, errors).trim());
            if(getUser().getUserId() == userId)
                form.setStatus(true);

            return new JspView<>("/org/labkey/authentication/duo/testResultDuo.jsp", form, errors);
        }

        public boolean handlePost(DuoForm form, BindException errors) throws Exception
        {
            return false;
        }

        public ActionURL getSuccessURL(DuoForm testDuoAction)
        {
            return null;   // Always reshow form
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }
}

