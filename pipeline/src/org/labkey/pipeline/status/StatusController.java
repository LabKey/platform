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

package org.labkey.pipeline.status;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleStreamAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.pipeline.NoSuchJobException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskPipelineRegistry;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.TroubleShooterPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.SpringErrorView;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.pipeline.PipelineController;
import org.labkey.pipeline.analysis.AnalysisController;
import org.labkey.pipeline.api.PipelineServiceImpl;
import org.labkey.pipeline.api.PipelineStatusFileImpl;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import static org.labkey.pipeline.api.PipelineStatusManager.cancelStatus;
import static org.labkey.pipeline.api.PipelineStatusManager.completeStatus;
import static org.labkey.pipeline.api.PipelineStatusManager.deleteStatus;
import static org.labkey.pipeline.api.PipelineStatusManager.getStatusFile;


public class StatusController extends SpringActionController
{
    private static final DefaultActionResolver _resolver = new DefaultActionResolver(StatusController.class);

    protected static final String _newline = System.getProperty("line.separator");

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
        setActionResolver(_resolver);
    }

    public static void registerAdminConsoleLinks()
    {
        ActionURL url = urlProvider(PipelineStatusUrls.class).urlBegin(ContainerManager.getRoot(), false);
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "pipeline", url, ReadPermission.class);
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Diagnostics, "pipelines and tasks",
                new ActionURL(AnalysisController.InternalListPipelinesAction.class, ContainerManager.getRoot()), ReadPermission.class);
    }

    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig p = super.defaultPageConfig();
        p.setHelpTopic("pipeline");
        return p;
    }

    public Container getContainerCheckAdmin()
    {
        Container c = getContainer();
        if (c == null || c.isRoot())
        {
            if (!getUser().hasRootPermission(TroubleShooterPermission.class))
            {
                throw new UnauthorizedException();
            }
        }

        return c;
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleRedirectAction
    {
        @Override
        public ActionURL getRedirectURL(Object o)
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
        @Override
        public ActionURL getSuccessURL(FORM form)
        {
            // Success leads to a reshow of this page with lastfilter=true
            return form.getReturnActionURL(urlShowList(getContainer(), true));
        }

        @Override
        public ModelAndView getView(FORM form, boolean reshow, BindException errors)
        {
            Container c = getContainerCheckAdmin();

            setHelpTopic("pipeline");

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
                HtmlView view = new HtmlView(HtmlString.of("You are not running the Enterprise Pipeline."));
                view.setTitle("Pipeline Overview");
                result.addView(view);
            }
            else
            {
                Set<String> locations = new TreeSet<>();
                TaskPipelineRegistry registry = PipelineJobService.get();
                for (TaskFactory<?> taskFactory : registry.getTaskFactories(null))
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

        @Override
        public void addNavTrail(NavTree root)
        {
            urlProvider(AdminUrls.class).addAdminNavTrail(root, "Data Pipeline", getClass(), getContainer());
        }
    }

    public static class EnterprisePipelineBean
    {
        private final Set<String> _locations;

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
        @Override
        public void validateCommand(ReturnUrlWithErrorForm target, Errors errors)
        {
            // Direct posts do nothing
        }

        @Override
        public boolean handlePost(ReturnUrlWithErrorForm o, BindException errors)
        {
            return true;    // Direct posts do nothing
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowListRegionAction extends ReadOnlyApiAction<ReturnUrlForm>
    {
        @Override
        public ApiResponse execute(ReturnUrlForm form, BindException errors) throws Exception
        {
            Container c = getContainerCheckAdmin();
            
            QueryView gridView = new PipelineQueryView(getViewContext(), errors, null, PipelineService.PipelineButtonOption.Standard, form.getReturnActionURL(new ActionURL(ShowListAction.class, c)));
            if (c.isRoot())
                gridView.disableContainerFilterSelection();
            gridView.render(getViewContext().getRequest(), getViewContext().getResponse());
            WebPartView.renderEndOfBodyScript(getPageConfig(), getViewContext().getResponse());
            return null;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowPartRegionAction extends ReadOnlyApiAction<ReturnUrlForm>
    {
        @Override
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
        return urlDetails(sf.lookupContainer(), sf.getRowId());
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

    public static class DetailsBean
    {
        public ActionURL cancelUrl;
        public ActionURL browseFilesUrl;
        public ActionURL retryUrl;
        public ActionURL showListUrl;
        public ActionURL dataUrl;
        public Date modified;
        public StatusDetailsBean status;
        public Integer queuePosition;
    }

    @RequiresPermission(ReadPermission.class)
    public class DetailsAction extends SimpleViewAction<RowIdForm>
    {
        private PipelineStatusFileImpl _statusFile;

        @Override
        public ModelAndView getView(RowIdForm form, BindException errors)
        {
            Container c = getContainerCheckAdmin();

            _statusFile = getStatusFile(form.getRowId());
            if (_statusFile == null)
                throw new NotFoundException("Could not find status file for rowId " + form.getRowId());

            if (!_statusFile.lookupContainer().equals(getContainer()))
            {
                ActionURL url = getViewContext().cloneActionURL();
                url.setContainer(_statusFile.lookupContainer());
                throw new RedirectException(url);
            }

            if (form.getErrorMessage() != null)
            {
                errors.addError(new LabKeyError(form.getErrorMessage()));
            }

            DetailsBean bean = new DetailsBean();

            if (_statusFile.isCancellable() && c.hasPermission(getUser(), DeletePermission.class))
                bean.cancelUrl = urlCancel(getContainer(), _statusFile.getRowId(), getViewContext().cloneActionURL());

            if (!getContainer().isRoot())
            {
                bean.browseFilesUrl = urlProvider(PipelineUrls.class).urlBrowse(_statusFile, getViewContext().getActionURL());
            }
            bean.showListUrl = urlShowList(getContainer(), true);

            if (_statusFile.getDataUrl() != null)
                bean.dataUrl = new ActionURL(StatusController.ShowDataAction.class, c).addParameter("rowId", _statusFile.getRowId());

            if (_statusFile.getJobStore() != null && (getUser().hasRootAdminPermission() || c.hasPermission(getUser(), UpdatePermission.class)))
                bean.retryUrl = urlRetry(_statusFile);

            bean.modified = _statusFile.getModified();
            bean.status = StatusDetailsBean.create(getContainer(), _statusFile, 0, 0);
            bean.queuePosition = PipelineService.get().getPipelineQueue().getQueuePositions().get(_statusFile.getJobId());

            return new JspView<>("/org/labkey/pipeline/status/details.jsp", bean, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Pipeline Jobs", new ActionURL(BeginAction.class, getContainer()));
            root.addChild(_statusFile == null ? "Job Status" : _statusFile.getDescription());
        }
    }

    public static class StatusDetailsForm extends RowIdForm
    {
        enum Params { rowId, offset, count }

        private long _offset;
        private int _count;

        public long getOffset()
        {
            return _offset;
        }

        public void setOffset(long offset)
        {
            _offset = offset;
        }

        public int getCount()
        {
            return _count;
        }

        public void setCount(int count)
        {
            _count = count;
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    public class StatusDetailsAction extends ReadOnlyApiAction<StatusDetailsForm>
    {
        @Override
        public Object execute(StatusDetailsForm form, BindException errors) throws Exception
        {
            Container c = getContainerCheckAdmin();

            PipelineStatusFile psf = getStatusFile(form.getRowId());
            if (psf == null)
                throw new NotFoundException("Could not find status file for rowId " + form.getRowId());

            var status = StatusDetailsBean.create(c, psf, form.getOffset(), form.getCount());
            return success(status);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowDataAction extends SimpleRedirectAction<RowIdForm>
    {
        @Override
        public ActionURL getRedirectURL(RowIdForm form)
        {
            Container c = getContainerCheckAdmin();

            PipelineStatusFile sf = getStatusFile(form.getRowId());
            if (sf == null)
                throw new NotFoundException("Could not find status file for rowId " + form.getRowId());

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
        @Override
        public ActionURL getRedirectURL(RowIdForm form)
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
                throw new RedirectException(urlProvider(ProjectUrls.class).getStartURL(c));
            }

            return urlDetails(c, form.getRowId());
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
        @Override
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
                    Path fileStatus = FileUtil.getPath(getContainer(), FileUtil.createUri(sf.getFilePath()));
                    String statusName = fileStatus.getFileName().toString();
                    String basename = statusName.substring(0, statusName.lastIndexOf('.'));

                    Path dir = fileStatus.getParent();
                    Path fileShow;
                    try
                    {
                        fileShow = dir.resolve(fileName);
                    }
                    catch (InvalidPathException ex)
                    {
                        fileShow = null;
                    }

                    // Ensure that the requested file is under the root for this container
                    PipeRoot root = PipelineService.get().findPipelineRoot(c);
                    if (root != null && root.isUnderRoot(fileShow) && NetworkDrive.exists(fileShow))
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

    private void renderFile(PrintWriter out, Path f)
    {
        try (BufferedReader br = Files.newBufferedReader(f))
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
            _filename = filename;
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
        @Override
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

        @Override
        public boolean handlePost(FORM form, BindException errors)
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
            // Don't clear the state yet because we're just validating at this point. We'll clear it as part of the
            // delete itself. See issue 44873
            Set<String> runs = DataRegionSelection.getSelected(getViewContext(), false);

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
        public ModelAndView getView(ConfirmDeleteStatusForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/pipeline/status/deleteStatus.jsp", form, errors);
        }

        @Override
        public boolean handlePost(ConfirmDeleteStatusForm form, BindException errors)
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
            URLHelper ret = form.getReturnURLHelper();
            if (null == ret)
                ret = urlShowList(getContainer(), true, null);
            return ret;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Confirm Deletion");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class CancelStatusAction extends PerformStatusActionBase<SelectStatusForm>
    {
        @Override
        public void handleSelect(SelectStatusForm form) throws PipelineProvider.HandlerException
        {
            cancelStatus(getViewBackgroundInfo(), DataRegionSelection.getSelectedIntegers(getViewContext(), true));
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class CompleteStatusAction extends PerformStatusActionBase<SelectStatusForm>
    {
        @Override
        public void handleSelect(SelectStatusForm form)
        {
            completeStatus(getUser(), DataRegionSelection.getSelectedIntegers(getViewContext(), true));
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class RetryStatusAction extends FormHandlerAction<RowIdForm>
    {
        ActionURL _successURL;

        @Override
        public void validateCommand(RowIdForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(RowIdForm form, BindException errors)
        {
            getContainerCheckAdmin();

            Set<Integer> rowIds;
            if (form.getRowId() != 0)
            {
                rowIds = Set.of(form.getRowId());
            }
            else
            {
                rowIds = DataRegionSelection.getSelectedIntegers(getViewContext(), false);
            }

            ActionURL firstDetailsURL = null;
            for (Integer rowId : rowIds)
            {
                var sf = getStatusFile(rowId);
                if (sf == null)
                    throw new NotFoundException("Could not find status file for rowId " + form.getRowId());

                if (firstDetailsURL == null)
                    firstDetailsURL = urlDetails(sf);

                if (!PipelineJob.TaskStatus.error.matches(sf.getStatus()) && !PipelineJob.TaskStatus.cancelled.matches(sf.getStatus()))
                {
                    errors.addError(new LabKeyError("Unable to retry job that is not in the ERROR or CANCELLED state"));
                }

                try
                {
                    PipelineJobService.get().getJobStore().retry(sf);
                }
                catch (IOException | NoSuchJobException e)
                {
                    errors.addError(new LabKeyError(e.getMessage()));
                }
            }

            if (errors.hasErrors())
                return false;

            DataRegionSelection.clearAll(getViewContext());
            _successURL = form.getReturnActionURL(firstDetailsURL);
            return true;
        }

        @Override
        public URLHelper getSuccessURL(RowIdForm rowIdForm)
        {
            return _successURL;
        }
    }

    private static ActionURL urlCancel(Container c, int rowId, @Nullable ActionURL returnUrl)
    {
        ActionURL url = new ActionURL(PipelineController.CancelJobAction.class, c);
        url.addParameter("rowId", rowId);
        if (returnUrl != null)
            url.addReturnURL(returnUrl);
        return url;
    }

    private static ActionURL urlRetry(PipelineStatusFile sf)
    {
        return new ActionURL(RetryStatusAction.class, sf.lookupContainer())
                .addParameter("rowId", sf.getRowId());
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
        @Override
        public ActionURL urlBegin(Container container)
        {
            return urlShowList(container, false);
        }

        @Override
        public ActionURL urlDetails(Container container, int rowId)
        {
            return StatusController.urlDetails(container, rowId);
        }

        @Override
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

        @Override
        public ActionURL urlCancel(Container container, int rowId, @Nullable ActionURL returnUrl)
        {
            return StatusController.urlCancel(container, rowId, returnUrl);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class ForceRefreshAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            PipelineServiceImpl.get().refreshLocalJobs();
            throw new RedirectException(new ActionURL(ShowListAction.class, ContainerManager.getRoot()));
        }

        @Override
        public void addNavTrail(NavTree root)
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
            assertTrue(user.hasSiteAdminPermission());

            StatusController controller = new StatusController();

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user, false,
                controller.new BeginAction(),
                controller.new ShowListAction(),
                controller.new ShowListRegionAction(),
                controller.new ShowPartRegionAction(),
                controller.new DetailsAction(),
                controller.new ShowDataAction(),
                controller.new ShowFolderAction(),
                controller.new ShowFileAction(),
                controller.new StatusDetailsAction(),
                controller.new DeleteStatusAction(),
                controller.new CancelStatusAction(),
                controller.new CompleteStatusAction()
            );

            // @RequiresPermission(UpdatePermission.class)
            assertForUpdateOrDeletePermission(user,
                controller.new RetryStatusAction()
            );

            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(user,
                controller.new ForceRefreshAction()
            );
        }
    }
}
