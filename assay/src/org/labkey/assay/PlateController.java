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

import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiJsonForm;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.JsonInputLimit;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateCustomField;
import org.labkey.api.assay.plate.PlateLayoutHandler;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresAnyOf;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.ContainerTree;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataViewSnapshotSelectionForm;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.ZipFile;
import org.labkey.assay.plate.PlateDataServiceImpl;
import org.labkey.assay.plate.PlateImpl;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.PlateSetExport;
import org.labkey.assay.plate.PlateUrls;
import org.labkey.assay.plate.TsvPlateLayoutHandler;
import org.labkey.assay.plate.WellGroupImpl;
import org.labkey.assay.plate.model.CreatePlateSetOptions;
import org.labkey.assay.plate.model.ReformatOptions;
import org.labkey.assay.view.AssayGWTView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Marshal(Marshaller.Jackson)
public class PlateController extends SpringActionController
{
    private static final SpringActionController.DefaultActionResolver _actionResolver = new DefaultActionResolver(PlateController.class);
    private static final Logger LOG = LogHelper.getLogger(PlateController.class, "Controller for plate related actions");

    public record SubmittedGroup(int rowId, String type, String name, List<PlatePosition> positions, Map<String, Object> properties)
    {
        public static SubmittedGroup from(JSONObject g)
        {
            int rowId = g.optInt("rowId", -1);
            String type = g.getString("type");
            String name = g.getString("name");
            JSONArray posArr = g.optJSONArray("positions");
            List<PlatePosition> positions = new ArrayList<>();
            if (posArr != null)
            {
                for (int j = 0; j < posArr.length(); j++)
                {
                    JSONObject p = posArr.getJSONObject(j);
                    positions.add(PlatePosition.from(p));
                }
            }
            JSONObject propsObj = g.optJSONObject("properties");
            Map<String, Object> props = new HashMap<>();
            if (propsObj != null)
            {
                for (String key : propsObj.keySet())
                {
                    Object val = propsObj.get(key);
                    props.put(key, val == JSONObject.NULL ? null : val);
                }
            }
            return new SubmittedGroup(rowId, type, name, positions, props);
        }
    }

    record PlatePosition(int row, int col)
    {
        public static PlatePosition from(JSONObject p)
        {
            return new PlatePosition(p.getInt("row"), p.getInt("col"));
        }
    }

    public PlateController()
    {
        setActionResolver(_actionResolver);
    }

    public static class PlateUrlsImpl implements PlateUrls
    {
        @Override
        public ActionURL getPlateListURL(Container c)
        {
            return new ActionURL(PlateListAction.class, c);
        }

