/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.security.UserManager;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.ViewContext;

import javax.ejb.Entity;
import javax.ejb.Transient;
import javax.servlet.ServletContext;
import java.io.Serializable;
import java.io.File;
import java.util.Date;
import java.util.Set;
//import java.io.Serializable;


/**
 * Attachment is not quite a full blown Entity so we have some duplication here
 * <p/>
 * NOTE: Attachment is only used to list the attachments, it does not contain document data
 * and cannot be used directly to insert or update attachments.
 */
@Entity
@javax.ejb.Table(name = "Documents")
public class Attachment implements Serializable
{
/*    @DependentObject
    public static class EntityName implements Serializable
        {
        String parent;
        String name;

        public String getParent()
            {
            return parent;
            }

        public void setParent(String parent)
            {
            this.parent = parent;
            }

        @Column(name="DocumentName")
        public String getName()
            {
            return name;
            }

        public void setName(String name)
            {
            this.name = name;
            }
        }
*/
//    @Id
//    private EntityName entityNamePK;

    private String parent; // entityid
    private String name;
    private String container;   // container path
    private int createdBy;
    private long created;
    private File file;

    private static final CaseInsensitiveHashMap<String> icons = new CaseInsensitiveHashMap<String>();

    public Attachment()
    {
        assert MemTracker.put(this);
    }

    @Transient
    public String getDownloadUrl(String pageFlow)
    {
        Container c = ContainerManager.getForId(getContainer());
        DownloadURL url = new DownloadURL(pageFlow, c.getPath(), getParent(), getName());
        return url.getLocalURIString();
    }


    @Transient
    public String getFileExtension()
    {
        if (null == name)
            return "doc";
        int dotPos = name.lastIndexOf(".");
        if (dotPos > -1 && dotPos < name.length() - 1)
            return name.substring(dotPos + 1).toLowerCase();
        return "doc";
    }

    @Transient
    public String getFileIcon()
    {
        return getFileIcon(name);
    }
    

    @Transient
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


    static String lookupIcon(String lookup)
    {
        synchronized (icons)
        {
            if (icons.size() == 0)
            {
                String iconFileExtension = ".gif";

                ServletContext context = ViewServlet.getViewServletContext();
                if (context != null)
                {
                    Set<String> paths = context.getResourcePaths("/_icons");
                    for (String fileName : paths)
                    {
                        String extension;
                        if (fileName.toLowerCase().endsWith(iconFileExtension))
                        {
                            int index = fileName.lastIndexOf('/');
                            if (-1 != index)
                                extension = fileName.substring(index + 1, fileName.length() - iconFileExtension.length());
                            else
                                extension = fileName.substring(0, fileName.length() - iconFileExtension.length());
                            icons.put(extension, fileName);
                        }
                    }
                }
            }
        }
        return icons.get(lookup);
    }


/*    @Id()
    public EntityName getEntityNamePK()
        {
        return entityNamePK;
        }


    public void setEntityNamePK(EntityName entityNamePK)
        {
        this.entityNamePK = entityNamePK;
        }
*/

    @Transient
    public String getName()
    {
        return name;
    }


    public void setDocumentName(String name)
    {
        this.name = name;
    }


    public void setName(String name)
    {
        this.name = name;
    }


    public int getCreatedBy()
    {
        return createdBy;
    }


    public void setCreatedBy(int createdBy)
    {
        this.createdBy = createdBy;
    }


    @Transient
    public String getCreatedByName(ViewContext context)
    {
        return UserManager.getDisplayName(createdBy, context);
    }


    public Date getCreated()
    {
        return new Date(created);
    }


    public void setCreated(Date created)
    {
        this.created = created.getTime();
    }

    public String getContainer()
    {
        return container;
    }

    public void setContainer(String container)
    {
        this.container = container;
    }


    @Transient
    public String getParent()
    {
        return parent;
    }


    public void setParent(String parent)
    {
        this.parent = parent;
    }

    public File getFile()
    {
        return file;
    }

    public void setFile(File file)
    {
        this.file = file;
    }
}
