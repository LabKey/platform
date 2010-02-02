/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

package org.labkey.list.view;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.*;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.*;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.defaults.ClearDefaultValuesAction;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.defaults.SetDefaultValuesListAction;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListImporterViewGetter;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.ZipFile;
import org.labkey.api.writer.ZipUtil;
import org.labkey.list.model.ListAuditViewFactory;
import org.labkey.list.model.ListImporter;
import org.labkey.list.model.ListManager;
import org.labkey.list.model.ListWriter;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Dec 30, 2007
 * Time: 12:44:30 PM
 */
public class ListController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ListController.class,
            SetDefaultValuesListAction.class,
            ClearDefaultValuesAction.class
            );

    public ListController()
    {
        setActionResolver(_actionResolver);
    }


    private NavTree appendRootNavTrail(NavTree root)
    {
        return appendRootNavTrail(root, getContainer());
    }


    public static NavTree appendRootNavTrail(NavTree root, Container c)
    {
        root.addChild("Lists", getBeginURL(c));
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


    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            return new JspView<Object>(this.getClass(), "begin.jsp", null);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Available Lists");
        }
    }


    @RequiresPermissionClass(DesignListPermission.class)
    public class NewListDefinitionAction extends FormViewAction<NewListForm>
    {
        private ListDefinition _list;

        public void validateCommand(NewListForm form, Errors errors)
        {
            if (null == form.ff_name)
            {
                errors.reject(ERROR_MSG, "List name must not be blank.");
            }
            if (null == form.ff_keyName)
            {
                errors.reject(ERROR_MSG, "Key name must not be blank.");
            }
            // TODO: More validation -- combine with UpdateListDefinition validation?
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
            // Domain.save() throws RuntimeSQLException instead of SQLException... unwrap to detect constraint
            // violation and provide a better error message.  TODO: Domain.save() should throw SQLException instead
            catch (RuntimeSQLException e)
            {
                if (SqlDialect.isConstraintException(e.getSQLException()))
                    errors.reject(ERROR_MSG, "A list with this name already exists; please choose a different name.");
                else
                    throw e;
            }

            return false;
        }

        public ActionURL getSuccessURL(NewListForm form)
        {
            if (!form.isFileImport())
                return _list.urlShowDefinition();
            else
                return _list.urlFor(Action.defineAndImportList);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Create New List");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DefineAndImportListAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            Map<String, String> props = new HashMap<String, String>();

            props.put("typeURI", _list.getDomain().getTypeURI());

            // Cancel should delete the dataset
            ActionURL cancelURL = new ActionURL(CancelDefineAndImportListAction.class, getContainer());
            cancelURL.addParameter("listId", _list.getListId());
            props.put("cancelURL", cancelURL.getLocalURIString());

            ActionURL successURL = _list.urlShowData();
            props.put("successURL", successURL.getLocalURIString());

            // need a comma-separated list of base columns
            TableInfo tInfo = _list.getTable(getUser());

            StringBuilder sb = new StringBuilder();
            boolean needComma = false;
            for (String baseColumnName : tInfo.getColumnNameSet())
            {
                if (needComma)
                    sb.append(",");
                else
                    needComma = true;
                sb.append(baseColumnName);
            }
            props.put("baseColumnNames", sb.toString());

            // TODO: return new GWTView(ListImporter.class, props);
            return ListImporterViewGetter.getView(props);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, null);
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CancelDefineAndImportListAction extends SimpleViewAction<ListDefinitionForm>
    {
        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            // Cancelling the import just deletes the list
            ListDefinition _list = form.getList();
            _list.delete(getUser());
            HttpView.throwRedirect(getBeginURL(getContainer()));
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DomainImportServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new ListImportServiceImpl(getViewContext());
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
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


    @RequiresPermissionClass(AdminPermission.class)
    public class EditListDefinitionAction extends FormViewAction<EditListDefinitionForm>
    {
        private ListDefinition _list;

        public void validateCommand(EditListDefinitionForm form, Errors errors)
        {
            if (null == form.ff_keyName)
            {
                errors.reject(ERROR_MSG, "Key name must not be blank.");
            }
        }

        public ModelAndView getView(EditListDefinitionForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!reshow)
                form.setDefaults();

            _list = form.getList();

            return FormPage.getView(ListController.class, form, errors, "editListDefinition.jsp");
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


    @RequiresPermissionClass(AdminPermission.class)
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


    @RequiresPermissionClass(ReadPermission.class)
    public class GridAction extends SimpleViewAction<ListQueryForm>
    {
        private ListDefinition _list;
        public ModelAndView getView(ListQueryForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            if (null == _list)
                throw new NotFoundException("List does not exist in this container");
            return new ListQueryView(form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, null);
        }
    }


    protected abstract class InsertUpdateAction extends FormViewAction<ListDefinitionForm>
    {
        private URLHelper _returnURL;
        // We don't want to construct multiple forms in the reshow case, otherwise we'll double up all the error messages,
        // so stash the form we create in the post handler here.  See #9031.  TODO: Fix this in 10.1, see #9033
        private ListQueryUpdateForm _form;
        protected ListDefinition _list;

        public void validateCommand(ListDefinitionForm target, Errors errors)
        {
        }

        public ModelAndView getView(ListDefinitionForm form, boolean reshow, BindException errors) throws Exception
        {
            _list = form.getList();
            TableInfo table = _list.getTable(getUser());
            ListQueryUpdateForm tableForm = null != _form ? _form : new ListQueryUpdateForm(table, getViewContext(), _list, errors);

            return getDataView(tableForm, form.getReturnURLHelper(), errors);
        }

        public boolean handlePost(ListDefinitionForm form, BindException errors) throws Exception
        {
            if (errors.hasErrors())
                return false;

            ListDefinition list = form.getList();
            _list = list;
            TableInfo table = list.getTable(getUser());

            ListQueryUpdateForm tableForm = new ListQueryUpdateForm(table, getViewContext(), list, errors);
            _form = tableForm;

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

            boolean transaction = false;
            ListItem item = null;

            try
            {
                if (!ExperimentService.get().isTransactionActive())
                {
                    ExperimentService.get().beginTransaction();
                    transaction = true;
                }

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
                for (ColumnInfo column : tableForm.getTable().getColumns())
                {
                    if (!tableForm.hasTypedValue(column))
                        continue;
                    Object formValue = tableForm.getTypedValue(column);
                    String mvIndicator = null;
                    if (column.isMvEnabled())
                    {
                        ColumnInfo mvColumn = tableForm.getTable().getColumn(column.getMvColumnName());
                        mvIndicator = (String)tableForm.getTypedValue(mvColumn);
                    }
                    DomainProperty property = domain.getPropertyByName(column.getName());
                    if (column.getName().equals(list.getKeyName()))
                    {
                        item.setKey(formValue);
                    }

                    if (mvIndicator == null)
                    {
                        if (property != null)
                        {
                            item.setProperty(property, formValue);
                        }
                    }
                    else
                    {
                        MvFieldWrapper mvWrapper = new MvFieldWrapper(formValue, mvIndicator);
                        item.setProperty(property, mvWrapper);
                    }
                }
                if (errors.hasErrors())
                    return false;
                item.save(getUser());
                if (transaction)
                {
                    ExperimentService.get().commitTransaction();
                    transaction = false;
                }

                _returnURL = form.getReturnURLHelper();

                // If user changed the PK then change returnURL to match
                if (!PageFlowUtil.nullSafeEquals(oldKey, item.getKey()) && null != _returnURL.getParameter("pk"))
                    _returnURL.replaceParameter("pk", item.getKey().toString());

                if (isInsert())
                {
                    DomainProperty[] properties = domain.getProperties();
                    Map<String, Object> requestMap = tableForm.getTypedValues();
                    Map<DomainProperty, Object> dataMap = new HashMap<DomainProperty, Object>(requestMap.size());
                    for (DomainProperty property : properties)
                    {
                        ColumnInfo tempCol = new ColumnInfo(property.getName());
                        dataMap.put(property, requestMap.get(tableForm.getFormFieldName(tempCol)));
                    }
                    DefaultValueService.get().setDefaultValues(getContainer(), dataMap, getUser());
                }

                return true;
            }
            catch (ValidationException ve)
            {
                for (ValidationError error : ve.getErrors())
                    errors.reject(ERROR_MSG, PageFlowUtil.filter(error.getMessage()));
            }
            catch (SQLException e)
            {
                if (SqlDialect.isConstraintException(e))
                {
                    if (null != item && null != item.getKey())
                        errors.reject(ERROR_MSG, "Error: A record having key \"" + item.getKey() + "\" already exists.");
                    else
                        errors.reject(ERROR_MSG, "Error: A record with that key already exists.");
                }
            }
            finally
            {
                if (transaction)
                {
                    ExperimentService.get().rollbackTransaction();
                }
            }

            return false;
        }

        public URLHelper getSuccessURL(ListDefinitionForm listDefinitionForm)
        {
            return _returnURL;
        }

        protected ButtonBar getButtonBar(ActionURL submitURL, URLHelper returnURL)
        {
            ButtonBar bb = new ButtonBar();
            ActionButton btnSubmit = new ActionButton(submitURL, "Submit");
            ActionButton btnCancel = new ActionButton("Cancel", returnURL);
            bb.add(btnSubmit);
            bb.add(btnCancel);

            return bb;
        }

        protected abstract DataView getDataView(ListQueryUpdateForm tableForm, URLHelper returnURL, BindException errors);
        protected abstract boolean isInsert();
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class InsertAction extends InsertUpdateAction
    {
        @Override
        protected DataView getDataView(ListQueryUpdateForm tableForm, URLHelper returnURL, BindException errors)
        {
            InsertView view = new InsertView(tableForm, errors);
            if (errors.getErrorCount() == 0)
            {
                Map<String, Object> defaults = new HashMap<String, Object>();
                Map<DomainProperty, Object> domainDefaults = DefaultValueService.get().getDefaultValues(getContainer(), tableForm.getDomain(), getUser());
                for (Map.Entry<DomainProperty, Object> entry : domainDefaults.entrySet())
                {
                    ColumnInfo tempCol = new ColumnInfo(entry.getKey().getName());
                    String formFieldName = tableForm.getFormFieldName(tempCol);
                    defaults.put(formFieldName, entry.getValue());
                }
                view.setInitialValues(defaults);
            }
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


    @RequiresPermissionClass(UpdatePermission.class)
    public class UpdateAction extends InsertUpdateAction
    {
        @Override
        protected DataView getDataView(ListQueryUpdateForm tableForm, URLHelper returnURL, BindException errors)
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


    // Unfortunate query hackery that orders details columns based on default view
    // TODO: Fix this... build into InsertView (or QueryInsertView or something)
    private void setDisplayColumnsFromDefaultView(int listId, DataRegion rgn)
    {
        ListQueryView lqv = new ListQueryView(new ListQueryForm(listId, getViewContext()), (BindException)null);
        List<DisplayColumn> defaultGridColumns = lqv.getDisplayColumns();
        List<DisplayColumn> displayColumns = new ArrayList<DisplayColumn>(defaultGridColumns.size());

        // Save old grid column list
        List<String> currentColumns = rgn.getDisplayColumnNames();

        rgn.setTable(lqv.getTable());

        for (DisplayColumn dc : defaultGridColumns)
        {
            assert null != dc;

            // Occasionally in production this comes back null -- not sure why.  See #8088
            if (null == dc)
                continue;

            if (dc instanceof UrlColumn)
                continue;

            if (dc.getColumnInfo() != null && dc.getColumnInfo().isShownInDetailsView())
            {
                displayColumns.add(dc);
            }
        }

        rgn.setDisplayColumns(displayColumns);

        // Add all columns that aren't in the default grid view
        for (String columnName : currentColumns)
            if (null == rgn.getDisplayColumn(columnName))
                rgn.addColumn(rgn.getTable().getColumn(columnName));
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DetailsAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            TableInfo table = _list.getTable(getUser());

            ListQueryUpdateForm tableForm = new ListQueryUpdateForm(table, getViewContext(), _list, errors);
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
            setDisplayColumnsFromDefaultView(_list.getListId(), details.getDataRegion());

            VBox view = new VBox();
            ListItem item = _list.getListItem(tableForm.getPkVal());

            if (null == item)
                throw new NotFoundException("List item '" + tableForm.getPkVal() + "' does not exist");

            view.addView(details);

            if (form.isShowHistory())
            {
                WebPartView linkView = new HtmlView("[<a href=\"" + PageFlowUtil.filter(getViewContext().cloneActionURL().deleteParameter("showHistory")) + "\">hide item history</a>]");
                linkView.setFrame(WebPartView.FrameType.NONE);
                view.addView(linkView);
                WebPartView history = ListAuditViewFactory.getInstance().createListItemDetailsView(getViewContext(), item.getEntityId());
                history.setFrame(WebPartView.FrameType.NONE);
                view.addView(history);
            }
            else
            {
                view.addView(new HtmlView("[<a href=\"" + PageFlowUtil.filter(getViewContext().cloneActionURL().addParameter("showHistory", "1")) + "\">show item history</a>]"));
            }

            if (_list.getDiscussionSetting().isLinked())
            {
                String entityId = item.getEntityId();

                DomainProperty titleProperty = _list.getDomain().getPropertyByName(_list.getTable(getUser()).getTitleColumn());
                Object title = (null != titleProperty ? item.getProperty(titleProperty) : null);
                String discussionTitle = (null != title ? title.toString() : "Item " + tableForm.getPkVal());

                ActionURL linkBackURL = _list.urlFor(Action.resolve).addParameter("entityId", entityId);
                DiscussionService.Service service = DiscussionService.get();
                boolean multiple = _list.getDiscussionSetting() == ListDefinition.DiscussionSetting.ManyPerItem;

                // Display discussion by default in single-discussion case, #4529
                DiscussionService.DiscussionView discussion = service.getDisussionArea(getViewContext(), entityId, linkBackURL, discussionTitle, multiple, !multiple);
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

        public ListQueryUpdateForm(TableInfo table, ViewContext ctx, ListDefinition list, BindException errors)
        {
            super(table, ctx, errors);
            _list = list;
        }

        public Object[] getPkVals()
        {
            Object[] pks = super.getPkVals();
            assert 1 == pks.length;
            pks[0] = _list.getKeyType().convertKey(pks[0]);
            return pks;
        }

        public Domain getDomain()
        {
            return _list != null ? _list.getDomain() : null;
        }
    }


    // Users can change the PK of a list item, so we don't want to store PK in discussion source URL (back link
    // from announcements to the object).  Instead, we tell discussion service to store a URL with ListId and
    // EntityId.  This action resolves to the current details URL for that item.
    @RequiresPermissionClass(ReadPermission.class)
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


    @RequiresPermissionClass(InsertPermission.class)
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
            if (form.ff_data == null)
            {
                errors.reject(ERROR_MSG, "Form contains no data");
                return false;
            }

            TabLoader tl = new TabLoader(form.ff_data, true);
            _list = form.getList();

            List<String> errorList = _list.insertListItems(getUser(), tl, null, null);

            if (errorList.isEmpty())
                return true;

            for (String error : errorList)
            {
                errors.reject(ERROR_MSG, error);
            }
            return false;
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

    @RequiresPermissionClass(ReadPermission.class)
    public class HistoryAction extends SimpleViewAction<ListQueryForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListQueryForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            if (_list != null)
                return ListAuditViewFactory.getInstance().createListHistoryView(getViewContext(), _list);
            else
                return new HtmlView("Unable to find the specified List");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, _list.getName() + ":History");
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class HistoryDetailAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            int id = NumberUtils.toInt((String)getViewContext().get("eventId"));
            AuditLogEvent event = AuditLogService.get().getEvent(id);

            if (event != null)
                return new JspView<AuditLogEvent>(this.getClass(), "historyDetail.jsp", event);
            else
                return HttpView.throwNotFound("Unable to find the audit history detail for this event");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "Audit History Detail");
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ListItemDetailsAction extends SimpleViewAction
    {
        private ListDefinition _list;

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int id = NumberUtils.toInt((String)getViewContext().get("rowId"));
            int listId = NumberUtils.toInt((String)getViewContext().get("listId"));
            _list = ListService.get().getList(getContainer(), listId);
            if (_list == null)
                HttpView.throwNotFound();

            AuditLogEvent event = AuditLogService.get().getEvent(id);
            if (event != null && event.getLsid() != null)
            {
                Map<String, Object> dataMap = OntologyManager.getProperties(ContainerManager.getSharedContainer(), event.getLsid());
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
                        return new ItemDetails(event, oldRecord, newRecord, isEncoded, getViewContext().getActionURL().getParameter("redirectURL"));
                    }
                    else
                        return new HtmlView("No details available for this event.");
                }
            }
            return HttpView.throwNotFound("Unable to find the audit history detail for this event");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_list != null)
                return appendListNavTrail(root, _list, "List Item Details");
            else
                return root.addChild("List Item Details"); 
        }
    }


    private static class ItemDetails extends WebPartView
    {
        AuditLogEvent _event;
        String _oldRecord;
        String _newRecord;
        boolean _isEncoded;
        String _returnUrl;

        public ItemDetails(AuditLogEvent event, String oldRecord, String newRecord, boolean isEncoded, String returnUrl)
        {
            _event = event;
            _oldRecord = oldRecord;
            _newRecord = newRecord;
            _isEncoded = isEncoded;
            _returnUrl = returnUrl;
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
                out.write("<tr><td>");
                if (_returnUrl != null)
                    out.write(PageFlowUtil.generateButton("Done", _returnUrl));
                out.write("</tr></td>");
                out.write("<tr><td></td></tr>");

                out.write("<tr class=\"labkey-wp-header\"><th align=\"left\">Item Changes</th></tr>");
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
            out.write("<tr class=\"labkey-wp-header\"><th colspan=\"2\" align=\"left\">Item Changes</th></tr>");
            out.write("<tr><td colspan=\"2\">Comment:&nbsp;<i>" + PageFlowUtil.filter(_event.getComment()) + "</i></td></tr>");
            out.write("<tr><td/>\n");

            for (Map.Entry<String, String> entry : prevProps.entrySet())
            {
                String newValue = newProps.remove(entry.getKey());
                if (!ObjectUtils.equals(newValue, entry.getValue()))
                {
                    out.write("<tr><td class=\"labkey-form-label\">");
                    out.write(entry.getKey());
                    out.write("</td><td>");

                    modified++;
                    out.write(entry.getValue());
                    out.write("&nbsp;&raquo;&nbsp;");
                    out.write(ObjectUtils.toString(newValue, ""));
                    out.write("</td></tr>\n");
                }
            }

            for (Map.Entry<String, String> entry : newProps.entrySet())
            {
                modified++;
                out.write("<tr><td class=\"labkey-form-label\">");
                out.write(entry.getKey());
                out.write("</td><td>");

                out.write("&nbsp;&raquo;&nbsp;");
                out.write(ObjectUtils.toString(entry.getValue(), ""));
                out.write("</td></tr>\n");
            }
            out.write("<tr><td/>\n");
            out.write("<tr><td colspan=\"2\">Summary:&nbsp;<i>");
            out.write(modified + " field(s) were modified</i></td></tr>");

            out.write("<tr><td>&nbsp;</td></tr>");
            out.write("<tr><td>");
            if (_returnUrl != null)
                out.write(PageFlowUtil.generateButton("Done", _returnUrl));
            out.write("</tr></td>");

            out.write("</table>\n");
        }
    }


    public static ActionURL getDownloadURL(Container c, String entityId, String filename)
    {
        return new DownloadURL(DownloadAction.class, c, entityId, filename);
    }


    @RequiresPermissionClass(ReadPermission.class)
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


    @RequiresPermissionClass(DesignListPermission.class)
    public class ExportAllListsAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            Container c = getContainer();
            ListWriter writer = new ListWriter();
            ZipFile zip = new ZipFile(response, c.getName() + "_lists.zip");
            writer.write(c, getUser(), zip);
            zip.close();
        }
    }


    @RequiresPermissionClass(DesignListPermission.class)
    public class ImportListsAction extends FormViewAction<Object>
    {
        public void validateCommand(Object target, Errors errors)
        {
        }

        public ModelAndView getView(Object o, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<Object>(this.getClass(), "importLists.jsp", null, errors);
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Map<String, MultipartFile> map = getFileMap();

            if (map.isEmpty())
            {
                errors.reject("listImport", "You must select a .list.zip file to import.");
            }
            else if (map.size() > 1)
            {
                errors.reject("listImport", "Only one file is allowed.");
            }
            else
            {
                MultipartFile file = map.values().iterator().next();

                if (0 == file.getSize() || StringUtils.isBlank(file.getOriginalFilename()))
                {
                    errors.reject("listImport", "You must select a .list.zip file to import.");
                }
                else
                {
                    InputStream is = file.getInputStream();

                    File dir = FileUtil.createTempDirectory("list");
                    ZipUtil.unzipToDirectory(is, dir);

                    ListImporter li = new ListImporter();
                    li.process(dir, dir, getContainer(), getUser(), Logger.getLogger(ListController.class));
                }
            }

            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(Object o)
        {
            return getBeginURL(getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Import List Archive");
        }
    }


    public enum Action
    {
        defineAndImportList,
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
