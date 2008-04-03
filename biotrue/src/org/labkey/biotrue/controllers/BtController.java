package org.labkey.biotrue.controllers;

import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionMessage;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.data.Container;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.ACL;
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

import javax.xml.rpc.Service;
import java.io.File;
import java.net.URL;

@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class BtController extends ViewController
{
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

    @Jpf.Action
    protected Forward begin() throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        VBox view = new VBox(new BtOverviewWebPart(getViewContext()));

        if (BtManager.get().getServers(getContainer()).length > 0)
        {
            BtSchema schema = new BtSchema(getUser(), getContainer());
            QuerySettings settings = new QuerySettings(getActionURL(), "Server");
            settings.setSchemaName(schema.getSchemaName());
            settings.setQueryName("Servers");
            settings.setAllowChooseQuery(false);
            view.addView(new BtServerView(getViewContext(), schema, settings));
        }
        return renderInTemplate(view, getContainer(), "BioTrue Connector");
    }
    

    @Jpf.Action
    protected Forward browse(ServerForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        BtServer server = form.getServer();
        Browse_response login = server.login();
        String ent = getRequest().getParameter("ent");
        BtEntity data = null;
        if (ent != null)
        {
            data = new BtEntity(server);
            data.setBioTrue_Ent(ent);
            data.setBioTrue_Id(getRequest().getParameter("id"));
        }
        Browse_response resp = server.browse(login.getData().getSession_id(), data);
        StringBuilder html = new StringBuilder();
        for (Entityinfo entity : resp.getData().getAllContent())
        {
            ActionURL url = cloneActionURL();
            if ("sample".equals(entity.getType()))
            {
                url.setAction("download");
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
        return renderInTemplate(new HtmlView(html.toString()), getContainer(), "Browse");
    }
    @Jpf.Action
    protected Forward download(ServerForm form) throws Exception
    {
        requiresPermission(ACL.PERM_UPDATE);
        BtServer server = form.getServer();
        Browse_response login = server.login();
        BtEntity data = new BtEntity(server);
        data.setBioTrue_Ent(getRequest().getParameter("ent"));
        data.setBioTrue_Id(getRequest().getParameter("id"));
        Download_response response = server.download(login.getData().getSession_id(), data);
        return new Forward(new URL(response.getData().getUrl()));
    }

    @Jpf.Action
    protected Forward synchronizeServer(ServerForm form) throws Exception
    {
        requiresPermission(ACL.PERM_UPDATE);
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
        return new ViewForward(forward);
    }

    @Jpf.Action
    protected Forward showServers(ViewForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        BtSchema schema = new BtSchema(getUser(), getContainer());
        QuerySettings settings = new QuerySettings(getActionURL(), "Server");
        settings.setSchemaName(schema.getSchemaName());
        settings.setQueryName("Servers");
        settings.setAllowChooseQuery(false);
        return renderInTemplate(new QueryView(schema, settings), getContainer(), "BioTrue Servers");
    }

    @Jpf.Action
    protected Forward showServer(ServerForm form) throws Exception
    {
        BtServer server = form.getServer();
        requiresPermission(ACL.PERM_READ, server.getContainer().getId());
        return renderInTemplate(FormPage.getView(BtController.class, form, "showServer.jsp"), getContainer(), server.getLabel());
    }

    @Jpf.Action
    protected Forward newServer(NewServerForm form) throws Exception
    {
        requiresGlobalAdmin();
        if (isPost())
        {
            Forward fwd = doNewServer(form);
            if (fwd != null)
                return fwd;
        }
        return renderInTemplate(FormPage.getView(BtController.class, form, "newServer.jsp"), getContainer(), "Define New Server");
    }

    protected boolean addError(String error)
    {
        PageFlowUtil.getActionErrors(getRequest(), true).add("main", new ActionError("Error", error));
        return true;
    }
    

    protected Forward doNewServer(NewServerForm form) throws Exception
    {
        boolean errors = false;
        if (form.ff_name == null)
        {
            errors = addError("Name is required.");
        }
        if (form.ff_physicalRoot == null)
        {
            errors = addError("Download location is required.");
        }
        else
        {
            try
            {
                File dir = new File(form.ff_physicalRoot);
                if (!dir.exists())
                {
                    errors = addError(form.ff_physicalRoot + " does not exist.");
                }
                else if (!dir.isDirectory())
                {
                    errors = addError(form.ff_physicalRoot + " is not a directory.");
                }
                else
                {
                    File[] files = dir.listFiles();
                    if (files == null)
                    {
                        errors = addError("Unable to get a listing of the files in " + form.ff_physicalRoot);
                    }
                    else
                    {
                        if (files.length != 0)
                        {
                            errors = addError("The directory " + form.ff_physicalRoot + " is not empty.  It must be empty.");
                        }
                    }
                }
            }
            catch (Throwable t)
            {
                _log.error("Error", t);
                addError("An exception occurred validating '" + form.ff_physicalRoot + "'" + t);
            }
        }
        if (errors)
            return null;
        Server server = new Server();
        server.setName(form.ff_name);
        server.setWsdlURL(form.ff_wsdlURL);
        server.setServiceNamespaceURI(form.ff_serviceNamespaceURI);
        server.setServiceLocalPart(form.ff_serviceLocalPart);
        server.setUserName(form.ff_username);
        server.setPassword(form.ff_password);
        server.setPhysicalRoot(form.ff_physicalRoot);
        server.setContainer(getContainer().getId());
        if (!validateServer(server))
        {
            return null;
        }
        server = BtManager.get().insert(server);
        return new ViewForward(new BtServer(server).detailsURL());
    }

    boolean validateServer(Server server)
    {
        BtServer btServer = new BtServer(server);
        try
        {
            Service service = btServer.getService();
        }
        catch (Throwable t)
        {
            _log.error("Error", t);
            addError("An exception occurred trying to fetch the service: " + t);
            return false;
        }
        try
        {
            Object value = btServer.login(null, "view");
            if (!(value instanceof Browse_response))
            {
                addError("The username or password appear to be invalid.  Response was a " + value.getClass() + " instead of Browse_response");
                return false;
            }
        }
        catch (Throwable t)
        {
            _log.error("Error", t);
            addError("An exception occurred trying to log in.");
            return false;
        }
        return true;
    }

    @Jpf.Action
    protected Forward admin() throws Exception
    {
        requiresAdmin();
        return renderInTemplate(new JspView("/org/labkey/biotrue/controllers/admin.jsp"), getContainer(), "Server Administration");
    }

    @Jpf.Action
    protected Forward scheduledSync(ServerForm form) throws Exception
    {
        requiresAdmin();
        return renderInTemplate(FormPage.getView(BtController.class, form, "scheduledSync.jsp"), getContainer(), "Scheduled Synchronization Settings");
    }

    @Jpf.Action
    protected Forward updateScheduledSync(ServerUpdateForm form) throws Exception
    {
        requiresAdmin();
        final Server server = BtManager.get().getServer(form.getServerId());

        server.setSyncInterval(form.getServerSyncInterval());
        server.setNextSync(null);
        BtManager.get().updateServer(getUser(), server);
        ScheduledTask.setTask(getUser(), server, null);

        ActionURL url = cloneActionURL();
        url.setAction("admin");
        return new ViewForward(url);
    }

    @Jpf.Action
    protected Forward updateAdmin() throws Exception
    {
        requiresAdmin();
        String[] servers = getRequest().getParameterValues("deleteServer");
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
                        PageFlowUtil.getActionErrors(getRequest(), true).add("main", new ActionMessage("Error", "The server: " + btServer.getName() + "cannot be deleted while it is synchronizing files"));
                }
            }
        }
        ActionURL url = cloneActionURL();
        url.setAction("admin");
        return new ViewForward(url);
    }

    @Jpf.Action
    protected Forward configurePassword(ServerForm form) throws Exception
    {
        requiresAdmin();
        return renderInTemplate(FormPage.getView(BtController.class, form, "configurePassword.jsp"), getContainer(), "Set Server Password");
    }

    @Jpf.Action
    protected Forward updatePassword(ServerUpdateForm form) throws Exception
    {
        requiresAdmin();
        final Server server = BtManager.get().getServer(form.getServerId());

        server.setPassword(form.getPassword());
        BtManager.get().updateServer(getUser(), server);

        ActionURL url = cloneActionURL();
        url.setAction("admin");
        return new ViewForward(url);
    }

    @Jpf.Action
    protected Forward cancelSynchronization(ServerForm form) throws Exception
    {
        BtTaskManager.get().cancelTasks(form.getServer());
        ActionURL url = cloneActionURL();
        url.setAction("admin");
        return new ViewForward(url);
    }

    public static class ServerUpdateForm extends ViewForm
    {
        private int _serverSyncInterval;
        private int _serverId;
        private String _password;

        public int getServerSyncInterval() {return _serverSyncInterval;}
        public void setServerSyncInterval(int serverSyncInterval){_serverSyncInterval = serverSyncInterval;}
        public int getServerId(){return _serverId;}
        public void setServerId(int serverId){_serverId = serverId;}
        public String getPassword(){return _password;}
        public void setPassword(String password){_password = password;}
    }
}
