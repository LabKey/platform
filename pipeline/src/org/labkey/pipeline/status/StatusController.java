/*
 * Copyright (c) 2005-2010 LabKey Corporation
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
import org.labkey.api.action.*;
import org.labkey.api.data.*;
import org.labkey.api.pipeline.*;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
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
import java.util.Set;
import java.util.TreeSet;

import static org.labkey.pipeline.api.PipelineStatusManager.*;


public class StatusController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(StatusController.class);
    private static final DefaultActionResolver _resolver = new DefaultActionResolver(StatusController.class);

    protected static final String _newline = System.getProperty("line.separator");
    protected static final String DATAREGION_STATUS = "dataregion_StatusFiles";

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

    @RequiresPermissionClass(ReadPermission.class)
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

            setHelpTopic(getHelpTopic("pipeline"));

            QueryView gridView = new PipelineQueryView(getViewContext(), errors, ShowListRegionAction.class, false);
            gridView.setTitle("Data Pipeline");

            if (!c.isRoot())
            {
                return gridView;
            }
            gridView.disableContainerFilterSelection();

            if (!PipelineService.get().isEnterprisePipeline())
            {
                HtmlView view = new HtmlView("You are not running the Enterprise Pipeline.");
                view.setTitle("Pipeline Overview");
                return new VBox(view, gridView);
            }

            Set<String> locations = new TreeSet<String>();
            TaskPipelineRegistry registry = PipelineJobService.get();
            for (TaskFactory taskFactory : registry.getTaskFactories())
            {
                locations.add(taskFactory.getExecutionLocation());
            }
            EnterprisePipelineBean bean = new EnterprisePipelineBean(locations);
            JspView<EnterprisePipelineBean> overview = new JspView<EnterprisePipelineBean>("/org/labkey/pipeline/status/enterprisePipelineAdmin.jsp", bean);
            overview.setTitle("Pipeline Overview");

            return new VBox(overview, gridView);
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

    @RequiresPermissionClass(ReadPermission.class)
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

    @RequiresPermissionClass(ReadPermission.class)
    public class ShowListRegionAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            QueryView gridView = new PipelineQueryView(getViewContext(), errors, null, false);
            gridView.disableContainerFilterSelection();
            gridView.render(getViewContext().getRequest(), getViewContext().getResponse());
            return null;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ShowPartRegionAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            PipelineQueryView gridView = new PipelineQueryView(getViewContext(), errors, null, true);
            gridView.render(getViewContext().getRequest(), getViewContext().getResponse());
            return null;
        }
    }

    public static StringExpression urlDetailsData(Container c)
    {
        // Would be better to return an ActionURL, but it doesn't yet
        // seem to support this binding.
        ActionURL url = new ActionURL(DetailsAction.class, c).addParameter(RowIdForm.Params.rowId, "${RowId}");
        return StringExpressionFactory.createURL(url);
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
            }
            return detailsView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Job Status");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
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

    @RequiresPermissionClass(ReadPermission.class)
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
                HttpView.throwRedirect(sf.getDataUrl());
            }

            return urlDetails(c, form.getRowId());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
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
                HttpView.throwRedirect(PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c));

            return urlDetails(c, form.getRowId());
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
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
                return HttpView.throwNotFound();

            PipelineProvider provider = PipelineService.get().getPipelineProvider(sf.getProvider());
            if (provider == null)
                return HttpView.throwNotFound();

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

    @RequiresPermissionClass(ReadPermission.class)
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

    @RequiresPermissionClass(ReadPermission.class)
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

    @RequiresPermissionClass(ReadPermission.class)
    public class DeleteStatusAction extends PerformStatusActionBase
    {
        public void handleSelect(SelectStatusForm form) throws Exception
        {
            deleteStatus(getViewBackgroundInfo(), DataRegionSelection.toInts(DataRegionSelection.getSelected(getViewContext(), true)));
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class CompleteStatusAction extends PerformStatusActionBase
    {
        public void handleSelect(SelectStatusForm form) throws Exception
        {
            completeStatus(getUser(), DataRegionSelection.toInts(DataRegionSelection.getSelected(getViewContext(), true)));
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
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

    @RequiresPermissionClass(ReadPermission.class)
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

    public static class EscalateMessageForm
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

    private DataRegion getDetails(Container c, User user, int rowId)
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

        PipelineStatusFile sf = getStatusFile(rowId);
        if (sf == null)
        {
            throw new NotFoundException("Could not find status file for rowId " + rowId);
        }

        ButtonBar bb = new ProviderButtonBar(sf);

        ActionButton showGrid = new ActionButton("showList.view?.lastFilter=true", "Show Grid");
        showGrid.setActionType(ActionButton.Action.LINK);
        showGrid.setDisplayPermission(ReadPermission.class);
        bb.add(showGrid);

        if (c == null || c.isRoot())
        {
            ActionButton showFolder = new ActionButton("showFolder.view?rowId=${rowId}", "Folder");
            showFolder.setActionType(ActionButton.Action.LINK);
            bb.add(showFolder);
        }

        if (sf.getDataUrl() != null)
        {
            ActionButton showData = new ActionButton("showData.view?rowId=${rowId}", "Data");
            showData.setActionType(ActionButton.Action.LINK);
            bb.add(showData);
        }

        // escalate pipeline failure button
        if (!PipelineJob.COMPLETE_STATUS.equals(sf.getStatus()))
        {
            final String escalationUsers = PipelineEmailPreferences.get().getEscalationUsers(c);
            if (!StringUtils.isEmpty(escalationUsers))
            {
                ActionButton escalate = new ActionButton("escalateJobFailure.view?rowId=${rowId}", "Escalate Job Failure");
                escalate.setActionType(ActionButton.Action.LINK);
                bb.add(escalate);
            }
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
    }

    @RequiresSiteAdmin
    public class ForceRefreshAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            PipelineServiceImpl.get().refreshLocalJobs();
            HttpView.throwRedirect(new ActionURL(ShowListAction.class, ContainerManager.getRoot()));
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }
}
