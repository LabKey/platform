/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.list.controllers;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.StaticLoggerGetter;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.BaseDownloadAction;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.view.AuditChangesView;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UrlColumn;
import org.labkey.api.defaults.ClearDefaultValuesAction;
import org.labkey.api.defaults.SetDefaultValuesAction;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.list.ListUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainAuditProvider;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.query.AbstractQueryImportAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpPostRedirectView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.ZipFile;
import org.labkey.list.model.ListAuditProvider;
import org.labkey.list.model.ListDefinitionImpl;
import org.labkey.list.model.ListEditorServiceImpl;
import org.labkey.list.model.ListManager;
import org.labkey.list.model.ListManagerSchema;
import org.labkey.list.model.ListWriter;
import org.labkey.list.view.ListDefinitionForm;
import org.labkey.list.view.ListImportServiceImpl;
import org.labkey.list.view.ListItemAttachmentParent;
import org.labkey.list.view.ListQueryForm;
import org.labkey.list.view.ListQueryView;
import org.springframework.beans.PropertyValue;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: adam
 * Date: Dec 30, 2007
 * Time: 12:44:30 PM
 */
public class ListController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ListController.class,
            ClearDefaultValuesAction.class
            );

    public ListController()
    {
        setActionResolver(_actionResolver);
    }


    private NavTree appendRootNavTrail(NavTree root)
    {
        return appendRootNavTrail(root, getContainer(), getUser());
    }

    public static class ListUrlsImpl implements ListUrls
    {
        @Override
        public ActionURL getManageListsURL(Container c)
        {
            return new ActionURL(ListController.BeginAction.class, c);
        }

        @Override
        public ActionURL getCreateListURL(Container c)
        {
            return new ActionURL(EditListDefinitionAction.class, c);
        }

    }


    public static NavTree appendRootNavTrail(NavTree root, Container c, User user)
    {
        if (c.hasPermission(user, AdminPermission.class) || user.isDeveloper())
        {
            root.addChild("Lists", getBeginURL(c));
        }
        return root;
    }


    private NavTree appendListNavTrail(NavTree root, ListDefinition list, @Nullable String title)
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

    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig config = super.defaultPageConfig();
        return config.setHelpTopic(new HelpTopic("lists"));
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction<QueryForm>
    {
        @Override
        public ModelAndView getView(QueryForm queryForm, BindException errors) throws Exception
        {
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), ListManagerSchema.SCHEMA_NAME);
            QuerySettings settings = schema.getSettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, ListManagerSchema.LIST_MANAGER);
            return schema.createView(getViewContext(), settings, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Available Lists");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DomainImportServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new ListImportServiceImpl(getViewContext());
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class ShowListDefinitionAction extends SimpleRedirectAction<ListDefinitionForm>
    {
        @Override
        public ActionURL getRedirectURL(ListDefinitionForm listDefinitionForm) throws Exception
        {
            if (listDefinitionForm.getListId() == null)
            {
                throw new NotFoundException();
            }
            return new ActionURL(EditListDefinitionAction.class, getContainer()).addParameter("listId", listDefinitionForm.getListId().intValue());
        }
    }


    @RequiresPermission(DesignListPermission.class)
    public class EditListDefinitionAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            _list = null;

            boolean createList = (null == form.getListId() || 0 == form.getListId()) && form.getName() == null;
            if (!createList)
                _list = form.getList();

            Map<String, String> props = new HashMap<>();

            URLHelper returnURL = form.getReturnURLHelper();

            props.put("listId", null == _list ? "0" : String.valueOf(_list.getListId()));
            props.put(ActionURL.Param.returnUrl.name(), returnURL.toString());
            props.put("allowFileLinkProperties", "0");
            props.put("allowAttachmentProperties", "1");
            props.put("showDefaultValueSettings", "1");
            props.put("hasDesignListPermission", getContainer().hasPermission(getUser(), DesignListPermission.class) ? "true":"false");
            props.put("hasInsertPermission", getContainer().hasPermission(getUser(), InsertPermission.class) ? "true":"false");
            // Why is this different than DesignListPermission???
            props.put("hasDeleteListPermission", getContainer().hasPermission(getUser(), AdminPermission.class) ? "true":"false");
            props.put("loading", "Loading...");

            return new GWTView("org.labkey.list.Designer", props);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (null == _list)
                root.addChild("Create new List");
            else
                appendListNavTrail(root, _list, null);
            return root;
        }
    }


    @RequiresPermission(AdminPermission.class)
    @Action(ActionType.SelectMetaData.class)
    public class ListEditorServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new ListEditorServiceImpl(getViewContext());
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class DeleteListDefinitionAction extends ConfirmAction<ListDefinitionForm>
    {
        private ArrayList<Integer> _listIDs = new ArrayList<>();
        private ArrayList<Container> _containers = new ArrayList<>();

        public void validateCommand(ListDefinitionForm form, Errors errors)
        {
            if (form.getListId() == null)
            {
                String failMessage = "You do not have permission to delete: \n";
                Set<String> listIDs = DataRegionSelection.getSelected(form.getViewContext(), true);
                for (String s : listIDs)
                {
                    String[] parts = s.split(",");
                    Container c = ContainerManager.getForId(parts[1]);
                    if(c.hasPermission(getUser(), AdminPermission.class)){
                        _listIDs.add(Integer.parseInt(parts[0]));
                        _containers.add(c);
                    }
                    else
                    {
                        failMessage = failMessage + "\t" + ListService.get().getList(c, Integer.parseInt(parts[0])).getName() + " in Container: " + c.getName() +"\n";
                    }
                }
                if(!failMessage.equals("You do not have permission to delete: \n"))
                    errors.reject("DELETE PERMISSION ERROR", failMessage);
            }
            else
            {
                //Accessed from the edit list page, where selection is not possible
                _listIDs.add(form.getListId());
                _containers.add(getContainer());
            }
        }

        @Override
        public ModelAndView getConfirmView(ListDefinitionForm form, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/list/view/deleteListDefinition.jsp", form, errors);
        }

        public boolean handlePost(ListDefinitionForm form, BindException errors) throws Exception
        {
            for(int i = 0; i < _listIDs.size(); i++)
            {
                ListDefinition listDefinition = ListService.get().getList(_containers.get(i), _listIDs.get(i));
                if (null != listDefinition)
                {
                    try
                    {
                        listDefinition.delete(getUser());
                    }
                    catch (Exception e)
                    {
                        errors.reject(ERROR_MSG, "Error deleting list '" + listDefinition.getName() + "'; another user may have deleted it.");
                    }
                }
            }
            if (errors.hasErrors())
                return false;
            return true;
        }

        @NotNull
        public URLHelper getSuccessURL(ListDefinitionForm form)
        {
            return form.getReturnURLHelper(getBeginURL(getContainer()));
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class GridAction extends SimpleViewAction<ListQueryForm>
    {
        private ListDefinition _list;
        private String _title;

        public ModelAndView getView(ListQueryForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            if (null == _list)
                throw new NotFoundException("List does not exist in this container");

            ListQueryView view = new ListQueryView(form, errors);

            TableInfo ti = view.getTable();
            if (ti != null)
            {
                _title = ti.getTitle();
            }

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, _title);
        }
    }


    public abstract class InsertUpdateAction extends FormViewAction<ListDefinitionForm>
    {
        protected abstract ActionURL getActionView(ListDefinition list, BindException errors);
        protected abstract Collection<Pair<String, String>> getInputs(ListDefinition list, ActionURL url, PropertyValue[] propertyValues);

        @Override
        public void validateCommand(ListDefinitionForm form, Errors errors)
        {
            /* No-op */
        }

        @Override
        public ModelAndView getView(ListDefinitionForm form, boolean reshow, BindException errors) throws Exception
        {
            ListDefinition list = form.getList(); // throws NotFoundException

            ActionURL url = getActionView(list, errors);
            Collection<Pair<String, String>> inputs = getInputs(list, url, getPropertyValues().getPropertyValues());

            if (getViewContext().getRequest().getMethod().equalsIgnoreCase("POST"))
            {
                getPageConfig().setTemplate(PageConfig.Template.None);
                return new HttpPostRedirectView(url.toString(), inputs);
            }

            throw new RedirectException(url);
        }

        @Override
        public boolean handlePost(ListDefinitionForm form, BindException errors) throws Exception
        {
            return true;
        }

        @Override
        public URLHelper getSuccessURL(ListDefinitionForm form)
        {
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    /**
     * DO NOT USE. This action has been deprecated in 13.2 in favor of the standard query/insertQueryRow action.
     * Only here for backwards compatibility to resolve requests and redirect.
     */
    @Deprecated
    @RequiresPermission(InsertPermission.class)
    public class InsertAction extends InsertUpdateAction
    {
        @Override
        protected ActionURL getActionView(ListDefinition list, BindException errors)
        {
            TableInfo listTable = list.getTable(getUser());
            return listTable.getUserSchema().getQueryDefForTable(listTable.getName()).urlFor(QueryAction.insertQueryRow, getContainer());
        }

        @Override
        protected Collection<Pair<String, String>> getInputs(ListDefinition list, ActionURL url, PropertyValue[] propertyValues)
        {
            Collection<Pair<String, String>> inputs = new ArrayList<>();

            for (PropertyValue value : propertyValues)
            {
                if (value.getName().equalsIgnoreCase("returnURL"))
                    url.addParameter("returnUrl", (String) value.getValue());
                else
                    inputs.add(Pair.of(value.getName(), value.getValue().toString()));
            }

            return inputs;
        }
    }


    /**
     * DO NOT USE. This action has been deprecated in 13.2 in favor of the standard query/updateQueryRow action.
     * Only here for backwards compatibility to resolve requests and redirect.
     */
    @Deprecated
    @RequiresPermission(UpdatePermission.class)
    public class UpdateAction extends InsertUpdateAction
    {
        @Override
        protected ActionURL getActionView(ListDefinition list, BindException errors)
        {
            TableInfo listTable = list.getTable(getUser());
            return listTable.getUserSchema().getQueryDefForTable(listTable.getName()).urlFor(QueryAction.updateQueryRow, getContainer());
        }

        @Override
        protected Collection<Pair<String, String>> getInputs(ListDefinition list, ActionURL url, PropertyValue[] propertyValues)
        {
            Collection<Pair<String, String>> inputs = new ArrayList<>();
            final String FORM_PREFIX = "quf_";

            for (PropertyValue value : getPropertyValues().getPropertyValues())
            {
                if (value.getName().equalsIgnoreCase("returnURL"))
                    url.addParameter("returnUrl", (String) value.getValue());
                else if (value.getName().equalsIgnoreCase(list.getKeyName()) || (FORM_PREFIX + list.getKeyName()).equalsIgnoreCase(value.getName()))
                {
                    url.addParameter(list.getKeyName(), (String) value.getValue());
                    inputs.add(Pair.of(value.getName(), value.getValue().toString()));
                }
                else
                    inputs.add(Pair.of(value.getName(), value.getValue().toString()));
            }

            // support for old values
            try
            {
                // convert to map
                HashMap<String, Object> oldValues = new HashMap<>();
                for (Pair<String, String> entry : inputs)
                {
                    oldValues.put(entry.getKey().replace(FORM_PREFIX, ""), entry.getValue());
                }
                inputs.add(Pair.of(DataRegion.OLD_VALUES_NAME, PageFlowUtil.encodeObject(oldValues)));
            }
            catch (IOException e)
            {
                throw new RuntimeException("Bad " + DataRegion.OLD_VALUES_NAME + " on List.UpdateAction");
            }


            return inputs;
        }
    }


    // Unfortunate query hackery that orders details columns based on default view
    // TODO: Fix this... build into InsertView (or QueryInsertView or something)
    private void setDisplayColumnsFromDefaultView(int listId, DataRegion rgn)
    {
        ListQueryView lqv = new ListQueryView(new ListQueryForm(listId, getViewContext()), null);
        List<DisplayColumn> defaultGridColumns = lqv.getDisplayColumns();
        List<DisplayColumn> displayColumns = new ArrayList<>(defaultGridColumns.size());

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


    @RequiresPermission(ReadPermission.class)
    public class DetailsAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            TableInfo table = _list.getTable(getUser(), getContainer());

            if (null == table)
                throw new NotFoundException("List does not exist");

            ListQueryUpdateForm tableForm = new ListQueryUpdateForm(table, getViewContext(), _list, errors);
            DetailsView details = new DetailsView(tableForm);

            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);

            ActionButton gridButton;
            ActionURL gridUrl = _list.urlShowData(getViewContext().getContainer());
            gridButton = new ActionButton("Show Grid", gridUrl);

            if (table.hasPermission(getUser(), UpdatePermission.class))
            {
                ActionURL updateUrl = _list.urlUpdate(getUser(), getContainer(), tableForm.getPkVal(), gridUrl);
                ActionButton editButton = new ActionButton("Edit", updateUrl);
                bb.add(editButton);
            }

            bb.add(gridButton);
            details.getDataRegion().setButtonBar(bb);
            setDisplayColumnsFromDefaultView(_list.getListId(), details.getDataRegion());

            VBox view = new VBox();
            ListItem item;
            item = _list.getListItem(tableForm.getPkVal(), getUser(), getContainer());

            if (null == item)
                throw new NotFoundException("List item '" + tableForm.getPkVal() + "' does not exist");

            view.addView(details);

            if (form.isShowHistory())
            {
                WebPartView linkView = new HtmlView(PageFlowUtil.textLink("hide item history", getViewContext().cloneActionURL().deleteParameter("showHistory")));
                linkView.setFrame(WebPartView.FrameType.NONE);
                view.addView(linkView);

                UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
                if (schema != null)
                {
                    QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);

                    SimpleFilter filter = new SimpleFilter();
                    filter.addCondition(FieldKey.fromParts(ListAuditProvider.COLUMN_NAME_LIST_ITEM_ENTITY_ID), item.getEntityId());

                    settings.setBaseFilter(filter);
                    settings.setQueryName(ListManager.LIST_AUDIT_EVENT);
                    QueryView history = schema.createView(getViewContext(), settings, errors);

                    history.setTitle("List Item History:");
                    history.setFrame(WebPartView.FrameType.NONE);
                    view.addView(history);
                }
            }
            else
            {
                view.addView(new HtmlView(PageFlowUtil.textLink("show item history", getViewContext().cloneActionURL().addParameter("showHistory", "1"))));
            }

            if (_list.getDiscussionSetting().isLinked() && LookAndFeelProperties.getInstance(getContainer()).isDiscussionEnabled())
            {
                String entityId = item.getEntityId();

                DomainProperty titleProperty = null;
                Domain d = _list.getDomain();
                if (null != d)
                    titleProperty = d.getPropertyByName(table.getTitleColumn());

                Object title = (null != titleProperty ? item.getProperty(titleProperty) : null);
                String discussionTitle = (null != title ? title.toString() : "Item " + tableForm.getPkVal());

                ActionURL linkBackURL = _list.urlFor(ResolveAction.class).addParameter("entityId", entityId);
                DiscussionService service = DiscussionService.get();
                boolean multiple = _list.getDiscussionSetting() == ListDefinition.DiscussionSetting.ManyPerItem;

                // Display discussion by default in single-discussion case, #4529
                DiscussionService.DiscussionView discussion = service.getDiscussionArea(getViewContext(), entityId, linkBackURL, discussionTitle, multiple, !multiple);
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
    @RequiresPermission(ReadPermission.class)
    public class ResolveAction extends SimpleRedirectAction<ListDefinitionForm>
    {
        public ActionURL getRedirectURL(ListDefinitionForm form) throws Exception
        {
            ListDefinition list = form.getList();
            ListItem item = list.getListItemForEntityId(getViewContext().getActionURL().getParameter("entityId"), getUser()); // TODO: Use proper form, validate
            ActionURL url = getViewContext().cloneActionURL().setAction(DetailsAction.class);   // Clone to preserve discussion params
            url.deleteParameter("entityId");
            url.addParameter("pk", item.getKey().toString());

            return url;
        }
    }


    @RequiresPermission(InsertPermission.class)
    public class UploadListItemsAction extends AbstractQueryImportAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        public UploadListItemsAction()
        {
            super(ListDefinitionForm.class);
        }
        
        @Override
        protected void initRequest(ListDefinitionForm form) throws ServletException
        {
            _list = form.getList();
            setTarget(_list.getTable(getUser(), getContainer()));
        }

        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            initRequest(form);
            return getDefaultImportView(form, errors);
        }

        @Override
        protected int importData(DataLoader dl, FileStream file, String originalName, BatchValidationException errors) throws IOException
        {
            int count = _list.insertListItems(getUser(),getContainer() , dl, errors, null, null, false, _importLookupByAlternateKey);
            return count;
        }

        @Override
        protected void validatePermission(User user, BindException errors)
        {
            super.validatePermission(user, errors);
            if (!_list.getAllowUpload())
                errors.reject(SpringActionController.ERROR_MSG, "This list does not allow uploading data");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendListNavTrail(root, _list, "Import Data");
        }
    }

    
    @RequiresPermission(ReadPermission.class)
    public class HistoryAction extends SimpleViewAction<ListQueryForm>
    {
        private ListDefinition _list;

        public ModelAndView getView(ListQueryForm form, BindException errors) throws Exception
        {
            _list = form.getList();
            if (_list != null)
            {
                UserSchema schema = AuditLogService.getAuditLogSchema(getUser(), getContainer());
                if (schema != null)
                {
                    VBox box = new VBox();
                    String domainUri = _list.getDomain().getTypeURI();

                    // list audit events
                    QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);
                    SimpleFilter eventFilter = new SimpleFilter();
                    eventFilter.addCondition(FieldKey.fromParts(ListManager.LISTID_FIELD_NAME), _list.getListId());
                    settings.setBaseFilter(eventFilter);
                    settings.setQueryName(ListManager.LIST_AUDIT_EVENT);

                    QueryView view = schema.createView(getViewContext(), settings, errors);
                    view.setTitle("List Events");
                    box.addView(view);

                    // domain audit events associated with this list
                    QuerySettings domainSettings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);

                    SimpleFilter domainFilter = new SimpleFilter();
                    domainFilter.addCondition(FieldKey.fromParts(DomainAuditProvider.COLUMN_NAME_DOMAIN_URI), domainUri);
                    domainSettings.setBaseFilter(domainFilter);

                    domainSettings.setQueryName(DomainAuditProvider.EVENT_TYPE);
                    QueryView domainView = schema.createView(getViewContext(), domainSettings, errors);

                    domainView.setTitle("List Design Changes");
                    box.addView(domainView);

                    return box;
                }
                return new HtmlView("Unable to create the List history view");
            }
            else
                return new HtmlView("Unable to find the specified List");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_list != null)
                return appendListNavTrail(root, _list, _list.getName() + ":History");
            else
                return root.addChild(":History");
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class ListItemDetailsAction extends SimpleViewAction
    {
        private ListDefinition _list;

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int id = NumberUtils.toInt((String)getViewContext().get("rowId"));
            int listId = NumberUtils.toInt((String)getViewContext().get("listId"));
            _list = ListService.get().getList(getContainer(), listId);
            if (_list == null)
            {
                return new HtmlView("This list is no longer available.");
            }

            String comment = null;
            String oldRecord = null;
            String newRecord = null;

            ListAuditProvider.ListAuditEvent event = AuditLogService.get().getAuditEvent(getUser(), ListManager.LIST_AUDIT_EVENT, id);

            if (event != null)
            {
                comment = event.getComment();
                oldRecord = event.getOldRecordMap();
                newRecord = event.getNewRecordMap();
            }

            if (!StringUtils.isEmpty(oldRecord) || !StringUtils.isEmpty(newRecord))
            {
                Map<String,String> oldData = ListAuditProvider.decodeFromDataMap(oldRecord);
                Map<String,String> newData = ListAuditProvider.decodeFromDataMap(newRecord);

                String srcUrl = getViewContext().getActionURL().getParameter(ActionURL.Param.redirectUrl);
                if (srcUrl == null)
                    srcUrl = getViewContext().getActionURL().getParameter(ActionURL.Param.returnUrl);
                if (srcUrl == null)
                    srcUrl = getViewContext().getActionURL().getParameter(QueryParam.srcURL);
                if (srcUrl == null)
                    srcUrl = _list.urlFor(ListController.HistoryAction.class, getContainer()).getLocalURIString();
                AuditChangesView view = new AuditChangesView(comment, oldData, newData);
                view.setReturnUrl(srcUrl);

                return view;
            }
            else
                return new HtmlView("No details available for this event.");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_list != null)
                return appendListNavTrail(root, _list, "List Item Details");
            else
                return root.addChild("List Item Details"); 
        }
    }


    public static class ListAttachmentForm extends AttachmentForm
    {
        private int _listId;

        public int getListId()
        {
            return _listId;
        }

        public void setListId(int listId)
        {
            _listId = listId;
        }
    }


    public static ActionURL getDownloadURL(ListDefinition list, String rowEntityId, String name)
    {
        return new ActionURL(DownloadAction.class, list.getContainer())
            .addParameter("listId", list.getListId())
            .addParameter("entityId", rowEntityId)
            .addParameter("name", name);
    }

    @RequiresPermission(ReadPermission.class)
    public class DownloadAction extends BaseDownloadAction<ListAttachmentForm>
    {
        @Nullable
        @Override
        public Pair<AttachmentParent, String> getAttachment(ListAttachmentForm form)
        {
            ListDefinitionImpl listDef = (ListDefinitionImpl)ListService.get().getList(getContainer(), form.getListId());
            if (listDef == null)
                throw new NotFoundException("List does not exist in this container");

            if (!listDef.hasListItemForEntityId(form.getEntityId(), getUser()))
                throw new NotFoundException("List does not have an item for the entityid");

            AttachmentParent parent = new ListItemAttachmentParent(form.getEntityId(), getContainer());

            return new Pair<>(parent, form.getName());
        }
    }


    @RequiresPermission(DesignListPermission.class)
    public class ExportListArchiveAction extends ExportAction<ListDefinitionForm>
    {
        public void export(ListDefinitionForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            Set<String> listIDs = DataRegionSelection.getSelected(form.getViewContext(), true);
            Integer[] IDs = new Integer[listIDs.size()];
            int i = 0;
            for(String s : listIDs)
            {
                IDs[i] = Integer.parseInt(s.substring(0, s.indexOf(',')));
                i++;
            }
            Container c = getContainer();
            String datatype = ("lists");
            FolderExportContext ctx = new FolderExportContext(getUser(), c, PageFlowUtil.set(datatype), "List Export", new StaticLoggerGetter(Logger.getLogger(ListController.class)));
            ctx.setListIds(IDs);
            ListWriter writer = new ListWriter();

            try (ZipFile zip = new ZipFile(response, FileUtil.makeFileNameWithTimestamp(c.getName(), "lists.zip")))
            {
                writer.write(c, getUser(), zip, ctx);
            }
        }
    }


    @RequiresPermission(DesignListPermission.class)
    public class ImportListArchiveAction extends FormViewAction<ListDefinitionForm>
    {
        public void validateCommand(ListDefinitionForm target, Errors errors)
        {
        }

        public ModelAndView getView(ListDefinitionForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/list/view/importLists.jsp", null, errors);
        }

        public boolean handlePost(ListDefinitionForm form, BindException errors) throws Exception
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
                    ListService.get().importListArchive(file.getInputStream(), errors, getContainer(), getUser());
                }
            }

            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(ListDefinitionForm form)
        {
            return form.getReturnActionURL( getBeginURL(getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendRootNavTrail(root).addChild("Import List Archive");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class BrowseListsAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            response.put("lists", getJSONLists(ListService.get().getLists(getContainer())));
            response.put("success", true);

            return response;
        }

        private List<JSONObject> getJSONLists(Map<String, ListDefinition> lists){
            List<JSONObject> listsJSON = new ArrayList<>();
            for(ListDefinition def : new TreeSet<>(lists.values())){
                JSONObject listObj = new JSONObject();
                listObj.put("name", def.getName());
                listObj.put("id", def.getListId());
                listObj.put("description", def.getDescription());
                listsJSON.add(listObj);
            }
            return listsJSON;
        }
    }

    @RequiresPermission(DesignListPermission.class)
    public class SetDefaultValuesListAction extends SetDefaultValuesAction
    {
    }
}
