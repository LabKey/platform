/*
 * Copyright (c) 2005-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.*;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.*;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.QueryView;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminReadPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.pipeline.PipelineController;
import org.labkey.pipeline.api.PipelineEmailPreferences;
import org.labkey.pipeline.api.PipelineServiceImpl;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.servlet.ServletException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.labkey.pipeline.api.PipelineStatusManager.*;


public class StatusController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(StatusController.class);
    private static final DefaultActionResolver _resolver = new DefaultActionResolver(StatusController.class);

    protected static final String _newline = System.getProperty("line.separator");

    private static HelpTopic getHelpTopic(String topic)
    {
        return new HelpTopic(topic);
    }

    private void reject(Errors errors, String message)
    {
        errors.reject(message);
    }

    private void reject(BindException errors, String message)
    {
        errors.reject(message);
    }

    public StatusController()
    {
        super();
        setActionResolver(_resolver);
    }

    public static void registerAdminConsoleLinks()
    {
        ActionURL url = PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(ContainerManager.getRoot(), false);
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "pipeline", url, ReadPermission.class);
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
            if (!getUser().hasRootPermission(AdminReadPermission.class))
            {
                throw new UnauthorizedException();
            }
        }

        return c;
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            return urlShowList(getContainer(), false);
        }
    }

    public static ActionURL urlShowList(Container container, boolean lastFilter)
    {
        return urlShowList(container, lastFilter, null);
    }

    public static ActionURL urlShowList(Container container, boolean lastFilter, String errorMessage)
    {
        ActionURL url = new ActionURL(ShowListAction.class, container);
        if (lastFilter)
            url.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
        if (errorMessage != null)
        {
            url.addParameter("errorMessage", errorMessage);
        }
        return url;
    }

    abstract public class ShowListBaseAction<FORM extends ReturnUrlWithErrorForm> extends FormViewAction<FORM>
    {
        public ActionURL getSuccessURL(FORM form)
        {
            // Success leads to a reshow of this page with lastfilter=true
            return form.getReturnActionURL(urlShowList(getContainer(), true));
        }

        public ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception
        {
            Container c = getContainerCheckAdmin();

            setHelpTopic(getHelpTopic("pipeline"));

            QueryView gridView = new PipelineQueryView(getViewContext(), errors, ShowListRegionAction.class, PipelineService.PipelineButtonOption.Standard, getViewContext().getActionURL());
            gridView.setTitle("Data Pipeline");

            VBox result = new VBox();
            if (form.getErrorMessage() != null)
            {
                errors.addError(new LabKeyError(form.getErrorMessage()));
            }
            if (errors.getErrorCount() > 0)
            {
                result.addView(new SpringErrorView(errors));
            }

            if (!c.isRoot())
            {
                result.addView(gridView);
                return result;
            }
            gridView.disableContainerFilterSelection();

            if (!PipelineService.get().isEnterprisePipeline())
            {
                HtmlView view = new HtmlView("You are not running the Enterprise Pipeline.");
                view.setTitle("Pipeline Overview");
                result.addView(view);
            }
            else
            {
                Set<String> locations = new TreeSet<>();
                TaskPipelineRegistry registry = PipelineJobService.get();
                for (TaskFactory taskFactory : registry.getTaskFactories(null))
                {
                    locations.add(taskFactory.getExecutionLocation());
                }
                EnterprisePipelineBean bean = new EnterprisePipelineBean(locations);
                JspView<EnterprisePipelineBean> overview = new JspView<>("/org/labkey/pipeline/status/enterprisePipelineAdmin.jsp", bean);
                overview.setTitle("Pipeline Overview");
                result.addView(overview);
            }

            result.addView(gridView);

            return result;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Data Pipeline");
        }
    }

    public static class EnterprisePipelineBean
    {
        private Set<String> _locations;

        public EnterprisePipelineBean(Set<String> locations)
        {
            _locations = locations;
        }

        public Set<String> getLocations()
        {
            return _locations;
        }
    }

    public static class ReturnUrlWithErrorForm extends ReturnUrlForm
    {
        private String _errorMessage;

        public String getErrorMessage()
        {
            return _errorMessage;
        }

        public void setErrorMessage(String errorMessage)
        {
            _errorMessage = errorMessage;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowListAction extends ShowListBaseAction<ReturnUrlWithErrorForm>
    {
        public void validateCommand(ReturnUrlWithErrorForm target, Errors errors)
        {
            // Direct posts do nothing
        }

        public boolean handlePost(ReturnUrlWithErrorForm o, BindException errors) throws Exception
        {
            return true;    // Direct posts do nothing
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowListRegionAction extends ApiAction<ReturnUrlForm>
    {
        public ApiResponse execute(ReturnUrlForm form, BindException errors) throws Exception
        {
            Container c = getContainerCheckAdmin();
            
            QueryView gridView = new PipelineQueryView(getViewContext(), errors, null, PipelineService.PipelineButtonOption.Standard, form.getReturnActionURL(new ActionURL(ShowListAction.class, c)));
            if (c.isRoot())
                gridView.disableContainerFilterSelection();
            gridView.render(getViewContext().getRequest(), getViewContext().getResponse());
            return null;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowPartRegionAction extends ApiAction<ReturnUrlForm>
    {
        public ApiResponse execute(ReturnUrlForm form, BindException errors) throws Exception
        {
            PipelineQueryView gridView = new PipelineQueryView(getViewContext(), errors, null, PipelineService.PipelineButtonOption.Minimal, form.getReturnActionURL(new ActionURL(ShowListAction.class, getContainer())));
            gridView.render(getViewContext().getRequest(), getViewContext().getResponse());
            return null;
        }
    }

    public static ActionURL urlDetails(Container c, int rowId)
    {
        return urlDetails(c, rowId, null);
    }

    public static ActionURL urlDetails(Container c, int rowId, String errorMessage)
    {
        ActionURL url = new ActionURL(DetailsAction.class, c);
        url.addParameter(RowIdForm.Params.rowId, Integer.toString(rowId));
        if (errorMessage != null)
        {
            url.addParameter("errorMessage", errorMessage);
        }
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
        private PipelineStatusFile _statusFile;

        public ActionURL getSuccessURL(FORM form)
        {
            // Just redirect back to this page
            return urlDetails(getContainer(), form.getRowId());
        }

        public ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception
        {
            Container c = getContainerCheckAdmin();

            DataRegion rgn = getDetails(c, getUser(), form.getRowId());
            DetailsView detailsView = new DetailsView(rgn, form.getRowId());
            if (c == null || c.isRoot())
            {
                detailsView.getRenderContext().setUseContainerFilter(false);
            }

            detailsView.setFrame(WebPartView.FrameType.PORTAL);
            detailsView.setTitle("Job Status");

            VBox result = new VBox(detailsView);
            if (form.getErrorMessage() != null)
            {
                errors.addError(new LabKeyError(form.getErrorMessage()));
            }
            if (errors.getErrorCount() > 0)
            {
                result.addView(new SpringErrorView(errors), 0);
            }

            _statusFile = getStatusFile(form.getRowId());
            if (_statusFile != null)
            {
                String strPath = _statusFile.getFilePath();
                if (null != strPath)
                {
                    Path path = FileUtil.stringToPath(c, strPath);
                    if (Files.exists(path))
                    {
                        try
                        {
                            ActionURL url = getViewContext().cloneActionURL();
                            url.replaceParameter("showDetails", Boolean.toString(!form.isShowDetails()));

                            String prefix = PageFlowUtil.textLink(form.isShowDetails() ? "Show summary" : "Show full log file", url);

                            // JobStatusLogView (ReaderView) responsible for closing stream
                            WebPartView logFileView = new JobStatusLogView(Files.newInputStream(path), form.isShowDetails(), prefix, "");
                            logFileView.setTitle(FileUtil.getFileName(path));
                            result.addView(logFileView);
                        }
                        catch (IOException e)
                        {
                            result.addView(new HtmlView("Unable to view file - " + (e.getMessage() == null ? e.toString() : e.getMessage())));
                        }
                    }
                }
            }

            return result;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Pipeline Jobs", new ActionURL(BeginAction.class, getContainer()));
            return root.addChild(_statusFile == null ? "Job Status" : _statusFile.getDescription());
        }
    }

    @RequiresPermission(ReadPermission.class)
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

    public static class RowIdForm extends ReturnUrlWithErrorForm
    {
        enum Params { rowId }

        private int _rowId;
        private boolean _showDetails;

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public boolean isShowDetails()
        {
            return _showDetails;
        }

        public void setShowDetails(boolean showDetails)
        {
            _showDetails = showDetails;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowDataAction extends SimpleRedirectAction<RowIdForm>
    {
        public ActionURL getRedirectURL(RowIdForm form) throws Exception
        {
            Container c = getContainerCheckAdmin();

            PipelineStatusFile sf = getStatusFile(form.getRowId());
            if (sf == null)
            {
                throw new NotFoundException();
            }

            if (sf.getDataUrl() != null)
            {
                throw new RedirectException(sf.getDataUrl());
            }

            return urlDetails(c, form.getRowId());
        }
    }

    @RequiresPermission(ReadPermission.class)
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
            {
                throw new RedirectException(PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c));
            }

            return urlDetails(c, form.getRowId());
        }
    }

    @RequiresPermission(UpdatePermission.class)
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
                throw new NotFoundException();

            PipelineProvider provider = PipelineService.get().getPipelineProvider(sf.getProvider());
            if (provider == null)
                throw new NotFoundException();

            try
            {
                _urlSuccess = provider.handleStatusAction(getViewContext(), form.getName(), sf);

                return HttpView.redirect(getSuccessURL(form));
            }
            catch (PipelineProvider.HandlerException e)
            {
                errors.addError(new LabKeyError(e.getMessage()));
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

    public static ActionURL urlShowFile(Container c, int rowId, String filename, boolean download)
    {
        return new ActionURL(ShowFileAction.class, c)
                .addParameter(RowIdForm.Params.rowId, Integer.toString(rowId))
                .addParameter(ShowFileForm.Params.filename, filename)
                .addParameter(ShowFileForm.Params.download, download);
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowFileAction extends SimpleStreamAction<ShowFileForm>
    {
        public void render(ShowFileForm form, BindException errors, PrintWriter out) throws Exception
        {
            Container c = getContainerCheckAdmin();

            boolean written = false;
            String fileName;

            PipelineStatusFile sf = getStatusFile(form.getRowId());
            if (sf != null)
            {
                fileName = form.getFilename();

                if (fileName != null && fileName.length() != 0)
                {
                    File fileStatus = new File(sf.getFilePath());
                    String statusName = fileStatus.getName();
                    String basename = statusName.substring(0, statusName.lastIndexOf('.'));

                    File dir = fileStatus.getParentFile();
                    File fileShow = new File(dir, fileName);

                    if (NetworkDrive.exists(fileShow))
                    {
                        boolean visible = isVisibleFile(fileName, basename);

                        String providerName = sf.getProvider();
                        if (providerName != null)
                        {
                            PipelineProvider provider = PipelineService.get().getPipelineProvider(providerName);
                            if (provider != null)
                                visible = provider.isStatusViewableFile(sf.lookupContainer(), fileName, basename);
                        }

                        if (visible)
                        {
                            if (!form.isDownload())
                            {
                                renderFile(out, fileShow);
                            }
                            else
                            {
                                PageFlowUtil.streamFile(getViewContext().getResponse(), fileStatus, true);
                            }
                            written = true;
                        }
                    }
                }
            }

            if (!written)
                out.print("File not found.");
        }
    }

    private void renderFile(PrintWriter out, File f)
    {
        try (BufferedReader br = Readers.getReader(f))
        {
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
    }

    public static class ShowFileForm extends RowIdForm
    {
        enum Params { filename, download }

        private boolean _download;
        private String _filename;

        public String getFilename()
        {
            return _filename;
        }

        public void setFilename(String filename)
        {
            this._filename = filename;
        }

        public boolean isDownload()
        {
            return _download;
        }

        public void setDownload(boolean download)
        {
            _download = download;
        }
    }

    abstract public class PerformStatusActionBase<FORM extends SelectStatusForm>
            extends ShowListBaseAction<FORM>
    {
        public void validateCommand(FORM target, Errors errors)
        {
            Set<String> runs = DataRegionSelection.getSelected(getViewContext(), true);

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
                errors.addError(new LabKeyError(e.getMessage()));
                return false;
            }

            return true;
        }

        abstract public void handleSelect(FORM form) throws PipelineProvider.HandlerException;
    }

    public static class SelectStatusForm extends ReturnUrlWithErrorForm
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

    @RequiresPermission(ReadPermission.class)
    public class RunActionAction extends PerformStatusActionBase<ActionForm>
    {
        public boolean handlePost(ActionForm form, BindException errors) throws Exception
        {
            // The method prototype for this method is used to determine the form type,
            // so it must be here, even though it does nothing more than call super.

            return super.handlePost(form, errors);
        }

        public void handleSelect(ActionForm form) throws PipelineProvider.HandlerException
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

    public static class ConfirmDeleteStatusForm extends SelectStatusForm
    {
        private String _dataRegionSelectionKey;
        private boolean _confirm;
        private boolean _deleteRuns;

        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            _dataRegionSelectionKey = dataRegionSelectionKey;
        }

        public boolean isConfirm()
        {
            return _confirm;
        }

        public void setConfirm(boolean confirm)
        {
            _confirm = confirm;
        }

        public boolean isDeleteRuns()
        {
            return _deleteRuns;
        }

        public void setDeleteRuns(boolean deleteRuns)
        {
            _deleteRuns = deleteRuns;
        }
    }

    // DeletePermission will be checked in PipelineStatusManager.deleteStatus()
    @RequiresPermission(ReadPermission.class)
    public class DeleteStatusAction extends FormViewAction<ConfirmDeleteStatusForm>
    {
        @Override
        public void validateCommand(ConfirmDeleteStatusForm form, Errors errors)
        {
            Set<String> runs = DataRegionSelection.getSelected(getViewContext(), true);

            try
            {
                form.setRowIds(PageFlowUtil.toInts(runs));
            }
            catch (NumberFormatException e)
            {
                reject(errors, "Invalid run: " + e.getMessage());
            }
        }

        @Override
        public ModelAndView getView(ConfirmDeleteStatusForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/pipeline/status/deleteStatus.jsp", form, errors);
        }

        @Override
        public boolean handlePost(ConfirmDeleteStatusForm form, BindException errors) throws Exception
        {
            if (!form.isConfirm())
                return false;

            getContainerCheckAdmin();
            try
            {
                deleteStatus(getViewBackgroundInfo().getContainer(), getViewBackgroundInfo().getUser(), form.isDeleteRuns(), DataRegionSelection.getSelectedIntegers(getViewContext(), true));
            }
            catch (PipelineProvider.HandlerException e)
            {
                errors.addError(new LabKeyError(e.getMessage() == null ? "Failed to delete at least one job. It may be referenced by other jobs" : e.getMessage()));
                return false;
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(ConfirmDeleteStatusForm form)
        {
            return form.getReturnURLHelper();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Confirm Deletion");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class CancelStatusAction extends PerformStatusActionBase
    {
        public void handleSelect(SelectStatusForm form) throws PipelineProvider.HandlerException
        {
            cancelStatus(getViewBackgroundInfo(), DataRegionSelection.getSelectedIntegers(getViewContext(), true));
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class CompleteStatusAction extends PerformStatusActionBase
    {
        public void handleSelect(SelectStatusForm form) throws PipelineProvider.HandlerException
        {
            completeStatus(getUser(), DataRegionSelection.getSelectedIntegers(getViewContext(), true));
        }
    }

    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
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
            if (sf == null)
            {
                throw new NotFoundException();
            }
            view.addView(new JspView<>("/org/labkey/pipeline/status/escalateJobFailure.jsp", sf));

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Escalate Pipeline Job Failure");
        }
    }

    @RequiresPermission(ReadPermission.class)
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

                final String message = (form.getEscalationMessage() == null ? "" : form.getEscalationMessage() + "\n\n") +
                        "Job details can be found:\n\n" + form.getDetailsUrl();
                m.setTextContent(message);
                m.setHtmlContent(message);
                m.setSubject(form.getEscalationSubject());
                m.addFrom(new Address[]{new InternetAddress(getUser().getEmail(), getUser().getFullName())});

                if (form.getEscalateAll())
                    recipients = PipelineEmailPreferences.get().getEscalationUsers(getContainer());
                else
                    recipients = form.getEscalateUser();
                if (!StringUtils.isEmpty(recipients))
                {
                    m.addRecipients(Message.RecipientType.TO, MailHelper.createAddressArray(recipients));
                    MailHelper.send(m, getUser(), getContainer());
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

    public static class EscalateMessageForm extends ReturnUrlWithErrorForm
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

    private DataRegion getDetails(Container c, User user, int rowId) throws RedirectException
    {
        DataRegion rgn = new DataRegion();

        rgn.setColumns(getTableInfo().getColumns("Created, Modified, Job, JobParent, JobStore, Provider, Container, Email, Status, Info, FilePath, DataUrl"));
        rgn.addDisplayColumn(new FileDisplayColumn());
        rgn.addDisplayColumn(new JobDisplayColumn(false));
        rgn.addDisplayColumn(new JobDisplayColumn(true));
        rgn.getDisplayColumn("Job").setVisible(false);
        rgn.getDisplayColumn("JobParent").setVisible(false);
        rgn.getDisplayColumn("JobStore").setVisible(false);
        rgn.getDisplayColumn("Provider").setVisible(false);
        rgn.getDisplayColumn("Status").setURL(null);
        rgn.getDisplayColumn("Container").setVisible(false);
        rgn.getDisplayColumn("DataUrl").setVisible(false);

        PipelineStatusFileImpl sf = getStatusFile(rowId);
        if (sf == null)
        {
            throw new NotFoundException("Could not find status file for rowId " + rowId);
        }
        
        if (!sf.lookupContainer().equals(getContainer()))
        {
            ActionURL url = getViewContext().cloneActionURL();
            url.setContainer(sf.lookupContainer());
            throw new RedirectException(url);
        }

        ButtonBar bb = new ProviderButtonBar(sf);

        ActionButton showGrid = new ActionButton(new ActionURL(ShowListAction.class, getContainer()).addParameter(DataRegion.LAST_FILTER_PARAM, "true"), "Show Grid");
        showGrid.setActionType(ActionButton.Action.LINK);
        showGrid.setDisplayPermission(ReadPermission.class);
        bb.add(showGrid);

        if (c == null || c.isRoot())
        {
            ActionURL url = new ActionURL(ShowFolderAction.class, c);
            url.addParameter("rowId", rowId);
            ActionButton showFolder = new ActionButton(url, "Folder");
            showFolder.setActionType(ActionButton.Action.LINK);
            bb.add(showFolder);
        }

        if (sf.getDataUrl() != null)
        {
            ActionURL url = new ActionURL(ShowDataAction.class, c);
            url.addParameter("rowId", rowId);
            ActionButton showData = new ActionButton(url, "Data");
            showData.setActionType(ActionButton.Action.LINK);
            bb.add(showData);
        }

        List<? extends ExpRun> runs = ExperimentService.get().getExpRunsForJobId(sf.getRowId());
        if (runs.size() == 1)
        {
            ExpRun run = runs.get(0);
            URLHelper url = run.detailsURL();
            ActionButton showRun = new ActionButton("Run", url);
            showRun.setActionType(ActionButton.Action.LINK);
            bb.add(showRun);
        }
        else if (runs.size() > 1)
        {
            MenuButton runsMenu = new MenuButton("Runs");
            for (ExpRun run : runs)
            {
                ActionURL url = (ActionURL)run.detailsURL();
                runsMenu.addMenuItem(run.getName(), url);
            }
            runsMenu.setDisplayModes(DataRegion.MODE_DETAILS);
            bb.add(runsMenu);
        }

        if (sf.getFilePath() != null)
        {
            File logFile = new File(sf.getFilePath());
            File dir = logFile.getParentFile();
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(sf.lookupContainer());
            if (NetworkDrive.exists(dir) && pipeRoot != null && pipeRoot.isUnderRoot(dir))
            {
                String relativePath = pipeRoot.relativePath(dir);

                // Issue 14693: changing the pipeline root or symlinks can result in bad paths.  if we cant locate the file, just dont display the browse button.
                if(relativePath != null)
                {
                    relativePath = relativePath.replace("\\", "/");
                    if (relativePath.equals("."))
                    {
                        relativePath = "/";
                    }
                    ActionURL url = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(sf.lookupContainer(), getViewContext().getActionURL(), relativePath);
                    ActionButton showData = new ActionButton(url, "Browse Files");
                    showData.setActionType(ActionButton.Action.LINK);
                    bb.add(showData);
                }
            }
        }

        // escalate pipeline failure button
        if (!PipelineJob.TaskStatus.complete.matches(sf.getStatus()))
        {
            final String escalationUsers = PipelineEmailPreferences.get().getEscalationUsers(c);
            if (!StringUtils.isEmpty(escalationUsers) && !getUser().isGuest())
            {
                ActionURL url = new ActionURL(EscalateJobFailureAction.class, c);
                url.addParameter("rowId", rowId);
                ActionButton escalate = new ActionButton(url, "Escalate Job Failure");
                escalate.setActionType(ActionButton.Action.LINK);
                bb.add(escalate);
            }
        }

        if (sf.isCancellable() && getContainer().hasPermission(getUser(), DeletePermission.class))
        {
            ActionURL url = new ActionURL(PipelineController.CancelJobAction.class, c);
            url.addParameter("rowId", sf.getRowId());
            url.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().toString());
            ActionButton showData = new ActionButton(url, "Cancel");
            showData.setActionType(ActionButton.Action.LINK);
            bb.add(showData);
        }

        rgn.setButtonBar(bb, DataRegion.MODE_DETAILS);

        return rgn;
    }

    protected static boolean isVisibleFile(String name, String basename)
    {
        if (name.endsWith(".def") ||
                name.endsWith(".def.err") ||
                name.equals("tandem.xml") ||
                name.equals("tandem.xml.err"))
            return true;
        if (!name.startsWith(basename) || name.length() == basename.length() ||
                name.charAt(basename.length()) != '.')
            return false;
        return name.endsWith(".log") || name.endsWith(".out");
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

        @Override
        public ActionURL urlShowFile(Container container, int rowId, String filename)
        {
            return StatusController.urlShowFile(container, rowId, filename, false);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class ForceRefreshAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            PipelineServiceImpl.get().refreshLocalJobs();
            throw new RedirectException(new ActionURL(ShowListAction.class, ContainerManager.getRoot()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.isInSiteAdminGroup());

            StatusController controller = new StatusController();

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user,
                controller.new BeginAction(),
                controller.new ShowListAction(),
                controller.new ShowListRegionAction(),
                controller.new ShowPartRegionAction(),
                controller.new DetailsAction(),
                controller.new ShowDataAction(),
                controller.new ShowFolderAction(),
                controller.new ShowFileAction(),
                controller.new RunActionAction(),
                controller.new DeleteStatusAction(),
                controller.new CancelStatusAction(),
                controller.new CompleteStatusAction(),
                controller.new EscalateJobFailureAction(),
                controller.new EscalateAction()
            );

            // @RequiresPermission(UpdatePermission.class)
            assertForUpdateOrDeletePermission(user,
                controller.new ProviderActionAction()
            );

            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(user,
                controller.new ForceRefreshAction()
            );
        }
    }
}
