/*
 * Copyright (c) 2004-2013 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.attachments;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.webdav.WebdavResolver;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Attachment is not quite a full blown Entity so we have some duplication here
 * <p/>
 * NOTE: Attachment is only used to list the attachments, it does not contain document data
 * and cannot be used directly to insert or update attachments.
 */
public class Attachment implements Serializable
{
    private String _parent; // entityid
    private String _name;
    private String _container;   // container path
    private int _createdBy;
    private long _created;
    private File _file;
    private Date _lastIndexed;

    public Attachment()
    {
        assert MemTracker.put(this);
    }


    public ActionURL getDownloadUrl(Class<? extends Controller> actionClass)
    {
        Container c = ContainerManager.getForId(getContainer());
        return new DownloadURL(actionClass, c, getParent(), getName());
    }


    public String getFileExtension()
    {
        return getFileExtension(_name);
    }

    public static String getFileExtension(String name)
    {
        if (null == name)
            return "doc";
        int dotPos = name.lastIndexOf(".");
        if (dotPos > -1 && dotPos < name.length() - 1)
            return name.substring(dotPos + 1).toLowerCase();
        return "doc";
    }

    public String getFileIcon()
    {
        return getFileIcon(_name);
    }
    

    public static String getFileIcon(String name)
    {
        String extension = "_generic"; // old-school default; used if name is null or file has no extension
        if (null != name)
        {
            extension = name;
            int dotPos = name.lastIndexOf(".");
            if (dotPos > -1)
                extension = name.substring(dotPos + 1).toLowerCase();
        }
        String icon = lookupIcon(extension);
        if (null == icon)
            icon = lookupIcon("_generic");

        if (null == icon)
            return ""; // Could not find specific icon or default, generic one. Punt.

        return icon;
    }


    static MimeMap mime = new MimeMap();
    private static final ConcurrentHashMap<String, String> icons = new ConcurrentHashMap<>();

    static String lookupIcon(String lookup)
    {
        synchronized (icons)
        {
            if (icons.size() == 0)
            {
                WebdavResolver staticFiles = ServiceRegistry.get().getService(WebdavResolver.class);
                if (staticFiles != null)
                {
                    Collection<String> names = staticFiles.lookup(new Path("_icons")).listNames();
                    for (String fileName : names)
                    {
                        int index = fileName.lastIndexOf('/');
                        int dot = fileName.lastIndexOf('.');
                        String extension = fileName.substring(index + 1, dot).toLowerCase();
                        if (mime.isInlineImageFor(fileName))    // .jpg .png .gif
                        {
                            String path = "/_icons/" + fileName;
                            icons.put(extension, path);
                            String contenttype = mime.getContentType(extension);
                            if (null != contenttype)
                                icons.put(contenttype, path);
                        }
                    }
                }
                if (icons.size() == 0)
                {
                    ServletContext context = ViewServlet.getViewServletContext();
                    if (null != context)
                    {
                        Set<String> paths = context.getResourcePaths("/_icons");
						if (null != paths)
							for (String fileName : paths)
							{
								int index = fileName.lastIndexOf('/');
								int dot = fileName.lastIndexOf('.');
								String extension = fileName.substring(index + 1, dot).toLowerCase();
								if (mime.isInlineImageFor(fileName))    // .jpg .png .gif
									icons.put(extension, fileName);
								String contenttype = mime.getContentType(extension);
								if (null != contenttype)
									icons.put(contenttype, fileName);
							}
                    }
                }
            }
        }

        String icon = icons.get(lookup);
        if (icon != null)
            return icon;

        String mimetype = mime.getContentType(lookup);
        if (mimetype != null)
        {
            icon = icons.get(mimetype);
            if (icon == null)
            {
                int i = mimetype.indexOf('/');
                if (i > 0)
                    icon = icons.get(mimetype.substring(0,i));
            }
            if (icon != null)
                icons.put(lookup, icon);
        }
        return icon;
    }


    public String getName()
    {
        return _name;
    }


    public void setDocumentName(String name)
    {
        _name = name;
    }


    public void setName(String name)
    {
        _name = name;
    }


    public int getCreatedBy()
    {
        return _createdBy;
    }


    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }


    public Date getLastIndexed()
    {
        return _lastIndexed;
    }


    public void setLastIndexed(Date lastIndexed)
    {
        _lastIndexed = lastIndexed;
    }


    public String getCreatedByName(User currentUser)
    {
        return UserManager.getDisplayName(_createdBy, currentUser);
    }


    public Date getCreated()
    {
        return new Date(_created);
    }


    public void setCreated(Date created)
    {
        _created = created.getTime();
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }


    public String getParent()
    {
        return _parent;
    }


    public void setParent(String parent)
    {
        _parent = parent;
    }

    public File getFile()
    {
        return _file;
    }

    public void setFile(File file)
    {
        _file = file;
    }
}
