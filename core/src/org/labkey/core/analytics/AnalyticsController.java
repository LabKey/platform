/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewForm;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashSet;
import java.util.Set;

public class AnalyticsController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(AnalyticsController.class);

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
        public Set<AnalyticsServiceImpl.TrackingStatus> ff_trackingStatus = AnalyticsServiceImpl.get().getTrackingStatus();
        public String ff_accountId = AnalyticsServiceImpl.get().getAccountId();
        public String ff_measurementId = AnalyticsServiceImpl.get().getMeasurementId();
        public String ff_trackingScript = AnalyticsServiceImpl.get().getSavedScript();

        public void setFf_accountId(String ff_accountId)
        {
            this.ff_accountId = ff_accountId;
        }

        public void setFf_trackingStatus(String[] statuses)
        {
            ff_trackingStatus = new HashSet<>();
            for (String s : statuses)
            {
                try
                {
                    ff_trackingStatus.add(AnalyticsServiceImpl.TrackingStatus.valueOf(s));
                }
                catch (IllegalArgumentException ignored) {}
            }
        }

        public void setFf_trackingScript(String ff_trackingScript)
        {
            this.ff_trackingScript = StringUtils.trimToNull(ff_trackingScript);
        }

        public void setFf_measurementId(String ff_measurementId)
        {
            this.ff_measurementId = StringUtils.trimToNull(ff_measurementId);
        }
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public class BeginAction extends FormViewAction<SettingsForm>
    {
        @Override
        public void addNavTrail(NavTree root)
        {
            urlProvider(AdminUrls.class).addAdminNavTrail(root, "Analytics", getClass(), getContainer());
        }

        @Override
        public void validateCommand(SettingsForm target, Errors errors)
        {
            if (target.ff_trackingStatus.contains(AnalyticsServiceImpl.TrackingStatus.ga4FullUrl) &&
                    StringUtils.isEmpty(target.ff_measurementId))
            {
                errors.reject("form", "Please specify a Measurement ID when using GA4");
            }

            if (target.ff_trackingStatus.contains(AnalyticsServiceImpl.TrackingStatus.enabled) &&
                    target.ff_trackingStatus.contains(AnalyticsServiceImpl.TrackingStatus.enabledFullURL))
            {
                errors.reject("form", "Please choose only one Universal Google Analytics option");
            }
        }

        @Override
        public ModelAndView getView(SettingsForm settingsForm, boolean reshow, BindException errors)
        {
            getPageConfig().setAllowTrackingScript(PageConfig.TrueFalse.False);
            return new JspView<>("/org/labkey/core/analytics/analyticsSettings.jsp", settingsForm, errors);
        }

        @Override
        public boolean handlePost(SettingsForm settingsForm, BindException errors)
        {
            AnalyticsServiceImpl.get().setSettings(settingsForm.ff_trackingStatus, settingsForm.ff_accountId, settingsForm.ff_measurementId, settingsForm.ff_trackingScript, getUser());
            return true;
        }

        @Override
        public ActionURL getSuccessURL(SettingsForm settingsForm)
        {
            return urlProvider(AdminUrls.class).getAdminConsoleURL();
        }
    }
}
