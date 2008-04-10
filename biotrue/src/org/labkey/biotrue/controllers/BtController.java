package org.labkey.biotrue.controllers;

import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.biotrue.datamodel.BtManager;
import org.labkey.biotrue.datamodel.Server;
import org.labkey.biotrue.datamodel.Task;
import org.labkey.biotrue.objectmodel.BtEntity;
import org.labkey.biotrue.objectmodel.BtServer;
import org.labkey.biotrue.query.BtSchema;
import org.labkey.biotrue.query.BtServerView;
import org.labkey.biotrue.soapmodel.Browse_response;
import org.labkey.biotrue.soapmodel.Download_response;
import org.labkey.biotrue.soapmodel.Entityinfo;
import org.labkey.biotrue.task.*;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.xml.rpc.Service;
import java.io.File;

public class BtController extends SpringActionController
{
    static SpringActionController.DefaultActionResolver _actionResolver = new DefaultActionResolver(BtController.class);
    static final private Logger _log = Logger.getLogger(BtController.class);

    public enum Param
    {
        serverId,
        dataId,
    }
    public enum Action
    {
        begin,
        newServer,
        showServer,
        showServers,
        editServer,
        deleteServer,
        synchronizeServer,
        admin,
        scheduledSync,
        configurePassword,
        cancelSynchronization;
        public ActionURL url(Container container)
        {
            return new ActionURL("biotrue", toString(), container);
        }
    }

