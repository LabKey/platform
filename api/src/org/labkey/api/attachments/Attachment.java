/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.Path;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.webdav.WebdavResolver;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Attachments are files stored as BLOBs in the database.
 * They are not quite a full blown Entity so we have some duplication here with what's defined in {@link org.labkey.api.data.Entity}
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

    private static final Map<String, String> extensionFontClsMap = new HashMap<>();

    static {
        extensionFontClsMap.put("_deleted", "fa fa-file-o");
        extensionFontClsMap.put("_generic", "fa fa-file-o");
        extensionFontClsMap.put("application", "fa fa-list-alt");
        extensionFontClsMap.put("audio", "fa fa-file-audio-o");
        extensionFontClsMap.put("dll", "fa fa-file-code-o");
        extensionFontClsMap.put("doc", "fa fa-file-word-o");
        extensionFontClsMap.put("docm", "fa fa-file-word-o");
        extensionFontClsMap.put("docx", "fa fa-file-word-o");
        extensionFontClsMap.put("dotm", "fa fa-file-word-o");
        extensionFontClsMap.put("dotx", "fa fa-file-word-o");
        extensionFontClsMap.put("exe", "fa fa-file-code-o");
        extensionFontClsMap.put("file_temporary", "fa fa-file-o");
        extensionFontClsMap.put("folder", "fa fa-folder-o");
        extensionFontClsMap.put("gz", "fa fa-file-archive-o");
        extensionFontClsMap.put("html", "fa fa-file-code-o");
        extensionFontClsMap.put("icon_folder1", "");
        extensionFontClsMap.put("icon_folder2", "");
        extensionFontClsMap.put("image", "fa fa-file-image-o");
        extensionFontClsMap.put("iqy", "fa fa-file-code-o");
        extensionFontClsMap.put("jar", "fa fa-file-archive-o");
        extensionFontClsMap.put("log", "fa fa-file-text-o");
        extensionFontClsMap.put("mdb", "fa fa-database");
        extensionFontClsMap.put("pdf", "fa fa-file-pdf-o");
        extensionFontClsMap.put("potm", "fa fa-file-powerpoint-o");
        extensionFontClsMap.put("potx", "fa fa-file-powerpoint-o");
        extensionFontClsMap.put("ppsm", "fa fa-file-powerpoint-o");
        extensionFontClsMap.put("ppsx", "fa fa-file-powerpoint-o");
        extensionFontClsMap.put("ppt", "fa fa-file-powerpoint-o");
        extensionFontClsMap.put("pptm", "fa fa-file-powerpoint-o");
        extensionFontClsMap.put("pptx", "fa fa-file-powerpoint-o");
        extensionFontClsMap.put("prg", "fa fa-file-code-o");
        extensionFontClsMap.put("ps", "fa fa-print");
        extensionFontClsMap.put("rtf", "fa fa-file-word-o");
        extensionFontClsMap.put("tar", "fa fa-file-archive-o");
        extensionFontClsMap.put("text", "fa fa-file-text-o");
        extensionFontClsMap.put("tgz", "fa fa-file-archive-o");
        extensionFontClsMap.put("tsv", "fa fa-file-excel-o");
        extensionFontClsMap.put("txt", "fa fa-file-text-o");
        extensionFontClsMap.put("video", "fa fa-file-video-o");
        extensionFontClsMap.put("vsd", "fa fa-file-image-o");
        extensionFontClsMap.put("wiki", "fa fa-file-code-o");
        extensionFontClsMap.put("xar", "fa fa-file-archive-o");
        extensionFontClsMap.put("xls", "fa fa-file-excel-o");
        extensionFontClsMap.put("xlsb", "fa fa-file-excel-o");
        extensionFontClsMap.put("xlsm", "fa fa-file-excel-o");
        extensionFontClsMap.put("xlsx", "fa fa-file-excel-o");
        extensionFontClsMap.put("xltm", "fa fa-file-excel-o");
        extensionFontClsMap.put("xltx", "fa fa-file-excel-o");
        extensionFontClsMap.put("xml", "fa fa-file-code-o");
        extensionFontClsMap.put("zip", "fa fa-file-archive-o");
    }

    public Attachment()
    {
        MemTracker.getInstance().put(this);
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

    public static String getFileIconFontCls(String name)
    {
        String fileName = getFileIcon(name);
        if (fileName == null)
            return null;
        int index = fileName.lastIndexOf('/');
        int dot = fileName.lastIndexOf('.');
        if (dot > index)
        {
            String extension = fileName.substring(index + 1, dot).toLowerCase();
            return extensionFontClsMap.get(extension);
        }

        return null;
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
