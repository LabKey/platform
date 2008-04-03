package org.labkey.study.controllers.plate;

import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.FormData;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.log4j.Logger;
import org.labkey.study.controllers.BaseController;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.view.*;
import org.labkey.api.study.*;
import org.labkey.api.gwt.client.util.ColorGenerator;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.util.ContainerTree;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.plate.PlateDataServiceImpl;
import org.labkey.study.plate.PlateManager;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.sql.SQLException;

/**
 * User: brittp
 * Date: Jan 29, 2007
 * Time: 3:53:57 PM
 */
@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class PlateController extends BaseController
{
    private static Logger _log = Logger.getLogger(PlateController.class);

    @Jpf.Action
    @RequiresPermission(ACL.PERM_READ)
    protected Forward begin() throws Exception
    {
        return new ViewForward(getActionURL().relativeUrl("plateTemplateList", null));
    }

    public static class PlateTemplateListForm extends FormData
    {
        private String _returnURL;

        public String getReturnURL()
        {
            return _returnURL;
        }

        public void setReturnURL(String returnURL)
        {
            _returnURL = returnURL;
        }
    }

    public static class PlateTemplateListBean
    {
        private PlateTemplate[] _templates;
        public PlateTemplateListBean(PlateTemplate[] templates)
        {
            _templates = templates;
        }

        public PlateTemplate[] getTemplates()
        {
            return _templates;
        }
    }

    @Jpf.Action
    @RequiresPermission(ACL.PERM_READ)
    protected Forward plateTemplateList(PlateTemplateListForm form) throws Exception
    {
        PlateTemplate[] plateTemplates = PlateService.get().getPlateTemplates(getContainer());
        JspView<PlateTemplateListBean> summaryView =
                new JspView<PlateTemplateListBean>("/org/labkey/study/plate/view/plateTemplateList.jsp", 
                        new PlateTemplateListBean(plateTemplates));
        return _renderInTemplate(summaryView, "Plate Templates", new NavTree("Plate Templates"));
    }

    @Jpf.Action
    @RequiresPermission(ACL.PERM_READ)
    protected Forward designerService() throws Exception
    {
        PlateDataServiceImpl service = new PlateDataServiceImpl(getViewContext());
        service.doPost(getRequest(), getResponse());
        return null;
    }

    public static class RowIdForm extends FormData
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

    @Jpf.Action
    @RequiresPermission(ACL.PERM_READ)
    protected Forward plateDetails(RowIdForm form) throws Exception
    {
        Plate plate = PlateService.get().getPlate(getContainer(), form.getRowId());
        if (plate == null)
            return HttpView.throwNotFound("Plate " + form.getRowId() + " does not exist.");
        ActionURL url = PlateManager.get().getDetailsURL(plate);
        if (url == null)
            return HttpView.throwNotFound("Details URL has not been configured for plate type " + plate.getName() + ".");
        return new ViewForward(url);
    }

    public class TemplateViewBean
    {
        private PlateTemplate _template;
        private ColorGenerator _colorGenerator;
        private WellGroup.Type _type;

        public TemplateViewBean(PlateTemplate template, WellGroup.Type type)
        {
            _template = template;
            _type = type;
            _colorGenerator = new ColorGenerator();
        }

        public ColorGenerator getColorGenerator()
        {
            return _colorGenerator;
        }

        public PlateTemplate getTemplate()
        {
            return _template;
        }

        public WellGroup.Type getType()
        {
            return _type;
        }
    }

    @Jpf.Action
    @RequiresPermission(ACL.PERM_INSERT)
    protected Forward designer(NameForm form) throws Exception
    {
        Map<String, String> properties = new HashMap<String, String>();
        if (form.getTemplateName() != null)
        {
            properties.put("copyTemplate", Boolean.toString(form.isCopy()));
            properties.put("templateName", form.getTemplateName());
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

        PlateTemplate[] templates = PlateService.get().getPlateTemplates(getContainer());
        for (int i = 0; i < templates.length; i++)
        {
            PlateTemplate template = templates[i];
            properties.put("templateName[" + i + "]", template.getName());
        }
        GWTView view = new GWTView("org.labkey.plate.designer.TemplateDesigner", properties);
        return _renderInTemplate(view, "Plate Template Editor");
    }

    @Jpf.Action
    @RequiresPermission(ACL.PERM_INSERT)
    protected Forward delete(NameForm form) throws Exception
    {
        PlateTemplate[] templates = PlateService.get().getPlateTemplates(getContainer());
        if (templates != null && templates.length > 1)
        {
            PlateTemplate template = PlateService.get().getPlateTemplate(getContainer(), form.getTemplateName());
            if (template != null)
                PlateService.get().deletePlate(getContainer(), template.getRowId());
        }

        return new ViewForward("Plate", "begin", getContainer());
    }

    public static class CopyTemplateBean
    {
        private String _treeHtml;
        private String _templateName;
        private String _selectedDestination;
        private PlateTemplate[] _destinationTemplates;

        public CopyTemplateBean(final Container container, final User user, final String templateName, final String selectedDestination)
        {
            _templateName = templateName;
            _selectedDestination = selectedDestination;
            ContainerTree tree = new ContainerTree("/", user, ACL.PERM_INSERT, null)
            {
                @Override
                protected void renderCellContents(StringBuilder html, Container c, ActionURL url)
                {
                    ActionURL copyURL = new ActionURL("Plate", "copyTemplate", container);
                    copyURL.addParameter("templateName", templateName);
                    copyURL.addParameter("destination", c.getPath());
                    boolean selected = c.getPath().equals(selectedDestination);
                    if (selected)
                        html.append("<span class=\"labkey-navtree-selected\">");
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
                    try
                    {
                        _destinationTemplates = PlateService.get().getPlateTemplates(dest);
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeSQLException(e);
                    }
                }
            }

            _treeHtml = tree.render().toString();
        }

        public String getSelectedDestination()
        {
            return _selectedDestination;
        }

        public String getTreeHtml()
        {
            return _treeHtml;
        }

        public String getTemplateName()
        {
            return _templateName;
        }

        public PlateTemplate[] getDestinationTemplates()
        {
            return _destinationTemplates;
        }
    }

    @Jpf.Action
    @RequiresPermission(ACL.PERM_INSERT)
    protected Forward copyTemplate(CopyForm form) throws Exception
    {
        if (form.getTemplateName() == null || form.getTemplateName().length() == 0)
            return new ViewForward("Plate", "begin", getContainer());
        
        JspView<CopyTemplateBean> summaryView =
                new JspView<CopyTemplateBean>("/org/labkey/study/plate/view/copyTemplate.jsp",
                        new CopyTemplateBean(getContainer(), getUser(), form.getTemplateName(), form.getDestination()));
        return _renderInTemplate(summaryView, "Select Copy Destination");
    }

    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "copyTemplate.do", name = "validate"))
    @RequiresPermission(ACL.PERM_INSERT)
    protected Forward handleCopy(CopyForm form) throws Exception
    {
        Container destination = ContainerManager.getForPath(form.getDestination());
        // earlier validation should prevent a null or inaccessible destination container:
        if (destination == null || !destination.hasPermission(getUser(), ACL.PERM_INSERT))
            return HttpView.throwNotFound();

        // earlier validation should prevent a missing source template:
        PlateTemplate template = PlateService.get().getPlateTemplate(getContainer(), form.getTemplateName());
        if (template == null)
            throw new RuntimeException("Plate " + form.getTemplateName() + " does not exist in source container.");

        // earlier validation should prevent an already-existing destination template:
        PlateTemplate destinationTemplate = PlateService.get().getPlateTemplate(destination, form.getTemplateName());
        if (destinationTemplate != null)
            throw new RuntimeException("Plate " + form.getTemplateName() + " already exists in destination container.");

        PlateService.get().copyPlateTemplate(template, getUser(), destination);
        return new ViewForward(new ActionURL("Plate", "plateTemplateList", destination));
    }


    private String getUniqueName(Container container, String originalName) throws SQLException
    {
        PlateTemplate[] templates = PlateService.get().getPlateTemplates(container);
        Set<String> existing = new HashSet<String>();
        for (PlateTemplate template : templates)
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

    public static class NameForm extends FormData
    {
        private String _templateName;
        private String _assayType;
        private String _templateType;
        private boolean _copy;

        public String getTemplateName()
        {
            return _templateName;
        }

        public void setTemplateName(String templateName)
        {
            _templateName = templateName;
        }

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
    }

    public static class CopyForm extends ViewForm
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

        @Override
        public ActionErrors validate(ActionMapping mapping, HttpServletRequest request)
        {
            ActionErrors errors = new ActionErrors();

            Container destination = ContainerManager.getForPath(getDestination());
            if (destination == null || !destination.hasPermission(getContext().getUser(), ACL.PERM_INSERT))
                errors.add("main", new ActionMessage("Error", "Destination container does not exist or permission is denied."));

            PlateTemplate destinationTemplate = null;
            try
            {
                destinationTemplate = PlateService.get().getPlateTemplate(destination, getTemplateName());
            }
            catch (SQLException e)
            {
                _log.error("Failure checking for template in destination container", e);
                errors.add("main", new ActionMessage("Error", "Unable to validate destination directory: " + e.getMessage()));
            }

            if (destinationTemplate != null)
                errors.add("main", new ActionMessage("Error", "A plate template with the same name already exists in the destination folder."));

            return errors;
        }
    }
}
