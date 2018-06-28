package org.labkey.api.webdav;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Path;

import java.beans.Introspector;
import java.util.Map;

public class DavException extends Exception
{
    protected WebdavStatus status;
    protected String message;
    protected WebdavResource resource;
    protected Path resourcePath;

    public DavException(WebdavStatus status)
    {
        this.status = status;
    }

    public DavException(WebdavStatus status, String message)
    {
        this.status = status;
        this.message = message;
    }

//        DavException(WebdavStatus status, String message, String path)
//        {
//            this.status = status;
//            this.message = message;
//            if (null != path)
//                this.resourcePath = Path.parse(path);
//        }

    public DavException(WebdavStatus status, String message, Path path)
    {
        this.status = status;
        this.message = message;
        this.resourcePath = path;
    }

    public DavException(WebdavStatus status, String message, Throwable t)
    {
        this.status = status;
        this.message = message;
        initCause(t);
    }

    public DavException(Throwable x)
    {
        this.status = WebdavStatus.SC_INTERNAL_SERVER_ERROR;
        initCause(x);
    }

    public WebdavStatus getStatus()
    {
        return status;
    }

    public int getCode()
    {
        return status.code;
    }

    public String getMessage()
    {
        return StringUtils.defaultIfEmpty(message, status.message);
    }

    public WebdavResource getResource()
    {
        return resource;
    }

    public Path getResourcePath()
    {
        if (null != resource)
            return resource.getPath();
        else if (null != resourcePath)
            return resourcePath;
        return null;
    }

    public JSONObject toJSON()
    {
        JSONObject o = new JSONObject();
        o.put("status", status.code);
        o.put("message", getMessage());
        Path p = getResourcePath();
        if (null != p)
        {
            o.put("resourcePath", p.toString());
            o.put("resourceName", p.getName());
        }
        // check for interesting annotations
        Map<Enum,String> map = ExceptionUtil.getExceptionDecorations(this);
        for (Map.Entry<Enum,String> e : map.entrySet())
        {
            if (e.getKey() == ExceptionUtil.ExceptionInfo.ResolveURL ||
                    e.getKey() == ExceptionUtil.ExceptionInfo.ResolveText ||
                    e.getKey() == ExceptionUtil.ExceptionInfo.HelpURL ||
                    e.getKey() == ExceptionUtil.ExceptionInfo.ExtraMessage)
            {
                o.put(Introspector.decapitalize(e.getKey().name()), e.getValue());
            }
        }
        return o;
    }
}


