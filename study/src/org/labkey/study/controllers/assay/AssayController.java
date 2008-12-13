/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegionSelection;
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
import org.labkey.api.util.ContainerTree;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.study.assay.AssayManager;
import org.labkey.study.assay.AssayServiceImpl;
import org.labkey.study.assay.query.AssayAuditViewFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

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
            PublishStartAction.class,
            PublishConfirmAction.class,
            UploadWizardAction.class,
            DeleteAction.class,
            DesignerAction.class,
            TemplateAction.class,
            AssayRunsAction.class,
            AssayDataAction.class,
            AssayDataDetailsAction.class
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
            return root.addChild("Assay List", getUrl("begin"));
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
            if (_id != null && protocol.getRowId() != _id)
                return false;
            if (_name != null && !_name.equals(protocol.getName()))
                return false;
            if (_type != null && !_type.equals(provider.getName()))
                return false;
            return true;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowSelectedDataAction extends RedirectAction<ShowSelectedForm>
    {
        public ActionURL getSuccessURL(ShowSelectedForm form)
        {
            Set<String> selection = DataRegionSelection.getSelected(getViewContext(), true);
            int[] selectedIds = new int[selection.size()];
            int i = 0;
            for (String id : selection)
                selectedIds[i++] = Integer.parseInt(id);
            ContainerFilter containerFilter = null;
            if (form.getContainerFilterName() != null)
                containerFilter = ContainerFilter.Filters.valueOf(form.getContainerFilterName());

            ActionURL url = AssayService.get().getAssayDataURL(getContainer(), form.getProtocol(), containerFilter, selectedIds);
            if (form.getContainerFilterName() != null)
                url.addParameter("containerFilterName", form.getContainerFilterName());
            return url;
        }

        public boolean doAction(ShowSelectedForm form, BindException errors) throws Exception
        {
            return true;
        }

        public void validateCommand(ShowSelectedForm target, Errors errors)
        {
        }
    }

    public static class ShowSelectedForm extends ProtocolIdForm
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
            _protocol = form.getRowId() != null ? ExperimentService.get().getExpProtocol(form.getRowId()) : null;
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
                    ActionURL copyURL = AssayService.get().getDesignerURL(c, _protocol, true);
                    html.append("<a href=\"");
                    html.append(copyURL.getEncodedLocalURIString());
                    html.append("\">");
                    html.append(PageFlowUtil.filter(c.getName()));
                    html.append("</a>");
                }
            };
            ActionURL copyHereURL = AssayService.get().getDesignerURL(form.getContainer(), _protocol, true);
            HtmlView fileTree = new HtmlView("<table><tr><td><b>Select destination folder:</b></td></tr>" +
                    tree.render().toString() + "</table>");
            HtmlView bbar = new HtmlView(
                    PageFlowUtil.generateButton("Cancel", getUrl("assayRuns").addParameter("rowId", _protocol.getRowId())) + " " +
                    (form.getContainer().hasPermission(getUser(), ACL.PERM_INSERT) ? PageFlowUtil.generateButton("Copy to Current Folder", copyHereURL) : ""));
            return new VBox(bbar, fileTree, bbar);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Assay List", getUrl("begin")).addChild(_protocol.getName(),
                    getUrl("assayRuns").addParameter("rowId", _protocol.getRowId())).addChild("Copy Assay Design");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class SummaryRedirectAction extends BaseAssayAction<ProtocolIdForm>
    {
        public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
        {
            ExpProtocol protocol = getProtocol(form);
            HttpView.throwRedirect(AssayService.get().getAssayRunsURL(getContainer(), protocol));
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
            ActionURL designerURL = AssayService.get().getDesignerURL(getContainer(), form.getProviderName());
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
            OntologyObject obj = OntologyManager.getOntologyObject(form.getObjectId());
            if (obj == null)
                HttpView.throwNotFound();
            if (!obj.getContainer().equals(getContainer().getId()))
            {
                ActionURL correctedURL = getViewContext().getActionURL().clone();
                Container objectContainer = ContainerManager.getForId(obj.getContainer());
                if (objectContainer == null)
                    HttpView.throwNotFound();
                correctedURL.setExtraPath(objectContainer.getPath());
                HttpView.throwRedirect(correctedURL);
            }

            PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(form.getPropertyId());
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
            return root.addChild("Assay List", getUrl("begin")).addChild(_protocol.getName(),
                    getUrl("assayRuns").addParameter("rowId", _protocol.getRowId())).addChild("Copy-to-Study History");
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
}
