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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.DataIntegrationUrls;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderManagement;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewForm;
import org.labkey.di.DataIntegrationQuerySchema;
import org.labkey.di.EtlDef;
import org.labkey.di.filters.FilterStrategy;
import org.labkey.di.pipeline.TransformConfiguration;
import org.labkey.di.pipeline.TransformDescriptor;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.labkey.di.DataIntegrationQuerySchema.ETL_DEF_TABLE_NAME;

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
            return new ActionURL(ViewJobsAction.class, container);
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
        public ModelAndView getView(Object o, BindException errors)
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
    public class ViewJobsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
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
    public class ViewTransformHistoryAction extends SimpleViewAction<TransformViewForm>
    {
        private String _displayName;

        @Override
        public ModelAndView getView(TransformViewForm form, BindException errors)
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
    public class ViewTransformDetailsAction extends ViewTransformHistoryAction
    {
        @Override
        public ModelAndView getView(TransformViewForm form, BindException errors)
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
            _type = type;
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

    ScheduledPipelineJobDescriptor getDescriptor(TransformViewForm form)
    {
        return TransformManager.get().getDescriptor(form.getTransformId(), getContainer());
    }

    ScheduledPipelineJobDescriptor getDescriptor(TransformConfigurationForm form)
    {
        return TransformManager.get().getDescriptor(form.getTransformId(), getContainer());
    }

    public static void registerAdminConsoleLinks()
    {
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "etl- all job histories", new ActionURL(ViewJobsAction.class, ContainerManager.getRoot()), ReadPermission.class);
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
        public ApiResponse execute(TransformConfigurationForm form, BindException errors)
        {
            boolean shouldStartStop = false;

            ScheduledPipelineJobDescriptor etl = getDescriptor(form);
            if (null == etl)
                throw new NotFoundException(form.getTransformId());

            TransformConfiguration config = TransformManager.get().getTransformConfigurations(getContainer())
                    .stream()
                    .filter(c -> c.getTransformId().equalsIgnoreCase(form.getTransformId()))
                    .findFirst()
                    .orElse(new TransformConfiguration(etl, getContainer()));

            if (null != form.isEnabled())
            {
                boolean enabling = form.isEnabled() && etl.isStandalone();
                shouldStartStop = (enabling != config.isEnabled());
                config.setEnabled(enabling);
            }
            if (null != form.isVerboseLogging())
                config.setVerboseLogging(form.isVerboseLogging());
            config = TransformManager.get().saveTransformConfiguration(getUser(), config);

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
            ret.put("success", true);
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
                Map<ParameterDescription, Object> params = new LinkedHashMap<>();
                for (ParameterDescription pd : (Set<ParameterDescription>) etl.getDeclaredVariables().keySet())
                {
                    String q = getViewContext().getRequest().getParameter(pd.getName());
                    if (null != q)
                        params.put(pd, q);
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
                    ret.put("pipelineURL", pipelineURL.toString());
                if (null != jobId)
                    ret.put("jobId", jobId.toString());
            }

            ret.put("success", true);
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
            ret.put("success", true);
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
                else if (rows != null)
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

    @RequiresPermission(AdminPermission.class)
    public class CustomTransformsAction extends FolderManagement.FolderManagementViewAction
    {
        @Override
        protected HttpView getTabView()
        {
            DataIntegrationQuerySchema schema = new DataIntegrationQuerySchema(getUser(), getContainer());
            final QuerySettings settings = new QuerySettings(getViewContext(), "transforms", ETL_DEF_TABLE_NAME);
            settings.setSchemaName(schema.getName());
            return schema.createView(getViewContext(), settings, null);
        }
    }

    public static class EtlDefinitionForm extends BeanViewForm<EtlDef>
    {
        private boolean _readOnly = false;

        public EtlDefinitionForm()
        {
            super(EtlDef.class, DataIntegrationQuerySchema.getEtlDefTableInfo());
        }

        public boolean isReadOnly()
        {
            return _readOnly;
        }

        public void setReadOnly(boolean readOnly)
        {
            _readOnly = readOnly;
        }

        public void validate(Errors errors)
        {
            EtlDef def = getBean();

            try
            {
                TransformDescriptor descriptor = def.getDescriptorThrow();
                if (descriptor.isSiteScope())
                {
                    errors.reject(ERROR_MSG, "Site-scoped ETLs can only be defined in module resources. If you need a site-scoped ETL, please contact your site administrator.");
                }
                if (TransformManager.get().getDescriptors(getContainer()).stream()
                        .anyMatch(cachedDescriptor -> cachedDescriptor.getName().equals(descriptor.getName())
                                        && !cachedDescriptor.getId().equals(descriptor.getId())))
                {
                    errors.reject(ERROR_MSG, "An ETL with that name is already defined in this folder.");
                }
                if (!errors.hasErrors())
                {
                    setTypedValue("name", descriptor.getName());
                    setTypedValue("description", descriptor.getDescription());
                }
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                // These are known exception types coming from malformed etl xml. Any other exception, we want to know about.
                if (!(XmlException.class.isAssignableFrom(e.getClass())
                        || XmlValidationException.class.isAssignableFrom(e.getClass())
                        || ConversionException.class.isAssignableFrom(e.getClass())))
                {
                    ExceptionUtil.logExceptionToMothership(getRequest(), e);
                }

            }
        }

        @Override
        public void refreshFromDb()
        {
            super.refreshFromDb();
            setTypedValue("definition", getBean().getPrettyPrintDefinition());
        }
    }

    private ActionURL getDefinitionsQueryUrl()
    {
        return Objects.requireNonNull(ModuleLoader.getInstance().getUrlProvider(QueryUrls.class)).urlExecuteQuery(getContainer(), DataIntegrationQuerySchema.SCHEMA_NAME, DataIntegrationQuerySchema.ETL_DEF_TABLE_NAME);
    }

    protected abstract class AbstractEtlDefinitionAction extends FormViewAction<EtlDefinitionForm>
    {
        @Override
        public void validateCommand(EtlDefinitionForm form, Errors errors)
        {
            form.validate(errors);
        }

        @Override
        public ModelAndView getView(EtlDefinitionForm form, boolean reshow, BindException errors)
        {
            if (null != form.getTypedValue("EtlDefId"))
            {
                form.refreshFromDb();
            }

            EtlDef def;
            try
            {
                def = form.getBean();
            }
            catch (ConversionException e)
            {
                throw new NotFoundException();
            }

            if (null != def.getContainerId() && !getContainer().equals(def.lookupContainer()))
                throw new UnauthorizedException();

            if (null == form.getReturnUrl())
                form.setReturnUrl(getDefinitionsQueryUrl().getLocalURIString());

            return new JspView<>("/org/labkey/di/view/etlDefinition.jsp", form, errors);
        }

        protected abstract void doAction(EtlDefinitionForm form, Errors errors) throws SQLException;

        @Override
        public boolean handlePost(EtlDefinitionForm form, BindException errors) throws Exception
        {
            if (errors.hasErrors())
                return false;

            try
            {
                doAction(form, errors);
            }
            catch (RuntimeSQLException e)
            {
                if (e.isConstraintException())
                {
                    errors.reject(ERROR_MSG, "An ETL with that name is already defined in this folder.");
                    return false;
                }

                throw e;
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(EtlDefinitionForm form)
        {
            return form.getReturnURLHelper() != null ? form.getReturnActionURL() : getDefinitionsQueryUrl();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Edit Custom ETL Definition");
            return root;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CreateDefinitionAction extends AbstractEtlDefinitionAction
    {
        @Override
        protected void doAction(EtlDefinitionForm form, Errors errors) throws SQLException
        {
            form.doInsert();
            TransformManager.get().etlDefChanged(form.getBean(), getContainer(), getUser(), EtlDef.Change.Insert);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class EditDefinitionAction extends AbstractEtlDefinitionAction
    {
        @Override
        protected void doAction(EtlDefinitionForm form, Errors errors) throws SQLException
        {
            EtlDef def = form.getBean();
            try (DbScope.Transaction tx = DataIntegrationQuerySchema.getSchema().getScope().ensureTransaction())
            {
                form.doUpdate();
                TransformManager.get().etlDefChanged(def, getContainer(), getUser(), EtlDef.Change.Update);
                tx.commit();
            }
        }
    }

    public static Collection<String> getEnabledTransformIds(Container container, Collection<EtlDef> etlDefs)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("Enabled"), true);
        filter.addInClause(FieldKey.fromString("TransformId"), etlDefs.stream().map(EtlDef::getConfigId).collect(Collectors.toSet()));
        return new TableSelector(DataIntegrationQuerySchema.getTransformConfigurationTableInfo(), Collections.singleton("TransformId"), filter, null).getCollection(String.class);
    }

    @RequiresPermission(ReadPermission.class)
    public class DefinitionDetailsAction extends SimpleViewAction<EtlDefinitionForm>
    {

        @Override
        public ModelAndView getView(EtlDefinitionForm form, BindException errors)
        {
            form.refreshFromDb();
            form.setReadOnly(true);
            if (null == form.getReturnUrl())
                form.setReturnUrl(getDefinitionsQueryUrl().getLocalURIString());

            return new JspView<>("/org/labkey/di/view/etlDefinition.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Custom ETL Definition");
        }
    }


    public static class DeleteDefinitionsForm extends ViewForm implements DataRegionSelection.DataSelectionKeyForm
    {
        private String _dataRegionSelectionKey;
        private boolean confirmed;
        private Set<EtlDef> defs;
        private Map<String, Boolean> selectedNames;
        private Set<Integer> etlDefIds;

        @Override
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        @Override
        public void setDataRegionSelectionKey(String key)
        {
            _dataRegionSelectionKey = key;
        }

        public boolean isConfirmed()
        {
            return confirmed;
        }

        public void setConfirmed(boolean confirmed)
        {
            this.confirmed = confirmed;
        }

        public Set<EtlDef> getDefs()
        {
            return defs;
        }

        public Set<Integer> getEtlDefIds()
        {
            return etlDefIds;
        }

        public void setEtlDefIds(String etlDefIds)
        {
            try
            {
                this.etlDefIds = new ObjectMapper().readValue(etlDefIds, Set.class);
            }
            catch (IOException e)
            {
                ExceptionUtil.logExceptionToMothership(_request, e);
                this.etlDefIds = Collections.emptySet();
            }
        }

        public List<String> getSelectedNames(boolean enabled)
        {
            if (null == selectedNames)
                return Collections.emptyList();
            return selectedNames.entrySet().stream()
                    .filter(def -> def.getValue().booleanValue() == enabled)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        public void loadEtlDefs(Set<Integer> etlDefIds)
        {
            defs = new HashSet<>();
            SimpleFilter filter = SimpleFilter.createContainerFilter(getContainer());
            filter.addInClause(FieldKey.fromString("EtlDefId"), etlDefIds);
            defs.addAll(new TableSelector(DataIntegrationQuerySchema.getEtlDefTableInfo(), filter, null).getCollection(EtlDef.class));
        }

        public void loadFromSelection()
        {
            etlDefIds = DataRegionSelection.getSelectedIntegers(getViewContext(), false);
            loadEtlDefs(etlDefIds);
            selectedNames = new TreeMap<>();
            selectedNames.putAll(defs.stream()
                    .collect(Collectors.toMap(EtlDef::getName, d -> getEnabledTransformIds(getContainer(), defs).contains(d.getConfigId()))));
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteDefinitionsAction extends FormViewAction<DeleteDefinitionsForm>
    {
        @Override
        public boolean handlePost(DeleteDefinitionsForm form, BindException errors)
        {
            if (!form.isConfirmed())
                return false;

            try (DbScope.Transaction tx = DataIntegrationQuerySchema.getSchema().getScope().ensureTransaction())
            {
                Table.delete(DataIntegrationQuerySchema.getEtlDefTableInfo(), new SimpleFilter().addInClause(FieldKey.fromParts("EtlDefId"), form.getEtlDefIds()));
                TransformManager.get().etlDefsChanged(form.getDefs(), getContainer(), getUser(), EtlDef.Change.Delete);
                tx.commit();
            }
            DataRegionSelection.clearAll(getViewContext());
            return true;
        }

        @Override
        public void validateCommand(DeleteDefinitionsForm form, Errors errors)
        {
            if (form.isConfirmed())
                form.loadEtlDefs(form.getEtlDefIds());
            else
                form.loadFromSelection();

            if (form.getEtlDefIds().size() > form.getDefs().size())
                throw new NotFoundException("Not all selected ETL definitions found in this container.");
        }

        @Override
        public ModelAndView getView(DeleteDefinitionsForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/di/view/confirmDeleteDefinitions.jsp", form, errors);
        }

        @Override
        public @NotNull URLHelper getSuccessURL(DeleteDefinitionsForm form)
        {
            return null != form.getReturnURLHelper() ? form.getReturnURLHelper() : getDefinitionsQueryUrl();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Confirm ETL Definition Deletion");
        }
    }
}
