/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.opensso;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.security.ACL;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.data.ContainerManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import java.util.HashMap;
import java.util.Map;

public class OpenSSOController extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(OpenSSOController.class);

    public OpenSSOController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }


    public static ActionURL getURL(Class<? extends Controller> actionClass, ActionURL returnURL)
    {
        return new ActionURL(actionClass, ContainerManager.getRoot()).addReturnURL(returnURL);
    }


    public static ActionURL getConfigureURL(ActionURL returnURL)
    {
        return getURL(ConfigureAction.class, returnURL);
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class ConfigureAction extends FormViewAction<ConfigProperties>
    {
        public ActionURL getSuccessURL(ConfigProperties form)
        {
            return new ActionURL(form.getReturnUrl());
        }

        public ModelAndView getView(ConfigProperties form, boolean reshow, BindException errors) throws Exception
        {
            form.props = OpenSSOManager.get().getSystemSettings();
            return new JspView<ConfigProperties>("/org/labkey/opensso/view/configure.jsp", form);
        }

        public boolean handlePost(ConfigProperties form, BindException errors) throws Exception
        {
            Map<String, String> props = new HashMap<String, String>(getViewContext().getExtendedProperties());
            props.remove("x");
            props.remove("y");
            props.remove(ReturnUrlForm.Params.returnUrl.toString());

            OpenSSOManager.get().writeSystemSettings(props);
            OpenSSOManager.get().initialize();

            return true;
        }

        public void validateCommand(ConfigProperties target, Errors errors)
        {
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    public static ActionURL getCurrentSettingsURL(ActionURL returnUrl)
    {
        return getURL(CurrentSettingsAction.class, returnUrl);
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class CurrentSettingsAction extends SimpleViewAction<ConfigProperties>
    {
        public ModelAndView getView(ConfigProperties form, BindException errors) throws Exception
        {
            form.props = OpenSSOManager.get().getSystemSettings();
            form.authLogoURL = getPickAuthLogoURL(getViewContext().getActionURL());
            form.pickRefererPrefixURL = getPickReferrerURL(getViewContext().getActionURL());
            return new JspView<ConfigProperties>("/org/labkey/opensso/view/currentSettings.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    public static class ConfigProperties
    {
        public String returnUrl;
        public Map<String, String> props;
        public ActionURL authLogoURL;
        public ActionURL pickRefererPrefixURL;

        public String getReturnUrl()
        {
            return returnUrl;
        }

        public void setReturnUrl(String returnUrl)
        {
            this.returnUrl = returnUrl;
        }
    }


    public static ActionURL getPickAuthLogoURL(ActionURL returnUrl)
    {
        return getURL(PickAuthLogoAction.class, returnUrl);
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class PickAuthLogoAction extends AuthenticationManager.PickAuthLogoAction
    {
        @Override
        protected String getProviderName()
        {
            return OpenSSOProvider.NAME;
        }
    }


    public static ActionURL getPickReferrerURL(ActionURL returnURL)
    {
        return getURL(PickReferrerAction.class, returnURL);
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class PickReferrerAction extends FormViewAction<PickReferrerForm>
    {
        public void validateCommand(PickReferrerForm target, Errors errors)
        {
        }

        public ModelAndView getView(PickReferrerForm form, boolean reshow, BindException errors) throws Exception
        {
            form.setPrefix(OpenSSOManager.get().getReferrerPrefix());
            return new JspView<PickReferrerForm>("/org/labkey/opensso/view/referrerPrefix.jsp", form);
        }

        public boolean handlePost(PickReferrerForm form, BindException errors) throws Exception
        {
            OpenSSOManager.get().saveReferrerPrefix(form.getPrefix());
            return true;
        }

        public ActionURL getSuccessURL(PickReferrerForm form)
        {
            return form.getReturnActionURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    public static class PickReferrerForm extends ReturnUrlForm
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