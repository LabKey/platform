/*
 * Copyright (c) 2014-2026 LabKey Corporation
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
package org.labkey.core.admin.miniprofiler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.IgnoresAllocationTracking;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.RequestInfo;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.TroubleshooterPermission;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Marshal(Marshaller.Jackson)
public class MiniProfilerController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(MiniProfilerController.class);

    public MiniProfilerController()
    {
        setActionResolver(_actionResolver);
    }

    public static class MiniProfilerSettingsForm extends BeanViewForm<MiniProfiler.Settings>
    {
        public MiniProfilerSettingsForm()
        {
            super(MiniProfiler.Settings.class);
        }
    }

    @RequiresPermission(TroubleshooterPermission.class)
    public static class ManageAction extends FormViewAction<MiniProfilerSettingsForm>
    {
        @Override
        public void validateCommand(MiniProfilerSettingsForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(MiniProfilerSettingsForm form, boolean reshow, BindException errors)
        {
            MiniProfiler.Settings settings = reshow ? form.getBean() : MiniProfiler.getSettings();
            return new JspView<>("/org/labkey/core/admin/miniprofiler/manage.jsp", settings, errors);
        }

        @Override
        public boolean handlePost(MiniProfilerSettingsForm form, BindException errors)
        {
            MiniProfiler.Settings settings = form.getBean();
            MiniProfiler.saveSettings(settings);

            return true;
        }

        @Override
        public URLHelper getSuccessURL(MiniProfilerSettingsForm form)
        {
            return urlProvider(AdminUrls.class).getAdminConsoleURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            urlProvider(AdminUrls.class).addAdminNavTrail(root, "Profiling Settings", getClass(), getContainer());
        }
    }

    // Invoked by test framework
    @RequiresPermission(TroubleshooterPermission.class)
    public static class EnableTroubleshootingStacktracesAction extends MutatingApiAction<EnableForm>
    {
        @Override
        public Object execute(EnableForm form, BindException errors)
        {
            MiniProfiler.setCollectTroubleshootingStackTraces(form.isEnabled());
            return success(Collections.singletonMap("enabled", MiniProfiler.isCollectTroubleshootingStackTraces()));
        }
    }

    @JsonIgnoreProperties("apiVersion")
    public static class EnableForm
    {
        private boolean _enabled;

        public boolean isEnabled()
        {
            return _enabled;
        }

        @SuppressWarnings("unused")
        public void setEnabled(boolean enabled)
        {
            _enabled = enabled;
        }
    }

    public static class RequestForm
    {
        private long _id;

        public long getId()
        {
            return _id;
        }

        @SuppressWarnings("unused")
        public void setId(long id)
        {
            _id = id;
        }
    }

    @RequiresNoPermission // permissions will be checked in the action
    @IgnoresAllocationTracking
    public static class SessionRequestsAction extends ReadOnlyApiAction<RequestForm>
    {
        @Override
        public Object execute(RequestForm o, BindException errors)
        {
            String sessionId = getViewContext().getSession().getId();
            List<RequestInfo> requests = MemTracker.getInstance()
                    .getNewRequests(o.getId()).stream()
                    .filter(requestInfo -> sessionId.equals(requestInfo.getSessionId()))
                    .toList();

            return Map.of("requests", requests, "sessionId", sessionId);
        }
    }

    @RequiresPermission(TroubleshooterPermission.class)
    @IgnoresAllocationTracking
    public static class RecentRequestsAction extends ReadOnlyApiAction<RequestForm>
    {
        @Override
        public Object execute(RequestForm o, BindException errors)
        {
            List<RequestInfo> requests = MemTracker.getInstance()
                    .getNewRequests(o.getId()).stream()
                    .filter(req -> getUser().equals(req.getUser()) || getUser().hasApplicationAdminPermission())
                    .toList();

            return Map.of("requests", requests);
        }
    }
}
