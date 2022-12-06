/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
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
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.audit.view.AuditChangesView;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.defaults.ClearDefaultValuesAction;
import org.labkey.api.defaults.SetDefaultValuesAction;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.list.ListUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainAuditProvider;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.lists.permissions.ManagePicklistsPermission;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.AbstractQueryImportAction;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.RequiresAnyOf;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.PlatformDeveloperPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpPostRedirectView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.ZipFile;
import org.labkey.list.model.ListAuditProvider;
import org.labkey.list.model.ListDef;
import org.labkey.list.model.ListDefinitionImpl;
import org.labkey.list.model.ListDomainKindProperties;
import org.labkey.list.model.ListManager;
import org.labkey.list.model.ListManagerSchema;
import org.labkey.list.model.ListWriter;
import org.labkey.list.view.ListDefinitionForm;
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
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
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ListController.class, ClearDefaultValuesAction.class);

    public ListController()
    {
        setActionResolver(_actionResolver);
    }


    private void addRootNavTrail(NavTree root)
    {
        addRootNavTrail(root, getContainer(), getUser());
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


    public static void addRootNavTrail(NavTree root, Container c, User user)
    {
        if (c.hasOneOf(user, DesignListPermission.class, PlatformDeveloperPermission.class))
        {
            root.addChild("Lists", getBeginURL(c));
        }
    }


    private void addListNavTrail(NavTree root, ListDefinition list, @Nullable String title)
    {
        addRootNavTrail(root);
        root.addChild(list.getName(), list.urlShowData());

        if (null != title)
            root.addChild(title);
    }


    public static ActionURL getBeginURL(Container c)
    {
        return new ActionURL(BeginAction.class, c);
    }

    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig config = super.defaultPageConfig();
        return config.setHelpTopic("lists");
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction<QueryForm>
    {
        @Override
        public ModelAndView getView(QueryForm queryForm, BindException errors)
        {
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), ListManagerSchema.SCHEMA_NAME);
            QuerySettings settings = schema.getSettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, ListManagerSchema.LIST_MANAGER);

            // users should see all lists without a category and public picklists and any lists they created.
            SimpleFilter filter = new SimpleFilter();

            SQLFragment sql = new SQLFragment("Category IS NULL OR Category = '")
                    .append(ListDefinition.Category.PublicPicklist.toString())
                    .append("' OR CreatedBy = ").append(getUser().getUserId());
            filter.addWhereClause(sql, FieldKey.fromParts("Category"), FieldKey.fromParts("CreatedBy"));
            settings.setBaseFilter(filter);

            if (null == StringUtils.trimToNull(settings.getContainerFilterName()))
                settings.setContainerFilterName(ContainerFilter.Type.CurrentPlusProjectAndShared.name());

            return schema.createView(getViewContext(), settings, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Available Lists");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowListDefinitionAction extends SimpleRedirectAction<ListDefinitionForm>
    {
        @Override
        public ActionURL getRedirectURL(ListDefinitionForm listDefinitionForm)
        {
            if (listDefinitionForm.getListId() == null)
            {
                throw new NotFoundException();
            }
            return new ActionURL(EditListDefinitionAction.class, getContainer()).addParameter("listId", listDefinitionForm.getListId().intValue());
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    public class GetListPropertiesAction extends ReadOnlyApiAction<ListDefinitionForm>
    {
        @Override
        public Object execute(ListDefinitionForm form, BindException errors) throws Exception
        {
            ListDomainKindProperties properties = ListManager.get().getListDomainKindProperties(getContainer(), form.getListId());
            if (properties != null)
                return properties;
            else
                throw new NotFoundException("List does not exist in this container for listId " + form.getListId() + ".");
        }
    }

    @RequiresPermission(DesignListPermission.class)
    public class EditListDefinitionAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;
        String listDesignerHeader = "List Designer";

        @Override
        public ModelAndView getView(ListDefinitionForm form, BindException errors)
        {
            _list = null;
            boolean createList = (null == form.getListId() || 0 == form.getListId()) && form.getName() == null;
            if (!createList)
                _list = form.getList();

            return ModuleHtmlView.get(ModuleLoader.getInstance().getModule("core"), ModuleHtmlView.getGeneratedViewPath("listDesigner"));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            if (null == _list)
            {
                root.addChild(listDesignerHeader);
            }
            else
            {
                addListNavTrail(root, _list, listDesignerHeader);
            }
        }
    }

    @RequiresAnyOf({DesignListPermission.class, ManagePicklistsPermission.class})
    public static class DeleteListDefinitionAction extends ConfirmAction<ListDeletionForm>
    {
        private boolean canDelete(Container listContainer, int listId)
        {
            ListDef listDef = ListManager.get().getList(listContainer, listId);
            ListDefinitionImpl list = ListDefinitionImpl.of(listDef);

            if (list == null)
                return false;

            boolean isPicklist = listDef.getCategory() != null;
            if (isPicklist)
            {
                boolean isOwnPicklist = listDef.getCreatedBy() == getUser().getUserId();
                return isOwnPicklist || (listDef.getCategory() == ListDefinition.Category.PublicPicklist && list.getContainer().hasPermission(getUser(), AdminPermission.class));
            }

            return list.getContainer().hasPermission(getUser(), DesignListPermission.class);
        }

        @Override
        public String getConfirmText()
        {
            return "Confirm Delete";
        }

        @Override
        public void validateCommand(ListDeletionForm form, Errors errors)
        {
            if (form.getListId() != null)
            {
                if (canDelete(getContainer(), form.getListId()))
                    form.getListContainerMap().add(Pair.of(form.getListId(), getContainer()));
                else
                    errors.reject(ERROR_MSG, String.format("You do not have permission to delete list %s in container %s", form.getListId(), getContainer().getName()));
            }
            else if (form.getName() != null)
            {
                var list = form.getList();
                if (canDelete(list.getContainer(), list.getListId()))
                    form.getListContainerMap().add(Pair.of(list.getListId(), getContainer()));
                else
                    errors.reject(ERROR_MSG, String.format("You do not have permission to delete list %s in container %s", list.getListId(), getContainer().getName()));
            }
            else
            {
                List<String> errorMessages = new ArrayList<>();
                Collection<String> listIDs;
                if (form.getListIds() != null)
                    listIDs = form.getListIds();
                else
                    listIDs = DataRegionSelection.getSelected(form.getViewContext(), true);

                for (Pair<Integer, Container> pair : getListIdContainerPairs(listIDs, getContainer(), errorMessages))
                {
                    var listId = pair.first;
                    var listContainer = pair.second;

                    if (canDelete(listContainer, listId))
                        form.getListContainerMap().add(pair);
                    else
                        errorMessages.add(String.format("You do not have permission to delete list %s in container %s", listId, listContainer.getName()));
                }

                if (!errorMessages.isEmpty())
                    errors.reject(ERROR_MSG,  StringUtils.join(errorMessages, "\n"));
            }

            if (form.getListContainerMap().isEmpty())
                errors.reject(ERROR_MSG, "You must specify a list or lists to delete.");
        }

        @Override
        public ModelAndView getConfirmView(ListDeletionForm form, BindException errors)
        {
            if (getPageConfig().getTitle() == null)
                setTitle("Confirm Deletion");
            return new JspView<>("/org/labkey/list/view/deleteListDefinition.jsp", form, errors);
        }

        @Override
        public boolean handlePost(ListDeletionForm form, BindException errors)
        {
            for (Pair<Integer, Container> pair : form.getListContainerMap())
            {
                ListDefinition listDefinition = ListService.get().getList(pair.second, pair.first);
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

            return !errors.hasErrors();
        }

        @Override @NotNull
        public URLHelper getSuccessURL(ListDeletionForm form)
        {
            return form.getReturnURLHelper(getBeginURL(getContainer()));
        }
    }

    public static class ListDeletionForm extends ListDefinitionForm
    {
        private List<String> _listIds;
        private final List<Pair<Integer, Container>> _listContainerMap = new ArrayList<>();

        public List<String> getListIds()
        {
            return _listIds;
        }

        public void setListIds(List<String> listIds)
        {
            _listIds = listIds;
        }

        public List<Pair<Integer, Container>> getListContainerMap()
        {
            return _listContainerMap;
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class GridAction extends SimpleViewAction<ListQueryForm>
    {
        private ListDefinition _list;
        private String _title;

        @Override
        public ModelAndView getView(ListQueryForm form, BindException errors)
        {
            _list = form.getList();
            if (null == _list)
                throw new NotFoundException("List does not exist in this container");

            if (!_list.isVisible(getUser()))
                throw new UnauthorizedException("User is not allowed to see this list.");

            ListQueryView view = new ListQueryView(form, errors);

            TableInfo ti = view.getTable();
            if (ti != null)
            {
                _title = ti.getTitle();
            }

            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addListNavTrail(root, _list, _title);
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
        public ModelAndView getView(ListDefinitionForm form, boolean reshow, BindException errors)
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
        public boolean handlePost(ListDefinitionForm form, BindException errors)
        {
            return true;
        }

        @Override
        public URLHelper getSuccessURL(ListDefinitionForm form)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
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
                if (value.getName().equals(ActionURL.Param.returnUrl.toString()))
                {
                    url.addParameter(ActionURL.Param.returnUrl, (String) value.getValue());
                }
                else if (value.getName().equalsIgnoreCase(ActionURL.Param.returnUrl.toString()))
                {
                    ReturnUrlForm.throwBadParam();
                }
                else
                    inputs.add(Pair.of(value.getName(), value.getValue().toString()));
            }

            return inputs;
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class DetailsAction extends SimpleViewAction<ListDefinitionForm>
    {
        private ListDefinition _list;

        @Override
        public ModelAndView getView(ListDefinitionForm form, BindException errors)
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

            VBox view = new VBox();
            ListItem item;
            item = _list.getListItem(tableForm.getPkVal(), getUser(), getContainer());

            if (null == item)
                throw new NotFoundException("List item '" + tableForm.getPkVal() + "' does not exist");

            view.addView(details);

            if (form.isShowHistory())
            {
                WebPartView linkView = new HtmlView(PageFlowUtil.link("hide item history").href(getViewContext().cloneActionURL().deleteParameter("showHistory")).build());
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
                view.addView(new HtmlView(PageFlowUtil.link("show item history").href(getViewContext().cloneActionURL().addParameter("showHistory", "1")).build()));
            }

            if (_list.getDiscussionSetting().isLinked() && LookAndFeelProperties.getInstance(getContainer()).isDiscussionEnabled() && DiscussionService.get() != null)
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
                if (discussion != null)
                {
                    view.addView(discussion);
                    getPageConfig().setFocusId(discussion.getFocusId());
                }
            }

            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addListNavTrail(root, _list, "View List Item");
        }
    }


    // Override to ensure that pk value type matches column type.  This is critical for PostgreSQL 8.3.
    public static class ListQueryUpdateForm extends QueryUpdateForm
    {
        private final ListDefinition _list;

        public ListQueryUpdateForm(TableInfo table, ViewContext ctx, ListDefinition list, BindException errors)
        {
            super(table, ctx, errors);
            _list = list;
        }

        @Override
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
        @Override
        public ActionURL getRedirectURL(ListDefinitionForm form)
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
        private QueryUpdateService.InsertOption _insertOption;

        @Override
        protected void initRequest(ListDefinitionForm form) throws ServletException
        {
            _list = form.getList();
            _insertOption = form.getInsertOption();
            setTarget(_list.getTableForInsert(getUser(), getContainer()));
        }

        @Override
        public ModelAndView getView(ListDefinitionForm form, BindException errors) throws Exception
        {
            initRequest(form);
            setShowImportOptions(_list.getKeyType() != ListDefinition.KeyType.AutoIncrementInteger);
            setSuccessMessageSuffix("imported");
            return getDefaultImportView(form, errors);
        }

        @Override
        protected int importData(DataLoader dl, FileStream file, String originalName, BatchValidationException errors, @Nullable AuditBehaviorType auditBehaviorType, @Nullable TransactionAuditProvider.TransactionAuditEvent auditEvent) throws IOException
        {
            return _list.insertListItems(getUser(), getContainer(), dl, errors, null, null, false, _importLookupByAlternateKey, _insertOption == QueryUpdateService.InsertOption.MERGE);
        }

        @Override
        protected void validatePermission(User user, BindException errors)
        {
            super.validatePermission(user, errors);
            if (!_list.getAllowUpload())
                errors.reject(SpringActionController.ERROR_MSG, "This list does not allow uploading data");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addListNavTrail(root, _list, "Import Data");
        }
    }

    
    @RequiresPermission(ReadPermission.class)
    public class HistoryAction extends SimpleViewAction<ListQueryForm>
    {
        private ListDefinition _list;

        @Override
        public ModelAndView getView(ListQueryForm form, BindException errors)
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

        @Override
        public void addNavTrail(NavTree root)
        {
            if (_list != null)
                addListNavTrail(root, _list, _list.getName() + ":History");
            else
                root.addChild(":History");
        }
    }

    private String getUrlParam(Enum param)
    {
        String s = getViewContext().getActionURL().getParameter(param);
        ReturnUrlForm form = new ReturnUrlForm();
        form.setReturnUrl(s);
        return form.getReturnUrl();
    }

    @RequiresPermission(ReadPermission.class)
    public class ListItemDetailsAction extends SimpleViewAction
    {
        private ListDefinition _list;

        @Override
        public ModelAndView getView(Object o, BindException errors)
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

                String srcUrl = getUrlParam(ActionURL.Param.redirectUrl);
                if (srcUrl == null)
                    srcUrl = getUrlParam(ActionURL.Param.returnUrl);
                if (srcUrl == null)
                    srcUrl = getUrlParam(QueryParam.srcURL);
                if (srcUrl == null)
                    srcUrl = _list.urlFor(ListController.HistoryAction.class, getContainer()).getLocalURIString();
                AuditChangesView view = new AuditChangesView(comment, oldData, newData);
                view.setReturnUrl(srcUrl);

                return view;
            }
            else
                return new HtmlView("No details available for this event.");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            if (_list != null)
                addListNavTrail(root, _list, "List Item Details");
            else
                root.addChild("List Item Details");
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
        @Override
        public void validate(ListAttachmentForm form, BindException errors)
        {
            if (!GUID.isGUID(form.getEntityId()))
            {
                errors.rejectValue("entityId", ERROR_MSG, "entityId is not a GUID: " + form.getEntityId());
            }
        }

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
        @Override
        public void export(ListDefinitionForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            Container c = getContainer();
            List<String> errorMessages = new ArrayList<>();
            Set<String> selection = DataRegionSelection.getSelected(form.getViewContext(), false);
            List<Integer> listIDs = new ArrayList<>();

            // List export is only supported for lists defined in the current folder
            for (Pair<Integer, Container> pair : getListIdContainerPairs(selection, c, errorMessages))
            {
                if (pair.second != c)
                {
                    errorMessages.add(String.format("Cannot export lists defined in %s from %s. List export is only supported for lists defined in the current folder.", pair.second.getPath(), c.getName()));
                    break;
                }
                listIDs.add(pair.first);
            }

            if (!errorMessages.isEmpty())
                throw new IllegalArgumentException(StringUtils.join(errorMessages, "\n"));

            FolderExportContext ctx = new FolderExportContext(getUser(), c, PageFlowUtil.set("lists"), "List Export", new StaticLoggerGetter(LogManager.getLogger(ListController.class)));
            ctx.setListIds(listIDs.toArray(new Integer[0]));
            ListWriter writer = new ListWriter();

            // Export to a temporary file first so exceptions are displayed by the standard error page, Issue #44152
            // Same pattern as ExportFolderAction
            Path tempDir = FileUtil.getTempDirectory().toPath();
            String filename = FileUtil.makeFileNameWithTimestamp(c.getName(), "lists.zip");

            try (ZipFile zip = new ZipFile(tempDir, filename))
            {
                writer.write(c, getUser(), zip, ctx);
            }

            Path tempZipFile = tempDir.resolve(filename);

            // No exceptions, so stream the resulting zip file to the browser and delete it
            try (OutputStream os = ZipFile.getOutputStream(getViewContext().getResponse(), filename))
            {
                Files.copy(tempZipFile, os);
            }
            finally
            {
                Files.delete(tempZipFile);
            }
        }
    }


    @RequiresPermission(DesignListPermission.class)
    public class ImportListArchiveAction extends FormViewAction<ListDefinitionForm>
    {
        @Override
        public void validateCommand(ListDefinitionForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(ListDefinitionForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/list/view/importLists.jsp", null, errors);
        }

        @Override
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

        @Override
        public ActionURL getSuccessURL(ListDefinitionForm form)
        {
            return form.getReturnActionURL( getBeginURL(getContainer()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addRootNavTrail(root);
            root.addChild("Import List Archive");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class BrowseListsAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            response.put("lists", getJSONLists(ListService.get().getLists(getContainer(), getUser(), true)));
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

    /**
     * Utility method to parse out Pair<ListId, Container> from a Collection<String> where the strings are encoded
     * pairs of listIds and container entityIds separated (e.g. "12,ff72c81e-ce2d-103a-b3ce-e8f660509016").
     */
    private static List<Pair<Integer, Container>> getListIdContainerPairs(
        Collection<String> listIdContainers,
        Container currentContainer,
        Collection<String> errors)
    {
        List<Pair<Integer, Container>> pairs = new ArrayList<>();

        for (String s : listIdContainers)
        {
            String[] parts = s.split(",");
            Container c;
            if (parts.length > 1)
                c = ContainerManager.getForId(parts[1]);
            else
                c = currentContainer;
            if (c == null)
            {
                errors.add(String.format("Container not found for %s", s));
                continue;
            }

            try
            {
                int listId = Integer.parseInt(parts[0]);
                pairs.add(Pair.of(listId, c));
            }
            catch (NumberFormatException badListId)
            {
                errors.add(String.format("Invalid listId: %s", s));
            }
        }

        return pairs;
    }
}
