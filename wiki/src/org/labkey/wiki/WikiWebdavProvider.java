/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.poi.util.IOUtils;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.module.Module;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.webdav.AbstractDocumentResource;
import org.labkey.api.webdav.AbstractWebdavResource;
import org.labkey.api.webdav.AbstractWebdavResourceCollection;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.data.xml.wiki.WikiType;
import org.labkey.data.xml.wiki.WikisDocument;
import org.labkey.data.xml.wiki.WikisType;
import org.labkey.wiki.export.WikiWriterFactory;
import org.labkey.wiki.model.Wiki;
import org.labkey.wiki.model.WikiTree;
import org.labkey.wiki.model.WikiVersion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: matthewb
 * Date: Oct 21, 2008
 * Time: 9:52:40 AM
 */

public class WikiWebdavProvider implements WebdavService.Provider
{
    final static String WIKI_NAME = "@wiki";
    
    // currently addChildren is called only for web folders
    public Set<String> addChildren(@NotNull WebdavResource target)
    {
        if (!(target instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) target;
        Container c = folder.getContainer();
        return hasWiki(c) ? PageFlowUtil.set(WIKI_NAME) : null;
    }


    public WebdavResource resolve(@NotNull WebdavResource parent, @NotNull String name)
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


    public static class WikiProviderResource extends AbstractWebdavResourceCollection
    {
        Container _c;
        
        public WikiProviderResource(WebdavResource parent, Container c)
        {
            super(parent.getPath(), WIKI_NAME);
            _c = c;
            _containerId = _c.getId();
            setPolicy(c.getPolicy());
        }

        public String getName()
        {
            return WIKI_NAME;
        }

        public WebdavResource find(String name)
        {
            if (name.equals(WikiWriterFactory.WIKIS_FILENAME))
            {
                return new WikiMetadata(this);
            }
            return new WikiFolder(this, name);
        }

        @NotNull
        public Collection<String> listNames()
        {
            List<String> names = WikiSelectManager.getPageNames(_c);
            ArrayList<String> strs = new ArrayList<>();
            strs.add(WikiWriterFactory.WIKIS_FILENAME);
            if (names != null)
                strs.addAll(names);

            return strs;
        }

        public boolean exists()
        {
            return true;
        }

        @Override
        public boolean canCreate(User user, boolean forCreate)
        {
            // create children NYI
            return false;
        }

        @Override
        public boolean canRename(User user, boolean forRename)
        {
            return false;
        }
        
        @Override
        public boolean canDelete(User user, boolean forDelete, List<String> message)
        {
            return false;
        }
        
        public boolean isFile()
        {
            return false;
        }

        @Override
        public long getCreated()
        {
            return Long.MIN_VALUE;
        }

        public long getLastModified()
        {
            return Long.MIN_VALUE;
        }

        @Override
        public String getExecuteHref(ViewContext context)
        {
            return getHref(context);
        }
    }

    /** An XML file with metadata about all wikis in current container,
     * including parenting, title, whether or not to show attachments, etc */
    public static class WikiMetadata extends AbstractDocumentResource
    {
        private final WikiProviderResource _parent;
        private WeakReference<byte[]> _content;

        protected WikiMetadata(WikiProviderResource parent)
        {
            super(parent.getPath(), WikiWriterFactory.WIKIS_FILENAME);
            _parent = parent;
            setPolicy(parent._c.getPolicy());
        }

        @Override
        public boolean canDelete(User user, boolean forDelete, List<String> message)
        {
            return false;
        }

        @Override
        public boolean exists()
        {
            return true;
        }

        @Override
        public boolean canWrite(User user, boolean forWrite)
        {
            return false;
        }

        @Override
        public String getContentType()
        {
            return "text/xml";
        }

        @Override
        public InputStream getInputStream(User user) throws IOException
        {
            return new ByteArrayInputStream(getContent());
        }

        private byte[] getContent() throws IOException
        {
            byte[] result = _content == null ? null : _content.get();
            if (result == null)
            {
                WikisDocument document = WikisDocument.Factory.newInstance();
                WikisType wikis = document.addNewWikis();

                for (WikiTree wikiTree : WikiSelectManager.getWikiTrees(_parent._c))
                {
                    WikiType wikiXml = wikis.addNewWiki();
                    Wiki wiki = WikiSelectManager.getWiki(_parent._c, wikiTree.getName());
                    wikiXml.setName(wiki.getName());
                    Wiki parentWiki = wiki.getParentWiki();
                    if (parentWiki != null)
                    {
                        wikiXml.setParent(parentWiki.getName());
                    }

                    WikiVersion wikiVersion = wiki.getLatestVersion();
                    wikiXml.setTitle(wikiVersion.getTitle());
                    wikiXml.setShowAttachments(wiki.isShowAttachments());
                    wikiXml.setShouldIndex(wiki.isShouldIndex());
                }

                XmlOptions options = new XmlOptions();
                options.setSavePrettyPrint();

                try (InputStream in = document.newInputStream(options); ByteArrayOutputStream out = new ByteArrayOutputStream())
                {
                    IOUtils.copy(in, out);
                    result = out.toByteArray();
                }

                _content = new WeakReference<>(result);
            }

            return result;
        }

        @Override
        public long copyFrom(User user, FileStream in) throws IOException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getContentLength() throws IOException
        {
            return getContent().length;
        }
    }
    

    public static class WikiFolder extends AbstractWebdavResourceCollection
    {
        Container _c;
        Wiki _wiki;
        WebdavResource _attachments;

        WikiFolder(WikiProviderResource folder, String name)
        {
            super(folder.getPath(), name);
            _c = folder._c;
            _containerId = _c.getId();
            setPolicy(_c.getPolicy());
            _wiki = WikiSelectManager.getWiki(_c, name);
            if (null != _wiki)
                _attachments = AttachmentService.get().getAttachmentResource(getPath(), _wiki.getAttachmentParent());
        }


        @Override
        public boolean canDelete(User user, boolean forDelete, List<String> message)
        {
            return false;   // NYI
        }


        @Override
        public boolean canRename(User user, boolean forRename)
        {
            return false;   // NYI
        }

        @Override
        public boolean canCreateCollection(User user, boolean forCreate)
        {
            return false;
        }


        public boolean exists()
        {
            return null != _wiki;
        }

        @NotNull
        public synchronized Collection<String> listNames()
        {
            if (!exists())
                return Collections.emptyList();
            List<String> ret = new ArrayList<>();
            ret.addAll(_attachments.listNames());
            ret.add(getDocumentName(_wiki));
            return ret;
        }

        public synchronized WebdavResource find(String name)
        {
            if (null == _wiki)
                return null;
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
            return new ActionURL(WikiController.PageAction.class, _c).addParameter("name", _wiki.getName()).toString();
        }
    }


    static String getResourcePath(Wiki page)
    {
        String docname = getDocumentName(page);
        return AbstractWebdavResource.c(page.getContainerPath(), WIKI_NAME, page.getName(), docname);
    }


    static String getResourcePath(Container c, String name, WikiRendererType type)
    {
        String docname = type.getDocumentName(name);
        return AbstractWebdavResource.c(c.getPath(), WIKI_NAME, name,docname);
    }


    public static String getDocumentName(Wiki wiki)
    {
        WikiVersion v = wiki.getLatestVersion();
        WikiRendererType r = WikiRendererType.HTML;
        try
        {
            r = v.getRendererTypeEnum();
        }
        catch (IllegalArgumentException x)
        {
        }
        return r.getDocumentName(wiki.getName());
    }


    public static class WikiPageResource extends AbstractDocumentResource
    {
        WikiFolder _folder = null;
        Wiki _wiki = null;
        WikiVersion _version = null;

        Container _c;
        String _entityId;
        String _name;
        String _body = null;
        WikiRendererType _type = WikiRendererType.HTML;
        private String _title;

        WikiPageResource(WikiFolder folder, Wiki wiki, String docName)
        {
            super(folder.getPath(), docName);
            init(folder._c, wiki.getName(), wiki.getEntityId(), folder, folder._c.getPolicy(), new HashMap<>());

            _wiki = wiki;
            WikiVersion v = getWikiVersion();

            if (null != v)
            {
                setBody(getWikiVersion().getBody());
                _title = getWikiVersion().getTitle();
                _type = getWikiVersion().getRendererTypeEnum();
                _properties.put(SearchService.PROPERTY.title.toString(), v.getTitle());
            }
        }


        WikiPageResource(Container c, String name, String entityId, String body, WikiRendererType rendererType, Map<String, Object> m)
        {
            super(new Path("wiki", c.getId(), name));
            init(c, name, entityId, null, c.getPolicy(), m);

            _type = rendererType;
            setBody(body);
        }


        private void init(Container c, String name, String entityId, WikiFolder folder, SecurityPolicy policy, Map<String, Object> properties)
        {
            _c = c;
            _containerId = _c.getId();
            _name = name;
            _entityId = entityId;
            _folder = folder;
            setPolicy(policy);
            _properties = properties;
            _properties.put(SearchService.PROPERTY.categories.toString(), WikiManager.searchCategory.getName());
        }


        @Override
        public void setLastIndexed(long ms, long modified)
        {
            WikiManager.get().setLastIndexed(_c, _name, ms);
        }
        

        protected void setBody(String body)
        {
            _body = body;
        }


        public String getDocumentId()
        {
            return "wiki:" + _entityId;
        }
        

        WikiVersion getWikiVersion()
        {
            if (_wiki != null && _version == null)
                _version = _wiki.getLatestVersion();
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
        public User getCreatedBy()
        {
            return UserManager.getUser(_wiki.getCreatedBy());
        }

        @Override
        public String getDescription()
        {
            return null != _title ? _title : null;
        }

        @Override
        public User getModifiedBy()
        {
            return UserManager.getUser(_wiki.getModifiedBy());
        }


        public FileStream getFileStream(User user) throws IOException
        {
            byte[] buf = (null == _body ? "" : _body).getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
            return new FileStream.ByteArrayFileStream(buf);
        }


        public InputStream getInputStream(User user) throws IOException
        {
            byte[] buf = (null == _body ? "" : _body).getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
            return new ByteArrayInputStream(buf);
        }

        public long copyFrom(User user, FileStream in) throws IOException
        {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            FileUtil.copyData(in.openInputStream(),buf);
            long len = buf.size();
            WikiVersion version = getWikiVersion();
            version.setBody(buf.toString(StringUtilsLabKey.DEFAULT_CHARSET.name()));

            try
            {
                WikiManager.get().updateWiki(user, _wiki, version);
                _version = null;
                return len;
            }
            catch (RuntimeSQLException x)
            {
                throw new IOException("error writing to wiki");
            }
        }


        @NotNull
        public List<WebdavResolver.History> getHistory()
        {
            WikiVersion[] versions = WikiSelectManager.getAllVersions(_wiki);
            List<WebdavResolver.History> list = new ArrayList<>();
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
            if (user != null && !canDelete(user, true, null))
                return false;
            copyFrom(user, FileStream.EMPTY);
            return true;
        }

        public WebdavResource parent()
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
            return null != v && null != v.getCreated() ? v.getCreated().getTime() : Long.MIN_VALUE;
        }

        public String getContentType()
        {
            return _type.getContentType();
        }

        public long getContentLength()
        {
            byte[] buf = (null == _body ? "" : _body).getBytes(StringUtilsLabKey.DEFAULT_CHARSET);
            return buf.length;
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
            if (WikiRendererType.RADEOX == v.getRendererTypeEnum())
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
