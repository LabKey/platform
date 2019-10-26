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
package org.labkey.core.authentication.ldap;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.ldap.LdapAuthenticationManager;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.AuthenticationConfigureAction;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

public class LdapController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(LdapController.class);

    public LdapController()
    {
        setActionResolver(_actionResolver);
    }


    public static ActionURL getConfigureURL(@Nullable Integer rowId, boolean reshow)
    {
        ActionURL url = new ActionURL(ConfigureAction.class, ContainerManager.getRoot());

        if (null != rowId)
            url.addParameter("configuration", rowId);

        if (reshow)
            url.addParameter("reshow", "1");

        return url;
    }


    @AdminConsoleAction(AdminOperationsPermission.class)
    public class ConfigureAction extends AuthenticationConfigureAction<LdapConfigureForm, LdapConfiguration>
    {
        @Override
        public ModelAndView getConfigureView(LdapConfigureForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/core/authentication/ldap/configure.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("configLdap");
            PageFlowUtil.urlProvider(LoginUrls.class).appendAuthenticationNavTrail(root).addChild("Configure LDAP Authentication");
            return root;
        }

        @Override
        protected void validateForm(LdapConfigureForm form, Errors errors)
        {
            String servers = form.getServers();

            if (StringUtils.isBlank(form.getServers()))
            {
                errors.reject(ERROR_MSG, "Invalid server URL(s): server URLs cannot be blank");
            }
            else
            {
                for (String server : servers.split(";"))
                {
                    server = server.trim();
                    if (!StringUtils.startsWithAny(server.toLowerCase(), "ldap://", "ldaps://"))
                        errors.reject(ERROR_MSG, "Invalid server URL - \"" + server + "\": LDAP URLs must start with ldap:// or ldaps://");
                }
            }

            if (StringUtils.isBlank(form.getDomain()))
            {
                errors.reject(ERROR_MSG, "Invalid domain: domain cannot be blank");
            }

            String template = form.getPrincipalTemplate();

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

        @Override
        public ActionURL getSuccessURL(LdapConfigureForm form)
        {
            return getConfigureURL(form.getRowId(), true);  // Redirect to same action -- reload props from database
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class TestLdapAction extends FormViewAction<TestLdapForm>
    {
        @Override
        public void validateCommand(TestLdapForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(TestLdapForm form, boolean reshow, BindException errors)
        {
            // First time through, set first server and substitute current user's email into principal template
            if (!reshow)
            {
                String server = StringUtils.trimToEmpty(form.getServer()).split(";")[0];
                form.setServer(server);

                User user = getUser();
                ValidEmail email;

                try
                {
                    email = new ValidEmail(user.getEmail());
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    throw new RuntimeException(e);
                }

                String principal = LdapAuthenticationManager.substituteEmailTemplate(form.getPrincipal(), email);

                if ("null".equals(principal))
                    principal = null;

                form.setPrincipal(principal);
            }

            HttpView view = new JspView<>("/org/labkey/core/authentication/ldap/testLdap.jsp", form, errors);
            PageConfig page = getPageConfig();
            if (null == form.getMessage())
                page.setFocusId("server");
            page.setTemplate(PageConfig.Template.Dialog);
            return view;
        }

        @Override
        public boolean handlePost(TestLdapForm form, BindException errors)
        {
            try
            {
                boolean success = LdapAuthenticationManager.connect(form.getServer(), form.getPrincipal(), form.getPassword(), form.getSasl());
                form.setMessage(HtmlString.unsafe("<b>Connected to server. Authentication " + (success ? "succeeded" : "failed") + ".</b>"));
            }
            catch (Exception e)
            {
                HtmlString message = HtmlString.unsafe("<b>Failed to connect with these settings. Error was:</b><br>" + ExceptionUtil.renderException(e));
                form.setMessage(message);
            }
            return false;
        }

        @Override
        public ActionURL getSuccessURL(TestLdapForm testLdapAction)
        {
            return null;   // Always reshow form
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class TestLdapForm
    {
        private String server = LdapAuthenticationManager.getServers()[0];
        private String principal;
        private String password;
        private HtmlString message;
        private boolean sasl = false;  // Always initialize to false because of checkbox behavior

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

        public boolean getSasl()
        {
            return sasl;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setSasl(boolean sasl)
        {
            this.sasl = sasl;
        }

        public HtmlString getMessage()
        {
            return message;
        }

        public void setMessage(HtmlString message)
        {
            this.message = message;
        }
    }    
}