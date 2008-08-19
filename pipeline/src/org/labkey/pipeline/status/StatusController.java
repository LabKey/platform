/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

package org.labkey.pipeline.status;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMessage;
import org.labkey.api.action.*;
import org.labkey.api.data.*;
import org.labkey.api.pipeline.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.pipeline.PipelineController;
import org.labkey.pipeline.api.PipelineEmailPreferences;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.labkey.pipeline.api.PipelineStatusManager;
import static org.labkey.pipeline.api.PipelineStatusManager.*;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URI;
import java.sql.SQLException;
import java.util.*;

public class StatusController extends SpringActionController
{
    private static Logger _log = Logger.getLogger(StatusController.class);
    private static DefaultActionResolver _resolver = new DefaultActionResolver(StatusController.class);

    private static final int MAX_DISPLAY_ROWS = 1000;
    protected static final String _newline = System.getProperty("line.separator");
    protected static final String DATAREGION_STATUS = "dataregion_StatusFiles";

    private static HelpTopic getHelpTopic(String topic)
    {
        return new HelpTopic(topic, HelpTopic.Area.SERVER);
    }
    
    private void reject(Errors errors, String message)
    {
        errors.reject(message);
        addMessageToStrutsHack(message);
    }

    private void reject(BindException errors, String message)
    {
        errors.reject(message);
        addMessageToStrutsHack(message);
    }

    // TODO: Fix spring rejecting to work for showing errors on the grid
    private void addMessageToStrutsHack(String message)
    {
        ActionErrors actionErrors = PageFlowUtil.getActionErrors(getViewContext().getRequest(), true);
        actionErrors.add(DATAREGION_STATUS, new ActionMessage("Error", message));
    }

    public StatusController()
    {
        super();
        setActionResolver(_resolver);
    }

