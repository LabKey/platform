/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.action.*;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CompareType;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.actions.*;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.ContainerTree;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.query.QueryParam;
import org.labkey.study.assay.AssayManager;
import org.labkey.study.assay.AssayServiceImpl;
import org.labkey.study.assay.query.AssayAuditViewFactory;
import org.labkey.study.controllers.assay.actions.SaveAssayBatchAction;
import org.labkey.study.controllers.assay.actions.GetAssayBatchAction;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import java.io.File;
import java.util.*;

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
            DeleteAction.class,
            DesignerAction.class,
            TemplateAction.class,
            AssayRunsAction.class,
            AssayDataAction.class,
            AssayDataDetailsAction.class,
            ShowSelectedDataAction.class
        );

    public AssayController()
    {
        super();
        setActionResolver(_resolver);
    }

    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends BaseAssayAction<ProtocolIdForm>
    {
        public ModelAndView getView(ProtocolIdForm o, BindException errors) throws Exception
        {
            return AssayService.get().createAssayListView(getViewContext(), false);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Assay List", new ActionURL(BeginAction.class, getContainer()));
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

    @RequiresPermission(ACL.PERM_READ)
    public class AssayListAction extends ApiAction<AssayListForm>
    {
        public ApiResponse execute(AssayListForm form, BindException errors) throws Exception
        {
            HashMap<ExpProtocol, AssayProvider> assayProtocols = new HashMap<ExpProtocol, AssayProvider>();
            ExpProtocol[] protocols = ExperimentService.get().getExpProtocols(getContainer());
            for (ExpProtocol protocol : protocols)
            {
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider != null && form.matches(protocol, provider))
                {
                    assayProtocols.put(protocol, provider);
                }
            }
            if (!getContainer().isProject())
            {
                protocols = ExperimentService.get().getExpProtocols(getContainer().getProject());
                for (ExpProtocol protocol : protocols)
                {
                    AssayProvider provider = AssayService.get().getProvider(protocol);
                    if (provider != null && form.matches(protocol, provider))
                    {
                        assayProtocols.put(protocol, provider);
                    }
                }
            }

            List<Map<String, Object>> assayList = new ArrayList<Map<String, Object>>();
            for (Map.Entry<ExpProtocol, AssayProvider> entry : assayProtocols.entrySet())
            {
                ExpProtocol protocol = entry.getKey();
                AssayProvider provider = entry.getValue();
                Map<String, Object> assayProperties = new HashMap<String, Object>();
                assayProperties.put("type", provider.getName());
                assayProperties.put("projectLevel", protocol.getContainer().isProject());
                assayProperties.put("description", protocol.getDescription());
                assayProperties.put("name", protocol.getName());
                assayProperties.put("id", protocol.getRowId());
                if (provider.isPlateBased())
                    assayProperties.put("plateTemplate", provider.getPlateTemplate(getContainer(), protocol));

                Map<String, List<Map<String, Object>>> domains = new HashMap<String, List<Map<String, Object>>>();
                for (Domain domain : provider.getDomains(protocol))
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
                        properties.put("formatString", property.getFormatString());
                        properties.put("required", property.isRequired());
                        if (property.getLookup() != null)
                        {
                            String containerPath = property.getLookup().getContainer() != null ? property.getLookup().getContainer().getPath() : null;
                            properties.put("lookupContainer", containerPath);
                            properties.put("lookupSchema", property.getLookup().getSchemaName());
                            properties.put("lookupQuery", property.getLookup().getQueryName());
                        }
                        propertyList.add(properties);
                    }

                    domains.put(domain.getName(), propertyList);
                }
                assayProperties.put("domains", domains);
                assayList.add(assayProperties);
            }
            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("definitions", assayList);
            return response;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
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

            ContainerTree tree = new ContainerTree("/", getUser(), ACL.PERM_INSERT, null)
            {
                @Override
                protected void renderCellContents(StringBuilder html, Container c, ActionURL url)
                {
                    ActionURL copyURL = PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(c, _protocol, true);
                    html.append("<a href=\"");
                    html.append(copyURL.getEncodedLocalURIString());
                    html.append("\">");
                    html.append(PageFlowUtil.filter(c.getName()));
                    html.append("</a>");
                }
            };
            ActionURL copyHereURL = PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(form.getContainer(), _protocol, true);
            HtmlView fileTree = new HtmlView("<table><tr><td><b>Select destination folder:</b></td></tr>" +
                    tree.render().toString() + "</table>");
            HtmlView bbar = new HtmlView(
                    PageFlowUtil.generateButton("Cancel", new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId())) + " " +
                    (form.getContainer().hasPermission(getUser(), ACL.PERM_INSERT) ? PageFlowUtil.generateButton("Copy to Current Folder", copyHereURL) : ""));
            return new VBox(bbar, fileTree, bbar);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Assay List", new ActionURL(BeginAction.class, getContainer())).addChild(_protocol.getName(),
                    new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId())).addChild("Copy Assay Design");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class SummaryRedirectAction extends BaseAssayAction<ProtocolIdForm>
    {
        public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
        {
            ExpProtocol protocol = getProtocol(form);
            HttpView.throwRedirect(PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), protocol));
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException("Redirects should not show nav trails");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DesignerRedirectAction extends BaseAssayAction<ProtocolIdForm>
    {
        public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
        {
            ActionURL designerURL = PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(getContainer(), form.getProviderName());
            HttpView.throwRedirect(designerURL);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException("Redirects should not show nav trails");
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ChooseAssayTypeAction extends BaseAssayAction<ProtocolIdForm>
    {
        public ModelAndView getView(ProtocolIdForm o, BindException errors) throws Exception
        {
            List<AssayProvider> providers = new ArrayList<AssayProvider>(AssayManager.get().getAssayProviders());
            Collections.sort(providers, new Comparator<AssayProvider>()
            {
                public int compare(AssayProvider o1, AssayProvider o2)
                {
                    return o1.getName().compareTo(o2.getName());
                }
            });
            return new JspView<List<AssayProvider>>("/org/labkey/study/assay/view/chooseAssayType.jsp", providers);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            NavTree result = super.appendNavTrail(root);
            result.addChild("New Assay Design");
            return result;
        }
    }

    @RequiresPermission(ACL.PERM_INSERT)
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
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DownloadFileAction extends SimpleViewAction<DownloadFileForm>
    {
        public ModelAndView getView(DownloadFileForm form, BindException errors) throws Exception
        {
            if (form.getPropertyId() == null || form.getObjectId() == null)
                HttpView.throwNotFound();
            OntologyObject obj = OntologyManager.getOntologyObject(form.getObjectId().intValue());
            if (obj == null)
                HttpView.throwNotFound();
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
                HttpView.throwNotFound();

            Map<String, ObjectProperty> properties = OntologyManager.getPropertyObjects(obj.getContainer(), obj.getObjectURI());
            ObjectProperty fileProperty = properties.get(pd.getPropertyURI());
            if (fileProperty == null || fileProperty.getPropertyType() != PropertyType.FILE_LINK || fileProperty.getStringValue() == null)
                HttpView.throwNotFound();
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

    @RequiresPermission(ACL.PERM_READ)
    public class PublishHistoryAction extends BaseAssayAction<PublishHistoryForm>
    {
        private ExpProtocol _protocol;
        public ModelAndView getView(PublishHistoryForm form, BindException errors) throws Exception
        {
            ContainerFilter containerFilter = ContainerFilter.Filters.CURRENT;
            if (form.getContainerFilterName() != null)
                containerFilter = ContainerFilter.Filters.valueOf(form.getContainerFilterName());

            _protocol = getProtocol(form);
            VBox view = new VBox();
            view.addView(new AssayHeaderView(_protocol, getProvider(form), false, containerFilter));
            view.addView(AssayAuditViewFactory.getInstance().createPublishHistoryView(getViewContext(), _protocol.getRowId(), containerFilter));
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Assay List", new ActionURL(BeginAction.class, getContainer())).addChild(_protocol.getName(),
                    new ActionURL(AssayRunsAction.class, getContainer()).addParameter("rowId", _protocol.getRowId())).addChild("Copy-to-Study History");
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
        public ActionURL getProtocolURL(Container container, ExpProtocol protocol, Class<? extends Controller> action)
        {
            ActionURL url = new ActionURL(action, container);
            if (protocol != null)
                url.addParameter("rowId", protocol.getRowId());
            return url;
        }

        public ActionURL getDesignerURL(Container container, ExpProtocol protocol, boolean copy)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider == null)
                return null;
            ActionURL url = getProtocolURL(container, protocol, provider.getDesignerAction());
            if (copy)
                url.addParameter("copy", "true");
            url.addParameter("providerName", provider.getName());
            return url;
        }

        public ActionURL getDesignerURL(Container container, String providerName)
        {
            AssayProvider provider = AssayService.get().getProvider(providerName);
            if (provider == null)
            {
                return null;
            }
            ActionURL url = getProtocolURL(container, null, provider.getDesignerAction());
            url.addParameter("providerName", provider.getName());
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

        public ActionURL getAssayRunsURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter)
        {
            ActionURL url = getProtocolURL(container, protocol, AssayRunsAction.class);
            if (containerFilter != null)
            {
                url.addParameter(protocol.getName() + " Runs." + QueryParam.containerFilterName, containerFilter.name());
            }
            return url;
        }

        public ActionURL getAssayListURL(Container container)
        {
            return getProtocolURL(container, null, AssayController.BeginAction.class);
        }

        public ActionURL getAssayDataURL(Container container, ExpProtocol protocol, int... runIds)
        {
            return getAssayDataURL(container, protocol, null, runIds);
        }

        public ActionURL getAssayDataURL(Container container, ExpProtocol protocol, ContainerFilter containerFilter, int... runIds)
        {
            ActionURL result = getProtocolURL(container, protocol, AssayDataAction.class);
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
                result.addFilter(provider.getRunDataTableName(protocol),
                        provider.getRunIdFieldKeyFromDataRow(), CompareType.IN, filterValue.toString());
            }
            else if (runIds.length == 1)
            {
                result.addFilter(provider.getRunDataTableName(protocol),
                        provider.getRunIdFieldKeyFromDataRow(), CompareType.EQUAL, runIds[0]);
            }
            if (containerFilter != null)
                result.addParameter(protocol.getName() + " Data." + QueryParam.containerFilterName, containerFilter.name());
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
    }

}
