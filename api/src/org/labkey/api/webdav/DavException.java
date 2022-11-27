/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.webdav;

import org.apache.commons.lang3.StringUtils;
import org.json.old.JSONObject;
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

    @Override
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
        Map<Enum<?>, String> map = ExceptionUtil.getExceptionDecorations(this);
        for (Map.Entry<Enum<?>, String> e : map.entrySet())
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


