/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
package org.labkey.authentication.opensso;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

@Deprecated
// No longer used, but might be helpful when implementing future SSO authentication providers (e.g., OpenID)
public class OpenSSOController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(OpenSSOController.class);

    public OpenSSOController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }


    public NavTree appendConfigNavTrail(NavTree root, String title)
    {
        NavTree auth = PageFlowUtil.urlProvider(LoginUrls.class).appendAuthenticationNavTrail(root);

        if (null != title)
        {
            auth.addChild("Configure OpenSSO Authentication", getCurrentSettingsURL());
            auth.addChild(title);
        }
        else
        {
            auth.addChild("Configure OpenSSO Authentication");
        }

        return root;
    }


    public static ActionURL getConfigureURL()
    {
        return new ActionURL(ConfigureAction.class, ContainerManager.getRoot());
    }


    @RequiresSiteAdmin @CSRF
    public class ConfigureAction extends FormViewAction<ConfigProperties>
    {
        public ActionURL getSuccessURL(ConfigProperties form)
        {
            return getCurrentSettingsURL();
        }

        public ModelAndView getView(ConfigProperties form, boolean reshow, BindException errors) throws Exception
        {
            form.props = OpenSSOManager.get().getSystemSettings();
            return new JspView<>("/org/labkey/authentication/opensso/view/configure.jsp", form);
        }

        public boolean handlePost(ConfigProperties form, BindException errors) throws Exception
        {
            OpenSSOManager.get().writeSystemSettings(null);
            OpenSSOManager.get().activate();

            return true;
        }

        public void validateCommand(ConfigProperties target, Errors errors)
        {
        }

        public NavTree appendNavTrail(NavTree root)
        {
            PageFlowUtil.urlProvider(LoginUrls.class).appendAuthenticationNavTrail(root).addChild("Configure OpenSSO Authentication");
            return root;
        }
    }


    public static ActionURL getCurrentSettingsURL()
    {
        return new ActionURL(CurrentSettingsAction.class, ContainerManager.getRoot());
    }


    @AdminConsoleAction
    public class CurrentSettingsAction extends SimpleViewAction<ConfigProperties>
    {
        public ModelAndView getView(ConfigProperties form, BindException errors) throws Exception
        {
            form.props = OpenSSOManager.get().getSystemSettings();
            form.pickRefererPrefixURL = getPickReferrerURL();
            return new JspView<>("/org/labkey/authentication/opensso/view/currentSettings.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendConfigNavTrail(root, null);
        }
    }


    public static class ConfigProperties
    {
        public Map<String, String> props;
        public ActionURL pickRefererPrefixURL;
    }


    public static ActionURL getPickReferrerURL()
    {
        return new ActionURL(PickReferrerAction.class, ContainerManager.getRoot());
    }


    @AdminConsoleAction
    public class PickReferrerAction extends FormViewAction<PickReferrerForm>
    {
        public void validateCommand(PickReferrerForm target, Errors errors)
        {
        }

        public ModelAndView getView(PickReferrerForm form, boolean reshow, BindException errors) throws Exception
        {
            form.setPrefix(OpenSSOManager.get().getReferrerPrefix());
            return new JspView<>("/org/labkey/authentication/opensso/view/referrerPrefix.jsp", form);
        }

        public boolean handlePost(PickReferrerForm form, BindException errors) throws Exception
        {
            OpenSSOManager.get().saveReferrerPrefix(form.getPrefix());
            return true;
        }

        public ActionURL getSuccessURL(PickReferrerForm form)
        {
            return getCurrentSettingsURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendConfigNavTrail(root, "Configure Referrer URL Prefix");
        }
    }


    public static class PickReferrerForm
    {
        private String _prefix;

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }
    }
}