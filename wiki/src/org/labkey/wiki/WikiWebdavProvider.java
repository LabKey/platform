/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.wiki;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.*;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HString;
import org.labkey.api.util.FileStream;
import org.labkey.api.webdav.*;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.settings.AppProps;
import org.labkey.api.search.SearchService;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiVersion;

import javax.swing.text.html.HTML;
import java.io.*;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Oct 21, 2008
 * Time: 9:52:40 AM
 */

class WikiWebdavProvider implements WebdavService.Provider
{
    final static String WIKI_NAME = "@wiki";
    
    // currently addChildren is called only for web folders
    public Set<String> addChildren(@NotNull Resource target)
    {
        if (!(target instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) target;
        Container c = folder.getContainer();
        return hasWiki(c) ? PageFlowUtil.set(WIKI_NAME) : null;
    }


    public Resource resolve(@NotNull Resource parent, @NotNull String name)
    {
        if (!WIKI_NAME.equalsIgnoreCase(name))
            return null;
        if (!(parent instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) parent;
        Container c = folder.getContainer();
        return WIKI_NAME.equals(name) ? new WikiProviderResource(folder,c) : null;
    }
    

    boolean hasWiki(Container c)
    {
        for (Module m : c.getActiveModules())
            if (m instanceof WikiModule)
                return true;
        return false;
    }


    class WikiProviderResource extends AbstractCollectionResource
    {
        Container _c;
        
        WikiProviderResource(Resource parent, Container c)
        {
            super(parent.getPath(), WIKI_NAME);
            _c = c;
            _policy = c.getPolicy();
        }

        @Override
        public String getName()
        {
            return WIKI_NAME;
        }

        public Resource find(String name)
        {
            return new WikiFolder(this, name);
        }

        @NotNull
        public List<String> listNames()
        {
            try
            {
                List<HString> names = WikiManager.getWikiNameList(_c);
                ArrayList<String> strs = new ArrayList<String>();
                if (names != null)
                for (HString name : names)
                    strs.add(name.getSource());
                return strs;
            }
            catch(SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        public boolean exists()
        {
            return true;
        }

        @Override
        public boolean canCreate(User user)
        {
            // create children NYI
            return false;
        }

        @Override
        public boolean canRename(User user)
        {
            return false;
        }
        
        @Override
        public boolean canDelete(User user)
        {
            return false;
        }
        
        @Override
        public boolean isFile()
        {
            return false;
        }

        @Override
        public long getCreated()
        {
            return Long.MIN_VALUE;
        }

        @Override
        public long getLastModified()
        {
            return Long.MIN_VALUE;
        }

        @Override
        public String getExecuteHref(ViewContext context)
        {
            return null;
        }
    }
    

    public static class WikiFolder extends AbstractCollectionResource
    {
        Container _c;
        Wiki _wiki;
        Resource _attachments;

        WikiFolder(WikiProviderResource folder, String name)
        {
            super(folder.getPath(), name);
            _c = folder._c;
            _policy = _c.getPolicy();
            _wiki = WikiManager.getWiki(_c, new HString(name));
            _attachments = AttachmentService.get().getAttachmentResource(getPath(), _wiki);               
        }


        @Override
        public boolean canDelete(User user)
        {
            return false;   // NYI
        }


        @Override
        public boolean canRename(User user)
        {
            return false;   // NYI
        }

        @Override
        public boolean canCreateCollection(User user)
        {
            return false;
        }


        public boolean exists()
        {
            return null != _wiki;
        }

        @NotNull
        public synchronized List<String> listNames()
        {
            if (!exists())
                return Collections.emptyList();
            List<String> ret = new ArrayList<String>();
            ret.addAll(_attachments.listNames());
            ret.add(getDocumentName(_wiki));
            return ret;
        }

        public synchronized Resource find(String name)
        {
            String docName = getDocumentName(_wiki);
            if (docName.equalsIgnoreCase(name))
            {
                return new WikiPageResource(this, _wiki, docName);
            }
            else
            {
                return _attachments.find(name);
            }
        }

        public long getCreated()
        {
            return Long.MIN_VALUE;
        }

        public long getLastModified()
        {
            return Long.MIN_VALUE;
        }

        public String getExecuteHref(ViewContext context)
        {
            return new ActionURL(WikiController.PageAction.class, _c).addParameter("name",_wiki.getName()).toString();
        }
    }


    static String getResourcePath(Wiki page)
    {
        String docname = getDocumentName(page);
        return AbstractResource.c(page.getContainerPath(),WIKI_NAME,page.getName().getSource(),docname);
    }


    static String getResourcePath(Container c, String name, WikiRendererType type)
    {
        String docname = getDocumentName(name, type);
        return AbstractResource.c(c.getPath(),WIKI_NAME,name,docname);
    }


    static String getDocumentName(String name, WikiRendererType type)
    {
        switch (type)
        {
            default:
            case HTML: return name + ".html";
            case RADEOX: return name +  ".wiki";
            case TEXT_WITH_LINKS: return name + ".txt";
        }
    }


    static String getDocumentName(Wiki wiki)
    {
        WikiVersion v = WikiManager.getLatestVersion(wiki);
        WikiRendererType r = WikiRendererType.HTML;
        try
        {
            r = WikiRendererType.valueOf(v.getRendererType());
        }
        catch (IllegalArgumentException x)
        {
        }
        return getDocumentName(wiki.getName().getSource(), r);
    }


    public static class WikiPageResource extends AbstractDocumentResource
    {
        WikiFolder _folder = null;
        Wiki _wiki = null;
        WikiVersion _version = null;

        Container _c;
        String _name;
        String _body = null;
        String _type = WikiRendererType.HTML.name();

        
        WikiPageResource(WikiFolder folder, Wiki wiki, String docName)
        {
            super(folder.getPath(), docName);
            _folder = folder;
            _policy = _folder._c.getPolicy();

            _c = _folder._c;
            _name = wiki.getName().getSource();
            _wiki = wiki;
            WikiVersion v = getWikiVersion();

            if (null != v)
            {
                _body = getWikiVersion().getBody();
                _type = getWikiVersion().getRendererType();
                _properties = new HashMap<String,Object>();
                _properties.put("title", v.getTitle().getSource());
                _properties.put(SearchService.PROPERTY.category.toString(),WikiManager.searchCategory.getName());
            }
        }


        WikiPageResource(Container c, String name, Map<String,Object> m)
        {
            super(name);

            _c = c;
            _name = name;
            _folder = null;
            _policy = c.getPolicy();
            if (null != m.get("renderertype"))
                _type = String.valueOf(m.get("renderertype"));
            _body = String.valueOf(m.get("body"));
            m.put("body",null);
            m.put("renderertype",null);
            _properties = m;
            _properties.put(SearchService.PROPERTY.category.toString(),WikiManager.searchCategory.getName());
        }


        WikiVersion getWikiVersion()
        {
            if (_wiki != null && _version == null)
                _version = WikiManager.getLatestVersion(_wiki);
            return _version;
        }

        public boolean exists()
        {
            return !_properties.isEmpty();
        }

        public boolean isCollection()
        {
            return false;
        }

        @Override
        public String getCreatedBy()
        {
            return UserManager.getDisplayName(_wiki.getCreatedBy(), null);
        }

        @Override
        public String getModifiedBy()
        {
            return UserManager.getDisplayName(_wiki.getModifiedBy(), null);
        }

        public InputStream getInputStream(User user) throws IOException
        {
            byte[] buf = (null==_body?"":_body).getBytes("UTF-8");
            return new ByteArrayInputStream(buf);
        }

        public long copyFrom(User user, FileStream in) throws IOException
        {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            FileUtil.copyData(in.openInputStream(),buf);
            long len = buf.size();
            WikiVersion version = getWikiVersion();
            version.setBody(buf.toString("UTF-8"));
            try
            {
                WikiManager.updateWiki(user, _wiki, version);
                WikiManager.getLatestVersion(_wiki, true);
                return len;
            }
            catch (SQLException x)
            {
                throw new IOException("error writing to wiki");
            }
        }


        @NotNull
        public List<WebdavResolver.History> getHistory()
        {
            WikiVersion[] versions = WikiManager.getAllVersions(_wiki);
            List<WebdavResolver.History> list = new ArrayList<WebdavResolver.History>();
            for (WikiVersion v : versions)
                list.add(new WikiHistory(_wiki, v));
            return list;
        }


        public static class WikiHistory implements WebdavResolver.History
        {
            Wiki w;
            WikiVersion v;

            WikiHistory(Wiki w, WikiVersion v)
            {
                this.w = w;
                this.v = v;    
            }

            public User getUser()
            {
                return UserManager.getUser(v.getCreatedBy());
            }

            public Date getDate()
            {
                return v.getCreated();
            }

            public String getMessage()
            {
                return "version " + v.getVersion();
            }

            public String getHref()
            {
                ActionURL url = new ActionURL(WikiController.VersionAction.class, ContainerManager.getForId(w.getContainerId()));
                url.addParameter("name", w.getName());
                url.addParameter("version", String.valueOf(v.getVersion()));
                return url.toString();
            }
        }

        // You can't actually delete this file, however, some clients do delete instead of overwrite,
        // so pretend we deleted it.
        public boolean delete(User user) throws IOException
        {
            if (user != null && !canDelete(user))
                return false;
            copyFrom(user, FileStream.EMPTY);
            return true;
        }

        public Resource parent()
        {
            return _folder;
        }

        public long getCreated()
        {
            return _wiki.getCreated().getTime();
        }

        public long getLastModified()
        {
            WikiVersion v = getWikiVersion();
            return v.getCreated().getTime();
        }

        public String getContentType()
        {
            WikiVersion v = getWikiVersion();
            if ("HTML".equals(_type))
                return "text/html";
            return "text/plain";
        }

        public long getContentLength()
        {
            WikiVersion v = getWikiVersion();
            String txt = v.getBody();
            try
            {
                byte[] buf = txt.getBytes("UTF-8");
                return buf.length;
            }
            catch (UnsupportedEncodingException e)
            {
                return 0;
            }
        }

		@Override
        public String getExecuteHref(ViewContext context)
        {
            return new ActionURL(WikiController.PageAction.class, _c).addParameter("name",_name).toString();
        }

        @Override
        public String getIconHref()
        {
            WikiVersion v = getWikiVersion();
            if (WikiRendererType.RADEOX.toString().equals(v.getRendererType()))
                return AppProps.getInstance().getContextPath() + "/_icons/wiki.png";
            return super.getIconHref();
        }

        @Override
        public Set<Class<? extends Permission>> getPermissions(User user)
        {
            // READ-WRITE for now
            Set<Class<? extends Permission>> perms = super.getPermissions(user);
            perms.add(ReadPermission.class);
            perms.add(UpdatePermission.class);
            return perms;
        }

        @Override
        public File getFile()
        {
            return null;
        }
    }
}
