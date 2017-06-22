/*
 * Copyright (c) 2013-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.di.DataIntegrationUrls;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.di.filters.FilterStrategy;
import org.labkey.di.pipeline.TransformConfiguration;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class DataIntegrationController extends SpringActionController
{
    private static final Logger LOG = Logger.getLogger(DataIntegrationController.class);
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(DataIntegrationController.class);


    @SuppressWarnings("UnusedDeclaration")
    public static class DataIntegrationUrlsImpl implements DataIntegrationUrls
    {
        @Override
        public ActionURL getBeginURL(Container container)
        {
            return new ActionURL(BeginAction.class, container);
        }

        @Override
        public ActionURL getViewJobsURL(Container container)
        {
            return new ActionURL(viewJobsAction.class, container);
        }
    }


    public DataIntegrationController()
    {
        setActionResolver(_actionResolver);
    }


    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            JspView<Object> result = new JspView<>(DataIntegrationController.class, "transformConfiguration.jsp", null);
            result.setTitle("ETL Configurations");
            return result;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("ETL Scheduler");
            return root;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class viewJobsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new ProcessJobsView(getUser(), getContainer());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Processed Jobs");
            return root;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class viewTransformHistoryAction extends SimpleViewAction<TransformViewForm>
    {
        private String _displayName;

        @Override
        public ModelAndView getView(TransformViewForm form, BindException errors) throws Exception
        {
            if (errors.hasErrors())
                return new SimpleErrorView(errors);

            return new JspView<>(DataIntegrationController.class, "transformHistory.jsp", form);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return appendNavTrail(root, "Transform History");
        }

        NavTree appendNavTrail(NavTree root, String title)
        {
            if (_displayName != null)
                title = title + " - " + _displayName;
            root.addChild(title);
            return root;
        }

        @Override
        public void validate(TransformViewForm form, BindException errors)
        {
            ScheduledPipelineJobDescriptor d;

            if (form.getTransformId() == null || form.getTransformRunId() == null)
            {
                errors.reject(ERROR_MSG, "both a TransformId and TransformRunId must be supplied");
            }

            d = getDescriptor(form);
            _displayName = d != null ? d.getName() : null;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class viewTransformDetailsAction extends viewTransformHistoryAction
    {
        @Override
        public ModelAndView getView(TransformViewForm form, BindException errors) throws Exception
        {
            if (errors.hasErrors())
                return new SimpleErrorView(errors);

            return new JspView<>(DataIntegrationController.class, "transformDetails.jsp", form);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return appendNavTrail(root, "Transform Details");
        }
    }

    // used for transform history and transform details action
    @SuppressWarnings("UnusedDeclaration")
    public static class TransformViewForm
    {
        Integer transformRunId = null;
        String transformId = null;

        public void setTransformId(String id)
        {
            this.transformId = id;
        }
        public String getTransformId()
        {
            return this.transformId;
        }
        public void setTransformRunId(Integer id)
        {
            this.transformRunId = id;
        }
        public Integer getTransformRunId()
        {
            return this.transformRunId;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class TransformConfigurationForm
    {
        String transformId = null;
        Boolean enabled = null;
        Boolean verboseLogging = null;
        Date dateWindowMin = null;
        Date dateWindowMax = null;
        Integer intWindowMin = null;
        Integer intWindowMax = null;
        FilterStrategy.Type _type = null;
        private boolean useDateWindow = false;
        private boolean useIntWindow = false;

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

        public Date getDateWindowMin()
        {
            return dateWindowMin;
        }

        public void setDateWindowMin(Date dateWindowMin)
        {
            this.dateWindowMin = dateWindowMin;
            useDateWindow = true;
        }

        public Date getDateWindowMax()
        {
            return dateWindowMax;
        }

        public void setDateWindowMax(Date dateWindowMax)
        {
            this.dateWindowMax = dateWindowMax;
            useDateWindow = true;
        }

        public Integer getIntWindowMin()
        {
            return intWindowMin;
        }

        public void setIntWindowMin(Integer intWindowMin)
        {
            this.intWindowMin = intWindowMin;
            useIntWindow = true;
        }

        public Integer getIntWindowMax()
        {
            return intWindowMax;
        }

        public void setIntWindowMax(Integer intWindowMax)
        {
            this.intWindowMax = intWindowMax;
            useIntWindow = true;
        }

        public FilterStrategy.Type getType()
        {
            return _type;
        }

        public void setType(FilterStrategy.Type type)
        {
            this._type = type;
        }

        public boolean isUseDateWindow()
        {
            return useDateWindow;
        }

        public boolean isUseIntWindow()
        {
            return useIntWindow;
        }
    }

    static ScheduledPipelineJobDescriptor getDescriptor(TransformViewForm form)
    {
        return TransformManager.get().getDescriptor(form.getTransformId());
    }

    static ScheduledPipelineJobDescriptor getDescriptor(TransformConfigurationForm form)
    {
        return TransformManager.get().getDescriptor(form.getTransformId());
    }

    public static void registerAdminConsoleLinks()
    {
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "etl- all job histories", new ActionURL(viewJobsAction.class, ContainerManager.getRoot()), ReadPermission.class);
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "etl- run site scope etls", new ActionURL(BeginAction.class, ContainerManager.getRoot()), ReadPermission.class);
    }

    @RequiresPermission(AdminPermission.class)
    public class UpdateTransformConfigurationAction extends MutatingApiAction<TransformConfigurationForm>
    {
        @Override
        public void validateForm(TransformConfigurationForm form, Errors errors)
        {
            super.validateForm(form, errors);
            if (StringUtils.isEmpty(form.getTransformId()))
                errors.rejectValue("transformId", SpringActionController.ERROR_REQUIRED);
            if (null == getDescriptor(form))
                throw new NotFoundException(form.getTransformId());
        }

        @Override
        public ApiResponse execute(TransformConfigurationForm form, BindException errors) throws Exception
        {
            ViewContext context = getViewContext();
            boolean shouldStartStop = false;

            ScheduledPipelineJobDescriptor etl = getDescriptor(form);
            if (null == etl)
                throw new NotFoundException(form.getTransformId());

            TransformConfiguration config = null;
            List<TransformConfiguration> configs = TransformManager.get().getTransformConfigurations(context.getContainer());
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
                boolean enabling = form.isEnabled() && etl.isStandalone();
                shouldStartStop = (enabling != config.isEnabled());
                config.setEnabled(enabling);
            }
            if (null != form.isVerboseLogging())
                config.setVerboseLogging(form.isVerboseLogging());
            config = TransformManager.get().saveTransformConfiguration(context.getUser(), config);

            if (shouldStartStop)
            {
                if (config.isEnabled())
                {
                    TransformManager.get().schedule(etl, getContainer(), getUser(), config.isVerboseLogging());
                }
                else
                {
                    TransformManager.get().unschedule(etl, getContainer(), getUser());
                }
            }

            JSONObject ret = new JSONObject();
            ret.put("success",true);
            ret.put("result", config.toJSON(null));
            return new ApiSimpleResponse(ret);
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class RunTransformAction extends MutatingApiAction<TransformConfigurationForm>
    {
        @Override
        public ApiResponse execute(TransformConfigurationForm form, BindException errors) throws Exception
        {
            ScheduledPipelineJobDescriptor etl = getDescriptor(form);
            if (null == etl)
                throw new NotFoundException(form.getTransformId());
            String status;
            JSONObject ret = new JSONObject();
            if (etl.isPending(getViewContext()) && !etl.isAllowMultipleQueuing())
            {
                status = TransformManager.getJobPendingMessage(null);
                LOG.info(status);
            }
            else if (!etl.isStandalone())
            {
                status = "Not queueing job because etl is a subcomponent of another etl.";
                LOG.info(status);
            }
            else
            {
                // pull variables off the URL
                Map<ParameterDescription,Object> params = new LinkedHashMap<>();
                for (ParameterDescription pd : (Set<ParameterDescription>)etl.getDeclaredVariables().keySet())
                {
                    String q = getViewContext().getRequest().getParameter(pd.getName());
                    if (null != q)
                        params.put(pd,q);
                }

                TransformJobContext context = (TransformJobContext) etl.getJobContext(getContainer(), getUser(), params);

                /* For testing purposes in dev mode, we allow a min/max range to be specified for the incremental filter,
                   overriding any persisted values.
                */
                if (form.isUseDateWindow() && FilterStrategy.Type.ModifiedSince == form.getType())
                {
                    context.setIncrementalWindow(new Pair<>(form.getDateWindowMin(), form.getDateWindowMax()));
                }
                else if (form.isUseIntWindow() && (FilterStrategy.Type.ModifiedSince == form.getType() || FilterStrategy.Type.Run == form.getType()))
                {
                    context.setIncrementalWindow(new Pair<>(form.getIntWindowMin(), form.getIntWindowMax()));
                }

                Integer jobId = TransformManager.get().runNowPipeline(etl, context);
                ActionURL pipelineURL = jobId == null ? null : PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlDetails(getContainer(), jobId);
                status = null == pipelineURL ? "No work" : "Queued";
                if (null != pipelineURL)
                    ret.put("pipelineURL",pipelineURL.toString());
                if (null != jobId)
                    ret.put("jobId", jobId.toString());
            }

            ret.put("success",true);
            ret.put("status", status);
            return new ApiSimpleResponse(ret);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ResetTransformStateAction extends MutatingApiAction<TransformConfigurationForm>
    {
        @Override
        public ApiResponse execute(TransformConfigurationForm form, BindException errors) throws Exception
        {
            ViewContext context = getViewContext();

            ScheduledPipelineJobDescriptor etl = getDescriptor(form);
            if (null == etl)
                throw new NotFoundException(form.getTransformId());

            String status;
            if (etl.isPending(context))
            {
                status = "Not resetting ETL state because job is pending.";
                LOG.info(status);
            }
            else
            {
                TransformConfiguration config = null;
                List<TransformConfiguration> configs = TransformManager.get().getTransformConfigurations(context.getContainer());
                for (TransformConfiguration c : configs)
                {
                    if (c.getTransformId().equalsIgnoreCase(form.getTransformId()))
                    {
                        config = c;
                        break;
                    }
                }

                if (config != null)
                {
                    config.setTransformState(null);
                    TransformManager.get().saveTransformConfiguration(context.getUser(), config);
                }
                status = "ETL state reset";
            }
            JSONObject ret = new JSONObject();
            ret.put("success",true);
            ret.put("status", status);
            return new ApiSimpleResponse(ret);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class TruncateTransformStateAction extends ResetTransformStateAction
    {
        @Override
        public ApiResponse execute(TransformConfigurationForm form, BindException errors) throws Exception
        {
            ScheduledPipelineJobDescriptor etl = getDescriptor(form);
            JSONObject ret = new JSONObject();
            ViewContext context = getViewContext();

            if (null == etl)
                throw new NotFoundException(form.getTransformId());

            if (etl.isPending(context))
            {
                ret.put("success", false);
                ret.put("error", "Not resetting ETL state because job is pending.");
                LOG.info("Not resetting ETL state because job is pending.");
            }
            else
            {
                // Truncate target tables
                Map<String, String> status = TransformManager.get().truncateTargets(form.getTransformId(), getUser(), getContainer());

                // Reset state
                super.execute(form, errors);

                // return status
                String error = status.get("error");
                String rows = status.get("rows");
                if (error != null)
                {
                    ret.put("error", error);
                    ret.put("status", "");
                }
                else if(rows != null)
                {
                    ret.put("success", true);
                    ret.put("deletedRows", rows);
                }
                else
                    ret.put("success", false);
            }

            return new ApiSimpleResponse(ret);
        }
    }
}