    public static void registerAdminConsoleLinks()
    {
        ActionURL url = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(ContainerManager.getRoot(), false);
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "pipeline", url);
    }

    public PageConfig defaultPageConfig()
    {
        PageConfig p = super.defaultPageConfig();
        p.setHelpTopic(getHelpTopic("pipeline"));
        return p;
    }

    public Container getContainerCheckAdmin() throws ServletException
    {
        Container c = getContainer();
        if (c == null || c.isRoot())
        {
            if (!getUser().isAdministrator())
                HttpView.throwUnauthorized();
        }

        return c;
    }

    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            return urlShowList(getContainer(), false);
        }
    }

    public static ActionURL urlShowList(Container container, boolean lastFilter)
    {
        ActionURL url = new ActionURL(ShowListAction.class, container);
        if (lastFilter)
            url.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
        return url;
    }

    abstract public class ShowListBaseAction<FORM> extends FormViewAction<FORM>
    {
        public ActionURL getSuccessURL(FORM form)
        {
            // Success leads to a reshow of this page with lastfilter=true
            return urlShowList(getContainer(), true);
        }

        public ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception
        {
            Container c = getContainerCheckAdmin();

            setHelpTopic(getHelpTopic("Pipeline-Status/status"));

            GridView gridView = getGridView(c, getUser(), ShowListRegionAction.class);
            gridView.setTitle("Data Pipeline");
            return gridView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Data Pipeline");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowListAction extends ShowListBaseAction
    {
        public void validateCommand(Object target, Errors errors)
        {
            // Direct posts do nothing
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            return true;    // Direct posts do nothing
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowListRegionAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            GridView gridView = getGridView(getContainer(), getUser(), null);
            gridView.render(getViewContext().getRequest(), getViewContext().getResponse());
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowPartRegionAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            GridView gridView = getPartView(getContainer(), getUser(), null);
            gridView.render(getViewContext().getRequest(), getViewContext().getResponse());
            return null;
        }
    }

    public static String urlDetailsData(Container c)
    {
        // Would be better to return an ActionURL, but it doesn't yet
        // seem to support this binding.
        return new ActionURL(DetailsAction.class, c).getLocalURIString() +
                RowIdForm.Params.rowId + "=${RowId}";
    }

    public static ActionURL urlDetails(Container c, int rowId)
    {
        ActionURL url = new ActionURL(DetailsAction.class, c);
        url.addParameter(RowIdForm.Params.rowId, Integer.toString(rowId));
        return url;
    }

    public static ActionURL urlDetails(PipelineStatusFileImpl sf)
    {
        ActionURL url = new ActionURL(DetailsAction.class, sf.lookupContainer());
        url.addParameter(RowIdForm.Params.rowId, Integer.toString(sf.getRowId()));
        return url;
    }
    
    abstract public class DetailsBaseAction<FORM extends RowIdForm> extends FormViewAction<FORM>
    {
        public ActionURL getSuccessURL(FORM form)
        {
            // Just redirect back to this page
            return urlDetails(getContainer(), form.getRowId());
        }

        public ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception
        {
            Container c = getContainerCheckAdmin();

            setHelpTopic(getHelpTopic("Pipeline-Status/details"));

            DataRegion rgn = getDetails(c, getUser(), form.getRowId());
            DetailsView detailsView = new DetailsView(rgn, form.getRowId());
            if (c == null || c.isRoot())
            {
                detailsView.getRenderContext().setUseContainerFilter(false);
                detailsView.getViewContext().setPermissions(ACL.PERM_READ);
            }
            return detailsView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Job Status");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DetailsAction extends DetailsBaseAction<RowIdForm>
    {
        public void validateCommand(RowIdForm target, Errors errors)
        {
            // Do nothing on post
        }

        public boolean handlePost(RowIdForm rowIdForm, BindException errors) throws Exception
        {
            return true;  // Do nothing on post
        }
    }

    public static class RowIdForm
    {
        enum Params { rowId }

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

    @RequiresPermission(ACL.PERM_READ)
    public class ShowDataAction extends SimpleRedirectAction<RowIdForm>
    {
        public ActionURL getRedirectURL(RowIdForm form) throws Exception
        {
            Container c = getContainerCheckAdmin();

            PipelineStatusFile sf = getStatusFile(form.getRowId());

            if (sf.getDataUrl() != null)
            {
                HttpView.throwRedirect(sf.getDataUrl());
            }

            return urlDetails(c, form.getRowId());
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowFolderAction extends SimpleRedirectAction<RowIdForm>
    {
        public ActionURL getRedirectURL(RowIdForm form) throws Exception
        {
            Container c = getContainerCheckAdmin();

            if (c == null || c.isRoot())
            {
                PipelineStatusFileImpl sf = getStatusFile(form.getRowId());
                if (sf.getContainerId() != null)
                    c = ContainerManager.getForId(sf.getContainerId());
            }

            if (c != null)
                HttpView.throwRedirect(ActionURL.toPathString("Project", "begin", c.getPath()));

            return urlDetails(c, form.getRowId());
        }
    }

    @RequiresPermission(ACL.PERM_UPDATE)
    public class ProviderActionAction extends DetailsBaseAction<ProviderActionForm>
    {
        ActionURL _urlSuccess;

        public void validateCommand(ProviderActionForm target, Errors errors)
        {
        }

        public boolean handlePost(ProviderActionForm form, BindException errors) throws Exception
        {
            // Never a post, since buttons user anchor.
            return true;
        }

        public ModelAndView getView(ProviderActionForm form, boolean reshow, BindException errors) throws Exception
        {
            // Looks a lot like post handling might.  Consider making the buttons post.
            getContainerCheckAdmin();

            PipelineStatusFileImpl sf = getStatusFile(form.getRowId());
            if (sf == null)
                return HttpView.throwNotFoundMV();

            PipelineProvider provider = PipelineService.get().getPipelineProvider(sf.getProvider());
            if (provider == null)
                return HttpView.throwNotFoundMV();

            try
            {
                _urlSuccess = provider.handleStatusAction(getViewContext(), form.getName(), sf);
                
                return HttpView.redirect(getSuccessURL(form));
            }
            catch (PipelineProvider.HandlerException e)
            {
                reject(errors, e.getMessage());
            }

            return super.getView(form, reshow, errors);
        }

        public ActionURL getSuccessURL(ProviderActionForm form)
        {
            // Just to be safe, return to the details page, if there is no success URL from
            // the provider.
            
            return (_urlSuccess != null ? _urlSuccess : urlDetails(getContainer(), form.getRowId()));
        }
    }

    public static class ProviderActionForm extends RowIdForm
    {
        private String name;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }
    }

    public static ActionURL urlShowFile(Container c, int rowId, String filename)
    {
        return new ActionURL(ShowFileAction.class, c)
                .addParameter(RowIdForm.Params.rowId, Integer.toString(rowId))
                .addParameter(ShowFileForm.Params.filename, filename);
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowFileAction extends SimpleStreamAction<ShowFileForm>
    {
        public void render(ShowFileForm form, BindException errors, PrintWriter out) throws Exception
        {
            Container c = getContainerCheckAdmin();

            boolean written = false;
            String fileName = "";

            PipelineStatusFile sf = getStatusFile(form.getRowId());
            if (sf != null)
            {
                fileName = form.getFilename();

                if (fileName == null || fileName.length() == 0)
                    fileName = "";
                else
                {
                    File fileStatus = new File(sf.getFilePath());
                    String statusName = fileStatus.getName();
                    String basename = statusName.substring(0, statusName.lastIndexOf('.'));

                    File dir = fileStatus.getParentFile();
                    File fileShow = new File(dir, fileName);

                    if (NetworkDrive.exists(fileShow))
                    {
                        boolean visible = false;

                        String providerName = sf.getProvider();
                        if (providerName == null)
                            visible = isVisibleFile(fileName, basename);
                        else
                        {
                            PipelineProvider provider = PipelineService.get().getPipelineProvider(providerName);
                            if (provider != null)
                                visible = provider.isStatusViewableFile(sf.lookupContainer(), fileName, basename);
                        }

                        if (visible)
                        {
                            renderFile(out, fileShow);
                            written = true;
                        }
                    }
                }
            }

            if (!written)
                out.print("File not found.");
        }
    }

    public static class ShowFileForm extends RowIdForm
    {
        enum Params { filename }

        private String _filename;

        public String getFilename()
        {
            return _filename;
        }

        public void setFilename(String filename)
        {
            this._filename = filename;
        }
    }

    abstract public class PerformStatusActionBase<FORM extends SelectStatusForm>
            extends ShowListBaseAction<FORM>
    {
        public void validateCommand(FORM target, Errors errors)
        {
            Set<String> runs = DataRegionSelection.getSelected(getViewContext(), false);

            int i = 0;
            int[] rowIds = new int[runs.size()];
            for (String run : runs)
            {
                try
                {
                    rowIds[i++] = Integer.parseInt(run);
                }
                catch (NumberFormatException e)
                {
                    reject(errors, "The run " + run + " is not a valid number.");
                    return;
                }
            }
            target.setRowIds(rowIds);
        }

        public boolean handlePost(FORM form, BindException errors) throws Exception
        {
            getContainerCheckAdmin();

            try
            {
                handleSelect(form);
            }
            catch (PipelineProvider.HandlerException e)
            {
                reject(errors, e.getMessage());
                return false;
            }
            catch (PipelineProvider.StatusUpdateException e)
            {
                reject(errors, e.getMessage());
                return false;
            }

            return true;
        }

        abstract public void handleSelect(FORM form) throws Exception;
    }

    public static class SelectStatusForm
    {
        private int[] _rowIds;

        public int[] getRowIds()
        {
            return _rowIds;
        }

        public void setRowIds(int[] rowIds)
        {
            _rowIds = rowIds;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class RunActionAction extends PerformStatusActionBase<ActionForm>
    {
        public boolean handlePost(ActionForm form, BindException errors) throws Exception
        {
            // The method prototype for this method is used to determine the form type,
            // so it must be here, even though it does nothing more than call super.

            return super.handlePost(form, errors);
        }

        public void handleSelect(ActionForm form) throws Exception
        {
            // Let the provider handle the action, if it can.
            for (int rowId : form.getRowIds())
            {
                PipelineStatusFileImpl sf = getStatusFile(rowId);
                if (sf == null)
                    continue;   // Already gone.

                PipelineProvider provider = PipelineService.get().getPipelineProvider(sf.getProvider());
                if (provider != null)
                    provider.handleStatusAction(getViewContext(), form.getAction(), sf);
            }
        }
    }

    public static class ActionForm extends SelectStatusForm
    {
        private String _action;

        public String getAction()
        {
            return _action;
        }

        public void setAction(String action)
        {
            _action = action;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class DeleteStatusAction extends PerformStatusActionBase
    {
        public void handleSelect(SelectStatusForm form) throws Exception
        {
            deleteStatus(getViewBackgroundInfo(), form.getRowIds());
            DataRegionSelection.clearAll(getViewContext());
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class CompleteStatusAction extends PerformStatusActionBase
    {
        public void handleSelect(SelectStatusForm form) throws Exception
        {
            completeStatus(getViewBackgroundInfo(), form.getRowIds());
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class EscalateJobFailureAction extends SimpleViewAction<RowIdForm>
    {
        public ModelAndView getView(RowIdForm form, BindException errors) throws Exception
        {
            DataRegion rgn = new DataRegion();

            rgn.setColumns(getTableInfo().getColumns("Created, Modified, Job, Provider, Email, Status, Info, FilePath, DataUrl"));
            rgn.addDisplayColumn(new FileDisplayColumn());
            DisplayColumn col = rgn.getDisplayColumn("Job");
            col.setVisible(false);
            col = rgn.getDisplayColumn("Provider");
            col.setVisible(false);
            col = rgn.getDisplayColumn("DataUrl");
            col.setVisible(false);
            rgn.setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);

            DetailsView detailsView = new DetailsView(rgn, form.getRowId());
            detailsView.setTitle("Job Status");

            VBox view = new VBox(detailsView);
            PipelineStatusFileImpl sf = getStatusFile(form.getRowId());
            view.addView(new JspView<PipelineStatusFileImpl>("/org/labkey/pipeline/status/escalateJobFailure.jsp", sf));

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Escalate Pipeline Job Failure");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class EscalateAction extends ShowListBaseAction<EscalateMessageForm>
    {
        public void validateCommand(EscalateMessageForm target, Errors errors)
        {
        }

        public boolean handlePost(EscalateMessageForm form, BindException errors) throws Exception
        {
            String recipients = "";
            try
            {
                MailHelper.MultipartMessage m = MailHelper.createMultipartMessage();

                final String message = form.getEscalationMessage() + "\n\nJob details can be found:\n\n" + form.getDetailsUrl();
                m.setBodyContent(message, "text/plain");
                m.setBodyContent(PageFlowUtil.filter(message, true, true), "text/html");
                m.setSubject(form.getEscalationSubject());
                m.addFrom(new Address[]{new InternetAddress(getUser().getEmail(), getUser().getFullName())});

                if (form.getEscalateAll())
                    recipients = PipelineEmailPreferences.get().getEscalationUsers(getContainer());
                else
                    recipients = form.getEscalateUser();
                if (!StringUtils.isEmpty(recipients))
                {
                    m.addRecipients(Message.RecipientType.TO, MailHelper.createAddressArray(recipients));
                    MailHelper.send(m);
                }
            }
            catch (Exception e)
            {
                _log.error("Failed creating an email escalation message for a pipeline job", e);
                if (StringUtils.isEmpty(recipients))
                    reject(errors, "Failed to send email.");
                else
                    reject(errors, "Failed sending email to " + recipients);
                return false;
            }

            return true;
        }
    }

    public static class EscalateMessageForm extends ViewForm
    {
        private String _escalateUser;
        private boolean _escalateAll;
        private String _escalationSubject;
        private String _escalationMessage;
        private String _detailsUrl;

        public void setEscalateUser(String escalateUser)
        {
            _escalateUser = escalateUser;
        }

        public String getEscalateUser()
        {
            return _escalateUser;
        }

        public void setEscalateAll(boolean escalateAll)
        {
            _escalateAll = escalateAll;
        }

        public boolean getEscalateAll()
        {
            return _escalateAll;
        }

        public void setEscalationSubject(String escalationSubject)
        {
            _escalationSubject = escalationSubject;
        }

        public String getEscalationSubject()
        {
            return _escalationSubject;
        }

        public void setEscalationMessage(String escalationMessage)
        {
            _escalationMessage = escalationMessage;
        }

        public String getEscalationMessage()
        {
            return _escalationMessage;
        }

        public void setDetailsUrl(String detailsUrl)
        {
            _detailsUrl = detailsUrl;
        }

        public String getDetailsUrl()
        {
            return _detailsUrl;
        }
    }

    private static class HideShowRetryColumn extends SimpleDisplayColumn
    {
        private ActionButton _btnRetry;
        private Map<String, Set<String>> _mapProvidersContainers = new HashMap<String, Set<String>>();

        public HideShowRetryColumn(ButtonBar bb)
        {
            List<DisplayElement> buttons = bb.getList();
            for (DisplayElement button : buttons)
            {
                if (PipelineProvider.CAPTION_RETRY_BUTTON.equals(button.getCaption()))
                    _btnRetry = (ActionButton) button;
            }
        }

        public boolean supportsRetry(PipelineProvider provider, Container container)
        {
            if (provider == null)
                return false;
            List<PipelineProvider.StatusAction> l = provider.addStatusActions(container);
            if (l == null)
                return false;

            for (PipelineProvider.StatusAction action : l)
            {
                if (PipelineProvider.CAPTION_RETRY_BUTTON.compareTo(action.getLabel()) == 0)
                    return true;
            }

            return false;
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            if (_btnRetry == null)
                return;

            Map cols = ctx.getRow();
            String providerName = (String) cols.get("Provider");
            String containerId = (String) cols.get("Container");
            Set<String> setContainers = _mapProvidersContainers.get(providerName);
            if (setContainers == null)
            {
                setContainers = new HashSet<String>();
                _mapProvidersContainers.put(providerName, setContainers);
            }
            if (!setContainers.contains(containerId))
            {
                setContainers.add(containerId);
                if (supportsRetry(PipelineService.get().getPipelineProvider(providerName),
                        ContainerManager.getForId(containerId)))
                    _btnRetry.setVisible(true);
            }
        }
    }

    public static GridView getGridView(Container c, User user, Class<? extends ApiAction> apiAction)
            throws SQLException
    {
        StatusDataRegion rgn = getGrid(c, user);
        rgn.setApiAction(apiAction);
        GridView gridView = new GridView(rgn);
        if (c == null || c.isRoot())
        {
            gridView.getRenderContext().setUseContainerFilter(false);
            gridView.getViewContext().setPermissions(ACL.PERM_READ);
        }
        gridView.setSort(new Sort("-Created"));
        return gridView;
    }

    public static GridView getPartView(Container c, User user, Class<? extends ApiAction> apiAction) throws SQLException
    {
        URI uriRoot = null;

        PipelineService service = PipelineService.get();
        boolean canModify = service.canModifyPipelineRoot(user, c);
        PipeRoot pr = service.findPipelineRoot(c);
        if (pr != null)
            uriRoot = pr.getUri(c);

        if (uriRoot == null && !canModify)
            return null;

        StatusDataRegion rgn = new StatusDataRegion();
        rgn.setShadeAlternatingRows(true);
        rgn.setShowBorders(true);
        rgn.setApiAction(apiAction);
        rgn.setColumns(PipelineStatusManager.getTableInfo().getColumns("Status, Created, FilePath, Description"));
        DisplayColumn col = rgn.getDisplayColumn("Status");
        col.setURL(urlDetailsData(c));
        col.setNoWrap(true);
        col = rgn.getDisplayColumn("FilePath");
        col.setVisible(false);
        col = rgn.getDisplayColumn("Description");
        col.setVisible(false);
        col = new DescriptionDisplayColumn(uriRoot);
        rgn.addDisplayColumn(col);

        String referer = PipelineController.RefererValues.protal.toString();
        ButtonBar bb = new ButtonBar();

        if (c.hasPermission(user, ACL.PERM_INSERT) && uriRoot != null)
        {
            ActionURL url = PipelineController.urlBrowse(c, referer);
            ActionButton button = new ActionButton(url, "Process and Import Data");
            button.setActionType(ActionButton.Action.GET);
            bb.add(button);
        }

        if (canModify)
        {
            ActionURL url = PipelineController.urlSetup(c, referer);
            ActionButton button = new ActionButton(url, "Setup");
            button.setActionType(ActionButton.Action.GET);
            bb.add(button);
        }

        rgn.setButtonBar(bb, DataRegion.MODE_GRID);

        GridView gridView = new GridView(rgn);
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Status", PipelineJob.COMPLETE_STATUS, CompareType.NEQ);
        gridView.setFilter(filter);
        gridView.setSort(new Sort("-Created"));
        return gridView;
    }

    private static StatusDataRegion getGrid(Container c, User user) throws SQLException
    {
        StatusDataRegion rgn = new StatusDataRegion();
        rgn.setShadeAlternatingRows(true);
        rgn.setShowBorders(true);
        rgn.setColumns(getTableInfo().getColumns("Status, Created, FilePath, Description, Provider, Container"));
        DisplayColumn col = rgn.getDisplayColumn("Status");
        col.setURL(urlDetailsData(c));
        col.setNoWrap(true);
        col = rgn.getDisplayColumn("Description");
        col.setVisible(false);
        col = rgn.getDisplayColumn("Provider");
        col.setVisible(false);
        col = rgn.getDisplayColumn("Container");
        col.setVisible(false);
        col = rgn.getDisplayColumn("FilePath");
        PipelineService service = PipelineService.get();
        boolean canModifyRoot = false;
        URI uriRoot = null;
        if (!c.isRoot())
        {
            canModifyRoot = service.canModifyPipelineRoot(user, c);
            PipeRoot pr = service.findPipelineRoot(c);
            if (pr != null)
            {
                uriRoot = pr.getUri(c);
            }

            col.setVisible(false);
            col = new DescriptionDisplayColumn(uriRoot);
            rgn.addDisplayColumn(col);
        }

        // Make table layout faster on IE
        rgn.setFixedWidthColumns(false);

        ButtonBar bb = new ButtonBar();

        if (c.hasPermission(user, ACL.PERM_INSERT) && uriRoot != null)
        {
            ActionButton button = new ActionButton("browse.view", "Process and Import Data");
            button.setActionType(ActionButton.Action.LINK);
            button.setURL(PipelineController.urlBrowse(c, PipelineController.RefererValues.pipeline.toString()));
            bb.add(button);
        }

        if (canModifyRoot)
        {
            ActionButton button = new ActionButton("setup.view", "Setup");
            button.setActionType(ActionButton.Action.LINK);
            button.setURL(PipelineController.urlSetup(c, PipelineController.RefererValues.pipeline.toString()));
            bb.add(button);
        }

        ActionButton retryStatus = new ActionButton("", PipelineProvider.CAPTION_RETRY_BUTTON);
        retryStatus.setScript("return verifySelected(this.form, \"runAction.view?action=" + PipelineProvider.CAPTION_RETRY_BUTTON + "\", \"post\", \"files\")");
        retryStatus.setActionType(ActionButton.Action.POST);
        if (!user.isAdministrator())
            retryStatus.setDisplayPermission(ACL.PERM_UPDATE);
        retryStatus.setVisible(false);
        bb.add(retryStatus);

        ActionButton deleteStatus = new ActionButton("", "Delete");
        deleteStatus.setScript("return verifySelected(this.form, \"deleteStatus.view\", \"post\", \"files\")");
        deleteStatus.setActionType(ActionButton.Action.POST);
        if (!user.isAdministrator())
            deleteStatus.setDisplayPermission(ACL.PERM_DELETE);
        bb.add(deleteStatus);

        ActionButton completeStatus = new ActionButton("", "Complete");
        completeStatus.setScript("return verifySelected(this.form, \"completeStatus.view\", \"post\", \"files\")");
        completeStatus.setActionType(ActionButton.Action.POST);
        if (!user.isAdministrator())
            completeStatus.setDisplayPermission(ACL.PERM_UPDATE);
        bb.add(completeStatus);

        // Display the "Show Queue" button, if this is not the Enterprise Pipeline,
        // the user is an administrator, and this is the pipeline administration page.
        if (!PipelineService.get().isEnterprisePipeline() &&
                user.isAdministrator() && c.isRoot())
        {
            ActionButton showQueue = new ActionButton((String)null, "Show Queue");
            showQueue.setURL(PipelineController.urlStatus(c, true));
            bb.add(showQueue);
        }

        col = new HideShowRetryColumn(bb);
        col.setWidth("35"); // Match the width if the checkbox.
        rgn.addDisplayColumn(col);

        rgn.setButtonBar(bb, DataRegion.MODE_GRID);
        rgn.setShowRecordSelectors(true);
        rgn.setMaxRows(MAX_DISPLAY_ROWS);

        return rgn;
    }

    private DataRegion getDetails(Container c, User user, int rowId)
    {
        DataRegion rgn = new DataRegion();

        rgn.setColumns(getTableInfo().getColumns("Created, Modified, Job, JobParent, JobStore, Provider, Container, Email, Status, Info, FilePath, DataUrl"));
        rgn.addDisplayColumn(new FileDisplayColumn());
        rgn.addDisplayColumn(new JobDisplayColumn(false));
        rgn.addDisplayColumn(new JobDisplayColumn(true));
        DisplayColumn col = rgn.getDisplayColumn("Job");
        col.setVisible(false);
        col = rgn.getDisplayColumn("JobParent");
        col.setVisible(false);
        col = rgn.getDisplayColumn("JobStore");
        col.setVisible(false);
        col = rgn.getDisplayColumn("Provider");
        col.setVisible(false);
        col = rgn.getDisplayColumn("Container");
        col.setVisible(false);
        col = rgn.getDisplayColumn("DataUrl");
        col.setVisible(false);

        ButtonBar bb = new ProviderButtonBar();

        ActionButton showGrid = new ActionButton("showList.view?.lastFilter=true", "Show Grid");
        showGrid.setActionType(ActionButton.Action.LINK);
        showGrid.setDisplayPermission(ACL.PERM_READ);
        bb.add(showGrid);

        if (c == null || c.isRoot())
        {
            ActionButton showFolder = new ActionButton("showFolder.view?rowId=${rowId}", "Folder");
            showFolder.setActionType(ActionButton.Action.LINK);
            bb.add(showFolder);
        }

        ActionButton showData = new ActionButton("showData.view?rowId=${rowId}", "Data");
        showData.setActionType(ActionButton.Action.LINK);

        try
        {
            showData.setVisibleExpr("null != dataUrl");
        }
        catch (Exception e)
        {
            assert false : "Compile error";
        }

        bb.add(showData);

        // escalate pipeline failure button
        try
        {
            PipelineStatusFile sf = getStatusFile(Integer.valueOf(rowId));
            if (sf != null && !PipelineJob.COMPLETE_STATUS.equals(sf.getStatus()))
            {
                final String escalationUsers = PipelineEmailPreferences.get().getEscalationUsers(c);
                if (!StringUtils.isEmpty(escalationUsers))
                {
                    ActionButton escalate = new ActionButton("escalateJobFailure.view?rowId=${rowId}", "Escalate Job Failure");
                    escalate.setActionType(ActionButton.Action.LINK);
                    bb.add(escalate);
                }
            }
        }
        catch (NumberFormatException nfe)
        {
            _log.error("Invalid row ID showing details.", nfe);
        }
        catch (SQLException e)
        {
            _log.error("Failed setting up escalation action", e);
        }
        rgn.setButtonBar(bb, DataRegion.MODE_DETAILS);

        return rgn;
    }

    private void renderFile(PrintWriter out, File f)
    {
        BufferedReader br = null;
        try
        {
            br = new BufferedReader(new FileReader(f));
            String line;

            while ((line = br.readLine()) != null)
            {
                out.write(line);
                out.write(_newline);
            }
        }
        catch (IOException e)
        {
            out.write("...Error reading file...");
            out.write(_newline);
        }
        finally
        {
            if (br != null)
            {
                try
                {
                    br.close();
                }
                catch (Exception e)
                {
                }
            }
        }
    }

    protected static boolean isVisibleFile(String name, String basename)
    {
        if (name.endsWith(".log") ||
                name.endsWith(".def") ||
                name.endsWith(".def.err") ||
                name.equals("tandem.xml") ||
                name.equals("tandem.xml.err"))
            return true;
        if (!name.startsWith(basename) || name.length() == basename.length() ||
                name.charAt(basename.length()) != '.')
            return false;
        return name.endsWith(".out") || name.endsWith(".err");
    }

    public abstract static class CallbackForm
    {
        private String _callbackPassword;

        public String getCallbackPassword()
        {
            return _callbackPassword;
        }

        public void setCallbackPassword(String callbackPassword)
        {
            _callbackPassword = callbackPassword;
        }
    }

    public static class JobStatusForm extends CallbackForm
    {
        private String _job;
        private String _status;

        public String getJob()
        {
            return _job;
        }

        public void setJob(String job)
        {
            _job = job;
        }

        public String getStatus()
        {
            return _status;
        }

        public void setStatus(String status)
        {
            _status = status;
        }
    }

    public static class RequeueRunningJobsForm extends CallbackForm
    {
        private String _location;

        public String getLocation()
        {
            return _location;
        }

        public void setLocation(String location)
        {
            _location = location;
        }
    }

    /** Used to set job status for Enterprise pipeline */
    @RequiresPermission(ACL.PERM_NONE)
    public class RequeueLostJobsAction extends AbstractCallbackAction<RequeueRunningJobsForm>
    {
        public RequeueLostJobsAction()
        {
            super(RequeueRunningJobsForm.class);
        }

        public void handleCallback(RequeueRunningJobsForm form) throws Exception
        {
            if (PipelineService.get().getPipelineQueue().isLocal())
            {
                // todo: This currently just causes the return code 404 to be displayed during remote
                //       server start-up.  Would be better to give the server administrator this message.
                HttpView.throwNotFound("Remote servers not supported.  Change configuration to use a remote queue.");
            }

            if (null == form.getLocation())
            {
                HttpView.throwNotFound("No location specified");
            }

            List<PipelineJob> pendingJobs = PipelineService.get().getPipelineQueue().findJobs(form.getLocation());
            Set<String> jobIds = new HashSet<String>();
            for (PipelineJob job : pendingJobs)
            {
                jobIds.add(job.getJobGUID().toLowerCase());
            }

            TaskPipelineRegistry registry = PipelineJobService.get();
            for (TaskFactory taskFactory : registry.getTaskFactories())
            {
                if (taskFactory.getExecutionLocation().equals(form.getLocation()))
                {
                    TaskId id = taskFactory.getId();
                    PipelineStatusFileImpl[] statusFiles =
                            PipelineStatusManager.getQueuedStatusFilesForActiveTaskId(id.toString());
                    for (PipelineStatusFileImpl sf : statusFiles)
                    {
                        // NOTE: JobIds end up all uppercase in the database, but they are lowercase in jobs
                        if (sf.getJobStore() != null && !jobIds.contains(sf.getJobId().toLowerCase()))
                            PipelineJobServiceImpl.get().getJobStore().retry(sf);
                    }
                }
            }
        }
    }

    public abstract class AbstractCallbackAction<Form extends CallbackForm> extends SimpleStreamAction<Form>
    {
        public AbstractCallbackAction(Class<? extends Form> c)
        {
            super(c);
        }

        public void render(Form form, BindException errors, PrintWriter out) throws Exception
        {
            if (!PipelineService.get().isEnterprisePipeline())
            {
                HttpView.throwUnauthorized("HTTP access disabled since Enterprise pipeline is not configured");
            }

            if (PipelineJobService.get().getAppProperties().getCallbackPassword() != null &&
                !PipelineJobService.get().getAppProperties().getCallbackPassword().equals(form.getCallbackPassword()))
            {
                HttpView.throwUnauthorized("Invalid callback password");
            }

            handleCallback(form);
        }

        protected abstract void handleCallback(Form form) throws Exception;
    }

    /** Used to set job status for Enterprise pipeline */
    @RequiresPermission(ACL.PERM_NONE)
    public class SetJobStatusAction extends AbstractCallbackAction<JobStatusForm>
    {
        public SetJobStatusAction()
        {
            super(JobStatusForm.class);
        }

        public void handleCallback(JobStatusForm form) throws Exception
        {
            PipelineStatusFileImpl status = PipelineStatusManager.getJobStatusFile(form.getJob());
            if (!status.getContainerId().equals(getContainer().getId()))
            {
                HttpView.throwNotFound("Attempting to set status in wrong container");
            }

            status.setStatus(form.getStatus());
            setStatusFile(getViewBackgroundInfo(), status, false);
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // Perl Pipeline actions

    @RequiresPermission(ACL.PERM_NONE)
    public class SetStatusFileAction extends SimpleStreamAction<StatusFileForm>
    {
        public void setResponseProperties(HttpServletResponse response)
        {
            // No caching, since this really RPC
            response.setHeader("Cache-Control", "no-cache");
        }

        public void render(StatusFileForm form, BindException errors, PrintWriter out) throws Exception
        {
            String status;
            try
            {
                if (!PipelineService.get().usePerlPipeline(getContainer()))
                    status = "ERROR->HTTP status updates disabled";
                else if (!form.isStatusModAllowed())
                    status = "ERROR->Access denied";
                else
                {
                    setStatusFile(getViewBackgroundInfo(), form.getBean(), false);
                    status = "SUCCESS";
                }
            }
            catch (Container.ContainerException e)
            {
                status = "ERROR->message=Wrong status container";
            }
            catch (SQLException e)
            {
                status = "ERROR->message=" + e.getMessage();
            }

            if (status.startsWith("ERROR"))
                getViewContext().getResponse().setStatus(400);    // Bad request.
            out.println(status);
        }
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class RemoveStatusFileAction extends SimpleStreamAction<StatusFileForm>
    {
        public void setResponseProperties(HttpServletResponse response)
        {
            // No caching, since this really RPC
            response.setHeader("Cache-Control", "no-cache");
        }

        public void render(StatusFileForm form, BindException errors, PrintWriter out) throws Exception
        {
            String status;
            try
            {
                if (!PipelineService.get().usePerlPipeline(getContainer()))
                    status = "ERROR->HTTP status updates disabled";
                else if (!form.isStatusModAllowed())
                    status = "ERROR->Access denied";
                else
                {
                    removeStatusFile(getViewBackgroundInfo(), form.getBean());
                    status = "SUCCESS";
                }
            }
            catch (SQLException e)
            {
                status = "ERROR->message=" + e.getMessage();
            }

            if (status.startsWith("ERROR"))
                getViewContext().getResponse().setStatus(400);    // Bad request.
            out.println(status);
        }
    }

    public static class StatusFileForm extends BeanViewForm<PipelineStatusFileImpl>
    {
        public StatusFileForm()
        {
            super(PipelineStatusFileImpl.class);
        }

        public boolean isStatusModAllowed() throws SQLException
        {
            String filePath = getBean().getFilePath();
            if (getStatusFile(filePath) != null)
                    return true;

            // If the status entry does not already exist, then this must be an existing
            // file with a .status extension.
            return (PipelineJob.FT_PERL_STATUS.isType(filePath) &&
                    NetworkDrive.exists(new File(filePath)));
        }
    }

/////////////////////////////////////////////////////////////////////////////
//  Public URL interface to this controller

    public static class PipelineStatusUrlsImp implements PipelineStatusUrls
    {
        public ActionURL urlBegin(Container container)
        {
            return urlShowList(container, false);
        }

        public ActionURL urlDetails(Container container, int rowId)
        {
            return StatusController.urlDetails(container, rowId);
        }

        public ActionURL urlBegin(Container container, boolean notComplete)
        {
            ActionURL url = urlBegin(container);

            if (notComplete)
                url.addParameter("StatusFiles.Status~neqornull", "COMPLETE");

            return url;
        }
    }
}
