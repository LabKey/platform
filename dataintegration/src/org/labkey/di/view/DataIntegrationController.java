/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.di.view;

import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.di.api.ScheduledPipelineJobDescriptor;
import org.labkey.di.pipeline.ETLManager;
import org.labkey.di.pipeline.TransformConfiguration;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class DataIntegrationController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(DataIntegrationController.class);

    public DataIntegrationController()
    {
        setActionResolver(_actionResolver);
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<Object>(DataIntegrationController.class, "transformConfiguration.jsp", null);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Scheduler");
            return root;
        }
    }


    @SuppressWarnings("UnusedDeclaration")
    public static class TransformConfigurationForm
    {
        String transformId = null;
        Boolean enabled = null;
        Boolean verboseLogging = null;

        public void setTransformId(String id)
        {
            this.transformId = id;
        }
        public String getTransformId()
        {
            return this.transformId;
        }

        public void setVerboseLogging(boolean verbose)
        {
            this.verboseLogging = verbose;
        }
        public Boolean isVerboseLogging()
        {
            return this.verboseLogging;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }
        public Boolean isEnabled()
        {
            return this.enabled;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class UpdateTransformConfigurationAction extends MutatingApiAction<TransformConfigurationForm>
    {
        @Override
        public ApiResponse execute(TransformConfigurationForm form, BindException errors) throws Exception
        {
            ViewContext context = getViewContext();
            boolean shouldStartStop = false;

            ScheduledPipelineJobDescriptor etl = null;
            List<ScheduledPipelineJobDescriptor> etls = ETLManager.get().getETLs();
            for (ScheduledPipelineJobDescriptor e : etls)
            {
                if (e.getId().equalsIgnoreCase(form.getTransformId()))
                {
                    etl = e;
                    break;
                }
            }
            if (null == etl)
                throw new NotFoundException(form.getTransformId());

            TransformConfiguration config = null;
            List<TransformConfiguration> configs = ETLManager.get().getTransformConfigurations(context.getContainer());
            for (TransformConfiguration c : configs)
            {
                if (c.getTransformId().equalsIgnoreCase(form.getTransformId()))
                {
                    config = c;
                    break;
                }
            }
            if (null == config)
                config = new TransformConfiguration(etl, context.getContainer());
            if (null != form.isEnabled())
            {
                shouldStartStop = (form.isEnabled() != config.isEnabled());
                config.setEnabled(form.isEnabled());
            }
            if (null != form.isVerboseLogging())
                config.setVerboseLogging(form.isVerboseLogging());
            config = ETLManager.get().saveTransformConfiguration(context.getUser(), config);

            if (shouldStartStop)
            {
                if (config.isEnabled())
                {
                    ETLManager.get().schedule(etl, getContainer(), getUser());
                }
                else
                {
                    ETLManager.get().unschedule(etl, getContainer(), getUser());
                }
            }

            JSONObject ret = new JSONObject();
            ret.put("success",true);
            ret.put("result", config.toJSON(null));
            return new ApiSimpleResponse(ret);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class RunTransformAction extends MutatingApiAction<TransformConfigurationForm>
    {
        @Override
        public ApiResponse execute(TransformConfigurationForm form, BindException errors) throws Exception
        {
            ScheduledPipelineJobDescriptor etl = null;
            List<ScheduledPipelineJobDescriptor> etls = ETLManager.get().getETLs();
            for (ScheduledPipelineJobDescriptor e : etls)
            {
                if (e.getId().equalsIgnoreCase(form.getTransformId()))
                {
                    etl = e;
                    break;
                }
            }
            if (null == etl)
                throw new NotFoundException(form.getTransformId());

            ETLManager.get().runNow(etl, getContainer(), getUser());

            JSONObject ret = new JSONObject();
            ret.put("success",true);
            return new ApiSimpleResponse(ret);
        }
    }
}
