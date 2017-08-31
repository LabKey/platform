/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.study.controllers.assay;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.AbstractFileUploadAction;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JsonWriter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.defaults.SetDefaultValuesAssayAction;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.actions.*;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayResultTable;
import org.labkey.api.study.assay.AssayRunsView;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.assay.AssayView;
import org.labkey.api.study.assay.PipelineDataCollectorRedirectAction;
import org.labkey.api.study.assay.PlateBasedAssayProvider;
import org.labkey.api.study.assay.ReplacedRunFilter;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.ContainerTree;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.study.assay.AssayImportServiceImpl;
import org.labkey.study.assay.AssayManager;
import org.labkey.study.assay.AssayServiceImpl;
import org.labkey.study.assay.FileBasedModuleDataHandler;
import org.labkey.study.assay.ModuleAssayProvider;
import org.labkey.study.assay.TsvImportAction;
import org.labkey.study.assay.query.AssayAuditProvider;
import org.labkey.study.controllers.assay.actions.GetAssayBatchAction;
import org.labkey.study.controllers.assay.actions.GetAssayBatchesAction;
import org.labkey.study.controllers.assay.actions.ImportRunApiAction;
import org.labkey.study.controllers.assay.actions.ProtocolAction;
import org.labkey.study.controllers.assay.actions.SaveAssayBatchAction;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * User: brittp
 * Date: Jun 20, 2007
 * Time: 11:09:51 AM
 */
public class AssayController extends SpringActionController
{
    private static final DefaultActionResolver _resolver = new DefaultActionResolver(AssayController.class,
            GetAssayBatchAction.class,
            GetAssayBatchesAction.class,
            SaveAssayBatchAction.class,
            PublishStartAction.class,
            PublishConfirmAction.class,
            ImportRunApiAction.class,
            UploadWizardAction.class,
            TransformResultsAction.class,
            PlateBasedUploadWizardAction.class,
            PipelineDataCollectorRedirectAction.class,
            DeleteAction.class,
            DesignerAction.class,
            ImportAction.class,
            TsvImportAction.class,
            TemplateAction.class,
            AssayBatchesAction.class,
            AssayBatchDetailsAction.class,
            AssayRunsAction.class,
            AssayRunDetailsAction.class,
            AssayResultsAction.class,
            AssayResultDetailsAction.class,
            ReimportRedirectAction.class,
            ShowSelectedRunsAction.class,
            ShowSelectedDataAction.class,
            SetDefaultValuesAssayAction.class,
            AssayDetailRedirectAction.class,
            ProtocolAction.class
        );

    public AssayController()
    {
        super();
        setActionResolver(_resolver);
    }

    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends BaseAssayAction<ProtocolIdForm>
    {
        public ModelAndView getView(ProtocolIdForm o, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("adminAssays"));
            return AssayService.get().createAssayListView(getViewContext(), false, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Assays", new ActionURL(BeginAction.class, getContainer())).addChild("Assay List", new ActionURL(BeginAction.class, getContainer()));
        }
    }

    public static class AssayListForm
    {
        private String _name;
        private String _type;
        private Integer _id;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }

        public Integer getId()
        {
            return _id;
        }

        public void setId(Integer id)
        {
            _id = id;
        }

        public boolean matches(ExpProtocol protocol, AssayProvider provider)
        {
            if (_id != null && protocol.getRowId() != _id.intValue())
                return false;
            if (_name != null && !_name.equals(protocol.getName()))
                return false;
            return !(_type != null && !_type.equals(provider.getName()));
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class AssayListAction extends ApiAction<AssayListForm>
    {
        public ApiResponse execute(AssayListForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            HashMap<ExpProtocol, AssayProvider> assayProtocols = new HashMap<>();
            List<ExpProtocol> protocols = AssayManager.get().getAssayProtocols(c);
            for (ExpProtocol protocol : protocols)
            {
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider != null && form.matches(protocol, provider))
                {
                    assayProtocols.put(protocol, provider);
                }
            }

            return serializeAssayDefinitions(assayProtocols, c, getUser());
        }

    }

    public static ApiResponse serializeAssayDefinitions(HashMap<ExpProtocol, AssayProvider> assayProtocols, Container c, User user)
    {
        List<Map<String, Object>> assayList = new ArrayList<>();
        for (Map.Entry<ExpProtocol, AssayProvider> entry : assayProtocols.entrySet())
        {
            ExpProtocol protocol = entry.getKey();
            AssayProvider provider = entry.getValue();
            Map<String, Object> assayProperties = serializeAssayDefinition(protocol, provider, c, user);
            assayList.add(assayProperties);
        }
        ApiSimpleResponse response = new ApiSimpleResponse();
        response.put("definitions", assayList);
        return response;
    }

