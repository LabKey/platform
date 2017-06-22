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

package org.labkey.core.analytics;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AdminConsole.SettingsLinkType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

public class AnalyticsController extends SpringActionController
{
    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(AnalyticsController.class);

    public AnalyticsController()
    {
        setActionResolver(_actionResolver);
    }

    public static void registerAdminConsoleLinks()
    {
        AdminConsole.addLink(SettingsLinkType.Configuration, "analytics settings", new ActionURL(BeginAction.class, ContainerManager.getRoot()));
    }

    static public class SettingsForm extends ViewForm
    {
        public AnalyticsServiceImpl.TrackingStatus ff_trackingStatus = AnalyticsServiceImpl.get().getTrackingStatus();
        public String ff_accountId = AnalyticsServiceImpl.get().getAccountId();
        public String ff_trackingScript = AnalyticsServiceImpl.get().getSavedScript();

        public void setFf_accountId(String ff_accountId)
        {
            this.ff_accountId = ff_accountId;
        }

        public void setFf_trackingStatus(String ff_trackingStatus)
        {
            this.ff_trackingStatus = AnalyticsServiceImpl.TrackingStatus.valueOf(ff_trackingStatus);
        }

        public void setFf_trackingScript(String ff_trackingScript)
        {
            this.ff_trackingScript = StringUtils.trimToNull(ff_trackingScript);
        }
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public class BeginAction extends FormViewAction<SettingsForm>
    {
        public NavTree appendNavTrail(NavTree root)
        {
            return PageFlowUtil.urlProvider(AdminUrls.class).appendAdminNavTrail(root, "Analytics", null);
        }

        public void validateCommand(SettingsForm target, Errors errors)
        {
        }

        public ModelAndView getView(SettingsForm settingsForm, boolean reshow, BindException errors) throws Exception
        {
            getPageConfig().setAllowTrackingScript(PageConfig.TrueFalse.False);
            return new JspView<>(AnalyticsController.class, "analyticsSettings.jsp", settingsForm, errors);
        }

        public boolean handlePost(SettingsForm settingsForm, BindException errors) throws Exception
        {
            AnalyticsServiceImpl.get().setSettings(settingsForm.ff_trackingStatus, settingsForm.ff_accountId, settingsForm.ff_trackingScript);
            return true;
        }

        public ActionURL getSuccessURL(SettingsForm settingsForm)
        {
            return PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL();
        }
    }
}
