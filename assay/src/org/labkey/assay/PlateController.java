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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateTemplate;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.security.RequiresAnyOf;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ContainerTree;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.assay.plate.PlateDataServiceImpl;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.PlateUrls;
import org.labkey.assay.view.AssayGWTView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jan 29, 2007
 * Time: 3:53:57 PM
 */
public class PlateController extends SpringActionController
{
    private static final SpringActionController.DefaultActionResolver _actionResolver = new DefaultActionResolver(PlateController.class);
    private static final Logger _log = LogManager.getLogger(PlateController.class);

    public PlateController()
    {
        setActionResolver(_actionResolver);
    }

    public static class PlateUrlsImpl implements PlateUrls
    {
        @Override
        public ActionURL getPlateTemplateListURL(Container c)
        {
            return new ActionURL(PlateTemplateListAction.class, c);
        }

        @Override
        public ActionURL getPlateDetailsURL(Container c)
        {
            return new ActionURL(PlateDetailsAction.class, c);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return HttpView.redirect(new ActionURL(PlateTemplateListAction.class, getContainer()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    public static class PlateTemplateListBean
    {
        private List<? extends PlateTemplate> _templates;
        public PlateTemplateListBean(List<? extends PlateTemplate> templates)
        {
            _templates = templates;
        }

        public List<? extends PlateTemplate> getTemplates()
        {
            return _templates;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class PlateTemplateListAction extends SimpleViewAction<ReturnUrlForm>
    {
        @Override
        public ModelAndView getView(ReturnUrlForm plateTemplateListForm, BindException errors)
        {
            setHelpTopic("editPlateTemplate");
            List<? extends PlateTemplate> plateTemplates = PlateService.get().getPlateTemplates(getContainer());
            return new JspView<>("/org/labkey/assay/plate/view/plateTemplateList.jsp",
                    new PlateTemplateListBean(plateTemplates));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Plate Templates");
        }           
    }

    @RequiresAnyOf({InsertPermission.class, DesignAssayPermission.class})
    public class DesignerServiceAction extends GWTServiceAction
    {
        @Override
        protected BaseRemoteService createService() throws IllegalStateException
        {
            return new PlateDataServiceImpl(getViewContext());
        }
    }

    public static class RowIdForm
    {
        private int _rowId;

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class PlateDetailsAction extends SimpleViewAction<RowIdForm>
    {
        @Override
        public ModelAndView getView(RowIdForm form, BindException errors)
        {
            Plate plate = PlateService.get().getPlate(getContainer(), form.getRowId());
            if (plate == null)
                throw new NotFoundException("Plate " + form.getRowId() + " does not exist.");
            ActionURL url = PlateManager.get().getDetailsURL(plate);
            if (url == null)
                throw new NotFoundException("Details URL has not been configured for plate type " + plate.getName() + ".");

            return HttpView.redirect(url);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresAnyOf({InsertPermission.class, DesignAssayPermission.class})
    public class DesignerAction extends SimpleViewAction<DesignerForm>
    {
        @Override
        public ModelAndView getView(DesignerForm form, BindException errors)
        {
            Map<String, String> properties = new HashMap<>();
            if (form.getTemplateName() != null)
            {
                properties.put("copyTemplate", Boolean.toString(form.isCopy()));
                properties.put("templateName", form.getTemplateName());
                if (form.getPlateId() != null)
                    properties.put("plateId", String.valueOf(form.getPlateId()));
                if (form.isCopy())
                    properties.put("defaultPlateName", getUniqueName(getContainer(), form.getTemplateName()));
                else
                    properties.put("defaultPlateName", form.getTemplateName());
            }
            if (form.getAssayType() != null)
            {
                properties.put("assayTypeName", form.getAssayType());
            }

            if (form.getTemplateType() != null)
            {
                properties.put("templateTypeName", form.getTemplateType());
            }

            properties.put("templateRowCount", "" + form.getRowCount());
            properties.put("templateColumnCount", "" + form.getColCount());

            List<? extends PlateTemplate> templates = PlateService.get().getPlateTemplates(getContainer());
            for (int i = 0; i < templates.size(); i++)
            {
                PlateTemplate template = templates.get(i);
                properties.put("templateName[" + i + "]", template.getName());
            }
            return new AssayGWTView(gwt.client.org.labkey.plate.designer.client.TemplateDesigner.class, properties);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("editPlateTemplate");
            root.addChild("Plate Template Editor");
        }
    }

    @RequiresAnyOf({InsertPermission.class, DesignAssayPermission.class})
    public class DeleteAction extends FormHandlerAction<NameForm>
    {
        @Override
        public void validateCommand(NameForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(NameForm form, BindException errors) throws Exception
        {
            PlateTemplate template = PlateService.get().getPlateTemplate(getContainer(), form.getPlateId());
            if (template != null)
                PlateService.get().deletePlate(getContainer(), template.getRowId());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(NameForm nameForm)
        {
            return new ActionURL(BeginAction.class, getContainer());
        }

    }

    public static class CopyTemplateBean
    {
        private final HtmlString _treeHtml;
        private final String _templateName;
        private final String _selectedDestination;
        private List<? extends PlateTemplate> _destinationTemplates;

        public CopyTemplateBean(final Container container, final User user, final String templateName, final String selectedDestination)
        {
            _templateName = templateName;
            _selectedDestination = selectedDestination;

            //Copy and Add to another folder requires InsertPermissions
            ContainerTree tree = new ContainerTree("/", user, InsertPermission.class, null)
            {
                @Override
                protected void renderCellContents(StringBuilder html, Container c, ActionURL url)
                {
                    ActionURL copyURL = new ActionURL(CopyTemplateAction.class, container);
                    copyURL.addParameter("templateName", templateName);
                    copyURL.addParameter("destination", c.getPath());
                    boolean selected = c.getPath().equals(selectedDestination);
                    if (selected)
                        html.append("<span class=\"labkey-nav-tree-selected\">");
                    html.append("<a href=\"");
                    html.append(copyURL.getEncodedLocalURIString());
                    html.append("\">");
                    html.append(PageFlowUtil.filter(c.getName()));
                    html.append("</a>");
                    if (selected)
                        html.append("</span>");
                }
            };

            if (_selectedDestination != null)
            {
                Container dest = ContainerManager.getForPath(_selectedDestination);
                if (dest != null)
                {
                    _destinationTemplates = PlateService.get().getPlateTemplates(dest);
                }
            }

            _treeHtml = tree.getHtmlString();
        }

        public String getSelectedDestination()
        {
            return _selectedDestination;
        }

        public HtmlString getTreeHtml()
        {
            return _treeHtml;
        }

        public String getTemplateName()
        {
            return _templateName;
        }

        public List<? extends PlateTemplate> getDestinationTemplates()
        {
            return _destinationTemplates;
        }
    }

    @RequiresAnyOf({InsertPermission.class, DesignAssayPermission.class})
    public class CopyTemplateAction extends FormViewAction<CopyForm>
    {
        @Override
        public void validateCommand(CopyForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(CopyForm form, boolean reshow, BindException errors)
        {
            if (form.getTemplateName() == null || form.getTemplateName().length() == 0)
                return HttpView.redirect(new ActionURL(BeginAction.class, getContainer()));

            return new JspView<>("/org/labkey/assay/plate/view/copyTemplate.jsp",
                    new CopyTemplateBean(getContainer(), getUser(), form.getTemplateName(), form.getDestination()), errors);
        }

        @Override
        public boolean handlePost(CopyForm form, BindException errors) throws Exception
        {
            return true;
        }

        @Override
        public ActionURL getSuccessURL(CopyForm copyForm)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Select Copy Destination");
        }
    }

    @RequiresAnyOf({InsertPermission.class, DesignAssayPermission.class})
    public class HandleCopyAction extends CopyTemplateAction
    {
        @Override
        public void validateCommand(CopyForm form, Errors errors)
        {
            Container destination = ContainerManager.getForPath(form.getDestination());
            if (destination == null || !destination.hasPermission(getUser(), InsertPermission.class))
                errors.reject("copyForm", "Destination container does not exist or permission is denied.");

            PlateTemplate destinationTemplate = PlateService.get().getPlateTemplate(destination, form.getTemplateName());

            if (destinationTemplate != null)
                errors.reject("copyForm", "A plate template with the same name already exists in the destination folder.");
        }

        @Override
        public boolean handlePost(CopyForm form, BindException errors) throws Exception
        {
            Container destination = ContainerManager.getForPath(form.getDestination());
            // earlier validation should prevent a null or inaccessible destination container:
            if (destination == null || !destination.hasPermission(getUser(), InsertPermission.class))
            {
                errors.reject("copyForm", "The destination is invalid or you do not have INSERT privileges on the specified container");
                return false;
            }
            // earlier validation should prevent a missing source template:
            PlateTemplate template = PlateService.get().getPlateTemplate(getContainer(), form.getTemplateName());
            if (template == null)
            {
                errors.reject("copyForm", "Plate " + form.getTemplateName() + " does not exist in source container.");
                return false;
            }

            // earlier validation should prevent an already-existing destination template:
            PlateTemplate destinationTemplate = PlateService.get().getPlateTemplate(destination, form.getTemplateName());
            if (destinationTemplate != null)
            {
                errors.reject("copyForm", "Plate " + form.getTemplateName() + " already exists in destination container.");
                return false;
            }
            PlateService.get().copyPlateTemplate(template, getUser(), destination);
            return true;
        }

        @Override
        public ActionURL getSuccessURL(CopyForm copyForm)
        {
            return new ActionURL(PlateTemplateListAction.class, getContainer());
        }
    }

    private String getUniqueName(Container container, String originalName)
    {
        Set<String> existing = new HashSet<>();
        for (PlateTemplate template : PlateService.get().getPlateTemplates(container))
            existing.add(template.getName());
        String baseUniqueName;
        if (!originalName.startsWith("Copy of "))
            baseUniqueName = "Copy of " + originalName;
        else
            baseUniqueName = originalName;
        String uniqueName = baseUniqueName;
        int idx = 2;
        while (existing.contains(uniqueName))
            uniqueName = baseUniqueName + " " + idx++;
        return uniqueName;
    }

    public static class NameForm
    {
        private String _templateName;
        private Integer _plateId;

        public String getTemplateName()
        {
            return _templateName;
        }

        public void setTemplateName(String templateName)
        {
            _templateName = templateName;
        }

        public Integer getPlateId()
        {
            return _plateId;
        }

        public void setPlateId(Integer plateId)
        {
            _plateId = plateId;
        }
    }

    public static class DesignerForm extends NameForm
    {
        private boolean _copy;
        private String _assayType;
        private String _templateType;
        private int _rowCount;
        private int _colCount;

        public String getAssayType()
        {
            return _assayType;
        }

        public void setAssayType(String assayType)
        {
            _assayType = assayType;
        }

        public String getTemplateType()
        {
            return _templateType;
        }

        public void setTemplateType(String templateType)
        {
            _templateType = templateType;
        }
        public boolean isCopy()
        {
            return _copy;
        }

        public void setCopy(boolean copy)
        {
            _copy = copy;
        }

        public int getRowCount()
        {
            return _rowCount;
        }

        public void setRowCount(int rowCount)
        {
            _rowCount = rowCount;
        }

        public int getColCount()
        {
            return _colCount;
        }

        public void setColCount(int colCount)
        {
            _colCount = colCount;
        }
    }

    public static class CopyForm
    {
        private String _destination;
        private String _templateName;

        public String getDestination()
        {
            return _destination;
        }

        public void setDestination(String destination)
        {
            _destination = destination;
        }

        public String getTemplateName()
        {
            return _templateName;
        }

        public void setTemplateName(String templateName)
        {
            _templateName = templateName;
        }
    }
}
