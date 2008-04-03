package org.labkey.core.ftp;

import org.apache.log4j.Logger;
import org.labkey.api.action.InterfaceAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.ftp.FtpConnector;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GroovyView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Oct 3, 2007
 * Time: 3:54:42 PM
 */
public class FtpController extends SpringActionController
{
    DefaultActionResolver _actionResolver = new DefaultActionResolver(FtpController.class);

    public FtpController()
    {
        super();
        setActionResolver(_actionResolver);
    }

    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws MultipartException
    {
        return super.handleRequest(request, response);
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class FtpConnectorAction extends InterfaceAction<FtpConnector>
    {
        public FtpConnectorAction()
        {
            super(FtpConnector.class, DefaultModule.CORE_MODULE_NAME);
        }

        public FtpConnector getInstance(int version)
        {
            return new FtpConnectorImpl(getViewContext().getRequest());
        }
    }

    private static class FtpConnectorImpl implements FtpConnector
    {
        HttpServletRequest _request = null;
        
        FtpConnectorImpl(HttpServletRequest request)
        {
             _request = request;
        }


        public int userid(String username, String password) throws Exception
        {
            User user;
            
            // first see if we are already authenticated (e.g. sessionid, or basic auth)
            if (null != _request)
            {
                user = (User)_request.getUserPrincipal();
                if (null != user && user.getEmail().equalsIgnoreCase(username))
                    return user.getUserId();
            }

            // authenticate with password
            user = AuthenticationManager.authenticate(username, password);
            return user == null ? -1 : user.getUserId();
        }


        public String[] getAllChildren(String folder)
        {
            if (folder == null)
                return null;
            Container c = ContainerManager.getForPath(folder);
            if (null == c)
                return null;

            PipeRoot[] pipes = PipelineService.get().getAllPipelineRoots();
            HashMap<String,PipeRoot> pipeMap = new HashMap<String, PipeRoot>();
            for (PipeRoot pipe : pipes)
                pipeMap.put(pipe.getContainer().getId(), pipe);

            Container[] all = ContainerManager.getAllChildren(c);
            ArrayList<String> paths = new ArrayList<String>(all.length*2);
            for (Container container : all)
            {
                paths.add(container.getPath());
                PipeRoot pipe = pipeMap.get(container.getId());
                if (null != pipe)
                    paths.add(container.getPath() + "/" + PIPELINE_LINK);
            }
            return paths.toArray(new String[paths.size()]);
        }


        public WebFolderInfo getFolderInfo(int userid, String folder)
        {
            User user = UserManager.getUser(userid);
            if (user == null)
                return null;
            if (folder == null)
                return null;

            boolean isPipelineLink = false;
            if (folder.endsWith("/"))
                folder = folder.substring(0,folder.length()-1);
            if (folder.endsWith("/" + PIPELINE_LINK))
            {
                isPipelineLink = true;
                folder = folder.substring(0, folder.length()- PIPELINE_LINK.length()-1);
            }
            
            Container c = ContainerManager.getForPath(folder);
            if (null == c)
                return null;

            FileSystemRoot fsRoot = null;
            int permFolder = c.getAcl().getPermissions(user);
            FileSystemRoot pipelineRoot = null;
            int permPipeline = 0;

            AttachmentDirectory dir = null;
            try
            {
                dir = AttachmentService.get().getMappedAttachmentDirectory(c, true);
            }
            catch (AttachmentService.UnsetRootDirectoryException x)
            {
                /* */
            }
            if (null != dir)
            {
                if (c.getId().equals(dir.getContainerId()))
                    fsRoot = initFileSystemRoot(dir.getFileSystemDirectory());
                // UNDONE: no separate permissions yet.  Just use folder permissions
            }

            try
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(c);
                URI uriRoot = (root != null) ? root.getUri(c) : null;
                if (uriRoot != null)
                {
                    ACL acl = SecurityManager.getACL(c, root.getEntityId());
                    if (null != acl)
                        permPipeline = acl.getPermissions(user);
                    pipelineRoot = initFileSystemRoot(new File(uriRoot));
                }
            }
            catch (SQLException x)
            {
                Logger.getLogger(FtpController.class).error("unexpected exception", x);
            }

            String[] names;
            if (isPipelineLink)
            {
                names = new String[0];
            }
            else
            {
                List<Container> children = ContainerManager.getChildren(c, user, ACL.PERM_READ);
                names = new String[children.size() + ((pipelineRoot!=null)?1:0)];
                int i=0;
                for (Container child : children)
                    names[i++] = child.getName();
                if (null != pipelineRoot)
                    names[i] = PIPELINE_LINK;
            }

            ActionURL url = new ActionURL(isPipelineLink ? "Pipeline" : "Project", "begin", c);

            WebFolderInfo info = new WebFolderInfo();
            info.url = url.getURIString();
            info.name = c.getName();
            info.path = isPipelineLink ? c.getPath() + "/" + PIPELINE_LINK : c.getPath();
            info.created = c.getCreated() == null ? 0 : c.getCreated().getTime();
            info.fsRoot = isPipelineLink ? pipelineRoot : fsRoot;
            info.perm = isPipelineLink ? permPipeline : permFolder;
            info.subfolders = names;
            return info;
        }
    }


    static FtpConnector.FileSystemRoot initFileSystemRoot(File f)
    {
        FtpConnector.FileSystemRoot root = new FtpConnector.FileSystemRoot(f);
        NetworkDrive drive = NetworkDrive.getNetworkDrive(f.getPath());
        if (null != drive)
        {
            root.drivePassword = drive.getPassword();
            root.drivePath = drive.getPath();
            root.driveUser = drive.getUser();
        }
        return root;
    }


    @RequiresPermission(ACL.PERM_READ) @RequiresLogin
    public class DropAction extends SimpleViewAction<FtpPage>
    {
        public ModelAndView getView(FtpPage form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            getPageConfig().setShowHeader(false);
            return new JspView<FtpPage>(FtpController.class, "drop.jsp", form);
            //return new GroovyView<FtpPage>("/org/labkey/core/ftp/drop.gm", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class InfoAction extends SimpleViewAction<FtpPage>
    {
        public ModelAndView getView(FtpPage form, BindException errors) throws Exception
        {
            JspView<FtpPage> detailsView = new JspView<FtpPage>(FtpController.class, "ftpdetails.jsp", form);
            detailsView.setTitle("FTP Instructions");
            getPageConfig().setTemplate(PageConfig.Template.None);
            return detailsView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }
    
}