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

package org.labkey.assay;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.AbstractFileUploadAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.assay.AssayDataType;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayQCService;
import org.labkey.api.assay.AssayResultTable;
import org.labkey.api.assay.AssayRunsView;
import org.labkey.api.assay.AssaySchema;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.AssayTableMetadata;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.AssayView;
import org.labkey.api.assay.ReplacedRunFilter;
import org.labkey.api.assay.actions.AssayDetailRedirectAction;
import org.labkey.api.assay.actions.AssayResultDetailsAction;
import org.labkey.api.assay.actions.AssayRunDetailsAction;
import org.labkey.api.assay.actions.AssayRunUploadForm;
import org.labkey.api.assay.actions.AssayRunsAction;
import org.labkey.api.assay.actions.BaseAssayAction;
import org.labkey.api.assay.actions.DesignerAction;
import org.labkey.api.assay.actions.ProtocolIdForm;
import org.labkey.api.assay.actions.ReimportRedirectAction;
import org.labkey.api.assay.actions.UploadWizardAction;
import org.labkey.api.assay.plate.PlateBasedAssayProvider;
import org.labkey.api.assay.sample.AssaySampleLookupContext;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JsonWriter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.defaults.DefaultValueService;
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
import org.labkey.api.files.FileContentService;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateManager;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.ElevatedUser;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AssayReadPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.MoveEntitiesPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.QCAnalystPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.actions.TransformResultsAction;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.util.ContainerTree;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResponseHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataViewSnapshotSelectionForm;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.WebPartView;
import org.labkey.assay.actions.AssayBatchDetailsAction;
import org.labkey.assay.actions.AssayBatchesAction;
import org.labkey.assay.actions.AssayResultsAction;
import org.labkey.assay.actions.DeleteAction;
import org.labkey.assay.actions.DeleteProtocolAction;
import org.labkey.assay.actions.GetAssayBatchAction;
import org.labkey.assay.actions.GetAssayBatchesAction;
import org.labkey.assay.actions.GetAssayRunAction;
import org.labkey.assay.actions.GetAssayRunsAction;
import org.labkey.assay.actions.GetProtocolAction;
import org.labkey.assay.actions.ImportRunApiAction;
import org.labkey.assay.actions.PipelineDataCollectorRedirectAction;
import org.labkey.assay.actions.SaveAssayBatchAction;
import org.labkey.assay.actions.SaveAssayRunsAction;
import org.labkey.assay.actions.SaveProtocolAction;
import org.labkey.assay.actions.SetDefaultValuesAssayAction;
import org.labkey.assay.actions.ShowSelectedDataAction;
import org.labkey.assay.actions.ShowSelectedRunsAction;
import org.labkey.assay.actions.TemplateAction;
import org.labkey.vfs.FileLike;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AssayController extends SpringActionController
{
    private static final DefaultActionResolver _resolver = new DefaultActionResolver(AssayController.class,
        AssayBatchDetailsAction.class,
        AssayBatchesAction.class,
        AssayDetailRedirectAction.class,
        AssayResultDetailsAction.class,
        AssayResultsAction.class,
        AssayRunDetailsAction.class,
        AssayRunsAction.class,
        DeleteAction.class,
        DeleteProtocolAction.class,
        DesignerAction.class,
        GetAssayBatchAction.class,
        GetAssayBatchesAction.class,
        GetAssayRunAction.class,
        GetAssayRunsAction.class,
        GetProtocolAction.class,
        ImportRunApiAction.class,
        PipelineDataCollectorRedirectAction.class,
        ReimportRedirectAction.class,
        SaveAssayBatchAction.class,
        SaveAssayRunsAction.class,
        SaveProtocolAction.class,
        SetDefaultValuesAssayAction.class,
        ShowSelectedDataAction.class,
        ShowSelectedRunsAction.class,
        TemplateAction.class,
        TransformResultsAction.class,
        UploadWizardAction.class
    );

    public AssayController()
    {
        setActionResolver(_resolver);
    }

    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermission(AssayReadPermission.class)
    public static class BeginAction extends BaseAssayAction<ProtocolIdForm>
    {
        @Override
        public ModelAndView getView(ProtocolIdForm o, BindException errors)
        {
            setHelpTopic("adminAssays");
            return AssayService.get().createAssayListView(getViewContext(), false, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Assays", new ActionURL(BeginAction.class, getContainer()));
            root.addChild("Assay List", new ActionURL(BeginAction.class, getContainer()));
        }
    }

    // Action for the "Assay" tab. Same as BeginAction, but tricks AppBar into leaving BeginAction links in the NavTrail,
    // which keeps the tests happy. Assay refactor necessitated this. We might want to update the tests to click the tab,
    // then remove this hack.
    @RequiresPermission(AssayReadPermission.class)
    public static class TabAction extends BeginAction
    {
    }

    public static class AssayListForm
    {
        private String _name;
        private String _type;
        private Integer _id;
        private Boolean _plateEnabled;
        private String _status;

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

        public Boolean getPlateEnabled()
        {
            return _plateEnabled;
        }

        public void setPlateEnabled(Boolean plateEnabled)
        {
            _plateEnabled = plateEnabled;
        }

        public String getStatus()
        {
            return _status;
        }

        public void setStatus(String status)
        {
            _status = status;
        }

        public boolean matches(ExpProtocol protocol, AssayProvider provider)
        {
            if (_id != null && protocol.getRowId() != _id.intValue())
                return false;
            if (_name != null && !_name.equals(protocol.getName()))
                return false;
            if (_type != null && !_type.equals(provider.getName()))
                return false;
            if (_status != null && !_status.equalsIgnoreCase(protocol.getStatus().name()))
                return false;
            return !(_plateEnabled != null && !_plateEnabled.equals(provider.isPlateMetadataEnabled(protocol)));
        }
    }

    @RequiresPermission(AssayReadPermission.class)
    public static class AssayListAction extends ReadOnlyApiAction<AssayListForm>
    {
        @Override
        public ApiResponse execute(AssayListForm form, BindException errors)
        {
            Container c = getContainer();
            Map<ExpProtocol, AssayProvider> assayProtocols = new HashMap<>();
            List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(c);
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

    public static ApiResponse serializeAssayDefinitions(Map<ExpProtocol, AssayProvider> assayProtocols, Container c, User user)
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
        assayProperties.put("status", protocol.getStatus());
        assayProperties.put("protocolSchemaName", provider.createProtocolSchema(user, c, protocol, null).getSchemaName());
        assayProperties.put("importController", provider.getImportURL(c, protocol).getController());
        assayProperties.put("importAction", provider.getImportURL(c, protocol).getAction());
        assayProperties.put("reRunSupport", provider.getReRunSupport());
        assayProperties.put("templateLink", urlProvider(AssayUrls.class).getProtocolURL(c, protocol, TemplateAction.class));
        if (provider instanceof PlateBasedAssayProvider plateBased)
            assayProperties.put("plateTemplate", plateBased.getPlate(c, protocol));
        assayProperties.put("requireCommentOnQCStateChange", AssayQCService.getProvider().isRequireCommentOnQCStateChange(protocol.getContainer()));
        assayProperties.put("plateEnabled", provider.isPlateMetadataEnabled(protocol));

        // XXX: UGLY: Get the TableInfo associated with the Domain -- loop over all tables and ask for the Domains.
        AssayProtocolSchema schema = provider.createProtocolSchema(user, c, protocol, null);
        Set<String> tableNames = schema.getTableNames();
        Map<String, TableInfo> tableInfoMap = new HashMap<>();
        for (String tableName : tableNames)
        {
            TableInfo table = schema.getTable(tableName, null, true, true);
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

        Map<ExpProtocol.AssayDomainTypes, String> domainTypes = new EnumMap<>(ExpProtocol.AssayDomainTypes.class);

        final Domain batchDomain = provider.getBatchDomain(protocol);
        domainTypes.put(ExpProtocol.AssayDomainTypes.Batch, batchDomain == null ? null : batchDomain.getName());

        final Domain runDomain = provider.getRunDomain(protocol);
        domainTypes.put(ExpProtocol.AssayDomainTypes.Run, runDomain == null ? null : runDomain.getName());

        final Domain resultsDomain = provider.getResultsDomain(protocol);
        domainTypes.put(ExpProtocol.AssayDomainTypes.Result, resultsDomain == null ? null : resultsDomain.getName());

        assayProperties.put("domains", domains);
        assayProperties.put("domainTypes", domainTypes);
        assayProperties.put("links", serializeAssayLinks(protocol, provider, c, user));
        return assayProperties;
    }

    private static Map<String, Object> serializeAssayLinks(ExpProtocol protocol, AssayProvider provider, Container c, User user)
    {
        Map<String, Object> links = new HashMap<>();
        AssayUrls urlProvider = urlProvider(AssayUrls.class);

        links.put("batches", urlProvider.getAssayBatchesURL(c, protocol, null));
        links.put("begin", urlProvider.getProtocolURL(c, protocol, AssayBeginAction.class));
        links.put("designCopy", urlProvider.getDesignerURL(c, protocol, true, null));
        links.put("designDelete", urlProvider.getDeleteDesignURL(protocol));
        links.put("designEdit", urlProvider.getDesignerURL(c, protocol, false, null));
        links.put("import", provider.getImportURL(c, protocol));
        links.put("results", urlProvider.getAssayResultsURL(c, protocol));
        links.put("runs", urlProvider.getAssayRunsURL(c, protocol, null));

        return links;
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
                    ((MutableColumnInfo)column).setDefaultValue(defaultValue.toString());

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
                properties.put("lookupSchema", Objects.toString(l.getSchemaKey(),null));
                properties.put("lookupQuery", l.getQueryName());

                // let's be consistent with Query metadata
                HashMap<String,String> lookup = new HashMap<>();
                lookup.put("schema", Objects.toString(l.getSchemaKey(),null));
                lookup.put("table", l.getQueryName());
                lookup.put("container", null!=l.getContainer() ? l.getContainer().getPath() : null);

                // Let's not make the client guess keyColumn/displayColumn
                // CONSIDER: move into a QueryService helper method?
                Container lookupContainer = l.getContainer();
                if (lookupContainer == null)
                    lookupContainer = property.getContainer();
                UserSchema schema = QueryService.get().getUserSchema(user, lookupContainer, l.getSchemaKey());
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

    @RequiresPermission(AssayReadPermission.class)
    public static class ChooseCopyDestinationAction extends BaseAssayAction<ProtocolIdForm>
    {
        private ExpProtocol _protocol;

        @Override
        public ModelAndView getView(ProtocolIdForm form, BindException errors)
        {
            _protocol = form.getProtocol(false);

            final Container currentContainer = getContainer();
            final User user = getUser();
            final ProjectUrls projectUrls = urlProvider(ProjectUrls.class);

            ContainerTree tree = new ContainerTree("/", getUser(), DesignAssayPermission.class, null)
            {
                @Override
                protected void renderCellContents(StringBuilder html, Container c, ActionURL url)
                {
                    //if user doesn't have read permission to the target container, set the return URL to be
                    //the current container
                    ActionURL returnURL = (c.hasPermission(user, ReadPermission.class)) ? projectUrls.getStartURL(c) : projectUrls.getStartURL(currentContainer);

                    ActionURL copyURL = urlProvider(AssayUrls.class).getDesignerURL(c, _protocol, true, returnURL);
                    html.append("<a href=\"");
                    html.append(copyURL.getEncodedLocalURIString());
                    html.append("\">");
                    html.append(PageFlowUtil.filter(c.getName()));
                    html.append("</a>");
                }
            };
            ActionURL copyHereURL = urlProvider(AssayUrls.class).getDesignerURL(form.getContainer(), _protocol, true, null);
            HtmlView fileTree = new HtmlView(HtmlStringBuilder.of()
                .unsafeAppend("<table><tr><td><b>Select destination folder:</b></td></tr>")
                .append(tree.getHtmlString())
                .unsafeAppend("</table>")
                .getHtmlString()
            );
            HtmlView bbar = HtmlView.unsafe(
            PageFlowUtil.button("Cancel").href(new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId())) + " " +
                    (form.getContainer().hasPermission(getUser(), InsertPermission.class) ? PageFlowUtil.button("Copy to Current Folder").href(copyHereURL) : "")
            );
            setHelpTopic("manageAssayDesign");
            return new VBox(bbar, fileTree, bbar);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Assay List", new ActionURL(BeginAction.class, getContainer()));
            root.addChild(_protocol.getName(), new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId()));
            root.addChild("Copy Assay Design");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class SummaryRedirectAction extends BaseAssayAction<ProtocolIdForm>
    {
        @Override
        public ModelAndView getView(ProtocolIdForm form, BindException errors)
        {
            throw new RedirectException(urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), form.getProtocol()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException("Redirects should not show nav trails");
        }
    }

    @RequiresPermission(AssayReadPermission.class)
    public static class AssayBeginAction extends BaseAssayAction<ProtocolIdForm>
    {
        private ExpProtocol _protocol;
        private boolean _hasCustomView = false;

        @Override
        public ModelAndView getView(ProtocolIdForm form, BindException errors)
        {
            _protocol = form.getProtocol();
            AssayProvider provider = form.getProvider();
            ModelAndView view = provider.createBeginView(getViewContext(), form.getProtocol());
            _hasCustomView = (null != view);
            setHelpTopic("workWithAssayData#runs");
            return (null == view ? new AssayRunsView(_protocol, false, errors) : view);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            if (_hasCustomView)
                root.addChild(_protocol.getName() + " Overview");
            else
                new AssayRunsAction(getViewContext(), _protocol).addNavTrail(root);
        }
    }

    public static class AssayProviderBean
    {
        String name;
        String description;
        List<String> fileTypes;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getDescription()
        {
            return description;
        }

        public void setDescription(String description)
        {
            this.description = description;
        }

        public List<String> getFileTypes()
        {
            return fileTypes;
        }

        public void setFileTypes(List<String> fileTypes)
        {
            this.fileTypes = fileTypes;
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(DesignAssayPermission.class)
    public static class GetAssayTypeSelectOptionsAction extends ReadOnlyApiAction<Object>
    {
        private List<AssayProviderBean> getProviders()
        {
            List<AssayProvider> providers = new ArrayList<>(AssayService.get().getAssayProviders());

            // Remove AssayProviders without a designer action
            providers.removeIf(provider -> provider.getDesignerAction() == null);

            FileContentService fileContentService = FileContentService.get();
            boolean isCloudRoot = fileContentService != null && fileContentService.isCloudRoot(getContainer());

            List<AssayProviderBean> beans = new ArrayList<>();
            AssayProviderBean bean;
            for (AssayProvider provider : providers)
            {
                // Is cloud setup and assay doesn't support cloud, then do not include
                if(isCloudRoot && null != provider.getPipelineProvider() && !provider.getPipelineProvider().supportsCloud())
                    continue;

                bean = new AssayProviderBean();
                bean.setName(provider.getName());
                bean.setDescription(provider.getDescription());
                AssayDataType dataType = provider.getDataType();
                bean.setFileTypes(dataType == null ? Collections.emptyList() : dataType.getFileType().getSuffixes());
                beans.add(bean);
            }

            beans.sort(Comparator.comparing(AssayProviderBean::getName));
            return beans;
        }

        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            response.put("providers", getProviders());

            Map<String, String> locations = new LinkedHashMap<>();
            String defaultLocation = null;
            for (Pair<Container, String> entry : AssayService.get().getLocationOptions(getContainer(), getUser()))
            {
                locations.put(entry.getKey().getPath(), entry.getValue());
                if (defaultLocation == null)
                    defaultLocation = entry.getKey().getPath();
            }

            response.put("defaultLocation", defaultLocation);
            response.put("locations", locations);

            return response;
        }
    }

    @RequiresPermission(DesignAssayPermission.class)
    public static class ChooseAssayTypeAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ModuleHtmlView view = ModuleHtmlView.get(ModuleLoader.getInstance().getModule("assay"), ModuleHtmlView.getGeneratedViewPath("assayTypeSelect"));
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Assay List", new ActionURL(BeginAction.class, getContainer()));
            root.addChild("New Assay Design");
            setHelpTopic("defineAssaySchema");
        }
    }

    @RequiresPermission(AssayReadPermission.class)
    public static class AssayFileDuplicateCheckAction extends MutatingApiAction<SimpleApiJsonForm>
    {
        @Override
        public ApiResponse execute(SimpleApiJsonForm form, BindException errors) throws Exception
        {
            boolean duplicate = false;   // if there is a filename conflict, set to true
            List<String> newFileNames = new ArrayList<>();   // if there is a filename conflict, this alternate filename (or filenames) will be used.
            List<List<String>> runNamesPerFile = new ArrayList<>();
            try
            {
                FileLike targetDirectory = AssayFileWriter.ensureUploadDirectory(getContainer());
                JSONArray fileNames = form.getJsonObject() == null ? null : form.getJsonObject().getJSONArray("fileNames");
                if (fileNames != null && fileNames.length() > 0)
                {
                    for (int i = 0; i < fileNames.length(); i++)
                    {
                        String fileName = (String)fileNames.get(i);
                        FileLike f = targetDirectory.resolveChild(fileName);
                        if (f.exists())
                        {
                            duplicate = true;
                            FileLike newFile = AssayFileWriter.findUniqueFileName(fileName, targetDirectory);
                            newFileNames.add(i, newFile.getName());  // will infer duplication by whether an element exists at that position or not
                            ExpData expData = ExperimentService.get().getExpDataByURL(f.toNioPathForRead().toFile(), null);
                            List<String> runNames = new ArrayList<>();
                            if (expData != null)
                            {
                                for (ExpRun targetRun : expData.getTargetRuns())
                                {
                                    runNames.add(targetRun.getName());
                                }
                            }
                            runNamesPerFile.add(i, runNames);
                        }
                        else
                        {
                            // fill in empty items anyway so lengths of lists are correct
                            newFileNames.add("");
                            runNamesPerFile.add(new ArrayList<>());
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
            map.put("newFileNames", newFileNames);
            map.put("runNamesPerFile", runNamesPerFile);
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

        @SuppressWarnings("unused")
        public void setProtocolId(Integer protocolId)
        {
            _protocolId = protocolId;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public static class AssayFileUploadAction extends AbstractFileUploadAction<AssayFileUploadForm>
    {
        @Override
        protected File getTargetFile(String filename) throws IOException
        {
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
                throw new UploadException("Pipeline root must be configured before uploading assay files", HttpServletResponse.SC_NOT_FOUND);

            try
            {
                FileLike targetDirectory = AssayFileWriter.ensureUploadDirectory(getContainer());
                return AssayFileWriter.findUniqueFileName(filename, targetDirectory).toNioPathForWrite().toFile();
            }
            catch (ExperimentException e)
            {
                throw new UploadException(e.getMessage(), HttpServletResponse.SC_NOT_FOUND);
            }
        }

        @Override
        public String getResponse(AssayFileUploadForm form, Map<String, Pair<File, String>> files)
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

                JSONObject jsonData = ExperimentJSONConverter.serializeData(data, getUser(), ExperimentJSONConverter.DEFAULT_SETTINGS);

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
         * requests its desired data LSID namespace prefix, validating that the file name matches the expected inputs.
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
    public static class DownloadSampleQCDataAction extends SimpleViewAction<ProtocolIdForm>
    {
        @Override
        public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
        {
            ExpProtocol protocol = form.getProtocol(true);
            AssayProvider provider = AssayService.get().getProvider(protocol);
            DataExchangeHandler handler = provider.createDataExchangeHandler();

            if (handler != null)
            {
                FileLike tempDir = getTempFolder();

                try {
                    handler.createSampleData(protocol, getViewContext(), tempDir);
                    File[] files = tempDir.toNioPathForRead().toFile().listFiles();

                    if (files.length > 0)
                    {
                        HttpServletResponse response = getViewContext().getResponse();

                        response.reset();
                        response.setContentType("application/zip");
                        ResponseHelper.setContentDisposition(response, ResponseHelper.ContentDispositionType.attachment, "sampleQCData.zip");
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
                    FileUtil.deleteDir(tempDir.toNioPathForWrite().toFile());
                }
            }
            return null;
        }

        private FileLike getTempFolder() throws IOException
        {
            FileLike tempDir = FileUtil.getTempDirectoryFileLike();
            FileLike tempFolder = tempDir.resolveChild("QCSampleData").resolveChild(String.valueOf(Thread.currentThread().getId()));
            if (!tempFolder.exists())
                FileUtil.mkdirs(tempFolder);

            return tempFolder;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class AssayUrlsImpl implements AssayUrls
    {
        @Override
        public ActionURL getProtocolURL(Container container, @Nullable ExpProtocol protocol, Class<? extends Controller> action)
        {
            ActionURL url = new ActionURL(action, container);
            if (protocol != null)
                url.addParameter("rowId", protocol.getRowId());
            return url;
        }

        @Override
        public @Nullable ActionURL getDesignerURL(Container container, ExpProtocol protocol, boolean copy, @Nullable ActionURL returnURL)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            return getDesignerURL(container, provider, protocol, copy, returnURL);
        }

        @Override
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
                url.addReturnURL(returnURL);

            return url;
        }

        @Override
        public ActionURL getAssayRunsURL(Container container, ExpProtocol protocol)
        {
            return getAssayRunsURL(container, protocol, null);
        }

        @Override
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

        @Override
        public ActionURL getAssayListURL(Container container)
        {
            return getProtocolURL(container, null, BeginAction.class);
        }

        @Override
        public ActionURL getAssayBatchesURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter)
        {
            ActionURL url = getProtocolURL(container, protocol, AssayBatchesAction.class);
            if (containerFilter != null && containerFilter.getType() != null)
            {
                url.addParameter("Batches." + QueryParam.containerFilterName, containerFilter.getType().name());
            }
            return url;
        }

        @Override
        public ActionURL getAssayResultsURL(Container container, ExpProtocol protocol, int... runIds)
        {
            return getAssayResultsURL(container, protocol, (ContainerFilter.Type)null, runIds);
        }

        @Override
        public ActionURL getAssayResultsURL(Container container, ExpProtocol protocol, @Nullable ContainerFilter containerFilter, int... runIds)
        {
            return getAssayResultsURL(container, protocol, null==containerFilter?null:containerFilter.getType(), runIds);
        }
        @Override
        public ActionURL getAssayResultsURL(Container container, ExpProtocol protocol, @Nullable ContainerFilter.Type containerFilterType, int... runIds)
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
            if (containerFilterType != null)
                result.addParameter("Data." + QueryParam.containerFilterName, containerFilterType.name());
            return result;
        }

        @Override
        public @Nullable ActionURL getAssayResultRowURL(AssayProvider provider, Container container, ExpProtocol protocol, int rowId)
        {
            ActionURL resultsURL = getAssayResultsURL(container, protocol);
            resultsURL.addFilter("Data", FieldKey.fromParts("rowId"), CompareType.EQUAL, rowId);
            return resultsURL;
        }

        @Override
        public ActionURL getAssayResultsURL(Container container, ExpProtocol protocol)
        {
            return getProtocolURL(container, protocol, AssayResultsAction.class);
        }

        @Override
        public ActionURL getShowUploadJobsURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter)
        {
            ActionURL result = getProtocolURL(container, protocol, ShowUploadJobsAction.class);
            if (containerFilter != null && containerFilter.getType() != null)
                result.addParameter("StatusFiles." + QueryParam.containerFilterName, containerFilter.getType().name());
            return result;
        }

        @Override
        public ActionURL getChooseCopyDestinationURL(ExpProtocol protocol, Container container)
        {
            // Check if the provider supports designing before handing out copy URL
            ActionURL designerURL = getDesignerURL(container, protocol, false, null);
            if (designerURL == null)
                return null;

            return getProtocolURL(container, protocol, ChooseCopyDestinationAction.class);
        }

        @Override
        public ActionURL getDeleteDesignURL(ExpProtocol protocol)
        {
            return getProtocolURL(protocol.getContainer(), protocol, DeleteAction.class);
        }

        @Override
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
            ActionURL url = new ActionURL(provider.getDesignerAction(), container);
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

        @Override
        public ActionURL getUpdateQCStateURL(Container container, ExpProtocol protocol)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider != null && provider.isQCEnabled(protocol))
            {
                return new ActionURL(QCStateAction.class, container);
            }
            return null;
        }

        @Override
        public ActionURL getBeginURL(Container container)
        {
            return new ActionURL(BeginAction.class, container);
        }

        @Override
        public ActionURL getSummaryRedirectURL(Container container)
        {
            return new ActionURL(SummaryRedirectAction.class, container);
        }

        @Override
        public ActionURL getSetResultFlagURL(Container container)
        {
            return new ActionURL(SetResultFlagAction.class, container);
        }

        @Override
        public ActionURL getChooseAssayTypeURL(Container container)
        {
            return new ActionURL(ChooseAssayTypeAction.class, container);
        }

        @Override
        public ActionURL getImportAssayDesignURL(Container container)
        {
            return getChooseAssayTypeURL(container).addParameter("tab", "import");
        }

        @Override
        public ActionURL getShowSelectedDataURL(Container container, ExpProtocol protocol)
        {
            return getProtocolURL(container, protocol, ShowSelectedDataAction.class);
        }

        @Override
        public ActionURL getShowSelectedRunsURL(Container container, ExpProtocol protocol, @Nullable ContainerFilter containerFilter)
        {
            ActionURL url = getProtocolURL(container, protocol, ShowSelectedRunsAction.class);

            if (containerFilter != null && containerFilter.getType() != null)
                url.addParameter("containerFilterName", containerFilter.getType().name());

            return url;
        }

        @Override
        public ActionURL getSetDefaultValuesAssayURL(Container container, String providerName, Domain domain, ActionURL returnUrl)
        {
            ActionURL url = new ActionURL(SetDefaultValuesAssayAction.class, container);
            url.addParameter("providerName", providerName);
            url.addParameter("domainId", domain.getTypeId());
            url.addReturnURL(returnUrl);

            return url;
        }

        @Override
        public String getBatchIdFilterParam()
        {
            // Unfortunately this seems to be the best way to figure out the name of the URL parameter to filter by batch id
            ActionURL fakeURL = new ActionURL(ShowSelectedRunsAction.class, ContainerManager.getHomeContainer());
            fakeURL.addFilter(AssayProtocolSchema.RUNS_TABLE_NAME, AbstractAssayProvider.BATCH_ROWID_FROM_RUN, CompareType.EQUAL, "${RowId}");
            return fakeURL.getParameters().get(0).getKey();
        }
    }

    @RequiresPermission(AssayReadPermission.class)
    public static class ShowUploadJobsAction extends BaseAssayAction<ProtocolIdForm>
    {
        private ExpProtocol _protocol;

        @Override
        public ModelAndView getView(ProtocolIdForm form, BindException errors)
        {
            _protocol = form.getProtocol();

            AssayView result = new AssayView();
            QueryView view = PipelineService.get().getPipelineQueryView(getViewContext(), PipelineService.PipelineButtonOption.Assay);
            view.setTitle("Data Pipeline");
            result.setupViews(view, false, form.getProvider(), form.getProtocol());

            return result;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            super.addNavTrail(root);
            root.addChild(_protocol.getName() + " Upload Jobs");
        }
    }


    public static class SetResultFlagForm extends ProtocolIdForm
    {
        private Integer[] _resultRowIds;
        private String[] _lsids;
        private String _comment;
        private String _columnName = "flag";

        public SetResultFlagForm()
        {
        }

        public Integer[] getResultRowIds()
        {
            return _resultRowIds;
        }

        public void setResultRowIds(Integer[] rowIds)
        {
            _resultRowIds = rowIds;
        }

        // These LSIDS are not 'real', it's the Data lsid + ":" + result.rowid
        public void setLsid(String[] lsids)
        {
            _lsids = lsids;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }

        public String getComment()
        {
            return _comment;
        }

        public String getColumnName()
        {
            return _columnName;
        }

        public void setColumnName(String columnName)
        {
            _columnName = columnName;
        }

        public List<Integer> getRowList()
        {
            if (null != _resultRowIds)
                return Arrays.asList(_resultRowIds);
            if (null == _lsids)
                return Collections.emptyList();
            ArrayList<Integer> ret = new ArrayList<>(_lsids.length);
            for (String lsid : _lsids)
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
                        int rowid = Integer.parseInt(lsid.substring(i+1));
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
    public static class SetResultFlagAction extends MutatingApiAction<SetResultFlagForm>
    {
        @Override
        protected @NotNull SetResultFlagForm getCommand(HttpServletRequest request)
        {
            return new SetResultFlagForm();
        }

        @Override
        public ApiResponse execute(SetResultFlagForm form, BindException errors)
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
                    Map<String, Object> flagComment = new HashMap<>();
                    flagComment.put(flagCol.getColumnName(), comment);
                    Table.update(getUser(), ti, flagComment, id);
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

    @RequiresPermission(AssayReadPermission.class)
    public static class GetQCStateAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public Object execute(Object form, BindException errors) throws Exception
        {
            String run = getViewContext().getRequest().getParameter("run");
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (run != null)
            {
                ExpRun expRun = ExperimentService.get().getExpRun(NumberUtils.toInt(run));
                if (expRun != null)
                {
                    response.put("success", true);
                    DataState state = AssayQCService.getProvider().getQCState(expRun.getProtocol(), expRun.getRowId());
                    if (state != null)
                    {
                        response.put("qcState", PageFlowUtil.map("label", state.getLabel(), "rowId", state.getRowId()));
                    }
                }
            }
            return response;
        }
    }

    public static class UpdateQCStateForm extends ReturnUrlForm
    {
        private Integer _state;
        private String _comment;
        private Set<Integer> _runs;

        public Integer getState()
        {
            return _state;
        }

        public void setState(Integer state)
        {
            _state = state;
        }

        public String getComment()
        {
            return _comment;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }

        public Set<Integer> getRuns()
        {
            return _runs;
        }

        public void setRuns(Set<Integer> runs)
        {
            _runs = runs;
        }

        public void setRun(Integer run)
        {
            if (_runs == null)
                _runs = new HashSet<>();
            _runs.add(run);
        }
    }

    @RequiresPermission(AssayReadPermission.class)
    public static class QCStateAction extends SimpleViewAction<UpdateQCStateForm>
    {
        @Override
        public ModelAndView getView(UpdateQCStateForm form, BindException errors) throws Exception
        {
            if (form.getRuns() == null)
            {
                if (DataRegionSelection.hasSelected(getViewContext()))
                    form.setRuns(DataRegionSelection.getSelectedIntegers(getViewContext(), true));
            }
            VBox view = new VBox();

            if (form.getRuns() != null && !form.getRuns().isEmpty())
            {
                if (getContainer().hasPermission(getUser(), QCAnalystPermission.class))
                {
                    JspView jspView = new JspView<>("/org/labkey/assay/view/updateQCState.jsp", form, errors);
                    jspView.setFrame(WebPartView.FrameType.PORTAL);
                    view.addView(jspView);
                }

                if (form.getRuns().size() == 1)
                {
                    // construct the audit log query view
                    User user = ElevatedUser.ensureCanSeeAuditLogRole(getContainer(), getUser());
                    UserSchema schema = AuditLogService.getAuditLogSchema(user, getContainer());
                    ExpRun run = ExperimentService.get().getExpRun(form.getRuns().stream().findFirst().get());
                    if (run != null && schema != null)
                    {
                        QuerySettings settings = new QuerySettings(getViewContext(), "auditHistory");
                        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RunLsid"), run.getLSID());
                        filter.addCondition(FieldKey.fromParts("QCState"), null, CompareType.NONBLANK);

                        settings.setBaseFilter(filter);
                        settings.setQueryName("ExperimentAuditEvent");

                        QueryView auditView = schema.createView(getViewContext(), settings, errors);
                        auditView.setTitle("QC History");

                        view.addView(auditView);
                    }
                }
                return view;
            }
            else
                return HtmlView.of("No runs have been selected to update");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Change QC State");
        }
    }

    @RequiresPermission(QCAnalystPermission.class)
    public static class UpdateQCStateAction extends MutatingApiAction<UpdateQCStateForm>
    {
        ExpRun _firstRun;

        @Override
        public void validateForm(UpdateQCStateForm form, Errors errors)
        {
            if (form.getState() == null)
                errors.reject(ERROR_MSG, "QC State cannot be blank");
            if (form.getRuns().isEmpty())
                errors.reject(ERROR_MSG, "No runs were selected to update their QC State");
            else
            {
                for (int id : form.getRuns())
                {
                    // just get the first run
                    _firstRun = ExperimentService.get().getExpRun(id);
                    if (_firstRun != null)
                        break;
                }

                if (_firstRun != null)
                {
                    if (AssayQCService.getProvider().isRequireCommentOnQCStateChange(_firstRun.getProtocol().getContainer()) && StringUtils.isBlank(form.getComment()))
                        errors.reject(ERROR_MSG, "A comment is required when changing a QC State for the selected run(s).");
                }
            }
        }

        @Override
        public ApiSimpleResponse execute(UpdateQCStateForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            if (form.getRuns() != null && _firstRun != null)
            {
                DataState state = DataStateManager.getInstance().getStateForRowId(_firstRun.getProtocol().getContainer(), form.getState());
                if (state != null)
                    AssayQCService.getProvider().setQCStates(_firstRun.getProtocol(), getContainer(), getUser(), List.copyOf(form.getRuns()), state, form.getComment());

                response.put("success", true);
            }
            return response;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public static class ModuleAssayUploadAction extends BaseAssayAction<AssayRunUploadForm>
    {
        private ExpProtocol _protocol;

        @Override
        public ModelAndView getView(AssayRunUploadForm form, BindException errors)
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
        public void addNavTrail(NavTree root)
        {
            Container c = getContainer();
            ActionURL batchListURL = urlProvider(AssayUrls.class).getAssayBatchesURL(c, _protocol, null);

            super.addNavTrail(root);
            root.addChild(_protocol.getName() + " Batches", batchListURL);
            root.addChild("Data Import");
        }
    }

    @RequiresPermission(AssayReadPermission.class)
    public static class GetValidPublishTargetsAction extends ReadOnlyApiAction
    {
        @Override
        public ApiResponse execute(Object object, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<Map<String, Object>> containersInfo = new ArrayList<>();

            StudyPublishService service = StudyPublishService.get();
            if (service != null)
            {
                // issue 42415 : for assays at either the shared or project scope, allow a linkage target which
                // translates to a study in the current data import folder
                if (getContainer().isProject() || getContainer().equals(ContainerManager.getSharedContainer()))
                {
                    containersInfo.add(Map.of(
                            "id", StudyPublishService.AUTO_LINK_TARGET_IMPORT_FOLDER.getId(),
                            "name", StudyPublishService.AUTO_LINK_TARGET_IMPORT_FOLDER.getName(),
                            "path", "(Data import folder)"));
                }

                for (Study study : StudyPublishService.get().getValidPublishTargets(getUser(), ReadPermission.class))
                {
                    Container container = study.getContainer();
                    Map<String, Object> containerInfo = new HashMap<>();
                    containerInfo.put("id", container.getId());
                    containerInfo.put("name", container.getName());
                    containerInfo.put("path", container.getPath());
                    containersInfo.add(containerInfo);
                }
            }

            response.put("containers", containersInfo);
            return response;
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    public static class GetAssayRunOperationConfirmationDataAction extends ReadOnlyApiAction<AssayOperationConfirmationForm>
    {

        @Override
        public Object execute(AssayOperationConfirmationForm form, BindException errors) throws Exception
        {
            ExperimentService service = ExperimentService.get();

            Collection<Integer> allowedIds = form.getIds(false);
            List<? extends ExpRun> allRuns = service.getExpRuns(allowedIds);

            Set<Integer> notAllowedIds = new HashSet<>();
            Map<String, Object> response = new HashMap<>();

            Collection<Container> containers = new HashSet<>();
            Collection<Integer> notPermittedIds = new ArrayList<>();
            Class<? extends Permission> permClass = form.getDataOperation().getPermissionClass();
            for (ExpRun expRun : allRuns)
            {
                Container c = expRun.getContainer();
                containers.add(c);
                if (permClass != null && !c.hasPermission(getUser(), permClass))
                    notPermittedIds.add(expRun.getRowId());
            }

            response.put("containers", containers.stream().map(c -> Map.of(
                    "id", c.getEntityId(),
                    "path", (Object) c.getPath(),
                    "permitted", permClass == null || c.hasPermission(getUser(), permClass)
            )).toList());

            response.put("notPermitted", notPermittedIds.stream().map(id -> Map.of("RowId", (Object) id)).toList());

            if (form.getDataOperation() == AssayRunOperations.Delete)
            {
                service.getObjectReferencers().forEach(referencer ->
                        notAllowedIds.addAll(referencer.getItemsWithReferences(allowedIds, "assay")));
                allowedIds.removeAll(notAllowedIds);

                List<Map<String, Object>> allowedRows = new ArrayList<>();
                List<Map<String, Object>> notAllowedRows = new ArrayList<>();

                allRuns.forEach((dataObject) -> {
                    Map<String, Object> rowMap = Map.of("RowId", dataObject.getRowId(), "ContainerPath", dataObject.getContainer().getPath());
                    if (allowedIds.contains(dataObject.getRowId()))
                        allowedRows.add(rowMap);
                    else
                        notAllowedRows.add(rowMap);
                });

                response.put("allowed", allowedRows);
                response.put("notAllowed", notAllowedRows);
                return success(response);
            }

            response.put("allowed", allowedIds.stream().map(id -> Map.of("RowId", id)).toList());
            response.put("notAllowed", notAllowedIds);
            return success(response);
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    public static class GetAssayResultsOperationConfirmationDataAction extends ReadOnlyApiAction<AssayOperationConfirmationForm>
    {
        @Override
        public Object execute(AssayOperationConfirmationForm form, BindException errors) throws Exception
        {
            Collection<Integer> allowedIds = form.getIds(false);

            ExperimentService service = ExperimentService.get();
            ExpProtocol protocol = service.getExpProtocol(form.getProtocolId());
            AssayProvider provider = AssayService.get().getProvider(protocol);
            AssaySchema schema = provider.createProtocolSchema(getUser(), getContainer(), protocol, null);
            TableInfo tableInfo = schema.getTableOrThrow(AssayProtocolSchema.DATA_TABLE_NAME, ContainerFilter.EVERYTHING);

            // need to query to get the dataIds for the data rowIds so that we can check container permissions on that exp.data table
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), allowedIds, CompareType.IN);
            Collection<Map<String, Object>> dataRows = new TableSelector(tableInfo, PageFlowUtil.set("rowid", "dataid"), filter, null).getMapCollection();
            List<Integer> permittedRowIds = new ArrayList<>();
            Set<Container> uniqueContainers = new HashSet<>();
            for (Map<String, Object> dataRow : dataRows)
            {
                Integer rowId = (Integer) dataRow.get("rowid");
                ExpData data = ExperimentService.get().getExpData((Integer) dataRow.get("dataid"));
                if (data == null || data.getRun() == null) continue;

                Container c = data.getRun().getContainer();
                uniqueContainers.add(c);
                if (c.hasPermission(getUser(), AssayRunOperations.Edit.getPermissionClass()))
                    permittedRowIds.add(rowId);
            }
            List<Map<String, Integer>> notPermitted = allowedIds.stream().filter(id -> !permittedRowIds.contains(id)).map(id -> Map.of("RowId", id)).toList();

            Map<String, Object> response = new HashMap<>();
            Class<? extends Permission> permClass = form.getDataOperation().getPermissionClass();
            response.put("containers", uniqueContainers.stream().map(c -> Map.of(
                    "id", c.getEntityId(),
                    "path", (Object) c.getPath(),
                    "permitted", permClass == null || c.hasPermission(getUser(), permClass)
            )).toList());
            response.put("allowed", allowedIds.stream().map(id -> Map.of("RowId", id)).toList());
            response.put("notAllowed", new HashSet<>());
            response.put("notPermitted", notPermitted);
            return success(response);
        }
    }

    public enum AssayRunOperations
    {
        Delete("deleting", DeletePermission.class),
        Edit("editing", UpdatePermission.class),
        Move("moving", MoveEntitiesPermission.class);

        private final String _description; // used as a suffix in messaging users about what is not allowed
        private final Class<? extends Permission> _permissionClass;

        AssayRunOperations(String description, Class<? extends Permission> permissionClass)
        {
            _description = description;
            _permissionClass = permissionClass;
        }

        public String getDescription()
        {
            return _description;
        }

        public Class<? extends Permission> getPermissionClass()
        {
            return _permissionClass;
        }
    }

    public static class AssayOperationConfirmationForm extends DataViewSnapshotSelectionForm
    {
        private AssayRunOperations _dataOperation;
        private Integer _protocolId;

        public AssayRunOperations getDataOperation()
        {
            return _dataOperation;
        }

        public void setDataOperation(AssayRunOperations dataOperation)
        {
            _dataOperation = dataOperation;
        }

        public Integer getProtocolId()
        {
            return _protocolId;
        }

        public void setProtocolId(Integer protocolId)
        {
            _protocolId = protocolId;
        }
    }

    public static class SyncRunLineageForm
    {
        private List<Integer> _runIds = new ArrayList<>();

        public List<Integer> getRunIds()
        {
            return _runIds;
        }

        public void setRunIds(List<Integer> runIds)
        {
            _runIds = runIds;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class SyncRunLineageAction extends MutatingApiAction<SyncRunLineageForm>
    {
        @Override
        public Object execute(SyncRunLineageForm form, BindException errors) throws Exception
        {
            var batchErrors = new BatchValidationException();
            var context = new AssaySampleLookupContext(form.getRunIds());

            context.syncLineage(getContainer(), getUser(), batchErrors);
            if (batchErrors.hasErrors())
                throw batchErrors;

            return success();
        }
    }
}
