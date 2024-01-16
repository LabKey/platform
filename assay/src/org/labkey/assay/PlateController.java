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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiJsonForm;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateCustomField;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.security.RequiresAnyOf;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.ContainerTree;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataViewSnapshotSelectionForm;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.vocabulary.security.DesignVocabularyPermission;
import org.labkey.assay.plate.PlateDataServiceImpl;
import org.labkey.assay.plate.PlateImpl;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.PlateSetImpl;
import org.labkey.assay.plate.PlateUrls;
import org.labkey.assay.plate.model.PlateType;
import org.labkey.assay.view.AssayGWTView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Marshal(Marshaller.Jackson)
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
        private List<? extends Plate> _templates;
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
    public class PlateTemplateListAction extends SimpleViewAction<ReturnUrlForm>
    {
        @Override
        public ModelAndView getView(ReturnUrlForm plateTemplateListForm, BindException errors)
        {
            setHelpTopic("editPlateTemplate");
            List<Plate> plateTemplates = PlateService.get().getPlateTemplates(getContainer());
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

            List<Plate> templates = PlateService.get().getPlateTemplates(getContainer());
            for (int i = 0; i < templates.size(); i++)
            {
                Plate template = templates.get(i);
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

    @RequiresAnyOf({DeletePermission.class, DesignAssayPermission.class})
    public class DeleteAction extends FormHandlerAction<NameForm>
    {
        @Override
        public void validateCommand(NameForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(NameForm form, BindException errors) throws Exception
        {
            Plate template = PlateService.get().getPlate(getContainer(), form.getPlateId());
            if (template != null)
                PlateService.get().deletePlate(getContainer(), getUser(), template.getRowId());
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
        private final Plate _plate;
        private final String _selectedDestination;
        private List<Plate> _destinationTemplates;

        public CopyTemplateBean(final Container container, final User user, final Integer plateId, final String selectedDestination)
        {
            _plate = PlateService.get().getPlate(container, plateId);
            if (_plate == null)
                throw new IllegalStateException("Could not resolve the plate with ID : " + plateId);

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
    public class CopyTemplateAction extends FormViewAction<CopyForm>
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
            return new JspView<>("/org/labkey/assay/plate/view/copyTemplate.jsp",
                    new CopyTemplateBean(getContainer(), getUser(), form.getPlateId(), form.getDestination()), errors);
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

            if (PlateManager.get().plateExists(_destination, _plate.getName()))
                errors.reject("copyForm", "A plate template with the same name already exists in the destination folder.");
        }

        @Override
        public boolean handlePost(CopyForm form, BindException errors) throws Exception
        {
            PlateService.get().copyPlate(_plate, getUser(), _destination);
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
        for (Plate template : PlateService.get().getPlateTemplates(container))
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
        private String _name;
        private Integer _plateSetId;
         private PlateType _plateType;
        private List<Map<String, Object>> _data = new ArrayList<>();

        public String getName()
        {
            return _name;
        }

        public Integer getPlateSetId()
        {
            return _plateSetId;
        }

        public PlateType getPlateType()
        {
            return _plateType;
        }

        public List<Map<String, Object>> getData()
        {
            return _data;
        }

        @Override
        public void bindJson(JSONObject json)
        {
            ObjectMapper objectMapper = JsonUtil.DEFAULT_MAPPER;

            if (json.has("name"))
                _name = json.getString("name");

            if (json.has("plateSetId"))
                _plateSetId = json.getInt("plateSetId");

            if (json.has("plateType"))
                _plateType = objectMapper.convertValue(json.getJSONObject("plateType"), PlateType.class);

            if (json.has("data"))
            {
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
    public static class CreatePlateAction extends MutatingApiAction<CreatePlateForm>
    {
        @Override
        public void validateForm(CreatePlateForm form, Errors errors)
        {
            if (form.getPlateType() == null)
                errors.reject(ERROR_REQUIRED, "Plate \"plateType\" is required.");
        }

        @Override
        public Object execute(CreatePlateForm form, BindException errors) throws Exception
        {
            try
            {
                Plate plate = PlateManager.get().createAndSavePlate(getContainer(), getUser(), form.getPlateType(), form.getName(), form.getPlateSetId(), form.getData());
                return success(plate);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_GENERIC, e.getMessage() != null ? e.getMessage() : "Failed to create plate. An error has occurred.");
            }

            return null;
        }
    }

    @RequiresAnyOf({ReadPermission.class, DesignAssayPermission.class})
    public static class GetPlateTypesAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            return PlateManager.get().getPlateTypes();
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

    @RequiresPermission(DesignVocabularyPermission.class)
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

    @RequiresPermission(DesignVocabularyPermission.class)
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
        private Integer _plateId;

        public Integer getPlateId()
        {
            return _plateId;
        }

        public void setPlateId(Integer plateId)
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

    @RequiresPermission(DesignVocabularyPermission.class)
    public static class EnsurePlateMetadataDomainAction extends MutatingApiAction<Object>
    {
        @Override
        public Object execute(Object form, BindException errors) throws Exception
        {
            return success(PlateManager.get().ensurePlateMetadataDomain(getContainer(), getUser()).getTypeId());
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

    // TODO: It'd be nice if we could not have to implement ApiJsonForm here and just bind via Jackson
    public static class CreatePlateSetForm implements ApiJsonForm
    {
        private String _name;
        private List<PlateType> _plateTypes = new ArrayList<>();

        public String getName()
        {
            return _name;
        }

        // TODO: Would be really nice to be able to reference plate types by a single identifier. Maybe we just give
        // them hard-coded names that can act as a "PK" which we can specify.
        public List<PlateType> getPlateTypes()
        {
            return _plateTypes;
        }

        @Override
        public void bindJson(JSONObject json)
        {
            ObjectMapper objectMapper = JsonUtil.DEFAULT_MAPPER;

            if (json.has("name"))
                _name = json.getString("name");

            if (json.has("plateTypes"))
            {
                JSONArray plateTypes = json.getJSONArray("plateTypes");

                for (int i = 0; i < plateTypes.length(); i++)
                {
                    JSONObject jsonObj = plateTypes.getJSONObject(i);
                    if (jsonObj != null)
                        _plateTypes.add(objectMapper.convertValue(jsonObj, PlateType.class));
                }
            }
        }
    }

    @Marshal(Marshaller.JSONObject)
    @RequiresPermission(InsertPermission.class)
    public static class CreatePlateSetAction extends MutatingApiAction<CreatePlateSetForm>
    {
        @Override
        public Object execute(CreatePlateSetForm form, BindException errors) throws Exception
        {
            try
            {
                PlateSetImpl plateSet = new PlateSetImpl();
                plateSet.setName(form.getName());

                plateSet = PlateManager.get().createPlateSet(getContainer(), getUser(), plateSet, form.getPlateTypes());
                return success(plateSet);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_GENERIC, e.getMessage() != null ? e.getMessage() : "Failed to create plate set. An error has occurred.");
            }

            return null;
        }
    }

    public static class ArchiveForm
    {
        private List<Integer> _plateSetIds;
        private boolean _restore;

        public List<Integer> getPlateSetIds()
        {
            return _plateSetIds;
        }

        public void setPlateSetIds(List<Integer> plateSetIds)
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
    public static class ArchivePlateSetsAction extends MutatingApiAction<ArchiveForm>
    {
        @Override
        public void validateForm(ArchiveForm form, Errors errors)
        {
            if (form.getPlateSetIds() == null)
                errors.reject(ERROR_GENERIC, "\"plateSetIds\" is a required field.");
        }

        @Override
        public Object execute(ArchiveForm form, BindException errors) throws Exception
        {
            try
            {
                PlateManager.get().archivePlateSets(getContainer(), getUser(), form.getPlateSetIds(), !form.isRestore());
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
}
