/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.cas;

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.AuthenticationManager.BaseSsoValidateAction;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by adam on 3/29/2015.
 */
public class CasController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(CasController.class);

    public CasController()
    {
        setActionResolver(_actionResolver);
    }

    public static ActionURL getValidateURL()
    {
        return new ActionURL(ValidateAction.class, ContainerManager.getRoot());
    }

    private static class CasForm
    {
        private String _ticket;

        public String getTicket()
        {
            return _ticket;
        }

        @SuppressWarnings("unused")
        public void setTicket(String ticket)
        {
            _ticket = ticket;
        }
    }

    // TODO: CAS server might POST to this action to invalidate sessions on logout; check with USF.

    @RequiresNoPermission
    @AllowedDuringUpgrade
    public class ValidateAction extends BaseSsoValidateAction<CasForm>
    {
        @NotNull
        @Override
        public String getProviderName()
        {
            return CasAuthenticationProvider.NAME;
        }

        @Nullable
        @Override
        public ValidEmail validateAuthentication(CasForm form, BindException errors) throws XmlException, IOException, ValidEmail.InvalidEmailException
        {
            String ticket = form.getTicket();
            return CasManager.getInstance().validate(ticket, errors);
        }
    }


    public static ActionURL getConfigureURL()
    {
        return new ActionURL(ConfigureAction.class, ContainerManager.getRoot());
    }


    @AdminConsoleAction
    @CSRF
    public class ConfigureAction extends FormViewAction<CasConfigureForm>
    {
        @Override
        public ModelAndView getView(CasConfigureForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/cas/configure.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("configureCas");
            PageFlowUtil.urlProvider(LoginUrls.class).appendAuthenticationNavTrail(root).addChild("Configure " + CasAuthenticationProvider.NAME + " Authentication");
            return root;
        }

        @Override
        public void validateCommand(CasConfigureForm form, Errors errors)
        {
            String url = form.getServerUrl();

            if (!StringUtils.isBlank(url))
            {
                try
                {
                    new URI(url);

                    URLHelper h = new URLHelper(url);
                    String scheme = h.getScheme() != null ? h.getScheme().toLowerCase() : null;

                    if ("https".equals(scheme))
                    {
                        if (!new URLHelper(url).getParsedPath().endsWith(new Path("cas")))
                        {
                            errors.reject(ERROR_MSG, "Server URL must end with \"/cas\"");
                        }
                        return;
                    }
                }
                catch (URISyntaxException ignored)
                {
                }
            }
            else
            {
                // Reset the server URL if the entry was blank
                form.setServerUrl(CasManager.getInstance().getServerUrlProperty());
            }

            // One of the checks failed, so display a general error message
            errors.reject(ERROR_MSG, "Enter a valid HTTP URL to your Apereo CAS server (e.g., https://test.org/cas)");
        }

        @Override
        public boolean handlePost(CasConfigureForm form, BindException errors) throws Exception
        {
            if (!form.getServerUrl().equalsIgnoreCase(CasManager.getInstance().getServerUrlProperty()))
                CasManager.getInstance().saveServerUrlProperty(form.getServerUrl(), getUser());

            return true;
        }

        @Override
        public ActionURL getSuccessURL(CasConfigureForm form)
        {
            return getConfigureURL();  // Redirect to same action -- reload props from database
        }
    }

    public static class CasConfigureForm extends ReturnUrlForm
    {
        private String _serverUrl = CasManager.getInstance().getServerUrlProperty();

        public String getServerUrl()
        {
            return _serverUrl;
        }

        public void setServerUrl(String serverUrl)
        {
            _serverUrl = serverUrl;
        }
    }
}
