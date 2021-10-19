/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.IgnoresAllocationTracking;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.RequestInfo;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * User: kevink
 * Date: 9/22/14
 */
@Marshal(Marshaller.Jackson)
public class MiniProfilerController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(MiniProfilerController.class);

    private static final Logger LOG = LogManager.getLogger(MiniProfilerController.class);

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

    @AdminConsoleAction
    @RequiresPermission(AdminPermission.class)
    public class ManageAction extends FormViewAction<MiniProfilerSettingsForm>
    {
        @Override
        public void validateCommand(MiniProfilerSettingsForm form, Errors errors)
        {
            MiniProfiler.Settings settings = form.getBean();
            if (settings.getTrivialMillis() <= 0)
                errors.rejectValue("trivialMillis", ERROR_MSG, "Trivial milliseconds must be greater than 0.");
        }

        @Override
        public ModelAndView getView(MiniProfilerSettingsForm form, boolean reshow, BindException errors)
        {
            MiniProfiler.Settings settings = reshow ? form.getBean() : MiniProfiler.getSettings(getUser());

            setHelpTopic(MiniProfiler.getHelpTopic());

            return new JspView<>("/org/labkey/core/admin/miniprofiler/manage.jsp", settings, errors);
        }

        @Override
        public boolean handlePost(MiniProfilerSettingsForm form, BindException errors)
        {
            MiniProfiler.Settings settings = form.getBean();
            MiniProfiler.saveSettings(settings, getUser());

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

    @JsonIgnoreProperties("apiVersion")
    public static class MinimizeForm
    {
        private boolean _minimized;

        public boolean isMinimized()
        {
            return _minimized;
        }

        public void setMinimized(boolean minimized)
        {
            _minimized = minimized;
        }
    }

    @IgnoresAllocationTracking
    @RequiresPermission(AdminPermission.class)
    public class MinimizeAction extends MutatingApiAction<MinimizeForm>
    {
        @Override
        public Object execute(MinimizeForm form, BindException errors) throws Exception
        {
            MiniProfiler.Settings settings = MiniProfiler.getSettings(getUser());
            settings.setStartMinimized(form.isMinimized());
            MiniProfiler.saveSettings(settings, getUser());
            boolean minimized = settings.isStartMinimized();
            return success(Collections.singletonMap("minimize", minimized));
        }
    }

    @AdminConsoleAction
    @RequiresPermission(AdminPermission.class)
    public class ResetAction extends FormHandlerAction
    {
        @Override
        public void validateCommand(Object o, Errors errors)
        {
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            MiniProfiler.resetSettings(getUser());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return urlProvider(AdminUrls.class).getAdminConsoleURL();
        }
    }

    // Invoked by test framework
    @RequiresSiteAdmin
    public class IsEnabledAction extends ReadOnlyApiAction
    {
        @Override
        public Object execute(Object o, BindException errors)
        {
            boolean enabled = MiniProfiler.isEnabled(getViewContext());
            return success(Collections.singletonMap("enabled", enabled));
        }
    }

    // Invoked by test framework
    @RequiresSiteAdmin
    public class EnableAction extends MutatingApiAction<EnableForm>
    {
        @Override
        public Object execute(EnableForm form, BindException errors)
        {
            MiniProfiler.Settings settings = MiniProfiler.getSettings(getUser());
            settings.setEnabled(form.isEnabled());
            MiniProfiler.saveSettings(settings, getUser());
            boolean enabled = settings.isEnabled();
            return success(Collections.singletonMap("enabled", enabled));
        }
    }

    // Invoked by test framework
    @RequiresSiteAdmin
    public class EnableTroubleshootingStacktracesAction extends MutatingApiAction<EnableForm>
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

        public void setEnabled(boolean enabled)
        {
            _enabled = enabled;
        }
    }

    public static class ReportForm
    {
        private long _id;

        public long getId()
        {
            return _id;
        }

        public void setId(long id)
        {
            _id = id;
        }
    }

    @RequiresNoPermission // permissions will be checked in the action
    @IgnoresAllocationTracking
    public class ReportAction extends MutatingApiAction<ReportForm>
    {
        @Override
        public Object execute(ReportForm form, BindException errors)
        {
            if (!MiniProfiler.isEnabled(getViewContext()))
                throw new UnauthorizedException();

            RequestInfo req = MemTracker.getInstance().getRequest(form.getId());
            MemTracker.get().setViewed(getUser(), form.getId());

            // Reset the X-MiniProfiler-Ids header to only include remaining unviewed (without the id we are returning)
            LinkedHashSet<Long> ids = new LinkedHashSet<>(MemTracker.get().getUnviewed(getUser()));
            getViewContext().getResponse().setHeader("X-MiniProfiler-Ids", ids.toString());

            return req;
        }
    }

    @RequiresNoPermission // permissions will be checked in the action
    @IgnoresAllocationTracking
    public class RecentRequestsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            if (!MiniProfiler.isEnabled(getViewContext()))
                throw new UnauthorizedException();

            setHelpTopic(MiniProfiler.getHelpTopic());

            // TODO: filter requests by user/session if not site admin
            List<RequestInfo> requests = MemTracker.getInstance().getNewRequests(0);
            //return new JspView<List<RequestInfo>>("...");
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }
}