        @Override
        public ActionURL getPlateDetailsURL(Container c)
        {
            return new ActionURL(PlateDetailsAction.class, c);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class BeginAction extends SimpleRedirectAction<Object>
    {
        @Override
        public ActionURL getRedirectURL(Object o)
        {
            return new ActionURL(PlateListAction.class, getContainer());
        }
    }

    public static class PlateTemplateListBean
    {
        private final List<? extends Plate> _templates;

        public PlateTemplateListBean(List<? extends Plate> templates)
        {
            _templates = templates;
        }

        public List<? extends Plate> getTemplates()
        {
            return _templates;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @ActionNames("plateList, plateTemplateList")
    public static class PlateListAction extends SimpleViewAction<ReturnUrlForm>
    {
        @Override
        public ModelAndView getView(ReturnUrlForm form, BindException errors)
        {
            setHelpTopic("editPlateTemplate");
            List<? extends Plate> plateTemplates = PlateService.get().getPlates(getContainer())
                    .stream()
                    .filter(p -> !TsvPlateLayoutHandler.TYPE.equalsIgnoreCase(p.getAssayType()))
                    .toList();
            return new JspView<>("/org/labkey/assay/plate/view/plateList.jsp",
                    new PlateTemplateListBean(plateTemplates));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Plates");
        }           
    }

    /** Delete soon! */
    @RequiresAnyOf({InsertPermission.class, DesignAssayPermission.class})
    public static class DesignerServiceAction extends GWTServiceAction
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

    @Marshal(Marshaller.JSONObject)
    @RequiresAnyOf({InsertPermission.class, DesignAssayPermission.class})
    @JsonInputLimit(10 * 1024 * 1024)
    public static class SaveDesignerTemplateAction extends MutatingApiAction<CreatePlateForm>
    {
        private PlateType _plateType;

        @Override
        public Object execute(CreatePlateForm form, BindException errors) throws Exception
        {
            Long rowId = form.getRowId();
            String name = form.getName();
            boolean updateExisting = false;
            PlateImpl plate;

            if (rowId != null && rowId > 0)
            {
                plate = PlateManager.get().getPlate(getContainer(), rowId);
                if (plate == null)
                    throw new NotFoundException("Plate template not found: " + rowId);
                Plate conflict = PlateManager.get().getPlateByName(getContainer(), name);
                if (conflict != null && !conflict.getRowId().equals(plate.getRowId()))
                    throw new ValidationException("A plate template with name '" + name + "' already exists.");
                if (!plate.getAssayType().equals(form.getAssayType()))
                    throw new ValidationException("Plate template type '" + plate.getAssayType() + "' cannot be changed for '" + name + "'");
                if (plate.getRows() != form.getRows() || plate.getColumns() != form.getCols())
                    throw new ValidationException("Plate template dimensions cannot be changed for '" + name + "'");
                updateExisting = true;
            }
            else
            {
                if (PlateManager.get().getPlateByName(getContainer(), name) != null)
                    throw new ValidationException("A plate template with name '" + name + "' already exists.");
                plate = PlateManager.get().createPlate(getContainer(), form.getAssayType(), _plateType);
                plate.setTemplate(true);
            }

            plate.setName(name);
            plate.setProperties(form.getPlateProperties());

            List<SubmittedGroup> submittedGroups = form.getGroups();
            Set<Integer> submittedGroupIds = new HashSet<>();
            for (SubmittedGroup g : submittedGroups)
                if (g.rowId() > 0)
                    submittedGroupIds.add(g.rowId());

            // Mark well groups absent from the submission for deletion
            List<WellGroupImpl> existingWellGroups = plate.getWellGroups();
            for (WellGroup existingGroup : existingWellGroups)
                if (existingGroup.getRowId() != null && !submittedGroupIds.contains(existingGroup.getRowId()))
                    plate.markWellGroupForDeletion(existingGroup);

            // Update existing or create new well groups
            for (SubmittedGroup gm : submittedGroups)
            {
                WellGroup.Type groupType;
                try
                {
                    groupType = WellGroup.Type.valueOf(gm.type());
                }
                catch (IllegalArgumentException e)
                {
                    throw new ValidationException("Unknown well group type: '" + gm.type() + "'");
                }

                List<Position> positions = new ArrayList<>();
                for (PlatePosition p : gm.positions())
                    positions.add(plate.getPosition(p.row(), p.col()));

                WellGroupImpl group;
                if (updateExisting && gm.rowId() > 0)
                {
                    group = findExistingWellGroup(existingWellGroups, gm.rowId());
                    if (group == null)
                        throw new ValidationException("Well group " + gm.rowId() + " was not found.");
                    if (group.getType() != groupType)
                        throw new ValidationException("Well group type cannot be changed: " + gm.name());
                    group.setName(gm.name());
                    group.setPositions(positions);
                    plate.storeWellGroup(group);
                }
                else
                {
                    group = plate.addWellGroup(gm.name(), groupType, positions);
                }
                group.setProperties(gm.properties());
            }

            PlateLayoutHandler handler = PlateManager.get().getPlateLayoutHandler(plate.getAssayType());
            if (handler == null)
                throw new NotFoundException("Invalid assay type");
            handler.validatePlate(getContainer(), getUser(), plate);
            long savedRowId = PlateService.get().save(getContainer(), getUser(), plate);
            return success(Map.of("rowId", savedRowId));
        }

        private WellGroupImpl findExistingWellGroup(List<WellGroupImpl> wellGroups, int rowId)
        {
            for (WellGroupImpl wg : wellGroups)
                if (wg.getRowId() != null && wg.getRowId() == rowId)
                    return wg;
            return null;
        }

        @Override
        public void validateForm(CreatePlateForm form, Errors errors)
        {
            if (form.getGroups() == null || form.getGroups().isEmpty())
            {
                errors.reject(ERROR_REQUIRED, "At least one group is required.");
            }
            // Template designer (groups) path: plateType resolved by rowId lookup on update,
            // or by rows×cols on create.
            if (form.getRowId() == null || form.getRowId() <= 0)
            {
                _plateType = form.getPlateType() != null
                        ? PlateManager.get().getPlateType(form.getPlateType())
                        : PlateService.get().getPlateType(form.getRows(), form.getCols());
                if (_plateType == null)
                    errors.reject(ERROR_REQUIRED, "The plate type (" + form.getRows() + " x " + form.getCols() + ") does not exist.");
            }
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class PlateDetailsAction extends SimpleRedirectAction<RowIdForm>
    {
        @Override
        public ActionURL getRedirectURL(RowIdForm form)
        {
            Plate plate = PlateManager.get().getPlate(getContainer(), form.getRowId());
            if (plate == null)
                throw new NotFoundException("Plate " + form.getRowId() + " does not exist.");
            ActionURL url = PlateManager.get().getDetailsURL(plate);
            if (url == null)
                throw new NotFoundException("Details URL has not been configured for plate type " + plate.getName() + ".");
            return url;
        }
    }

    @RequiresAnyOf({InsertPermission.class, DesignAssayPermission.class})
    public class GetDesignerTemplateDefinitionAction extends ReadOnlyApiAction<DesignerForm>
    {
        @Override
        public Object execute(DesignerForm form, BindException errors) throws Exception
        {
            String templateName = form.getTemplateName();
            Long plateId = form.getPlateId();
            boolean copyTemplate = form.isCopy();

            if (templateName == null && plateId != null)
            {
                Plate plate = PlateManager.get().getPlate(getContainer(), plateId);
                if (plate != null)
                {
                    templateName = plate.getName();
                }
            }

            Plate template;
            PlateLayoutHandler handler;

            if (templateName != null)
            {
                if (plateId == null)
                    throw new NotFoundException("plateId is required when templateName is specified.");
                template = PlateService.get().getPlate(getContainer(), plateId);
                if (template == null)
                    throw new NotFoundException("Plate referenced by plateId does not exist.");
                handler = PlateManager.get().getPlateLayoutHandler(template.getAssayType());
                if (handler == null)
                    throw new ValidationException("Plate template type '" + template.getAssayType() + "' does not exist.");
            }
            else
            {
                String assayTypeName = form.getAssayType();
                String templateTypeName = form.getTemplateType();
                int rowCount = form.getRowCount();
                int colCount = form.getColCount();

                handler = PlateManager.get().getPlateLayoutHandler(assayTypeName);
                if (handler == null)
                    throw new ValidationException("Plate template type '" + assayTypeName + "' does not exist.");
                PlateType plateType = PlateService.get().getPlateType(rowCount, colCount);
                if (plateType == null)
                    throw new ValidationException("The plate type (" + rowCount + " x " + colCount + ") does not exist.");
                template = handler.createPlate(templateTypeName, getContainer(), plateType);
            }

            // Build groups list
            List<? extends WellGroup> groups = template.getWellGroups();
            List<Map<String, Object>> groupList = new ArrayList<>();
            for (int i = 0; i < groups.size(); i++)
            {
                WellGroup group = groups.get(i);
                List<Map<String, Object>> positions = new ArrayList<>();
                for (Position position : group.getPositions())
                {
                    Map<String, Object> pos = new HashMap<>();
                    pos.put("row", position.getRow());
                    pos.put("col", position.getColumn());
                    positions.add(pos);
                }
                Map<String, Object> groupProps = new HashMap<>();
                for (String propName : group.getPropertyNames())
                {
                    Object propValue = group.getProperty(propName);
                    groupProps.put(propName, (propValue == null || propValue == JSONObject.NULL) ? null : propValue.toString());
                }

                int wellGroupId = copyTemplate || group.getRowId() == null ? -1 * (i + 1) : group.getRowId();
                Map<String, Object> g = new HashMap<>();
                g.put("rowId", wellGroupId);
                g.put("type", group.getType().name());
                g.put("name", group.getName());
                g.put("positions", positions);
                g.put("properties", groupProps);
                g.put("allowNewGroups", handler.canCreateNewGroups(group.getType()));
                groupList.add(g);
            }

            Map<String, Object> templateProperties = new HashMap<>();
            for (String propName : template.getPropertyNames())
                templateProperties.put(propName, template.getProperty(propName) == null ? null : template.getProperty(propName).toString());

            // Build type list
            List<String> typeList = new ArrayList<>();
            for (WellGroup.Type type : handler.getWellGroupTypes())
                typeList.add(type.name());

            // Build canCreateGroupsByType
            Map<String, Boolean> canCreateGroupsByType = new LinkedHashMap<>();
            for (WellGroup.Type type : handler.getWellGroupTypes())
                canCreateGroupsByType.put(type.name(), handler.canCreateNewGroups(type));

            // Build typesToDefaultGroups
            Map<String, List<String>> typesToDefaultGroups = handler.getDefaultGroupsForTypes();

            // Existing template names in container
            List<String> existingTemplateNames = new ArrayList<>();
            for (Plate p : PlateService.get().getPlates(getContainer()))
                existingTemplateNames.add(p.getName());

            long responseRowId = copyTemplate || template.getRowId() == null ? -1 : template.getRowId();
            String defaultPlateName;
            if (templateName != null)
            {
                defaultPlateName = copyTemplate ? getUniqueName(getContainer(), templateName) : templateName;
            }
            else
            {
                defaultPlateName = "";
            }

            Map<String, Object> result = new HashMap<>();
            result.put("rowId", responseRowId);
            result.put("name", template.getName());
            result.put("type", template.getAssayType());
            result.put("rows", template.getRows());
            result.put("cols", template.getColumns());
            result.put("groupTypes", typeList);
            result.put("canCreateGroupsByType", canCreateGroupsByType);
            result.put("groups", groupList);
            result.put("plateProperties", templateProperties);
            result.put("typesToDefaultGroups", typesToDefaultGroups);
            result.put("showWarningPanel", handler.showEditorWarningPanel());
            result.put("existingTemplateNames", existingTemplateNames);
            result.put("copyMode", copyTemplate);
            result.put("defaultPlateName", defaultPlateName);

            return success(result);
        }
    }

    @RequiresAnyOf({InsertPermission.class, DesignAssayPermission.class})
    public static class DesignerAction extends SimpleViewAction<DesignerForm>
    {
        @Override
        public ModelAndView getView(DesignerForm form, BindException errors)
        {
            return ModuleHtmlView.get(
                ModuleLoader.getInstance().getModule("assay"),
                ModuleHtmlView.getGeneratedViewPath("plateTemplateDesigner")
            );
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("editPlateTemplate");
            root.addChild("Plate Editor");
        }
    }

    /** Delete soon! */
    @RequiresAnyOf({InsertPermission.class, DesignAssayPermission.class})
    public class DesignerGwtAction extends SimpleViewAction<DesignerForm>
    {
        @Override
        public ModelAndView getView(DesignerForm form, BindException errors)
        {
            Map<String, String> properties = new HashMap<>();
            String templateName = null;
            Long plateId = null;

            if (form.getTemplateName() != null)
            {
                plateId = form.getPlateId();
                templateName = form.getTemplateName();
            }
            else if (form.getPlateId() != null)
            {
                Plate plate = PlateManager.get().getPlate(getContainer(), form.getPlateId());
                if (plate != null)
                {
                    plateId = plate.getRowId();
                    templateName = plate.getName();
                }
            }

            if (templateName != null)
            {
                properties.put("copyTemplate", Boolean.toString(form.isCopy()));
                properties.put("templateName", templateName);
                if (plateId != null)
                    properties.put("plateId", String.valueOf(plateId));
                if (form.isCopy())
                    properties.put("defaultPlateName", getUniqueName(getContainer(), templateName));
                else
                    properties.put("defaultPlateName", templateName);
            }

            if (form.getAssayType() != null)
                properties.put("assayTypeName", form.getAssayType());
            if (form.getTemplateType() != null)
                properties.put("templateTypeName", form.getTemplateType());

            properties.put("templateRowCount", String.valueOf(form.getRowCount()));
            properties.put("templateColumnCount", String.valueOf(form.getColCount()));

            return new AssayGWTView(gwt.client.org.labkey.plate.designer.client.TemplateDesigner.class, properties);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("editPlateTemplate");
            root.addChild("Plate Editor (GWT)");
        }
    }

    @RequiresAnyOf({DeletePermission.class, DesignAssayPermission.class})
    public static class DeleteAction extends FormHandlerAction<NameForm>
    {
        @Override
        public void validateCommand(NameForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(NameForm form, BindException errors) throws Exception
        {
            Plate plate = PlateService.get().getPlate(getContainer(), form.getPlateId());
            if (plate != null && plate.getRowId() != null)
                PlateService.get().deletePlate(getContainer(), getUser(), plate.getRowId());
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
        private HtmlString _treeHtml;
        private Plate _plate;
        private String _selectedDestination;
        private List<? extends Plate> _destinationTemplates;

        public CopyTemplateBean(final Container container, final User user, final Integer plateId, final String selectedDestination)
        {
            if (plateId != null)
            {
                _plate = PlateService.get().getPlate(container, plateId);
                if (_plate != null)
                {
                    _selectedDestination = selectedDestination;

                    //Copy and Add to another folder requires InsertPermissions
                    ContainerTree tree = new ContainerTree("/", user, InsertPermission.class, null)
                    {
                        @Override
                        protected void renderCellContents(StringBuilder html, Container c, ActionURL url)
                        {
                            ActionURL copyURL = new ActionURL(CopyTemplateAction.class, container);
                            copyURL.addParameter("plateId", _plate.getRowId());
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
                            _destinationTemplates = PlateService.get().getPlates(dest);
                        }
                    }

                    _treeHtml = tree.getHtmlString();
                }
            }
        }

        public String getSelectedDestination()
        {
            return _selectedDestination;
        }

        public HtmlString getTreeHtml()
        {
            return _treeHtml;
        }

        public Plate getPlate()
        {
            return _plate;
        }

        public List<? extends Plate> getDestinationTemplates()
        {
            return _destinationTemplates;
        }
    }

    @RequiresAnyOf({InsertPermission.class, DesignAssayPermission.class})
    public static class CopyTemplateAction extends FormViewAction<CopyForm>
    {
        @Override
        public void validateCommand(CopyForm form, Errors errors)
        {
            if (form.getPlateId() == null)
                errors.reject(ERROR_REQUIRED, "Plate ID must not be blank");
        }

        @Override
        public ModelAndView getView(CopyForm form, boolean reshow, BindException errors)
        {
            CopyTemplateBean bean = new CopyTemplateBean(getContainer(), getUser(), form.getPlateId(), form.getDestination());
            if (bean.getPlate() != null)
                return new JspView<>("/org/labkey/assay/plate/view/copyTemplate.jsp", bean, errors);
            else
                return HtmlView.err("Source Plate does not exist.");
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
    public static class HandleCopyAction extends CopyTemplateAction
    {
        private Plate _plate;
        private Container _destination;

        @Override
        public void validateCommand(CopyForm form, Errors errors)
        {
            _destination = ContainerManager.getForPath(form.getDestination());
            if (_destination == null || !_destination.hasPermission(getUser(), InsertPermission.class))
                errors.reject("copyForm", "Destination container does not exist or permission is denied.");

            _plate = PlateService.get().getPlate(getContainer(), form.getPlateId());
            if (_plate == null)
                errors.reject(ERROR_REQUIRED, "Unable to retrieve source plate with ID : " + form.getPlateId());

            if (PlateManager.get().isDuplicatePlateName(_destination, getUser(), _plate.getName(), null))
                errors.reject("copyForm", "A plate template with the same name already exists in the destination folder.");
        }

        @Override
        public boolean handlePost(CopyForm form, BindException errors) throws Exception
        {
            PlateManager.get().copyPlateDeprecated(_plate, getUser(), _destination);
            return true;
        }

        @Override
        public ActionURL getSuccessURL(CopyForm copyForm)
        {
            return new ActionURL(PlateListAction.class, getContainer());
        }
    }

    private String getUniqueName(Container container, String originalName)
    {
        Set<String> existing = new HashSet<>();
        for (Plate plate : PlateService.get().getPlates(container))
            existing.add(plate.getName());
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
        private Long _plateId;

        public String getTemplateName()
        {
            return _templateName;
        }

        public void setTemplateName(String templateName)
        {
            _templateName = templateName;
        }

        public Long getPlateId()
        {
            return _plateId;
        }

        public void setPlateId(Long plateId)
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
        private Integer _plateId;

        public String getDestination()
        {
            return _destination;
        }

        public void setDestination(String destination)
        {
            _destination = destination;
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

    public static class CreatePlateForm implements ApiJsonForm
    {
        private String _assayType = TsvPlateLayoutHandler.TYPE;
        private List<Map<String, Object>> _data;
        private String _description;
        private String _name;
        private String _barcode;
        private Long _plateSetId;
        private Long _plateType;
        private boolean _template;
        private Long _templateId;

        // Template designer fields (groups path)
        private Long _rowId;
        private int _rows;
        private int _cols;
        private List<SubmittedGroup> _groups;       // non-null activates the template-upsert path
        private final Map<String, Object> _plateProperties = new HashMap<>();

        public String getDescription()
        {
            return _description;
        }

        public String getName()
        {
            return _name;
        }

        public String getBarcode()
        {
            return _barcode;
        }

        public Long getPlateSetId()
        {
            return _plateSetId;
        }

        public Long getPlateType()
        {
            return _plateType;
        }

        public List<Map<String, Object>> getData()
        {
            return _data;
        }

        public String getAssayType()
        {
            return _assayType;
        }

        public Boolean isTemplate()
        {
            return _template;
        }

        public Long getTemplateId()
        {
            return _templateId;
        }

        public Long getRowId()
        {
            return _rowId;
        }

        public int getRows()
        {
            return _rows;
        }

        public int getCols()
        {
            return _cols;
        }

        public List<SubmittedGroup> getGroups()
        {
            return _groups;
        }

        public Map<String, Object> getPlateProperties()
        {
            return _plateProperties;
        }

        @Override
        public void bindJson(JSONObject json)
        {
            if (json.has("name"))
                _name = json.getString("name");

            if (json.has("barcode"))
                _barcode = json.getString("barcode");

            if (json.has("plateSetId"))
                _plateSetId = json.getLong("plateSetId");

            if (json.has("plateType"))
                _plateType = json.getLong("plateType");

            if (json.has("assayType"))
                _assayType = json.getString("assayType");

            if (json.has("description"))
                _description = json.getString("description");

            if (json.has("template"))
                _template = json.getBoolean("template");

            if (json.has("templateId"))
                _templateId = json.getLong("templateId");

            // Template designer fields
            if (json.has("rowId"))
                _rowId = json.getLong("rowId");
            // "type" is how the React designer sends assayType
            if (json.has("type") && !json.has("assayType"))
                _assayType = json.getString("type");
            if (json.has("rows"))
                _rows = json.getInt("rows");
            if (json.has("cols"))
                _cols = json.getInt("cols");
            if (json.has("plateProperties"))
            {
                JSONObject props = json.getJSONObject("plateProperties");
                for (String key : props.keySet())
                {
                    Object val = props.get(key);
                    _plateProperties.put(key, val == JSONObject.NULL ? null : val);
                }
            }
            if (json.has("groups"))
            {
                _groups = new ArrayList<>();
                JSONArray arr = json.getJSONArray("groups");
                for (int i = 0; i < arr.length(); i++)
                    _groups.add(SubmittedGroup.from(arr.getJSONObject(i)));
            }

            if (json.has("data"))
            {
                _data = new ArrayList<>();
                RowMapFactory<Object> factory = new RowMapFactory<>();
                JSONArray data = json.getJSONArray("data");
                for (int i = 0; i < data.length(); i++)
                {
                    JSONObject jsonObj = data.getJSONObject(i);
                    if (jsonObj != null)
                    {
                        Map<String, Object> rowMap = factory.getRowMap();
                        JsonUtil.fillMapShallow(jsonObj, rowMap);
                        _data.add(rowMap);
                    }
                }
            }
        }
    }

    @Marshal(Marshaller.JSONObject)
    @RequiresAnyOf({InsertPermission.class, DesignAssayPermission.class})
    @JsonInputLimit(10 * 1024 * 1024)
    public static class CreatePlateAction extends MutatingApiAction<CreatePlateForm>
    {
        private PlateType _plateType;

        @Override
        public void validateForm(CreatePlateForm form, Errors errors)
        {
            if (form.getPlateType() == null)
                errors.reject(ERROR_REQUIRED, "Plate \"plateType\" is required.");
            if (form.getGroups() != null)
                errors.reject(ERROR_REQUIRED, "Group values are not supported.");

            _plateType = PlateManager.get().getPlateType(form.getPlateType());
            if (_plateType == null)
                errors.reject(ERROR_REQUIRED, String.format("Plate type id (%d) is invalid.", form.getPlateType()));

            if (form.getData() != null && form.getTemplateId() != null)
                errors.reject(ERROR_GENERIC, "Either \"data\" or a \"templateId\" can be specified but not both.");


            // If the user is specifying a plate set ID then we want to ensure they're not trying to add a plate
            // from a template to a primary plate set.
            if (form.getPlateSetId() != null)
            {
                PlateSet plateSet = PlateManager.get().getPlateSet(getContainer(), form.getPlateSetId());

                if (plateSet == null)
                {
                    errors.reject(ERROR_GENERIC, String.format("PlateSet with id \"%d\" not found.", form.getPlateSetId()));
                }

                if (plateSet != null && plateSet.isPrimary() && form.getTemplateId() != null)
                {
                    errors.reject(ERROR_GENERIC, "Cannot add a plate from a template to a primary plate set.");
                }
            }
        }

        @Override
        public Object execute(CreatePlateForm form, BindException errors) throws Exception
        {
            try
            {
                PlateImpl newPlate = new PlateImpl(getContainer(), form.getName(), form.getBarcode(), form.getAssayType(), _plateType);
                if (form.getData() == null && form.getTemplateId() != null && TsvPlateLayoutHandler.TYPE.equalsIgnoreCase(newPlate.getAssayType()))
                {
                    newPlate = PlateManager.get().copyPlate(
                        getContainer(),
                        getUser(),
                        form.getTemplateId(),
                        form.isTemplate(),
                        form.getPlateSetId(),
                        form.getName(),
                        form.getDescription(),
                        false
                    );
                }
                else
                {
                    if (form.isTemplate() != null)
                        newPlate.setTemplate(form.isTemplate());
                    newPlate.setDescription(form.getDescription());

                    List<Map<String, Object>> data = form.getData();
                    if (form.isTemplate() && data == null)
                        data = PlateManager.get().prepareEmptyPlateTemplateData(getContainer(), _plateType);

                    newPlate = PlateManager.get().createAndSavePlate(getContainer(), getUser(), newPlate, form.getPlateSetId(), data);
                }

                return success(newPlate);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_GENERIC, e.getMessage() != null ? e.getMessage() : "Failed to create plate. An error has occurred.");
            }

            return null;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetPlateOperationConfirmationDataAction extends ReadOnlyApiAction<DataViewSnapshotSelectionForm>
    {
        @Override
        public void validateForm(DataViewSnapshotSelectionForm form, Errors errors)
        {
            if (form.getDataRegionSelectionKey() == null && form.getRowIds() == null)
                errors.reject(ERROR_REQUIRED, "You must provide either a set of rowIds or a dataRegionSelectionKey");
        }

        @Override
        public Object execute(DataViewSnapshotSelectionForm form, BindException errors) throws Exception
        {
            var results = PlateManager.get().getPlateOperationConfirmationData(getContainer(), getUser(), form.getIds(false));
            return success(results);
        }
    }

    public static class CreatePlateMetadataFieldsForm
    {
        private List<GWTPropertyDescriptor> _fields;

        public List<GWTPropertyDescriptor> getFields()
        {
            return _fields;
        }

        public void setFields(List<GWTPropertyDescriptor> fields)
        {
            _fields = fields;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class CreatePlateMetadataFields extends MutatingApiAction<CreatePlateMetadataFieldsForm>
    {
        @Override
        public void validateForm(CreatePlateMetadataFieldsForm form, Errors errors)
        {
            if (form.getFields().isEmpty())
                errors.reject(ERROR_REQUIRED, "No metadata fields were specified.");
        }

        @Override
        public Object execute(CreatePlateMetadataFieldsForm form, BindException errors) throws Exception
        {
            List<PlateCustomField> newFields = PlateManager.get().createPlateMetadataFields(getContainer(), getUser(), form.getFields());
            return success(newFields);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetPlateMetadataFields extends ReadOnlyApiAction<Object>
    {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            return success(PlateManager.get().getPlateMetadataFields(getContainer(), getUser()));
        }
    }

    public static class DeletePlateMetadataFieldsForm
    {
        private List<PlateCustomField> _fields;

        public List<PlateCustomField> getFields()
        {
            return _fields;
        }

        public void setFields(List<PlateCustomField> fields)
        {
            _fields = fields;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class DeletePlateMetadataFields extends MutatingApiAction<DeletePlateMetadataFieldsForm>
    {
        @Override
        public void validateForm(DeletePlateMetadataFieldsForm form, Errors errors)
        {
            if (form.getFields().isEmpty())
                errors.reject(ERROR_REQUIRED, "No metadata fields were specified to be deleted.");
        }

        @Override
        public Object execute(DeletePlateMetadataFieldsForm form, BindException errors) throws Exception
        {
            return success(PlateManager.get().deletePlateMetadataFields(getContainer(), getUser(), form.getFields()));
        }
    }

    public static class CustomFieldsForm extends DeletePlateMetadataFieldsForm
    {
        private Long _plateId;

        public Long getPlateId()
        {
            return _plateId;
        }

        public void setPlateId(Long plateId)
        {
            _plateId = plateId;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class AddFieldsAction extends MutatingApiAction<CustomFieldsForm>
    {
        @Override
        public Object execute(CustomFieldsForm form, BindException errors) throws Exception
        {
            return success(PlateManager.get().addFields(getContainer(), getUser(), form.getPlateId(), form.getFields()));
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetFieldsAction extends ReadOnlyApiAction<CustomFieldsForm>
    {
        @Override
        public Object execute(CustomFieldsForm form, BindException errors) throws Exception
        {
            return success(PlateManager.get().getFields(getContainer(), form.getPlateId()));
        }
    }

    @RequiresPermission(DeletePermission.class)
    public static class RemoveFieldsAction extends MutatingApiAction<CustomFieldsForm>
    {
        @Override
        public Object execute(CustomFieldsForm form, BindException errors) throws Exception
        {
            return success(PlateManager.get().removeFields(getContainer(), getUser(), form.getPlateId(), form.getFields()));
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class SetFieldsAction extends MutatingApiAction<CustomFieldsForm>
    {
        @Override
        public Object execute(CustomFieldsForm form, BindException errors) throws Exception
        {
            return success(PlateManager.get().setFields(getContainer(), getUser(), form.getPlateId(), form.getFields()));
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class EnsurePlateMetadataDomainAction extends MutatingApiAction<Object>
    {
        @Override
        public Object execute(Object form, BindException errors) throws Exception
        {
            return success(PlateManager.get().ensurePlateMetadataDomain(getContainer(), getUser(), false).getTypeId());
        }
    }

    public static class GetPlateForm
    {
        private Integer _rowId;
        private ContainerFilter.Type _containerFilter;
        private Boolean _includeRunCount;

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

        public ContainerFilter.Type getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(ContainerFilter.Type containerFilter)
        {
            _containerFilter = containerFilter;
        }

        public Boolean getIncludeRunCount()
        {
            return _includeRunCount;
        }

        public void setIncludeRunCount(Boolean includeRunCount)
        {
            _includeRunCount = includeRunCount;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetPlateAction extends ReadOnlyApiAction<GetPlateForm>
    {
        @Override
        public void validateForm(GetPlateForm form, Errors errors)
        {
            if (form.getRowId() == null)
                errors.reject(ERROR_REQUIRED, "Plate \"rowId\" is required.");
        }

        @Override
        public Object execute(GetPlateForm form, BindException errors) throws Exception
        {
            ContainerFilter cf = ContainerFilter.Type.Current.create(getViewContext());

            // if an optional container filter is specified
            if (form.getContainerFilter() != null)
                cf = form.getContainerFilter().create(getViewContext());

            Plate plate = PlateManager.get().getPlate(cf, form.getRowId());

            if (plate != null && Boolean.TRUE.equals(form.getIncludeRunCount()))
                ((PlateImpl) plate).setRunCount(PlateManager.get().getRunCountUsingPlate(plate.getContainer(), getUser(), plate));

            return plate;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public static class CreatePlateSetAction extends MutatingApiAction<CreatePlateSetOptions>
    {
        @Override
        public Object execute(CreatePlateSetOptions options, BindException errors) throws Exception
        {
            try
            {
                PlateSet plateSet = PlateManager.get().createOrAddToPlateSet(getContainer(), getUser(), options);
                return success(plateSet);
            }
            catch (Exception e)
            {
                String message = "Failed to create plate set.";
                if (e.getMessage() != null)
                    message += " " + e.getMessage();
                errors.reject(ERROR_GENERIC, message);
            }

            return null;
        }
    }

    public static class ArchiveForm
    {
        private List<Long> _plateIds;
        private List<Long> _plateSetIds;
        private boolean _restore;

        public List<Long> getPlateIds()
        {
            return _plateIds;
        }

        public void setPlateIds(List<Long> plateIds)
        {
            _plateIds = plateIds;
        }

        public List<Long> getPlateSetIds()
        {
            return _plateSetIds;
        }

        public void setPlateSetIds(List<Long> plateSetIds)
        {
            _plateSetIds = plateSetIds;
        }

        public boolean isRestore()
        {
            return _restore;
        }

        public void setRestore(boolean restore)
        {
            _restore = restore;
        }
    }

    @Marshal(Marshaller.JSONObject)
    @RequiresPermission(UpdatePermission.class)
    public static class ArchiveAction extends MutatingApiAction<ArchiveForm>
    {
        @Override
        public void validateForm(ArchiveForm form, Errors errors)
        {
            if (form.getPlateIds() == null && form.getPlateSetIds() == null)
                errors.reject(ERROR_GENERIC, "Either \"plateIds\" or \"plateSetIds\" must be specified.");
        }

        @Override
        public Object execute(ArchiveForm form, BindException errors) throws Exception
        {
            try
            {
                PlateManager.get().archive(getContainer(), getUser(), form.getPlateSetIds(), form.getPlateIds(), !form.isRestore());
                return success();
            }
            catch (Exception e)
            {
                String action = form.isRestore() ? "restore" : "archive";
                errors.reject(ERROR_GENERIC, e.getMessage() != null ? e.getMessage() : "Failed to " + action + " plate sets. An error has occurred.");
            }

            return null;
        }
    }

    public static class PlateSetLineageForm
    {
        private ContainerFilter.Type _containerFilter;
        private int _seed;

        public ContainerFilter.Type getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(ContainerFilter.Type containerFilter)
        {
            _containerFilter = containerFilter;
        }

        public int getSeed()
        {
            return _seed;
        }

        public void setSeed(int seed)
        {
            _seed = seed;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class LineageAction extends ReadOnlyApiAction<PlateSetLineageForm>
    {
        @Override
        public void validateForm(PlateSetLineageForm form, Errors errors)
        {
            if (form.getSeed() <= 0)
                errors.reject(ERROR_REQUIRED, "Specifying a \"seed\" Plate Set rowId is required.");
        }

        @Override
        public Object execute(PlateSetLineageForm form, BindException errors) throws Exception
        {
            ContainerFilter cf = null;

            // if an optional container filter is specified
            if (form.getContainerFilter() != null)
                cf = form.getContainerFilter().create(getViewContext());

            return PlateManager.get().getPlateSetLineage(getContainer(), getUser(), form.getSeed(), cf);
        }
    }

    public static class HitForm
    {
        private int _assayProtocolId;
        private Boolean _markAsHit;
        private List<Long> _resultRowIds;
        private String _resultSelectionKey;

        public int getAssayProtocolId()
        {
            return _assayProtocolId;
        }

        public void setAssayProtocolId(int assayProtocolId)
        {
            _assayProtocolId = assayProtocolId;
        }

        public Boolean isMarkAsHit()
        {
            return _markAsHit;
        }

        public void setMarkAsHit(Boolean markAsHit)
        {
            _markAsHit = markAsHit;
        }

        public List<Long> getResultRowIds()
        {
            return _resultRowIds;
        }

        public void setResultRowIds(List<Long> resultRowIds)
        {
            _resultRowIds = resultRowIds;
        }

        public String getResultSelectionKey()
        {
            return _resultSelectionKey;
        }

        public void setResultSelectionKey(String resultSelectionKey)
        {
            _resultSelectionKey = resultSelectionKey;
        }
    }

    @Marshal(Marshaller.JSONObject)
    @RequiresPermission(UpdatePermission.class)
    public static class HitAction extends MutatingApiAction<HitForm>
    {
        @Override
        public void validateForm(HitForm form, Errors errors)
        {
            if (form.isMarkAsHit() == null)
                errors.reject(ERROR_REQUIRED, "Specifying \"markAsHit\" is required.");
        }

        @Override
        public Object execute(HitForm form, BindException errors) throws Exception
        {
            PlateManager.get().markHits(
                getContainer(),
                getUser(),
                form.getAssayProtocolId(),
                form.isMarkAsHit(),
                form.getResultRowIds(),
                form.getResultSelectionKey()
            );
            return success();
        }
    }

    public static class PlateSetAssaysForm
    {
        private ContainerFilter.Type _containerFilter;
        private int _plateSetId;

        public ContainerFilter.Type getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(ContainerFilter.Type containerFilter)
        {
            _containerFilter = containerFilter;
        }

        public int getPlateSetId()
        {
            return _plateSetId;
        }

        public void setPlateSetId(int plateSetId)
        {
            _plateSetId = plateSetId;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class AssaysAction extends ReadOnlyApiAction<PlateSetAssaysForm>
    {
        @Override
        public void validateForm(PlateSetAssaysForm form, Errors errors)
        {
            if (form.getPlateSetId() <= 0)
                errors.reject(ERROR_REQUIRED, "\"plateSetId\" is required.");
        }

        @Override
        public Object execute(PlateSetAssaysForm form, BindException errors) throws Exception
        {
            ContainerFilter cf = null;

            // if an optional container filter is specified
            if (form.getContainerFilter() != null)
                cf = form.getContainerFilter().create(getViewContext());

            return PlateManager.get().getPlateSetAssays(getContainer(), getUser(), form.getPlateSetId(), cf);
        }
    }

    public enum FileType
    {
        CSV,
        Excel,
        TSV
    }

    public static class WorklistForm
    {
        private ContainerFilter.Type _containerFilter;
        private int _sourcePlateSetId;
        private int _destinationPlateSetId;
        private FileType _fileType;

        public ContainerFilter.Type getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(ContainerFilter.Type containerFilter)
        {
            _containerFilter = containerFilter;
        }

        public int getSourcePlateSetId()
        {
            return _sourcePlateSetId;
        }

        public void setSourcePlateSetId(int sourcePlateSetId)
        {
            _sourcePlateSetId = sourcePlateSetId;
        }

        public int getDestinationPlateSetId()
        {
            return _destinationPlateSetId;
        }

        public void setDestinationPlateSetId(int destinationPlateSetId)
        {
            _destinationPlateSetId = destinationPlateSetId;
        }

        public FileType getFileType()
        {
            return _fileType;
        }

        public void setFileType(FileType fileType)
        {
            _fileType = fileType;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class WorkListAction extends ReadOnlyApiAction<WorklistForm>
    {
        @Override
        public Object execute(WorklistForm form, BindException errors) throws Exception
        {
            try
            {
                PlateSet plateSetSource = PlateManager.get().getPlateSet(getContainer(), form.getSourcePlateSetId());
                PlateSet plateSetDestination = PlateManager.get().getPlateSet(getContainer(), form.getDestinationPlateSetId());
                if (plateSetSource == null || plateSetDestination == null)
                    throw new NotFoundException("Unable to resolve Plate Set.");

                ContainerFilter cf = ContainerFilter.Type.Current.create(getViewContext());
                if (form.getContainerFilter() != null)
                    cf = form.getContainerFilter().create(getViewContext());

                List<FieldKey> sourceIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateSetSource, getContainer(), getUser(), cf);
                List<FieldKey> destinationIncludedMetadataCols = PlateManager.get().getMetadataColumns(plateSetDestination, getContainer(), getUser(), cf);

                List<ColumnDescriptor> sourceColumns = PlateSetExport.getColumnDescriptors(PlateSetExport.SOURCE, sourceIncludedMetadataCols);
                List<ColumnDescriptor> destinationColumns = PlateSetExport.getColumnDescriptors(PlateSetExport.DESTINATION, destinationIncludedMetadataCols);
                List<ColumnDescriptor> exportColumns = new ArrayList<>(sourceColumns);
                exportColumns.addAll(destinationColumns);

                List<Object[]> plateDataRows = PlateManager.get().getWorklist(form.getSourcePlateSetId(), form.getDestinationPlateSetId(), sourceIncludedMetadataCols, destinationIncludedMetadataCols, getContainer(), getUser());

                String fullFileName = plateSetSource.getName() + " - " + plateSetDestination.getName();

                PlateManager.get().getPlateSetExportFile(fullFileName, exportColumns, plateDataRows, form.getFileType(), getViewContext().getResponse());
                SimpleMetricsService.get().increment(AssayModule.NAME, "plateSet", "exportWorklist");
            }
            catch (Exception e)
            {
                errors.reject(ERROR_GENERIC, e.getMessage() != null ? e.getMessage() : "Failed to create Worklist.");
            }

            return null;
        }
    }

    public static class InstrumentInstructionForm
    {
        private ContainerFilter.Type _containerFilter;
        private int _plateSetId;
        private FileType _fileType;

        public ContainerFilter.Type getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(ContainerFilter.Type containerFilter)
        {
            _containerFilter = containerFilter;
        }

        public int getPlateSetId()
        {
            return _plateSetId;
        }

        public void setPlateSetId(int plateSetId)
        {
            _plateSetId = plateSetId;
        }

        public FileType getFileType()
        {
            return _fileType;
        }

        public void setFileType(FileType fileType)
        {
            _fileType = fileType;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class InstrumentInstructionAction extends ReadOnlyApiAction<InstrumentInstructionForm>
    {
        @Override
        public Object execute(InstrumentInstructionForm form, BindException errors) throws Exception
        {
            try
            {
                PlateSet plateSet = PlateManager.get().getPlateSet(getContainer(), form.getPlateSetId());
                if (plateSet == null)
                    throw new NotFoundException("Unable to resolve Plate Set.");
                if (!plateSet.isAssay())
                    throw new ValidationException("Instrument Instructions cannot be generated for non-Assay Plate Sets.");

                ContainerFilter cf = ContainerFilter.Type.Current.create(getViewContext());
                if (form.getContainerFilter() != null)
                    cf = form.getContainerFilter().create(getViewContext());

                List<FieldKey> includedMetadataCols = PlateManager.get().getMetadataColumns(plateSet, getContainer(), getUser(), cf);
                List<ColumnDescriptor> exportColumns = PlateSetExport.getColumnDescriptors("", includedMetadataCols);
                List<Object[]> plateDataRows = PlateManager.get().getInstrumentInstructions(form.getPlateSetId(), includedMetadataCols, getContainer(), getUser());

                PlateManager.get().getPlateSetExportFile(plateSet.getName() + "-instructions", exportColumns, plateDataRows, form.getFileType(), getViewContext().getResponse());
            }
            catch (Exception e)
            {
                errors.reject(ERROR_GENERIC, e.getMessage() != null ? e.getMessage() : "Failed to create Instrument Instruction.");
            }

            return null;
        }
    }

    public enum PlateExportType
    {
        CSV,
        TSV,
        Map,
    }

    public static class PlateExportForm
    {
        private ContainerFilter.Type _containerFilter;

        private List<Integer> _plateIds;

        private PlateExportType _exportType;

        private String _filename;

        public ContainerFilter.Type getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(ContainerFilter.Type containerFilter)
        {
            _containerFilter = containerFilter;
        }

        public List<Integer> getPlateIds()
        {
            return _plateIds;
        }

        public void setPlateIds(List<Integer> plateIds)
        {
            _plateIds = plateIds;
        }

        public PlateExportType getExportType()
        {
            return _exportType;
        }

        public void setExportType(PlateExportType exportType)
        {
            _exportType = exportType;
        }

        public String getFilename()
        {
            return _filename;
        }

        public void setFilename(String filename)
        {
            _filename = filename;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class PlateExportAction extends ReadOnlyApiAction<PlateExportForm>
    {
        @Override
        public void validateForm(PlateExportForm form, Errors errors)
        {
            if (form.getPlateIds() == null)
                errors.reject(ERROR_REQUIRED, "\"plateIds\" is required");

            if (form.getPlateIds().size() > PlateSet.MAX_PLATES)
                errors.reject(ERROR_GENERIC, "Too many \"plateIds\", maximum of " + PlateSet.MAX_PLATES + " can be exported at a time");

            if (form.getExportType() == null)
                errors.reject(ERROR_REQUIRED, "\"exportType\" is required");
        }

        @Override
        public Object execute(PlateExportForm form, BindException errors) throws Exception
        {
            ContainerFilter cf = ContainerFilter.Type.Current.create(getViewContext());
            // if an optional container filter is specified
            if (form.getContainerFilter() != null)
                cf = form.getContainerFilter().create(getViewContext());

            List<PlateManager.PlateFileBytes> fileBytes;
            String fileExtension;
            String mapSuffix = form.getExportType() == PlateExportType.Map ? "-map" : "";
            SimpleMetricsService metricsService = SimpleMetricsService.get();

            if (form.getExportType() == PlateExportType.CSV)
            {
                metricsService.increment(AssayModule.NAME, "plate", "exportCSV");
                fileBytes = PlateManager.get().exportPlateData(getContainer(), getUser(), cf, form.getPlateIds(), TSVWriter.DELIM.COMMA);
                fileExtension = TSVWriter.DELIM.COMMA.extension;
            }
            else if (form.getExportType() == PlateExportType.TSV)
            {
                metricsService.increment(AssayModule.NAME, "plate", "exportTSV");
                fileBytes = PlateManager.get().exportPlateData(getContainer(), getUser(), cf, form.getPlateIds(), TSVWriter.DELIM.TAB);
                fileExtension = TSVWriter.DELIM.TAB.extension;
            }
            else
            {
                metricsService.increment(AssayModule.NAME, "plate", "exportMAP");
                fileBytes = PlateManager.get().exportPlateMaps(getContainer(), getUser(), cf, form.getPlateIds());
                fileExtension = "xlsx";
            }

            if (fileBytes.isEmpty())
            {
                return null;
            }
            else if (fileBytes.size() == 1)
            {
                PlateManager.PlateFileBytes plateFileBytes = fileBytes.get(0);
                String fileName = FileUtil.makeLegalName(plateFileBytes.plateName() + mapSuffix + "." + fileExtension);
                PageFlowUtil.streamFileBytes(getViewContext().getResponse(), fileName, plateFileBytes.bytes().toByteArray(), true);
                return null;
            }

            String zipFileName = form.getFilename();

            if (zipFileName == null)
                zipFileName = "plates.zip";
            else
                zipFileName = zipFileName + mapSuffix + ".zip";

            zipFileName = FileUtil.makeLegalName(zipFileName);

            // Export to a temporary file first so exceptions are displayed by the standard error page
            Path tempDir = FileUtil.getTempDirectory().toPath();
            Path tempZipFile = tempDir.resolve(zipFileName);

            try (ZipFile zip = new ZipFile(tempDir, zipFileName))
            {
                for (PlateManager.PlateFileBytes plateFileBytes : fileBytes)
                {
                    String fileName = FileUtil.makeLegalName(plateFileBytes.plateName() + "." + fileExtension);
                    try (
                        InputStream is = new ByteArrayInputStream(plateFileBytes.bytes().toByteArray());
                        OutputStream os = zip.getOutputStream(fileName)
                    )
                    {
                        FileUtil.copyData(is, os);
                    }
                }
            }
            catch (Throwable t)
            {
                Files.deleteIfExists(tempZipFile);
                throw t;
            }

            try (OutputStream os = ZipFile.getOutputStream(getViewContext().getResponse(), zipFileName))
            {
                Files.copy(tempZipFile, os);
            }
            finally
            {
                Files.delete(tempZipFile);
            }

            return null;
        }
    }

    public static class CopyPlateForm
    {
        private boolean _copyAsTemplate;
        private String _description;
        private String _name;
        private Long _sourcePlateRowId;

        public boolean isCopyAsTemplate()
        {
            return _copyAsTemplate;
        }

        public void setCopyAsTemplate(boolean copyAsTemplate)
        {
            _copyAsTemplate = copyAsTemplate;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public Long getSourcePlateRowId()
        {
            return _sourcePlateRowId;
        }

        public void setSourcePlateRowId(Long sourcePlateRowId)
        {
            _sourcePlateRowId = sourcePlateRowId;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public static class CopyPlateAction extends MutatingApiAction<CopyPlateForm>
    {
        @Override
        public void validateForm(CopyPlateForm form, Errors errors)
        {
            if (form.getSourcePlateRowId() == null)
                errors.reject(ERROR_REQUIRED, "Specifying \"sourcePlateRowId\" is required.");
        }

        @Override
        public Object execute(CopyPlateForm form, BindException errors) throws Exception
        {
            Plate plate = PlateManager.get().copyPlate(
                getContainer(),
                getUser(),
                form.getSourcePlateRowId(),
                form.isCopyAsTemplate(),
                null,
                form.getName(),
                form.getDescription(),
                null
            );
            return success(plate);
        }
    }

    @RequiresPermission(InsertPermission.class)
    public static class ReformatAction extends MutatingApiAction<ReformatOptions>
    {
        @Override
        public Object execute(ReformatOptions options, BindException errors) throws Exception
        {
            try
            {
                var reformatResult = PlateManager.get().reformat(getContainer(), getUser(), options);
                return success(reformatResult);
            }
            catch (Exception e)
            {
                if (e instanceof ValidationException || e instanceof BatchValidationException)
                    LOG.debug("Request failed due to a validation exception", e);
                else
                    LOG.error("Request failed due to an exception", e);
                String message = "Failed to reformat plates.";
                if (e.getMessage() != null)
                    message += " " + e.getMessage();
                errors.reject(ERROR_GENERIC, message);
            }

            return null;
        }
    }
}
