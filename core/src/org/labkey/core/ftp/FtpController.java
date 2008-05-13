/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.core.ftp;

import org.labkey.api.action.InterfaceAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.ftp.FtpConnector;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.*;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.PageConfig;
import org.labkey.core.webdav.WebdavResolver;
import org.labkey.core.webdav.WebdavResolverImpl;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
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
            return new FtpConnectorImpl(getViewContext().getUser());
        }
    }

    public static class FtpConnectorImpl implements FtpConnector
    {
        //HttpServletRequest _request = null;
        User _user = null;
        WebdavResolver _resolver = WebdavResolverImpl.get();
        
        public FtpConnectorImpl(User user)
        {
             _user = user;
        }

        public int userid(String username, String password) throws Exception
        {
            // first see if we are already authenticated (e.g. sessionid, or basic auth)
            if (null != _user)
            {
                if (_user.getEmail().equalsIgnoreCase(username))
                    return _user.getUserId();
            }

            // authenticate with password
            _user = AuthenticationManager.authenticate(username, password);
            return _user == null ? -1 : _user.getUserId();
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


        public WebFolderInfo getFolderInfo(int userid, String path)
        {
            User user = UserManager.getUser(userid);
            if (user == null)
                return null;
            if (path == null)
                return null;

            WebdavResolverImpl.ResourceImpl  resource = (WebdavResolverImpl.ResourceImpl)_resolver.lookup(path);
            if (!resource.isWebFolder())
                return null;

            path = resource.getPath();
            boolean isPipelineLink = path.endsWith("/" + PIPELINE_LINK);
            String folder = isPipelineLink ? path.substring(0, path.length()-("/"+PIPELINE_LINK).length()) : path;
            ActionURL url = new ActionURL(isPipelineLink ? "Pipeline" : "Project", "begin", folder);

            WebFolderInfo info = new WebFolderInfo();
            info.url = url.getURIString();
            info.name = resource.getName();
            info.path = resource.getPath();
            info.created = resource.getCreation();
            info.fsRoot = resource.getFile() == null ? null : initFileSystemRoot(resource.getFile());
            info.perm = resource.getPermissions(user);
            List<String> webFoldersNames = resource.getWebFoldersNames(user);
            info.subfolders = webFoldersNames.toArray(new String[webFoldersNames.size()]);
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