    public static Map<String, Object> serializeAssayDefinition(ExpProtocol protocol, AssayProvider provider, Container c, User user)
    {
        Map<String, Object> assayProperties = new HashMap<>();
        assayProperties.put("type", provider.getName());
        assayProperties.put("projectLevel", protocol.getContainer().isProject());
        assayProperties.put("description", protocol.getDescription());
        assayProperties.put("containerPath", protocol.getContainer().getPath());
        assayProperties.put("name", protocol.getName());
        assayProperties.put("id", protocol.getRowId());
        assayProperties.put("protocolSchemaName", provider.createProtocolSchema(user, c, protocol, null).getSchemaName());
        assayProperties.put("importController", provider.getImportURL(c, protocol).getController());
        assayProperties.put("importAction", provider.getImportURL(c, protocol).getAction());
        assayProperties.put("templateLink", PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(c, protocol, TemplateAction.class));
        if (provider instanceof PlateBasedAssayProvider)
            assayProperties.put("plateTemplate", ((PlateBasedAssayProvider)provider).getPlateTemplate(c, protocol));

        // XXX: UGLY: Get the TableInfo associated with the Domain -- loop over all tables and ask for the Domains.
        AssayProtocolSchema schema = provider.createProtocolSchema(user, c, protocol, null);
        Set<String> tableNames = schema.getTableNames();
        Map<String, TableInfo> tableInfoMap = new HashMap<>();
        for (String tableName : tableNames)
        {
            TableInfo table = schema.getTable(tableName, true);
            if (table != null)
            {
                Domain domain = table.getDomain();
                if (domain != null)
                    tableInfoMap.put(domain.getTypeURI(), table);
            }
        }

        Map<String, List<Map<String, Object>>> domains = new HashMap<>();
        for (Pair<Domain, Map<DomainProperty, Object>> domain : provider.getDomains(protocol))
        {
            TableInfo table = tableInfoMap.get(domain.getKey().getTypeURI());
            domains.put(domain.getKey().getName(), serializeDomain(domain.getKey(), table, user));
        }
        assayProperties.put("domains", domains);
        return assayProperties;
    }

    private static List<Map<String, Object>> serializeDomain(Domain domain, TableInfo tableInfo, User user)
    {
        Map<DomainProperty, Object> domainDefaults = DefaultValueService.get().getDefaultValues(domain.getContainer(), domain, user);

        if (tableInfo != null)
        {
            // Serialize the Domain properties using TableInfo columns which may include metadata overrides.
            // Issue 14546: Don't include all TableInfo columns in the response -- just Domain properties.
            Collection<FieldKey> fields = new ArrayList<>();
            Map<FieldKey, Object> defaults = new HashMap<>();
            for (DomainProperty property : domain.getProperties())
            {
                FieldKey field = FieldKey.fromParts(property.getName());
                fields.add(field);

                Object defaultValue = domainDefaults.get(property);
                if(defaultValue != null)
                    defaults.put(field, defaultValue);
            }

            Map<FieldKey, ColumnInfo> columnMap = QueryService.get().getColumns(tableInfo, fields);
            Collection<DisplayColumn> displayColumns = new ArrayList<>(columnMap.size());
            for (ColumnInfo column : columnMap.values())
            {
                Object defaultValue = defaults.get(column.getFieldKey());
                if (defaultValue != null)
                    column.setDefaultValue(defaultValue.toString());

                displayColumns.add(column.getDisplayColumnFactory().createRenderer(column));
            }

            return new ArrayList<>(JsonWriter.getNativeColProps(displayColumns, null, true).values());
        }

        List<Map<String, Object>> propertyList = new ArrayList<>();
        for (DomainProperty property : domain.getProperties())
        {
            HashMap<String, Object> properties = new HashMap<>();
            properties.put("name", property.getName());
            properties.put("typeName", property.getType().getLabel());
            properties.put("typeURI", property.getType().getTypeURI());
            properties.put("label", property.getLabel());
            properties.put("description", property.getDescription());
            properties.put("formatString", property.getFormat());
            properties.put("required", property.isRequired());
            properties.put("defaultValue", domainDefaults.get(property));
            if (property.getLookup() != null)
            {
                Lookup l = property.getLookup();
                // @deprecated (remove in future API version), use lookup.{} instead
                String containerPath = l.getContainer() != null ? l.getContainer().getPath() : null;
                properties.put("lookupContainer", containerPath);
                properties.put("lookupSchema", l.getSchemaName());
                properties.put("lookupQuery", l.getQueryName());

                // let's be consistent with Query metadata
                HashMap<String,String> lookup = new HashMap<>();
                lookup.put("schema", l.getSchemaName());
                lookup.put("table", l.getQueryName());
                lookup.put("container", null!=l.getContainer() ? l.getContainer().getPath() : null);

                // Let's not make the client guess keyColumn/displayColumn
                // CONSIDER: move into a QueryService helper method?
                Container lookupContainer = l.getContainer();
                if (lookupContainer == null)
                    lookupContainer = property.getContainer();
                UserSchema schema = QueryService.get().getUserSchema(user, lookupContainer, l.getSchemaName());
                if (schema != null)
                {
                    try
                    {
                        TableInfo table = schema.getTable(property.getLookup().getQueryName());
                        if (table != null)
                        {
                            String key = null;
                            List<String> pks = table.getPkColumnNames();
                            if (null != pks && pks.size() > 0)
                                key = pks.get(0);
                            if (null != pks && pks.size() == 2 && ("container".equalsIgnoreCase(key) || "containerid".equalsIgnoreCase(key)))
                                key = pks.get(1);
                            String title = table.getTitleColumn();
                            lookup.put("keyColumn", key);
                            lookup.put("displayColumn", title);
                        }
                    }
                    catch (UnauthorizedException ignored) { /* If the current user can't read the target schema, just ignore the lookup */ }
                }
                properties.put("lookup", lookup);
            }
            propertyList.add(properties);
        }
        return propertyList;
    }

