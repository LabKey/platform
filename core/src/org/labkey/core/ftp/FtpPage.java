package org.labkey.core.ftp;

import org.labkey.api.data.Container;
import org.labkey.api.ftp.FtpConnector;
import org.labkey.api.security.User;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.apache.commons.lang.StringUtils;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jan 27, 2008
 * Time: 2:21:42 PM
 */
public class FtpPage
{
    public String pipeline = null;

    public String getScheme()
    {
        return AppProps.getInstance().isPipelineFTPSecure() ? "ftps" : "ftp";
    }
    
    public String getHost()
    {
        return AppProps.getInstance().getPipelineFTPHost();
    }

    public String getPort()
    {
        return StringUtils.defaultIfEmpty(AppProps.getInstance().getPipelineFTPPort(),"21");
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

    public String getPath(Container c)
    {
        StringBuilder path = new StringBuilder(100);
        path.append(c.getPath());
        if (null != pipeline && pipeline.length() > 0)
        {
            String subdir = PageFlowUtil.decode(pipeline);
            if (subdir.equals(".") || subdir.startsWith("./"))
                subdir = subdir.substring(1);
            if (path.charAt(path.length()-1) != '/')
                path.append('/');
            path.append(FtpConnector.PIPELINE_LINK);
            if (!subdir.startsWith("/"))
                path.append('/');
            path.append(subdir);
        }
        return path.toString();
    }

    private String _getHostPath(Container c)
    {
        StringBuilder path = new StringBuilder(100);
        path.append(getHost());
        if (getPort().length() != 0)
            path.append(":").append(getPort());
        path.append(getPath(c));
        return path.toString();
    }
    
    public String getURL(Container c, User user)
    {
        String ftpUser = user.getEmail();
        return getScheme()+"://"+PageFlowUtil.encode(ftpUser)+"@"+_getHostPath(c);
    }

    public String getURL(Container c)
    {
        return getScheme()+"://"+_getHostPath(c);
    }
}