    public BtController()
    {
        super();
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            VBox view = new VBox(new BtOverviewWebPart(getViewContext()));

            if (BtManager.get().getServers(getContainer()).length > 0)
            {
                BtSchema schema = new BtSchema(getUser(), getContainer());
                QuerySettings settings = new QuerySettings(getViewContext().getActionURL(), "Server");
                settings.setSchemaName(schema.getSchemaName());
                settings.setQueryName("Servers");
                settings.setAllowChooseQuery(false);
                view.addView(new BtServerView(getViewContext(), schema, settings));
            }
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("BioTrue Connector");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class BrowseAction extends SimpleViewAction<ServerForm>
    {
        public ModelAndView getView(ServerForm form, BindException errors) throws Exception
        {
            BtServer server = form.getServer();
            Browse_response login = server.login();
            String ent = getViewContext().getRequest().getParameter("ent");
            BtEntity data = null;
            if (ent != null)
            {
                data = new BtEntity(server);
                data.setBioTrue_Ent(ent);
                data.setBioTrue_Id(getViewContext().getRequest().getParameter("id"));
            }
            Browse_response resp = server.browse(login.getData().getSession_id(), data);
            StringBuilder html = new StringBuilder();
            for (Entityinfo entity : resp.getData().getAllContent())
            {
                ActionURL url = getViewContext().cloneActionURL();
                if ("sample".equals(entity.getType()))
                {
                    url.setAction(DownloadAction.class);
                }
                url.replaceParameter("ent", entity.getType());
                url.replaceParameter("id", entity.getId());
                html.append("<a href=\"");
                html.append(PageFlowUtil.filter(url.toString()));
                html.append("\">");
                html.append(PageFlowUtil.filter(entity.getName()));
                html.append("</a>");
                html.append("<br>");
            }
            return new HtmlView(html.toString());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Browse");
        }
    }

    @RequiresPermission(ACL.PERM_UPDATE)
    public class DownloadAction extends SimpleViewAction<ServerForm>
    {
        public ModelAndView getView(ServerForm form, BindException errors) throws Exception
        {
            BtServer server = form.getServer();
            Browse_response login = server.login();
            BtEntity data = new BtEntity(server);
            data.setBioTrue_Ent(getViewContext().getRequest().getParameter("ent"));
            data.setBioTrue_Id(getViewContext().getRequest().getParameter("id"));
            Download_response response = server.download(login.getData().getSession_id(), data);

            return HttpView.redirect(response.getData().getUrl());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_UPDATE)
    public class SynchronizeServerAction extends SimpleViewAction<ServerForm>
    {
        public ModelAndView getView(ServerForm form, BindException errors) throws Exception
        {
            BtServer server = form.getServer();
            if (!BtTaskManager.get().anyTasks(form.getServer()))
            {
                Task task = new Task();
                task.setServerId(server.getRowId());
                task.setOperation(Operation.view.toString());
                new BrowseTask(task).doRun();
            }
            BtThreadPool.get();

            ActionURL forward = QueryService.get().urlFor(getContainer(), QueryAction.executeQuery, BtSchema.name, BtSchema.TableType.Tasks.toString());
            return HttpView.redirect(forward);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowServersAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            BtSchema schema = new BtSchema(getUser(), getContainer());
            QuerySettings settings = new QuerySettings(getViewContext().getActionURL(), "Server");
            settings.setSchemaName(schema.getSchemaName());
            settings.setQueryName("Servers");
            settings.setAllowChooseQuery(false);

            return new QueryView(schema, settings);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("BioTrue Servers");
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class ShowServerAction extends SimpleViewAction<ServerForm>
    {
        String _label;

        public ModelAndView getView(ServerForm form, BindException errors) throws Exception
        {
            BtServer server = form.getServer();
            _label = server.getLabel();

            return FormPage.getView(BtController.class, form, "showServer.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_label);
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class NewServerAction extends FormViewAction<NewServerForm>
    {
        private ActionURL _detailsURL;

        public void validateCommand(NewServerForm form, Errors errors)
        {
            if (form.ff_name == null)
            {
                errors.reject("newServerForm", "Name is required.");
            }
            if (form.ff_physicalRoot == null)
            {
                errors.reject("newServerForm", "Download location is required.");
            }
            else
            {
                try
                {
                    File dir = new File(form.ff_physicalRoot);
                    if (!dir.exists())
                    {
                        errors.reject("newServerForm", form.ff_physicalRoot + " does not exist.");
                    }
                    else if (!dir.isDirectory())
                    {
                        errors.reject("newServerForm", form.ff_physicalRoot + " is not a directory.");
                    }
                    else
                    {
                        File[] files = dir.listFiles();
                        if (files == null)
                        {
                            errors.reject("newServerForm", "Unable to get a listing of the files in " + form.ff_physicalRoot);
                        }
                        else
                        {
                            if (files.length != 0)
                            {
                                errors.reject("newServerForm", "The directory " + form.ff_physicalRoot + " is not empty.  It must be empty.");
                            }
                        }
                    }
                }
                catch (Throwable t)
                {
                    _log.error("Error", t);
                    errors.reject("newServerForm", "An exception occurred validating '" + form.ff_physicalRoot + "'" + t);
                }
            }
        }

        public ModelAndView getView(NewServerForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!getViewContext().getUser().isAdministrator())
                return HttpView.throwUnauthorizedMV();

            return FormPage.getView(BtController.class, form, errors, "newServer.jsp");
        }

        public boolean handlePost(NewServerForm form, BindException errors) throws Exception
        {
            Server server = new Server();
            server.setName(form.ff_name);
            server.setWsdlURL(form.ff_wsdlURL);
            server.setServiceNamespaceURI(form.ff_serviceNamespaceURI);
            server.setServiceLocalPart(form.ff_serviceLocalPart);
            server.setUserName(form.ff_username);
            server.setPassword(form.ff_password);
            server.setPhysicalRoot(form.ff_physicalRoot);
            server.setContainer(getContainer().getId());
            if (validateServer(server, errors))
            {
                server = BtManager.get().insert(server);
                _detailsURL = new BtServer(server).detailsURL();
                return true;
            }
            return false;
        }

        public ActionURL getSuccessURL(NewServerForm newServerForm)
        {
            return _detailsURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Define New Server");
        }

        private boolean validateServer(Server server, BindException errors)
        {
            BtServer btServer = new BtServer(server);
            try
            {
                Service service = btServer.getService();
            }
            catch (Throwable t)
            {
                _log.error("Error", t);
                errors.reject("newServerForm", "An exception occurred trying to fetch the service: " + t);
                return false;
            }
            try
            {
                Object value = btServer.login(null, "view");
                if (!(value instanceof Browse_response))
                {
                    errors.reject("newServerForm", "The username or password appear to be invalid.  Response was a " + value.getClass() + " instead of Browse_response");
                    return false;
                }
            }
            catch (Throwable t)
            {
                _log.error("Error", t);
                errors.reject("newServerForm", "An exception occurred trying to log in.");
                return false;
            }
            return true;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class AdminAction extends FormViewAction
    {
        public void validateCommand(Object target, Errors errors)
        {
        }

        public ModelAndView getView(Object o, boolean reshow, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/biotrue/controllers/admin.jsp", null, errors);
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            String[] servers = getViewContext().getRequest().getParameterValues("deleteServer");
            if (servers != null)
            {
                for (String id : servers)
                {
                    final BtServer btServer = BtServer.fromId(NumberUtils.toInt(id));
                    if (btServer != null)
                    {
                        // for now only allow deletion if a server is not currently synchronizing
                        if (!BtTaskManager.get().anyTasks(btServer))
                            BtManager.get().deleteServer(NumberUtils.toInt(id));
                        else
                        {
                            errors.reject("adminAction", "The server: " + btServer.getName() + "cannot be deleted while it is synchronizing files");
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(Object o)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Server Administration");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ScheduledSyncAction extends SimpleViewAction<ServerForm>
    {
        public ModelAndView getView(ServerForm form, BindException errors) throws Exception
        {
            return FormPage.getView(BtController.class, form, "scheduledSync.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Scheduled Synchronization Settings");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class UpdateScheduledSync extends SimpleViewAction<ServerUpdateForm>
    {
        public ModelAndView getView(ServerUpdateForm form, BindException errors) throws Exception
        {
            final Server server = BtManager.get().getServer(form.getServerId());

            server.setSyncInterval(form.getServerSyncInterval());
            server.setNextSync(null);
            BtManager.get().updateServer(getUser(), server);
            ScheduledTask.setTask(getUser(), server, null);

            ActionURL url = getViewContext().cloneActionURL();
            url.setAction(AdminAction.class);

            return HttpView.redirect(url);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ConfigurePasswordAction extends FormViewAction<ServerUpdateForm>
    {
        public void validateCommand(ServerUpdateForm target, Errors errors)
        {
        }

        public ModelAndView getView(ServerUpdateForm form, boolean reshow, BindException errors) throws Exception
        {
            return FormPage.getView(BtController.class, form, errors, "configurePassword.jsp");
        }

        public boolean handlePost(ServerUpdateForm form, BindException errors) throws Exception
        {
            final Server server = BtManager.get().getServer(form.getServerId());

            server.setPassword(form.getPassword());
            BtManager.get().updateServer(getUser(), server);

            return true;
        }

        public ActionURL getSuccessURL(ServerUpdateForm serverForm)
        {
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Set Server Password");
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class CancelSynchronizationAction extends SimpleViewAction<ServerForm>
    {
        public ModelAndView getView(ServerForm form, BindException errors) throws Exception
        {
            BtTaskManager.get().cancelTasks(form.getServer());
            ActionURL url = getViewContext().cloneActionURL();
            url.setAction(AdminAction.class);

            return HttpView.redirect(url);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class ServerUpdateForm extends ServerForm
    {
        private int _serverSyncInterval;
        private String _password;

        public int getServerSyncInterval() {return _serverSyncInterval;}
        public void setServerSyncInterval(int serverSyncInterval){_serverSyncInterval = serverSyncInterval;}
        public String getPassword(){return _password;}
        public void setPassword(String password){_password = password;}
    }
}