    @RequiresPermission(ReadPermission.class)
    public class ChooseCopyDestinationAction extends BaseAssayAction<ProtocolIdForm>
    {
        private ExpProtocol _protocol;

        public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
        {
            _protocol = form.getProtocol(false);

            final Container currentContainer = getContainer();
            final User user = getUser();
            final ProjectUrls projectUrls = PageFlowUtil.urlProvider(ProjectUrls.class);

            ContainerTree tree = new ContainerTree("/", getUser(), DesignAssayPermission.class, null)
            {
                @Override
                protected void renderCellContents(StringBuilder html, Container c, ActionURL url)
                {
                    //if user doesn't have read permission to the target container, set the return URL to be
                    //the current container
                    ActionURL returnURL = (c.hasPermission(user, ReadPermission.class)) ? projectUrls.getStartURL(c) : projectUrls.getStartURL(currentContainer);

                    ActionURL copyURL = PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(c, _protocol, true, returnURL);
                    html.append("<a href=\"");
                    html.append(copyURL.getEncodedLocalURIString());
                    html.append("\">");
                    html.append(PageFlowUtil.filter(c.getName()));
                    html.append("</a>");
                }
            };
            ActionURL copyHereURL = PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(form.getContainer(), _protocol, true, null);
            HtmlView fileTree = new HtmlView("<table><tr><td><b>Select destination folder:</b></td></tr>" +
                    tree.render().toString() + "</table>");
            HtmlView bbar = new HtmlView(
                    PageFlowUtil.button("Cancel").href(new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId())) + " " +
                    (form.getContainer().hasPermission(getUser(), InsertPermission.class) ? PageFlowUtil.button("Copy to Current Folder").href(copyHereURL) : ""));
            setHelpTopic(new HelpTopic("manageAssayDesign"));
            return new VBox(bbar, fileTree, bbar);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Assay List", new ActionURL(BeginAction.class, getContainer())).addChild(_protocol.getName(),
                    new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId())).addChild("Copy Assay Design");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class SummaryRedirectAction extends BaseAssayAction<ProtocolIdForm>
    {
        ExpProtocol _protocol;
        public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
        {
            _protocol = form.getProtocol();
            throw new RedirectException(PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException("Redirects should not show nav trails");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class AssayBeginAction extends BaseAssayAction<ProtocolIdForm>
    {
        ExpProtocol _protocol;
        boolean _hasCustomView = false;

        @Override
        public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
        {
            _protocol = form.getProtocol();
            AssayProvider provider = form.getProvider();
            ModelAndView view = provider.createBeginView(getViewContext(), form.getProtocol());
            _hasCustomView = (null != view);
            setHelpTopic("workWithAssayData#runs");
            return (null == view ? new AssayRunsView(_protocol, false, errors) : view);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return _hasCustomView ? root.addChild(_protocol.getName() + " Overview") : new AssayRunsAction(getViewContext(), _protocol).appendNavTrail(root);
        }
    }

    public static class CreateAssayForm extends ProtocolIdForm
    {
        private String _assayContainer;

        public CreateAssayForm() { }

        public String getAssayContainer()
        {
            return _assayContainer;
        }

        public void setAssayContainer(String assayContainer)
        {
            _assayContainer = assayContainer;
        }

        public Container getCreateContainer()
        {
            if (_assayContainer != null)
            {
                Container c = ContainerManager.getForId(_assayContainer);
                if (c != null)
                    return c;
            }
            return getContainer();
        }
    }

    public static class ChooseAssayBean
    {
        ActionURL returnURL;

        public List<AssayProvider> getProviders()
        {
            List<AssayProvider> providers = new ArrayList<>(AssayManager.get().getAssayProviders());

            // Remove AssayProviders without a designer action
            providers.removeIf(provider -> provider.getDesignerAction() == null);
            providers.sort(Comparator.comparing(AssayProvider::getName));
            return providers;
        }

        public ActionURL getReturnURL()
        {
            return returnURL;
        }
    }

    @RequiresPermission(DesignAssayPermission.class)
    public class ChooseAssayTypeAction extends FormViewAction<CreateAssayForm>
    {
        Container createIn;

        public void validateCommand(CreateAssayForm form, Errors errors)
        {
        }

        public boolean handlePost(CreateAssayForm form, BindException errors) throws Exception
        {
            if (form.getProviderName() == null || PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(getContainer(), form.getProviderName(), null) == null)
            {
                errors.addError(new LabKeyError("Please select an assay type."));
                return false;
            }
            this.createIn = form.getCreateContainer();

            return true;
        }

        public ActionURL getSuccessURL(CreateAssayForm form)
        {
            ActionURL returnURL = form.getReturnActionURL();
            return PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(createIn, form.getProviderName(), returnURL);
        }

        public ModelAndView getView(CreateAssayForm form, boolean reshow, BindException errors) throws Exception
        {
            ChooseAssayBean bean = new ChooseAssayBean();
            bean.returnURL = form.getReturnActionURL();
            return new JspView<>("/org/labkey/study/assay/view/chooseAssayType.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Assay List", new ActionURL(BeginAction.class, getContainer()));
            root.addChild("New Assay Design");
            setHelpTopic(new HelpTopic("defineAssaySchema"));
            return root;
        }
    }

    @RequiresPermission(DesignAssayPermission.class)
    public class ServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new AssayServiceImpl(getViewContext());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class PublishHistoryAction extends BaseAssayAction<PublishHistoryForm>
    {
        private ExpProtocol _protocol;
        public ModelAndView getView(PublishHistoryForm form, BindException errors) throws Exception
        {
            ContainerFilter containerFilter = ContainerFilter.CURRENT;
            if (form.getContainerFilterName() != null)
                containerFilter = ContainerFilter.getContainerFilterByName(form.getContainerFilterName(), getUser());

            _protocol = form.getProtocol();
            VBox view = new VBox();
            view.addView(new AssayHeaderView(_protocol, form.getProvider(), false, true, containerFilter));

            UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());

            if (schema != null)
            {
                QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);

                SimpleFilter filter = new SimpleFilter();
                if (_protocol.getRowId() != -1)
                    filter.addCondition(FieldKey.fromParts(AssayAuditProvider.COLUMN_NAME_PROTOCOL), _protocol.getRowId());
                filter.addCondition(containerFilter.createFilterClause(ExperimentService.get().getSchema(), FieldKey.fromParts(AssayAuditProvider.COLUMN_NAME_CONTAINER), getContainer()));

                settings.setBaseFilter(filter);
                settings.setQueryName(AssayAuditProvider.ASSAY_PUBLISH_AUDIT_EVENT);
                view.addView(schema.createView(getViewContext(), settings, errors));
            }
            setHelpTopic(new HelpTopic("publishHistory"));
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Assay List", new ActionURL(BeginAction.class, getContainer())).addChild(_protocol.getName(),
                    new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId())).addChild("Copy-to-Study History");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class AssayFileDuplicateCheckAction extends ApiAction<SimpleApiJsonForm>
    {
        @Override
        public ApiResponse execute(SimpleApiJsonForm form, BindException errors) throws Exception
        {
            boolean duplicate = false;   // if there is a filename conflict, set to true
            String newFileName = null;   // if there is a filename conflict, this alternate filename will be used.
            List<String> runNames = new ArrayList<>();
            AssayFileWriter writer = new AssayFileWriter();
            try
            {
                File targetDirectory = writer.ensureUploadDirectory(getContainer());
                String fileName = form.getJsonObject() == null ? null : form.getJsonObject().getString("fileName");
                if (fileName != null)
                {
                    File f = new File(targetDirectory, fileName);
                    if (f.exists())
                    {
                        duplicate = true;
                        File newFile = AssayFileWriter.findUniqueFileName(fileName, targetDirectory);
                        newFileName = newFile.getName();
                        ExpData expData = ExperimentService.get().getExpDataByURL(f, null); 
                        if (expData != null)
                        {
                            for (ExpRun targetRun : expData.getTargetRuns())
                            {
                                runNames.add(targetRun.getName());
                            }
                        }
                    }
                }
            }
            catch (ExperimentException e)
            {
                throw new AbstractFileUploadAction.UploadException(e.getMessage(), HttpServletResponse.SC_NOT_FOUND);
            }
            Map<String,Object> map = new HashMap<>();
            map.put("duplicate", duplicate);
            map.put("newFileName", newFileName);
            map.put("runNames", runNames);
            return new ApiSimpleResponse(map);
        }
    }

    public static class AssayFileUploadForm extends AbstractFileUploadAction.FileUploadForm
    {
        private Integer _protocolId;

        public Integer getProtocolId()
        {
            return _protocolId;
        }

        public void setProtocolId(Integer protocolId)
        {
            _protocolId = protocolId;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class AssayFileUploadAction extends AbstractFileUploadAction<AssayFileUploadForm>
    {
        public AssayFileUploadAction()
        {
            super(AssayFileUploadForm.class);
        }

        protected File getTargetFile(String filename) throws IOException
        {
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
                throw new UploadException("Pipeline root must be configured before uploading assay files", HttpServletResponse.SC_NOT_FOUND);
            
            AssayFileWriter writer = new AssayFileWriter();
            try
            {
                File targetDirectory = writer.ensureUploadDirectory(getContainer());
                return writer.findUniqueFileName(filename, targetDirectory);
            }
            catch (ExperimentException e)
            {
                throw new UploadException(e.getMessage(), HttpServletResponse.SC_NOT_FOUND);
            }
        }

        protected String getResponse(Map<String, Pair<File, String>> files, AssayFileUploadForm form) throws UploadException
        {
            JSONObject fullMap = new JSONObject();
            for (Map.Entry<String, Pair<File, String>> entry : files.entrySet())
            {
                String paramName = entry.getKey();
                File file = entry.getValue().getKey();
                String originalName = entry.getValue().getValue();

                DataType dataType = getDataType(form, originalName);

                ExpData data = ExperimentService.get().createData(getContainer(), dataType);

                data.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(file).toURI());
                data.setName(originalName);
                data.save(getUser());

                JSONObject jsonData = ExperimentJSONConverter.serializeData(data);

                if (files.size() == 1 && !form.isForceMultipleResults())
                {
                    // Make sure that Ext treats the submission as a success
                    jsonData.put("success", true);
                    return jsonData.toString();
                }
                fullMap.put(paramName, jsonData);
            }
            // Make sure that Ext treats the submission as a success
            fullMap.put("success", true);
            return fullMap.toString();
        }

        /**
         * Checks if we've been given a protocol id to use as a reference. If so, tracks down its assay provider and
         * request its desired data LSID namespace prefix, validing that the file name matches the expected inputs.
         */
        private DataType getDataType(AssayFileUploadForm form, String originalName)
        {
            if (form.getProtocolId() != null)
            {
                ExpProtocol protocol = ExperimentService.get().getExpProtocol(form.getProtocolId());
                if (protocol == null)
                {
                    throw new NotFoundException("No such assay design: " + form.getProtocolId());
                }
                if (!AssayService.get().getAssayProtocols(getContainer()).contains(protocol))
                {
                    throw new NotFoundException("Assay design " + form.getProtocolId() + " is not in scope for " + getContainer().getPath());
                }
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider == null)
                {
                    throw new NotFoundException("Assay provider not found for assay design '" + protocol.getName() + "'");
                }
                if (provider.getDataType() != null)
                {
                    if (!provider.getDataType().getFileType().isType(originalName))
                    {
                        throw new IllegalArgumentException("File '" + originalName + "' does not match expected suffixes for " + provider.getName());
                    }
                    return provider.getDataType();
                }
            }
            return ExperimentService.get().getDataType(FileBasedModuleDataHandler.NAMESPACE);
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class ModuleAssayUploadAction extends BaseAssayAction<AssayRunUploadForm>
    {
        private ExpProtocol _protocol;

        @Override
        public ModelAndView getView(AssayRunUploadForm form, BindException errors) throws Exception
        {
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
                throw new NotFoundException("Pipeline root must be configured before uploading assay files");

            _protocol = form.getProtocol();

            AssayProvider ap = form.getProvider();
            if (!(ap instanceof ModuleAssayProvider))
                throw new NotFoundException("Assay must be a ModuleAssayProvider, but assay design " + _protocol.getName() + " was of type '" + ap.getName() + "', implemented by " + ap.getClass().getName());
            ModuleAssayProvider provider = (ModuleAssayProvider) ap;
            return provider.createUploadView(form);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            Container c = getContainer();
            ActionURL batchListURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayBatchesURL(c, _protocol, null);

            return super.appendNavTrail(root)
                .addChild(_protocol.getName() + " Batches", batchListURL)
                .addChild("Data Import");
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class DownloadSampleQCDataAction extends SimpleViewAction<ProtocolIdForm>
    {
        public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
        {
            ExpProtocol protocol = form.getProtocol(true);
            AssayProvider provider = AssayService.get().getProvider(protocol);
            DataExchangeHandler handler = provider.createDataExchangeHandler();

            if (handler != null)
            {
                File tempDir = getTempFolder();

                try {
                    handler.createSampleData(protocol, getViewContext(), tempDir);
                    File[] files = tempDir.listFiles();

                    if (files.length > 0)
                    {
                        HttpServletResponse response = getViewContext().getResponse();

                        response.reset();
                        response.setContentType("application/zip");
                        response.setHeader("Content-Disposition", "attachment; filename=\"" + "sampleQCData" + ".zip\"");
                        ZipOutputStream stream = new ZipOutputStream(response.getOutputStream());
                        byte[] buffer = new byte[1024];
                        for (File file : files)
                        {
                            if (file.canRead())
                            {
                                ZipEntry entry = new ZipEntry(file.getName());
                                stream.putNextEntry(entry);
                                InputStream is = new FileInputStream(file);
                                int cb;
                                while((cb = is.read(buffer)) > 0)
                                {
                                    stream.write(buffer, 0, cb);
                                }
                                is.close();
                            }
                        }
                        stream.close();
                    }
                }
                finally
                {
                    FileUtil.deleteDir(tempDir);
                }
            }
            return null;
        }

        private File getTempFolder()
        {
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            File tempFolder = new File(tempDir.getAbsolutePath() + File.separator + "QCSampleData", String.valueOf(Thread.currentThread().getId()));
            if (!tempFolder.exists())
                tempFolder.mkdirs();

            return tempFolder;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class PublishHistoryForm extends ProtocolIdForm
    {
        private String containerFilterName;

        public String getContainerFilterName()
        {
            return containerFilterName;
        }

        public void setContainerFilterName(String containerFilterName)
        {
            this.containerFilterName = containerFilterName;
        }
    }

    public static class AssayUrlsImpl implements AssayUrls
    {
        public ActionURL getProtocolURL(Container container, @Nullable ExpProtocol protocol, Class<? extends Controller> action)
        {
            ActionURL url = new ActionURL(action, container);
            if (protocol != null)
                url.addParameter("rowId", protocol.getRowId());
            return url;
        }

        public @Nullable ActionURL getDesignerURL(Container container, ExpProtocol protocol, boolean copy, @Nullable ActionURL returnURL)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            return getDesignerURL(container, provider, protocol, copy, returnURL);
        }

        public @Nullable ActionURL getDesignerURL(Container container, String providerName, ActionURL returnURL)
        {
            AssayProvider provider = AssayService.get().getProvider(providerName);
            if (provider == null)
            {
                return null;
            }
            return getDesignerURL(container, provider, null, false, returnURL);
        }

        private ActionURL getDesignerURL(Container container, @NotNull AssayProvider provider, @Nullable ExpProtocol protocol, boolean copy, ActionURL returnURL)
        {
            Class<? extends Controller> designerAction = provider.getDesignerAction();
            if (designerAction == null)
                return null;

            ActionURL url = getProtocolURL(container, protocol, designerAction);
            if (copy)
                url.addParameter("copy", "true");
            url.addParameter("providerName", provider.getName());
            if (returnURL != null)
                url.addParameter(ActionURL.Param.returnUrl, returnURL.toString());

            return url;
        }

        public ActionURL getCopyToStudyURL(Container container, ExpProtocol protocol)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            return getProtocolURL(container, protocol, PublishStartAction.class);
        }

        public ActionURL getCopyToStudyConfirmURL(Container container, ExpProtocol protocol)
        {
            return getProtocolURL(container, protocol, PublishConfirmAction.class);
        }

        public ActionURL getAssayRunsURL(Container container, ExpProtocol protocol)
        {
            return getAssayRunsURL(container, protocol, null);
        }

        public ActionURL getAssayRunsURL(Container container, ExpProtocol protocol, @Nullable ContainerFilter containerFilter, int... batchIds)
        {
            ActionURL result = getProtocolURL(container, protocol, AssayRunsAction.class);
            if (batchIds.length > 1)
            {
                String sep = "";
                StringBuilder filterValue = new StringBuilder();
                for (int batchId : batchIds)
                {
                    filterValue.append(sep).append(batchId);
                    sep = ";";
                }
                result.addFilter(AssayProtocolSchema.RUNS_TABLE_NAME,
                        AbstractAssayProvider.BATCH_ROWID_FROM_RUN, CompareType.IN, filterValue.toString());
            }
            else if (batchIds.length == 1)
            {
                result.addFilter(AssayProtocolSchema.DATA_TABLE_NAME,
                        AbstractAssayProvider.BATCH_ROWID_FROM_RUN, CompareType.EQUAL, batchIds[0]);
            }
            if (containerFilter != null && containerFilter.getType() != null)
                result.addParameter("Runs." + QueryParam.containerFilterName, containerFilter.getType().name());
            return result;
        }

        public ActionURL getAssayListURL(Container container)
        {
            return getProtocolURL(container, null, AssayController.BeginAction.class);
        }

        public ActionURL getAssayBatchesURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter)
        {
            ActionURL url = getProtocolURL(container, protocol, AssayBatchesAction.class);
            if (containerFilter != null && containerFilter.getType() != null)
            {
                url.addParameter("Batches." + QueryParam.containerFilterName, containerFilter.getType().name());
            }
            return url;
        }

        public ActionURL getAssayResultsURL(Container container, ExpProtocol protocol, int... runIds)
        {
            return getAssayResultsURL(container, protocol, null, runIds);
        }

        public ActionURL getAssayResultsURL(Container container, ExpProtocol protocol, @Nullable ContainerFilter containerFilter, int... runIds)
        {
            ActionURL result = getProtocolURL(container, protocol, AssayResultsAction.class);
            AssayProvider provider = AssayService.get().getProvider(protocol);
            // It's possible that the assay provider is no longer installed
            if (provider == null)
            {
                return null;
            }
            AssayTableMetadata tableMetadata = provider.getTableMetadata(protocol);
            String resultsTableName = AssayProtocolSchema.DATA_TABLE_NAME;

            // Check if we need to set a filter on the URL to show replaced data, which is usually filtered out
            if (provider.getReRunSupport() == AssayProvider.ReRunSupport.ReRunAndReplace)
            {
                for (int runId : runIds)
                {
                    ExpRun run = ExperimentService.get().getExpRun(runId);
                    if (run != null && run.getReplacedByRun() != null)
                    {
                        ReplacedRunFilter.Type.ALL.addToURL(result, resultsTableName, new FieldKey(tableMetadata.getRunFieldKeyFromResults(), ExpRunTable.Column.Replaced));
                        break;
                    }
                }
            }

            if (runIds.length > 1)
            {
                String sep = "";
                StringBuilder filterValue = new StringBuilder();
                for (int runId : runIds)
                {
                    filterValue.append(sep).append(runId);
                    sep = ";";
                }
                result.addFilter(resultsTableName,
                        tableMetadata.getRunRowIdFieldKeyFromResults(), CompareType.IN, filterValue.toString());
            }
            else if (runIds.length == 1)
            {
                result.addFilter(resultsTableName,
                        tableMetadata.getRunRowIdFieldKeyFromResults(), CompareType.EQUAL, runIds[0]);
            }
            if (containerFilter != null && containerFilter.getType() != null)
                result.addParameter("Data." + QueryParam.containerFilterName, containerFilter.getType().name());
            return result;
        }

        public ActionURL getShowUploadJobsURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter)
        {
            ActionURL result = getProtocolURL(container, protocol, ShowUploadJobsAction.class);
            if (containerFilter != null && containerFilter.getType() != null)
                result.addParameter("StatusFiles." + QueryParam.containerFilterName, containerFilter.getType().name());
            return result;
        }

        public ActionURL getChooseCopyDestinationURL(ExpProtocol protocol, Container container)
        {
            // Check if the provider supports designing before handing out copy URL
            ActionURL designerURL = getDesignerURL(container, protocol, false, null);
            if (designerURL == null)
                return null;

            return getProtocolURL(container, protocol, ChooseCopyDestinationAction.class);
        }

        public ActionURL getDeleteDesignURL(ExpProtocol protocol)
        {
            return getProtocolURL(protocol.getContainer(), protocol, DeleteAction.class);
        }

        public ActionURL getImportURL(Container container, ExpProtocol protocol, String path, File[] files)
        {
            ActionURL url = new ActionURL(PipelineDataCollectorRedirectAction.class, container);
            url.addParameter("protocolId", protocol.getRowId());
            if (path != null)
            {
                assert files != null : "If you specify a path you must include files as well";
                url.addParameter("path", path);
                for (File file : files)
                {
                    url.addParameter("file", file.getName());
                }
            }
            return url;
        }

        @Override
        public ActionURL getImportURL(Container container, String providerName, String path, File[] files)
        {
            AssayProvider provider = AssayService.get().getProvider(providerName);
            if (provider == null)
            {
                return null;
            }
            ActionURL url = new ActionURL(provider.getDataImportAction(), container);
            url.addParameter("providerName", provider.getName());

            if (path != null)
            {
                assert files != null : "If you specify a path you must include files as well";
                url.addParameter("path", path);
                for (File file : files)
                {
                    url.addParameter("file", file.getName());
                }
            }
            return url;
        }
    }

    @RequiresPermission(DesignAssayPermission.class)
    public class AssayImportServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new AssayImportServiceImpl(getViewContext());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowUploadJobsAction extends BaseAssayAction<ProtocolIdForm>
    {
        private ExpProtocol _protocol;

        @Override
        public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
        {
            _protocol = form.getProtocol();

            AssayView result = new AssayView();
            QueryView view = PipelineService.get().getPipelineQueryView(getViewContext(), PipelineService.PipelineButtonOption.Assay);
            view.setTitle("Data Pipeline");
            result.setupViews(view, false, form.getProvider(), form.getProtocol());

            return result;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            NavTree result = super.appendNavTrail(root);
            return result.addChild(_protocol.getName() + " Upload Jobs");
        }
    }


    public static class SetResultFlagForm extends ProtocolIdForm
    {
        Integer[] resultRowIds;
        String[] lsids;
        String comment;
        String columnName = "flag";

        public SetResultFlagForm()
        {
        }

        public Integer[] getResultRowIds()
        {
            return resultRowIds;
        }

        public void setResultRowIds(Integer[] rowIds)
        {
            this.resultRowIds = rowIds;
        }

        // These LSIDS are not 'real', it's the Data lsid + ":" + result.rowid
        public void setLsid(String[] lsids)
        {
            this.lsids = lsids;
        }

        public void setComment(String comment)
        {
            this.comment = comment;
        }

        public String getComment()
        {
            return this.comment;
        }

        public String getColumnName()
        {
            return columnName;
        }

        public void setColumnName(String columnName)
        {
            this.columnName = columnName;
        }

        public List<Integer> getRowList()
        {
            if (null != resultRowIds)
                return Arrays.asList(resultRowIds);
            if (null == lsids)
                return Collections.EMPTY_LIST;
            ArrayList<Integer> ret = new ArrayList<>(lsids.length);
            for (String lsid : lsids)
            {
                try
                {
                    if (lsid.endsWith("]"))
                    {
                        int i = lsid.lastIndexOf("[");
                        int rowid = Integer.parseInt(lsid.substring(i+1,lsid.length()-1));
                        ret.add(rowid);
                    }
                    else
                    {
                        int i = lsid.lastIndexOf(":");
                        int rowid = Integer.parseInt(lsid.substring(i+1,lsid.length()));
                        ret.add(rowid);
                    }
                }
                catch (NumberFormatException x) {}
            }
            return ret;
        }
    }


    /**
     * This is different from ExperimentController$SetFlagAction since Result Rows are not ExpObjects,
     * and we store flag directly in the materialized table
     */
    @RequiresPermission(UpdatePermission.class)
    public class SetResultFlagAction extends MutatingApiAction<SetResultFlagForm>
    {
        @Override
        protected SetResultFlagForm getCommand(HttpServletRequest request) throws Exception
        {
            return new SetResultFlagForm();
        }

        @Override
        public ApiResponse execute(SetResultFlagForm form, BindException errors) throws Exception
        {
            form.setContainer(getContainer());
            ExpProtocol protocol = form.getProtocol();
            String tableName = AssayProtocolSchema.DATA_TABLE_NAME;
            AssaySchema schema = form.getProvider().createProtocolSchema(getUser(), getContainer(), protocol, null);
            TableInfo table = schema.getTable(tableName);
            if (!(table instanceof AssayResultTable))
                throw new NotFoundException();
            if (null == form.getColumnName())
                throw new NotFoundException();
            AssayResultTable assayResultTable = (AssayResultTable) table;
            TableInfo ti = assayResultTable.getSchemaTableInfo();
            String comment = StringUtils.trimToNull(form.getComment());

            ColumnInfo flagCol = assayResultTable.getColumn(form.getColumnName());
            if (null == form.getColumnName())
                throw new NotFoundException();
            if (!org.labkey.api.gwt.client.ui.PropertyType.expFlag.getURI().equals(flagCol.getConceptURI()))
                throw new NotFoundException();

            DbScope scope = ti.getSchema().getScope();
            int rowsAffected  = 0 ;
            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (Integer id : form.getRowList())
                {
                    // assuming that column in storage table has same name
                    Table.update(getUser(), ti, Collections.singletonMap(flagCol.getColumnName(),comment), id);
                    rowsAffected++;
                }
                transaction.commit();
            }

            // the flag is editable even if the assay is not
            JSONObject res = new JSONObject();
            res.put("success", true);
            res.put("comment", form.getComment());
            res.put("rowsAffected", rowsAffected);
            return new ApiSimpleResponse(res);
        }
    }
}
