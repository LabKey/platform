/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.experiment.controllers.list;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.*;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.util.Pair;
import org.labkey.experiment.list.ListAuditViewFactory;
import org.labkey.experiment.list.ListManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Dec 30, 2007
 * Time: 12:44:30 PM
 */
public class ListController extends SpringActionController
{
    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(ListController.class);
    private static final Object ERROR_VALUE = new Object();

    public ListController()
    {
        super();
        setActionResolver(_actionResolver);
    }


    private NavTree appendRootNavTrail(NavTree root)
    {
        root.addChild("Lists", getBeginURL(getContainer()));
        return root;
    }


    private NavTree appendListNavTrail(NavTree root, ListDefinition list, String title)
    {
        appendRootNavTrail(root);
        root.addChild(list.getName(), list.urlShowData());

        if (null != title)
            root.addChild(title);

        return root;
    }


    public static ActionURL getBeginURL(Container c)
    {
        return new ActionURL(BeginAction.class, c);
    }


    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleViewAction<ViewForm>
    {
        public ModelAndView getView(ViewForm form, BindException errors) throws Exception
        {
            return FormPage.getView(ListController.class, form, "begin.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root);
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class NewListDefinitionAction extends FormViewAction<NewListForm>
    {
        private ListDefinition _list;

        public void validateCommand(NewListForm target, Errors errors)
        {
            // TODO: Validation
        }

        public ModelAndView getView(NewListForm form, boolean reshow, BindException errors) throws Exception
        {
            getPageConfig().setFocusId("ff_name");
                
            return FormPage.getView(ListController.class, form, errors, "newListDefinition.jsp");
        }

        public boolean handlePost(NewListForm form, BindException errors) throws Exception
        {
            try
            {
                _list = ListService.get().createList(getContainer(), form.ff_name);
                _list.setKeyName(form.ff_keyName);
                _list.setKeyType(ListDefinition.KeyType.valueOf(form.ff_keyType));
                _list.save(getUser());
                return true;
            }
            catch(Exception e)
            {
                errors.reject(ERROR_MSG, "List creation failed: " + e.getMessage());
                return false;
            }
        }

        public ActionURL getSuccessURL(NewListForm form)
        {
            return _list.urlShowDefinition();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root);
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ShowListDefinitionAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            return FormPage.getView(ListController.class, form, "showListDefinition.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, null);
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class EditListDefinitionAction extends FormViewAction<EditListDefinitionForm>
    {
        private ListDefinition _list;

        public void validateCommand(EditListDefinitionForm target, Errors errors)
        {
        }

        public ModelAndView getView(EditListDefinitionForm form, boolean reshow, BindException errors) throws Exception
        {
            form.setDefaults();
            _list = form.getList();

            return FormPage.getView(ListController.class, form, "editListDefinition.jsp");
        }

        public boolean handlePost(EditListDefinitionForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            _list.setTitleColumn(form.ff_titleColumn);
            _list.setKeyName(form.ff_keyName);
            _list.setDescription(form.ff_description);
            _list.setDiscussionSetting(form.ff_discussionSetting);
            _list.setAllowDelete(form.ff_allowDelete);
            _list.setAllowUpload(form.ff_allowUpload);
            _list.setAllowExport(form.ff_allowExport);
            _list.save(getUser());

            return true;
        }

        public ActionURL getSuccessURL(EditListDefinitionForm form)
        {
            return _list.urlShowDefinition();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "Edit " + _list.getName());
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteListDefinitionAction extends FormViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public void validateCommand(ListDefinitionForm target, Errors errors)
        {
        }

        public ModelAndView getView(ListDefinitionForm form, boolean reshow, BindException errors) throws Exception
        {
            _list = form.getList();
            return FormPage.getView(ListController.class, form, "deleteListDefinition.jsp");
        }

        public boolean handlePost(ListDefinitionForm form, BindException errors) throws Exception
        {
            form.getList().delete(getUser());
            return true;
        }

        public ActionURL getSuccessURL(ListDefinitionForm listDefinitionForm)
        {
            return getBeginURL(getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "Delete List");

        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class GridAction extends SimpleViewAction<ListQueryForm>
    {
        private ListDefinition _list;
        public ModelAndView getView(ListQueryForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            return new ListQueryView(form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, null);
        }
    }


    protected abstract class InsertUpdateAction extends FormViewAction<ListDefinitionForm>
    {
        private ActionURL _returnURL;
        protected ListDefinition _list;

        public void validateCommand(ListDefinitionForm target, Errors errors)
        {
            // TODO: Call validate, move validation out of reset()
        }

        public ModelAndView getView(ListDefinitionForm form, boolean reshow, BindException errors) throws Exception
        {
            _list = form.getList();
            TableInfo table = _list.getTable(getUser(), null);
            ListQueryUpdateForm tableForm = new ListQueryUpdateForm(table, getViewContext().getRequest(), _list);

            return getDataView(tableForm, form.getReturnActionURL(), errors);
        }

        public boolean handlePost(ListDefinitionForm form, BindException errors) throws Exception
        {
            ListDefinition list = form.getList();
            TableInfo table = list.getTable(getUser(), null);
            ListQueryUpdateForm tableForm = new ListQueryUpdateForm(table, getViewContext().getRequest(), list);
            tableForm.populateValues(errors);

            Map<String, MultipartFile> fileMap = getFileMap();
            if (null != fileMap)
            {
                for (String key : fileMap.keySet())
                {
                    SpringAttachmentFile file = new SpringAttachmentFile(fileMap.get(key));
                    tableForm.setTypedValue(key, file.isEmpty() ? null : file);
                }
            }

            if (errors.hasErrors())
                return false;

            try
            {
                ListItem item;
                if (isInsert())
                {
                    item = list.createListItem();
                }
                else
                {
                    item = list.getListItem(tableForm.getPkVal());
                }
                Object oldKey = item.getKey();
                Domain domain = list.getDomain();
                for (ColumnInfo column : tableForm.getTable().getColumnsList())
                {
                    if (!tableForm.hasTypedValue(column))
                        continue;
                    Object formValue = tableForm.getTypedValue(column);
                    DomainProperty property = domain.getPropertyByName(column.getName());
                    if (column.getName().equals(list.getKeyName()))
                    {
                        item.setKey(formValue);
                    }
                    if (property != null)
                    {
                        item.setProperty(property, formValue);
                    }
                }
                item.save(getUser());

                _returnURL = form.getReturnActionURL();

                // If user changed the PK then change returnURL to match
                if (!PageFlowUtil.nullSafeEquals(oldKey, item.getKey()) && null != _returnURL.getParameter("pk"))
                    _returnURL.replaceParameter("pk", item.getKey().toString());

                return true;
            }
            catch (Exception e)   // TODO: Check for specific errors and get rid of catch(Exception)
            {
                errors.reject(ERROR_MSG, "An exception occurred: " + e);
            }

            return false;
        }

        public ActionURL getSuccessURL(ListDefinitionForm listDefinitionForm)
        {
            return _returnURL;
        }

        protected ButtonBar getButtonBar(ActionURL submitURL, ActionURL returnURL)
        {
            ButtonBar bb = new ButtonBar();
            ActionButton btnSubmit = new ActionButton(submitURL.getEncodedLocalURIString(), "Submit");
            ActionButton btnCancel = new ActionButton("Cancel", returnURL);
            bb.add(btnSubmit);
            bb.add(btnCancel);

            return bb;
        }

        protected abstract DataView getDataView(ListQueryUpdateForm tableForm, ActionURL returnURL, BindException errors);
        protected abstract boolean isInsert();
    }


    @RequiresPermission(ACL.PERM_INSERT)
    public class InsertAction extends InsertUpdateAction
    {
        @Override
        protected DataView getDataView(ListQueryUpdateForm tableForm, ActionURL returnURL, BindException errors)
        {
            InsertView view = new InsertView(tableForm, errors);
            view.setFocusId("firstInputField");
            getPageConfig().setFocusId(view.getFocusId());
            view.getDataRegion().setButtonBar(getButtonBar(_list.urlFor(Action.insert).addParameter("returnUrl", returnURL.getLocalURIString()), returnURL));

            return view;
        }

        protected boolean isInsert()
        {
            return true;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "Add New List Item");
        }
    }


    @RequiresPermission(ACL.PERM_UPDATE)
    public class UpdateAction extends InsertUpdateAction
    {
        @Override
        protected DataView getDataView(ListQueryUpdateForm tableForm, ActionURL returnURL, BindException errors)
        {
            DataView view = new UpdateView(tableForm, errors);
            view.getDataRegion().setButtonBar(getButtonBar(_list.urlUpdate(tableForm.getPkVal(), returnURL), returnURL));
            return view;
        }

        protected boolean isInsert()
        {
            return false;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "Edit List Item");
        }

        public void setList(ListDefinition list)
        {
            _list = list;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class DetailsAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            TableInfo table = _list.getTable(getUser(), null);

            ListQueryUpdateForm tableForm = new ListQueryUpdateForm(table, getViewContext().getRequest(), _list);
            DetailsView details = new DetailsView(tableForm);

            ButtonBar bb = new ButtonBar();

            if (getViewContext().hasPermission(ACL.PERM_UPDATE))
            {
                ActionURL updateUrl = _list.urlUpdate(tableForm.getPkVal(), getViewContext().getActionURL());
                ActionButton editButton = new ActionButton("Edit", updateUrl);
                bb.add(editButton);
            }

            ActionButton gridButton = new ActionButton("Show Grid", _list.urlShowData());
            bb.add(gridButton);
            details.getDataRegion().setButtonBar(bb);

            VBox view = new VBox();
            ListItem item = _list.getListItem(tableForm.getPkVal());

            if (null == item)
                throw new NotFoundException("List item '" + tableForm.getPkVal() + "' does not exist");

            view.addView(details);

            if (form.isShowHistory())
            {
                WebPartView linkView = new HtmlView("[<a href=\"" + getViewContext().cloneActionURL().deleteParameter("showHistory") + "\">hide item history</a>]");
                linkView.setFrame(WebPartView.FrameType.NONE);
                view.addView(linkView);
                WebPartView history = ListAuditViewFactory.getInstance().createListItemDetailsView(getViewContext(), item.getEntityId());
                history.setFrame(WebPartView.FrameType.NONE);
                view.addView(history);
            }
            else
            {
                view.addView(new HtmlView("[<a href=\"" + getViewContext().cloneActionURL().addParameter("showHistory", "1") + "\">show item history</a>]"));
            }

            if (_list.getDiscussionSetting().isLinked())
            {
                String entityId = item.getEntityId();

                DomainProperty titleProperty = _list.getDomain().getPropertyByName(_list.getTitleColumn());
                Object title = (null != titleProperty ? item.getProperty(titleProperty) : null);
                String discussionTitle = (null != title ? title.toString() : "Row " + tableForm.getPkVal());

                ActionURL linkBackUrl = _list.urlFor(Action.resolve).addParameter("entityId", entityId);
                DiscussionService.Service service = DiscussionService.get();
                boolean multiple = _list.getDiscussionSetting() == ListDefinition.DiscussionSetting.ManyPerRow;

                // Display discussion by default in single-discussion case, #4529
                DiscussionService.DiscussionView discussion = service.getDisussionArea(getViewContext(), entityId, linkBackUrl, discussionTitle, multiple, !multiple);
                view.addView(discussion);

                getPageConfig().setFocusId(discussion.getFocusId());
            }

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "View List Item");
        }
    }


    // Override to ensure that pk value type matches column type.  This is critical for PostgreSQL 8.3.
    public static class ListQueryUpdateForm extends QueryUpdateForm
    {
        private ListDefinition _list;

        public ListQueryUpdateForm(TableInfo table, HttpServletRequest request, ListDefinition list)
        {
            super(table, request);
            _list = list;
        }

        public Object[] getPkVals()
        {
            Object[] pks = super.getPkVals();
            assert 1 == pks.length;
            pks[0] = _list.getKeyType().convertKey(pks[0]);
            return pks;
        }
    }


    // Users can change the PK of a list item, so we don't want to store PK in discussion source URL (back link
    // from announcements to the object).  Instead, we tell discussion service to store a URL with ListId and
    // EntityId.  This action resolves to the current details URL for that item.
    @RequiresPermission(ACL.PERM_READ)
    public class ResolveAction extends SimpleRedirectAction<ListDefinitionForm>
    {
        public ActionURL getRedirectURL(ListDefinitionForm form) throws Exception
        {
            ListDefinition list = form.getList();
            ListItem item = list.getListItemForEntityId(getViewContext().getActionURL().getParameter("entityId")); // TODO: Use proper form, validate
            ActionURL url = getViewContext().cloneActionURL().setAction(Action.details.name());   // Clone to preserve discussion params
            url.deleteParameter("entityId");
            url.addParameter("pk", item.getKey().toString());

            return url;
        }
    }


    @RequiresPermission(ACL.PERM_DELETE)
    public class DeleteAction extends SimpleRedirectAction<ListDefinitionForm>
    {
        public ActionURL getRedirectURL(ListDefinitionForm form) throws Exception
        {
            form.getList().deleteListItems(getUser(), DataRegionSelection.getSelected(getViewContext(), true));
            return form.getList().urlShowData();
        }
    }


    @RequiresPermission(ACL.PERM_INSERT)
    public class UploadListItemsAction extends FormViewAction<UploadListItemsForm>
    {
        private ListDefinition _list;

        public void validateCommand(UploadListItemsForm target, Errors errors)
        {
        }

        public ModelAndView getView(UploadListItemsForm form, boolean reshow, BindException errors) throws Exception
        {
            _list = form.getList();
            return FormPage.getView(ListController.class, form, errors, "uploadListItems.jsp");
        }

        public boolean handlePost(UploadListItemsForm form, BindException errors) throws Exception
        {
            boolean hasError = false;
            TabLoader tl = new TabLoader(form.ff_data, true);
            _list = form.getList();
            Map<String, DomainProperty> propertiesByName = new CaseInsensitiveHashMap<DomainProperty>();
            Map<String, DomainProperty> propertiesByCaption = new CaseInsensitiveHashMap<DomainProperty>();
            for (DomainProperty property : _list.getDomain().getProperties())
            {
                propertiesByName.put(property.getName(), property);
                if (property.getLabel() != null)
                {
                    propertiesByCaption.put(property.getLabel(), property);
                }
            }
            Map<String, DomainProperty> properties = new CaseInsensitiveHashMap<DomainProperty>();
            TabLoader.ColumnDescriptor cdKey = null;
            if (form.ff_data == null)
                hasError = hasError || addError(errors, "Form contains no data");
            else
            {
                for (TabLoader.ColumnDescriptor cd : tl.getColumns())
                {
                    DomainProperty property = propertiesByName.get(cd.name);
                    cd.errorValues = ERROR_VALUE;

                    if (property == null)
                    {
                        property = propertiesByCaption.get(cd.name);
                    }
                    if (property != null)
                    {
                        cd.clazz = property.getPropertyDescriptor().getPropertyType().getJavaType();

                        if (properties.containsKey(cd.name))
                        {
                            hasError = hasError || addError(errors, "The field '" + property.getName() + "' appears more than once.");
                        }
                        if (properties.containsValue(property))
                        {
                            hasError = hasError || addError(errors, "The fields '" + property.getName() + "' and '" + property.getLabel() + "' refer to the same property.");
                        }
                        properties.put(cd.name, property);
                        cd.name = property.getPropertyURI();
                    }
                    else if (!_list.getKeyName().equalsIgnoreCase(cd.name))
                    {
                        hasError = hasError || addError(errors, "The field '" + cd.name + "' could not be matched to an existing field in this list.");
                    }
                    if (_list.getKeyName().equalsIgnoreCase(cd.name))
                    {
                        if (cdKey != null)
                        {
                            hasError = hasError || addError(errors, "The field '" + _list.getKeyName() + "' appears more than once.");
                        }
                        else
                        {
                            cdKey = cd;
                        }
                    }
                }
                if (cdKey == null && _list.getKeyType() != ListDefinition.KeyType.AutoIncrementInteger)
                {
                    hasError = hasError || addError(errors, "There must be a field with the name '" + _list.getKeyName() + "'");
                }
            }
            if (hasError)
                return false;
            Map[] rows = (Map[]) tl.load();

            if (_list.getKeyType() == ListDefinition.KeyType.Integer && !Integer.class.equals(cdKey.clazz))
            {
                errors.reject(ERROR_MSG, "Expected keys to be of type Integer but they are of type " + cdKey.clazz.getSimpleName());
                return false;
            }

            Set<Object> keyValues = new HashSet<Object>();
            Set<String> missingValues = new HashSet<String>();
            Set<String> wrongTypes = new HashSet<String>();
            Set<String> noUpload = new HashSet<String>();

            for (Map row : rows)
            {
                row = new CaseInsensitiveHashMap<Object>(row);
                for (DomainProperty domainProperty : _list.getDomain().getProperties())
                {
                    Object o = row.get(domainProperty.getPropertyURI());
                    String value = o == null ? null : o.toString();
                    boolean valueMissing = (value == null || value.length() == 0);
                    if (domainProperty.isRequired() && valueMissing && !missingValues.contains(domainProperty.getName()))
                    {
                        missingValues.add(domainProperty.getName());
                        errors.reject(ERROR_MSG, domainProperty.getName() + " is required.");
                    }
                    else if (!valueMissing && domainProperty.getPropertyDescriptor().getPropertyType() == PropertyType.ATTACHMENT && !noUpload.contains(domainProperty.getName()))   // TODO: Change to allowUpload() getter on property 
                    {
                        noUpload.add(domainProperty.getName());
                        errors.reject(ERROR_MSG, "Can't upload to field " + domainProperty.getName() + " with type " + domainProperty.getType().getLabel() + ".");
                    }
                    else if (!valueMissing && o == ERROR_VALUE && !wrongTypes.contains(domainProperty.getName()))
                    {
                        wrongTypes.add(domainProperty.getName());
                        errors.reject(ERROR_MSG, domainProperty.getName() + " must be of type " + domainProperty.getType().getLabel() + ".");
                    }
                }

                if (cdKey != null)
                {
                    Object key = row.get(cdKey.name);
                    if (null == key)
                    {
                        errors.reject(ERROR_MSG, "Blank values are not allowed in field " + cdKey.name);
                        return false;
                    }
                    else if (!keyValues.add(key))
                    {
                        errors.reject(ERROR_MSG, "There are multiple rows with key value " + row.get(cdKey.name));
                        return false;
                    }
                }
            }

            if (errors.hasErrors())
                return false;

            boolean transaction = false;
            try
            {
                if (!ExperimentService.get().isTransactionActive())
                {
                    ExperimentService.get().beginTransaction();
                    transaction = true;
                }
                DomainProperty[] dps = properties.values().toArray(new DomainProperty[properties.values().size()]);
                PropertyDescriptor[] pds = new PropertyDescriptor[dps.length];
                for (int i = 0; i < dps.length; i ++)
                {
                    pds[i] = dps[i].getPropertyDescriptor();
                }
                ListImportHelper helper = new ListImportHelper(getUser(), _list, dps, cdKey);

                OntologyManager.insertTabDelimited(getContainer(), null, helper, pds, rows, true);
                if (transaction)
                {
                    ExperimentService.get().commitTransaction();
                    transaction = false;
                }
            }
            finally
            {
                if (transaction)
                {
                    ExperimentService.get().rollbackTransaction();
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(UploadListItemsForm form)
        {
            return _list.urlShowData();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "Import Data");
        }
    }


    private boolean addError(BindException errors, String message)
    {
        errors.reject(ERROR_MSG, message);
        return true;
    }


    @RequiresPermission(ACL.PERM_READ)
    public class HistoryAction extends SimpleViewAction<ListQueryForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListQueryForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            return ListAuditViewFactory.getInstance().createListHistoryView(getViewContext(), _list);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, _list.getName() + ":History");
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class HistoryDetailAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            int id = NumberUtils.toInt((String)getViewContext().get("eventId"));
            AuditLogEvent event = AuditLogService.get().getEvent(id);

            if (event != null)
                return new JspView<AuditLogEvent>("/org/labkey/experiment/controllers/list/historyDetail.jsp", event);
            else
                return HttpView.throwNotFoundMV("Unable to find the audit history detail for this event");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "Audit History Detail");
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ListItemDetailsAction extends SimpleViewAction
    {
        private ListDefinition _list;

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            boolean empty = true;
            String entityId = (String)getViewContext().get("entityId");
            int id = NumberUtils.toInt((String)getViewContext().get("rowId"));
            int listId = NumberUtils.toInt((String)getViewContext().get("listId"));
            _list = ListService.get().getList(listId);

            VBox view = new VBox();
            if (_list != null && entityId != null)
            {
                ListItem item = _list.getListItemForEntityId(entityId);
                if (item != null)
                {
                    view.addView(new ListDetailsView(new ListQueryForm(listId, getUser(), getContainer()), item.getKey()));
                    empty = false;
                }
            }

            AuditLogEvent event = AuditLogService.get().getEvent(id);
            if (event != null && event.getLsid() != null)
            {
                Map<String, Object> dataMap = OntologyManager.getProperties(ContainerManager.getSharedContainer().getId(), event.getLsid());
                if (dataMap != null)
                {
                    String oldRecord;
                    String newRecord;
                    boolean isEncoded = false;
                    if (dataMap.containsKey(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, "oldRecordMap")) ||
                            dataMap.containsKey(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, "newRecordMap")))
                    {
                        isEncoded = true;
                        oldRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, "oldRecordMap"));
                        newRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, "newRecordMap"));
                    }
                    else
                    {
                        oldRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, "oldRecord"));
                        newRecord = (String)dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, "newRecord"));
                    }

                    if (!StringUtils.isEmpty(oldRecord) || !StringUtils.isEmpty(newRecord))
                    {
                        empty = false;
                        view.addView(new ItemDetails(event, oldRecord, newRecord, isEncoded));
                    }
                }
            }

            if (empty)
                return HttpView.throwNotFoundMV("Unable to find the audit history detail for this event");
            else
                return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "List Item Details");
        }
    }


    private static class ItemDetails extends WebPartView
    {
        AuditLogEvent _event;
        String _oldRecord;
        String _newRecord;
        boolean _isEncoded;

        public ItemDetails(AuditLogEvent event, String oldRecord, String newRecord, boolean isEncoded)
        {
            _event = event;
            _oldRecord = oldRecord;
            _newRecord = newRecord;
            _isEncoded = isEncoded;
        }

        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            if (_isEncoded)
            {
                _renderViewEncoded(model, out);
            }
            else
            {
                out.write("<table>\n");
                out.write("<tr class=\"wpHeader\"><th class=\"wpTitle\" align=\"left\">Item Changes</th></tr>");
                out.write("<tr><td>Comment:&nbsp;<i>" + PageFlowUtil.filter(_event.getComment()) + "</i></td></tr>");
                out.write("<tr><td><table>\n");
                if (!StringUtils.isEmpty(_oldRecord))
                    _renderRecord("previous:", _oldRecord, out);

                if (!StringUtils.isEmpty(_newRecord))
                    _renderRecord("current:", _newRecord, out);
                out.write("</table></td></tr>\n");
                out.write("</table>\n");
            }
        }

        private void _renderRecord(String title, String record, PrintWriter out)
        {
            Pair<String, String>[] params = PageFlowUtil.fromQueryString(record);
            out.write("<tr><td><b>" + title + "</b></td>");
            for (Pair<String, String> param : params)
            {
                out.write("<td>" + param.getValue() + "</td>");
            }
        }

        private void _renderViewEncoded(Object model, PrintWriter out)
        {
            Map<String, String> prevProps = ListAuditViewFactory.decodeFromDataMap(_oldRecord);
            Map<String, String> newProps = ListAuditViewFactory.decodeFromDataMap(_newRecord);
            int modified = 0;

            out.write("<table>\n");
            out.write("<tr class=\"wpHeader\"><th colspan=\"2\" class=\"wpTitle\" align=\"left\">Item Changes</th></tr>");
            out.write("<tr><td colspan=\"2\">Comment:&nbsp;<i>" + PageFlowUtil.filter(_event.getComment()) + "</i></td></tr>");
            out.write("<tr><td/>\n");

            for (Map.Entry<String, String> entry : prevProps.entrySet())
            {
                out.write("<tr><td class=\"ms-searchform\">");
                out.write(entry.getKey());
                out.write("</td><td>");

                StringBuffer sb = new StringBuffer();
                sb.append(entry.getValue());

                String newValue = newProps.remove(entry.getKey());
                if (newValue != null && !newValue.equals(entry.getValue()))
                {
                    modified++;
                    sb.append("&nbsp;&raquo;&nbsp;");
                    sb.append(newValue);
                }
                out.write(sb.toString());
                out.write("</td></tr>\n");
            }

            for (Map.Entry<String, String> entry : newProps.entrySet())
            {
                modified++;
                out.write("<tr><td class=\"ms-searchform\">");
                out.write(entry.getKey());
                out.write("</td><td>");

                StringBuffer sb = new StringBuffer();
                sb.append("&nbsp;&raquo;&nbsp;");
                sb.append(entry.getValue());
                out.write(sb.toString());
                out.write("</td></tr>\n");
            }
            out.write("<tr><td/>\n");
            out.write("<tr><td colspan=\"2\">Summary:&nbsp;<i>");
            out.write(modified + " field(s) were modified</i></td></tr>");
            out.write("</table>\n");
        }
    }


    public static ActionURL getDownloadURL(Container c, String entityId, String filename)
    {
        return new DownloadURL(DownloadAction.class, c, entityId, filename);
    }


    @RequiresPermission(ACL.PERM_READ)
    public class DownloadAction extends SimpleViewAction<AttachmentForm>
    {
        public ModelAndView getView(final AttachmentForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            final AttachmentParent parent = new ListItemAttachmentParent(form.getEntityId(), getContainer());

            return new HttpView()
            {
                protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
                {
                    AttachmentService.get().download(response, parent, form.getName());
                }
            };
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public enum Action
    {
        begin,
        newListDefinition,
        deleteListDefinition,
        showListDefinition,
        editListDefinition,
        grid,
        details,
        insert,
        update,
        uploadListItems,
        history,
        resolve
    }
}
