/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.ftp.FtpConnector;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.view.ViewContext;
import org.labkey.api.webdav.WebdavService;
import org.labkey.core.webdav.DavController;
import org.apache.commons.lang.StringUtils;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jan 27, 2008
 * Time: 2:21:42 PM
 */
public class FtpPage implements HasViewContext
{
    ViewContext context = null;
    boolean useFTP = false;

    // set ONE of these
    public String path = null;
    public String pipeline = null;
    public String fileSetName = null;

    public FtpPage()
    {
//        useFTP = null != StringUtils.trimToNull(AppProps.getInstance().getPipelineFTPHost());
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public void setFileSetName(String fileSetName)
    {
        this.fileSetName = fileSetName;
    }

    public String getFileSetName()
    {
        return fileSetName;
    }

    public void setViewContext(ViewContext context)
    {
        this.context = context;
    }

    public ViewContext getViewContext()
    {
        return context;
    }

    public String getScheme()
    {
        if (useFTP)
            return AppProps.getInstance().isPipelineFTPSecure() ? "ftps" : "ftp";
        else
            return getViewContext().getRequest().getScheme();
    }
    
    public String getHost()
    {
        if (useFTP)
            return AppProps.getInstance().getPipelineFTPHost();
        else
            return getViewContext().getRequest().getServerName();
    }

    public String getPort()
    {
        if (useFTP)
            return StringUtils.defaultIfEmpty(AppProps.getInstance().getPipelineFTPPort(),"21");
        else
            return "" + getViewContext().getRequest().getServerPort();
    }

    // encoded path
    public void setPipeline(String pipeline)
    {
        this.pipeline = pipeline;
    }

    public String getPipeline()
    {
        return this.pipeline;
    }

    public String getPath()
    {
        if (path != null)
            return path;

        // compute path from current container and pipeline relative path or filesetName
        Container c = getViewContext().getContainer();
        StringBuilder path = new StringBuilder(100);
        path.append(c.getEncodedPath());
        if (!StringUtils.isEmpty(pipeline))
        {
            String subdir = PageFlowUtil.decode(pipeline);
            if (subdir.equals(".") || subdir.startsWith("./"))
                subdir = subdir.substring(1);
            if (path.charAt(path.length()-1) != '/')
                path.append('/');
            path.append(FtpConnector.PIPELINE_LINK);
            if (!subdir.startsWith("/"))
                path.append('/');
            path.append(PageFlowUtil.encodePath(subdir));
        }
        else if (!StringUtils.isEmpty(fileSetName))
        {
            if (path.charAt(path.length()-1) != '/')
                path.append('/');
            path.append("@files/");
            path.append(PageFlowUtil.encodePath(fileSetName));
        }        
        return path.toString();
    }

    private String _getHostPath()
    {
        StringBuilder path = new StringBuilder(200);
        path.append(getHost());
        if (getPort().length() != 0)
            path.append(":").append(getPort());
        if (!useFTP)
            path.append(getViewContext().getContextPath()).append("/").append(WebdavService.getServletPath());
        path.append(getPath());
        return path.toString();
    }
    
    public String getUserURL()
    {
        String ftpUser = getViewContext().getUser().getEmail();
        return getScheme()+"://"+PageFlowUtil.encode(ftpUser)+"@"+_getHostPath();
    }

    public String getURL()
    {
        return getScheme()+"://"+_getHostPath();
    }
}
