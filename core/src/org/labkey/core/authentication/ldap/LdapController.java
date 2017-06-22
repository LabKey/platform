/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.core.authentication.ldap;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

public class LdapController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(LdapController.class);

    public LdapController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }


    public static ActionURL getConfigureURL(boolean reshow)
    {
        ActionURL url = new ActionURL(ConfigureAction.class, ContainerManager.getRoot());

        if (reshow)
            url.addParameter("reshow", "1");

        return url;
    }


    @AdminConsoleAction(AdminOperationsPermission.class)
    @CSRF
    public class ConfigureAction extends FormViewAction<Config>
    {
        public ModelAndView getView(Config form, boolean reshow, BindException errors) throws Exception
        {
            form.setSASL(LdapAuthenticationManager.useSASL());
            return new JspView<>("/org/labkey/core/authentication/ldap/configure.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("configLdap");
            PageFlowUtil.urlProvider(LoginUrls.class).appendAuthenticationNavTrail(root).addChild("Configure LDAP Authentication");
            return root;
        }

        public void validateCommand(Config target, Errors errors)
        {
            String template = target.getPrincipalTemplate();

            if (StringUtils.isBlank(template))
            {
                errors.reject(ERROR_MSG, "Invalid template: template cannot be blank");
            }
            else
            {
                String noTemplates = StringUtils.remove(StringUtils.remove(template, "${email}"), "${uid}");

                if (noTemplates.contains("${"))
                    errors.reject(ERROR_MSG, "Invalid template: valid replacements are ${email} and ${uid}");
            }
        }

        public boolean handlePost(Config config, BindException errors) throws Exception
        {
            LdapAuthenticationManager.saveProperties(config);
            return true;
        }

        public ActionURL getSuccessURL(Config config)
        {
            return getConfigureURL(true);  // Redirect to same action -- reload props from database
        }
    }


    public static class Config extends ReturnUrlForm
    {
        public boolean reshow = false;

        private String servers = StringUtils.join(LdapAuthenticationManager.getServers(), ";");
        private String domain = LdapAuthenticationManager.getDomain();
        private String principalTemplate = LdapAuthenticationManager.getPrincipalTemplate();
        private boolean useSASL = false;   // Always initialize to false because of checkbox behavior

        public String getServers()
        {
            return servers;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setServers(String servers)
        {
            this.servers = servers;
        }

        public String getDomain()
        {
            return domain;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setDomain(String domain)
        {
            this.domain = domain;
        }

        public String getPrincipalTemplate()
        {
            return principalTemplate;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setPrincipalTemplate(String principalTemplate)
        {
            this.principalTemplate = principalTemplate;
        }

        public boolean getSASL()
        {
            return useSASL;
        }

        public void setSASL(boolean useSASL)
        {
            this.useSASL = useSASL;
        }

        @SuppressWarnings("UnusedDeclaration")
        public boolean isReshow()
        {
            return reshow;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setReshow(boolean reshow) {
            this.reshow = reshow;
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class TestLdapAction extends FormViewAction<TestLdapForm>
    {
        public void validateCommand(TestLdapForm target, Errors errors)
        {
        }

        public ModelAndView getView(TestLdapForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!reshow)
                form.setSASL(LdapAuthenticationManager.useSASL());

            HttpView view = new JspView<>("/org/labkey/core/authentication/ldap/testLdap.jsp", form, errors);
            PageConfig page = getPageConfig();
            if (null == form.getMessage() || form.getMessage().length() < 200)
                page.setFocusId("server");
            page.setTemplate(PageConfig.Template.Dialog);
            return view;
        }

        public boolean handlePost(TestLdapForm form, BindException errors) throws Exception
        {
            try
            {
                boolean success = LdapAuthenticationManager.connect(form.getServer(), form.getPrincipal(), form.getPassword(), form.getSASL());
                form.setMessage("<b>Connected to server. Authentication " + (success ? "succeeded" : "failed") + ".</b>");
            }
            catch (Exception e)
            {
                String message = "<b>Failed to connect with these settings. Error was:</b><br>" + ExceptionUtil.renderException(e);
                form.setMessage(message);
            }
            return false;
        }

        public ActionURL getSuccessURL(TestLdapForm testLdapAction)
        {
            return null;   // Always reshow form
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class TestLdapForm extends ReturnUrlForm implements HasViewContext
    {
        private String server = LdapAuthenticationManager.getServers()[0];
        private String principal;
        private String password;
        private String message;
        private boolean useSASL = false;  // Always initialize to false because of checkbox behavior

        @Override
        public void setViewContext(ViewContext context)
        {
            User user = context.getUser();
            ValidEmail email;

            try
            {
                email = new ValidEmail(user.getEmail());
            }
            catch(ValidEmail.InvalidEmailException e)
            {
                throw new RuntimeException(e);
            }

            principal = LdapAuthenticationManager.substituteEmailTemplate(LdapAuthenticationManager.getPrincipalTemplate(), email);

            if ("null".equals(principal))
                principal = null;
        }

        @Override
        public ViewContext getViewContext()
        {
            return null;
        }

        public String getPrincipal()
        {
            return (null == principal ? "" : principal);
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setPrincipal(String principal)
        {
            this.principal = principal;
        }

        public String getPassword()
        {
            return (null == password ? "" : password);
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setPassword(String password)
        {
            this.password = password;
        }

        public String getServer()
        {
            return (null == server ? "" : server);
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setServer(String server)
        {
            this.server = server;
        }

        public boolean getSASL()
        {
            return useSASL;
        }

        public void setSASL(boolean useSASL)
        {
            this.useSASL = useSASL;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = message;
        }
    }    
}