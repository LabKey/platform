/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.defaults.SetDefaultValuesAssayAction;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.actions.*;
import org.labkey.api.study.assay.*;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.AppBar;
import org.labkey.study.assay.AssayManager;
import org.labkey.study.assay.AssayServiceImpl;
import org.labkey.study.assay.ModuleAssayProvider;
import org.labkey.study.assay.query.AssayAuditViewFactory;
import org.labkey.study.controllers.assay.actions.GetAssayBatchAction;
import org.labkey.study.controllers.assay.actions.SaveAssayBatchAction;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
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
            SaveAssayBatchAction.class,
            PublishStartAction.class,
            PublishConfirmAction.class,
            UploadWizardAction.class,
            PipelineDataCollectorRedirectAction.class,
            DeleteAction.class,
            DesignerAction.class,
            TemplateAction.class,
            AssayBatchesAction.class,
            AssayBatchDetailsAction.class,
            AssayRunsAction.class,
            AssayRunDetailsAction.class,
            AssayResultsAction.class,
            AssayResultDetailsAction.class,
            ShowSelectedRunsAction.class,
            ShowSelectedDataAction.class,
            SetDefaultValuesAssayAction.class,
            AssayDetailRedirectAction.class
        );

    public AssayController()
    {
        super();
        setActionResolver(_resolver);
    }

    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends BaseAssayAction<ProtocolIdForm>
    {
        public ModelAndView getView(ProtocolIdForm o, BindException errors) throws Exception
        {
            return AssayService.get().createAssayListView(getViewContext(), false);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Assays", new ActionURL(BeginAction.class, getContainer())).addChild("Assay List", new ActionURL(BeginAction.class, getContainer()));
        }

        public AppBar getAppBar()
        {
            return getAppBar(null);
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

    @RequiresPermissionClass(ReadPermission.class)
    public static class AssayListAction extends ApiAction<AssayListForm>
    {
        public ApiResponse execute(AssayListForm form, BindException errors) throws Exception
        {
            Container c = getViewContext().getContainer();
            HashMap<ExpProtocol, AssayProvider> assayProtocols = new HashMap<ExpProtocol, AssayProvider>();
            ExpProtocol[] protocols = ExperimentService.get().getExpProtocols(c);
            for (ExpProtocol protocol : protocols)
            {
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider != null && form.matches(protocol, provider))
                {
                    assayProtocols.put(protocol, provider);
                }
            }
            if (!c.isProject())
            {
                protocols = ExperimentService.get().getExpProtocols(c.getProject());
                for (ExpProtocol protocol : protocols)
                {
                    AssayProvider provider = AssayService.get().getProvider(protocol);
                    if (provider != null && form.matches(protocol, provider))
                    {
                        assayProtocols.put(protocol, provider);
                    }
                }
            }

            return serializeAssayDefinitions(assayProtocols, c, getViewContext().getUser());
        }

    }

    public static ApiResponse serializeAssayDefinitions(HashMap<ExpProtocol, AssayProvider> assayProtocols, Container c, User user)
    {
        List<Map<String, Object>> assayList = new ArrayList<Map<String, Object>>();
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
        Map<String, Object> assayProperties = new HashMap<String, Object>();
        assayProperties.put("type", provider.getName());
        assayProperties.put("projectLevel", protocol.getContainer().isProject());
        assayProperties.put("description", protocol.getDescription());
        assayProperties.put("name", protocol.getName());
        assayProperties.put("id", protocol.getRowId());
        if (provider instanceof PlateBasedAssayProvider)
            assayProperties.put("plateTemplate", ((PlateBasedAssayProvider)provider).getPlateTemplate(c, protocol));

        Map<String, List<Map<String, Object>>> domains = new HashMap<String, List<Map<String, Object>>>();
        for (Pair<Domain, Map<DomainProperty, Object>> domain : provider.getDomains(protocol))
        {
            domains.put(domain.getKey().getName(), serializeDomain(domain.getKey(), user));
        }
        assayProperties.put("domains", domains);
        return assayProperties;
    }

    private static List<Map<String, Object>> serializeDomain(Domain domain, User user)
    {
        List<Map<String, Object>> propertyList = new ArrayList<Map<String, Object>>();
        for (DomainProperty property : domain.getProperties())
        {
            HashMap<String, Object> properties = new HashMap<String, Object>();
            properties.put("name", property.getName());
            properties.put("typeName", property.getType().getLabel());
            properties.put("typeURI", property.getType().getTypeURI());
            properties.put("label", property.getLabel());
            properties.put("description", property.getDescription());
            properties.put("formatString", property.getFormat());
            properties.put("required", property.isRequired());
            if (property.getLookup() != null)
            {
                Lookup l = property.getLookup();
                // @deprecated (remove in future API version), use lookup.{} instead
                String containerPath = l.getContainer() != null ? l.getContainer().getPath() : null;
                properties.put("lookupContainer", containerPath);
                properties.put("lookupSchema", l.getSchemaName());
                properties.put("lookupQuery", l.getQueryName());

                // let's be consistent with Query metadata
                HashMap<String,String> lookup = new HashMap<String,String>();
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
                properties.put("lookup", lookup);
            }
            propertyList.add(properties);
        }
        return propertyList;
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ChooseCopyDestinationAction extends BaseAssayAction<ProtocolIdForm>
    {
        private ExpProtocol _protocol;

        public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
        {
            _protocol = form.getRowId() != null ? ExperimentService.get().getExpProtocol(form.getRowId().intValue()) : null;
            if (_protocol == null)
            {
                HttpView.throwNotFound();
                return null; // return to hide intellij warnings
            }

            final Container currentContainer = getViewContext().getContainer();
            final User user = getViewContext().getUser();
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
                    PageFlowUtil.generateButton("Cancel", new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId())) + " " +
                    (form.getContainer().hasPermission(getUser(), InsertPermission.class) ? PageFlowUtil.generateButton("Copy to Current Folder", copyHereURL) : ""));
            return new VBox(bbar, fileTree, bbar);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Assay List", new ActionURL(BeginAction.class, getContainer())).addChild(_protocol.getName(),
                    new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId())).addChild("Copy Assay Design");
        }

        public AppBar getAppBar()
        {
            return getAppBar(_protocol);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SummaryRedirectAction extends BaseAssayAction<ProtocolIdForm>
    {
        ExpProtocol _protocol;
        public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
        {
            _protocol = getProtocol(form);
            HttpView.throwRedirect(PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException("Redirects should not show nav trails");
        }

        public AppBar getAppBar()
        {
            throw new UnsupportedOperationException("Redirects should not show nav trails");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class AssayBeginAction extends BaseAssayAction<ProtocolIdForm>
    {
        ExpProtocol _protocol;
        boolean _hasCustomView = false;

        @Override
        public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
        {
            _protocol = form.getProtocol();
            AssayProvider provider = form.getProvider();
            if (null == provider)
                throw new NotFoundException("No assay was found with id " + form.getRowId());
            ModelAndView view = provider.createBeginView(getViewContext(), form.getProtocol());
            _hasCustomView = (null != view);
            return (null == view ? new AssayRunsView(_protocol, false) : view);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return _hasCustomView ? root.addChild(_protocol.getName() + " Overview") : new AssayRunsAction(getViewContext(), _protocol).appendNavTrail(root);
        }
    }

    public static class CreateAssayForm extends ProtocolIdForm
    {
        private boolean createInProject;
        private ReturnURLString returnURL;

        public CreateAssayForm() { }

        public boolean isCreateInProject()
        {
            return createInProject;
        }

        public void setCreateInProject(boolean createInProject)
        {
            this.createInProject = createInProject;
        }

        public ReturnURLString getReturnURL()
        {
            return returnURL;
        }

        public void setReturnURL(ReturnURLString returnURL)
        {
            this.returnURL = returnURL;
        }
    }

    public static class ChooseAssayBean
    {
        ActionURL returnURL;

        public List<AssayProvider> getProviders()
        {
            List<AssayProvider> providers = new ArrayList<AssayProvider>(AssayManager.get().getAssayProviders());
            Collections.sort(providers, new Comparator<AssayProvider>()
            {
                public int compare(AssayProvider o1, AssayProvider o2)
                {
                    return o1.getName().compareTo(o2.getName());
                }
            });
            return providers;
        }

        public ActionURL getReturnURL()
        {
            return returnURL;
        }
    }

    @RequiresPermissionClass(DesignAssayPermission.class)
    public class ChooseAssayTypeAction extends FormViewAction<CreateAssayForm>
    {
        Container createIn;

        public void validateCommand(CreateAssayForm form, Errors errors)
        {
        }

        public boolean handlePost(CreateAssayForm form, BindException errors) throws Exception
        {
            if (PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(getContainer(), form.getProviderName(), null) == null)
            {
                errors.addError(new LabkeyError("Please select an assay type."));
                return false;
            }

            Container createIn = form.isCreateInProject() ? getContainer().getProject() : getContainer();
            if (!createIn.hasPermission(getUser(), DesignAssayPermission.class))
            {
                errors.addError(new LabkeyError("You don't have permission to create assay designs in folder '" + createIn.getName() + "'."));
                return false;
            }
            this.createIn = createIn;

            return true;
        }

        public ActionURL getSuccessURL(CreateAssayForm form)
        {
            ActionURL returnURL = null;
            if (form.getReturnURL() != null)
                returnURL = new ActionURL(form.getReturnURL());
            return PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(createIn, form.getProviderName(), returnURL);
        }

        public ModelAndView getView(CreateAssayForm form, boolean reshow, BindException errors) throws Exception
        {
            ChooseAssayBean bean = new ChooseAssayBean();
            if (form.getReturnURL() != null)
                bean.returnURL = new ActionURL(form.getReturnURL());
            return new JspView<ChooseAssayBean>("/org/labkey/study/assay/view/chooseAssayType.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Assay List", new ActionURL(BeginAction.class, getContainer()));
            root.addChild("New Assay Design");
            setHelpTopic(new HelpTopic("defineAssaySchema"));
            return root;
        }
    }

    @RequiresPermissionClass(DesignAssayPermission.class)
    public class ServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new AssayServiceImpl(getViewContext());
        }
    }

    public static class DownloadFileForm
    {
        private Integer _propertyId;
        private Integer _objectId;
        private String _objectURI;

        public Integer getObjectId()
        {
            return _objectId;
        }

        public void setObjectId(Integer objectId)
        {
            _objectId = objectId;
        }

        public Integer getPropertyId()
        {
            return _propertyId;
        }

        public void setPropertyId(Integer propertyId)
        {
            _propertyId = propertyId;
        }

        public String getObjectURI()
        {
            return _objectURI;
        }

        public void setObjectURI(String objectURI)
        {
            _objectURI = objectURI;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadFileAction extends SimpleViewAction<DownloadFileForm>
    {
        public ModelAndView getView(DownloadFileForm form, BindException errors) throws Exception
        {
            if (form.getPropertyId() == null)
                HttpView.throwNotFound();
            OntologyObject obj = null;
            if (form.getObjectId() != null)
            {
                obj = OntologyManager.getOntologyObject(form.getObjectId().intValue());
            }
            else if (form.getObjectURI() != null)
            {
                // Don't filter by container - we'll redirect to the correct container ourselves
                obj = OntologyManager.getOntologyObject(null, form.getObjectURI());
            }
            if (obj == null)
                throw new NotFoundException();
            if (!obj.getContainer().equals(getContainer()))
            {
                ActionURL correctedURL = getViewContext().getActionURL().clone();
                Container objectContainer = obj.getContainer();
                if (objectContainer == null)
                    HttpView.throwNotFound();
                correctedURL.setContainer(objectContainer);
                HttpView.throwRedirect(correctedURL);
            }

            PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(form.getPropertyId().intValue());
            if (pd == null)
                throw new NotFoundException();

            Map<String, ObjectProperty> properties = OntologyManager.getPropertyObjects(obj.getContainer(), obj.getObjectURI());
            ObjectProperty fileProperty = properties.get(pd.getPropertyURI());
            if (fileProperty == null || fileProperty.getPropertyType() != PropertyType.FILE_LINK || fileProperty.getStringValue() == null)
                throw new NotFoundException();
            File file = new File(fileProperty.getStringValue());
            if (!file.exists())
                HttpView.throwNotFound("File " + file.getPath() + " does not exist on the server file system.");
            PageFlowUtil.streamFile(getViewContext().getResponse(), file, true);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException("Not Yet Implemented");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class PublishHistoryAction extends BaseAssayAction<PublishHistoryForm>
    {
        private ExpProtocol _protocol;
        public ModelAndView getView(PublishHistoryForm form, BindException errors) throws Exception
        {
            ContainerFilter containerFilter = ContainerFilter.CURRENT;
            if (form.getContainerFilterName() != null)
                containerFilter = ContainerFilter.getContainerFilterByName(form.getContainerFilterName(), getViewContext().getUser());

            _protocol = getProtocol(form);
            VBox view = new VBox();
            view.addView(new AssayHeaderView(_protocol, getProvider(form), false, true, containerFilter));
            view.addView(AssayAuditViewFactory.getInstance().createPublishHistoryView(getViewContext(), _protocol.getRowId(), containerFilter));
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Assay List", new ActionURL(BeginAction.class, getContainer())).addChild(_protocol.getName(),
                    new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId())).addChild("Copy-to-Study History");
        }

        public AppBar getAppBar()
        {
            return getAppBar(_protocol);
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class AssayFileUploadAction extends AbstractFileUploadAction
    {
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

        protected String handleFile(File file, String originalName) throws UploadException
        {
            ExpData data = ExperimentService.get().createData(getContainer(), ModuleAssayProvider.RAW_DATA_TYPE);

            data.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(file).toURI());
            data.setName(originalName);
            data.save(getViewContext().getUser());

            JSONObject json = ExperimentJSONConverter.serializeData(data);
            json.put("success", true);
            return json.toString();
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class ModuleAssayUploadAction extends BaseAssayAction<AssayRunUploadForm>
    {
        private ExpProtocol _protocol;

        @Override
        public ModelAndView getView(AssayRunUploadForm form, BindException errors) throws Exception
        {
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
                HttpView.throwNotFound("Pipeline root must be configured before uploading assay files");

            _protocol = getProtocol(form);

            AssayProvider ap = AssayService.get().getProvider(_protocol);
            if (ap == null)
                HttpView.throwNotFound("Assay not found for protocol with lsid: " + _protocol.getLSID());
            if (!(ap instanceof ModuleAssayProvider))
                throw new RuntimeException("Assay must be a ModuleAssayProvider");
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

    @RequiresPermissionClass(InsertPermission.class)
    public class DownloadSampleQCDataAction extends SimpleViewAction<ProtocolIdForm>
    {
        public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
        {
            ExpProtocol protocol = form.getProtocol(true);
            AssayProvider provider = AssayService.get().getProvider(protocol);
            DataExchangeHandler handler = ((AbstractAssayProvider)provider).getDataExchangeHandler();

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

    @Override
    public AppBar getAppBar(Controller action)
    {
        if (action instanceof AppBarAction)
            return ((AppBarAction) action).getAppBar();
        else
            return new AppBar("Assays", new NavTree("Add Runs", "#"), new NavTree("View Assay Types", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(getContainer())));
    }

    public static class AssayUrlsImpl implements AssayUrls
    {
        public ActionURL getProtocolURL(Container container, ExpProtocol protocol, Class<? extends Controller> action)
        {
            ActionURL url = new ActionURL(action, container);
            if (protocol != null)
                url.addParameter("rowId", protocol.getRowId());
            return url;
        }

        public ActionURL getDesignerURL(Container container, ExpProtocol protocol, boolean copy, ActionURL returnUrl)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider == null)
                return null;
            ActionURL url = getProtocolURL(container, protocol, provider.getDesignerAction());
            if (copy)
                url.addParameter("copy", "true");
            url.addParameter("providerName", provider.getName());
            if (null != returnUrl)
                url.addParameter("returnURL", returnUrl.toString());
            return url;
        }

        public ActionURL getDesignerURL(Container container, String providerName, ActionURL returnURL)
        {
            AssayProvider provider = AssayService.get().getProvider(providerName);
            if (provider == null)
            {
                return null;
            }
            ActionURL url = getProtocolURL(container, null, provider.getDesignerAction());
            url.addParameter("providerName", provider.getName());
            if (returnURL != null)
            {
                url.addParameter("returnURL", returnURL.toString());
            }
            return url;
        }

        public ActionURL getCopyToStudyConfirmURL(Container container, ExpProtocol protocol)
        {
            return getProtocolURL(container, protocol, PublishConfirmAction.class);
        }

        public ActionURL getAssayRunsURL(Container container, ExpProtocol protocol)
        {
            return getAssayRunsURL(container, protocol, null);
        }

        public ActionURL getAssayRunsURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter, int... batchIds)
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
                result.addFilter(AssayService.get().getRunsTableName(protocol),
                        AbstractAssayProvider.BATCH_ROWID_FROM_RUN, CompareType.IN, filterValue.toString());
            }
            else if (batchIds.length == 1)
            {
                result.addFilter(AssayService.get().getResultsTableName(protocol),
                        AbstractAssayProvider.BATCH_ROWID_FROM_RUN, CompareType.EQUAL, batchIds[0]);
            }
            if (containerFilter != null && containerFilter != ContainerFilter.EVERYTHING)
                result.addParameter(protocol.getName() + " Runs." + QueryParam.containerFilterName, containerFilter.getType().name());
            return result;
        }

        public ActionURL getAssayListURL(Container container)
        {
            return getProtocolURL(container, null, AssayController.BeginAction.class);
        }

        public ActionURL getAssayBatchesURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter)
        {
            ActionURL url = getProtocolURL(container, protocol, AssayBatchesAction.class);
            if (containerFilter != null && containerFilter != ContainerFilter.EVERYTHING)
            {
                url.addParameter(protocol.getName() + " Batches." + QueryParam.containerFilterName, containerFilter.getType().name());
            }
            return url;
        }

        public ActionURL getAssayResultsURL(Container container, ExpProtocol protocol, int... runIds)
        {
            return getAssayResultsURL(container, protocol, null, runIds);
        }

        public ActionURL getAssayResultsURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter, int... runIds)
        {
            ActionURL result = getProtocolURL(container, protocol, AssayResultsAction.class);
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (runIds.length > 1)
            {
                String sep = "";
                StringBuilder filterValue = new StringBuilder();
                for (int runId : runIds)
                {
                    filterValue.append(sep).append(runId);
                    sep = ";";
                }
                result.addFilter(AssayService.get().getResultsTableName(protocol),
                        provider.getTableMetadata().getRunRowIdFieldKeyFromResults(), CompareType.IN, filterValue.toString());
            }
            else if (runIds.length == 1)
            {
                result.addFilter(AssayService.get().getResultsTableName(protocol),
                        provider.getTableMetadata().getRunRowIdFieldKeyFromResults(), CompareType.EQUAL, runIds[0]);
            }
            if (containerFilter != null && containerFilter != ContainerFilter.EVERYTHING)
                result.addParameter(protocol.getName() + " Data." + QueryParam.containerFilterName, containerFilter.getType().name());
            return result;
        }

        public ActionURL getChooseCopyDestinationURL(ExpProtocol protocol, Container container)
        {
            return getProtocolURL(container, protocol, ChooseCopyDestinationAction.class);
        }

        public ActionURL getDeleteDesignURL(Container container, ExpProtocol protocol)
        {
            return getProtocolURL(container, protocol, DeleteAction.class);
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
    }
}
