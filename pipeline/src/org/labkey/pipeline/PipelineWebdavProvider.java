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
package org.labkey.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.webdav.FileSystemResource;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.pipeline.api.PipeRootImpl;
import org.labkey.pipeline.api.PipelineServiceImpl;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: matthewb
 * Date: Oct 21, 2008
 * Time: 1:16:17 PM
 */
public class PipelineWebdavProvider implements WebdavService.Provider
{
    public static final String PIPELINE_LINK = "@pipeline";

    // currently addChildren is called only for web folders
    @Nullable
    public Set<String> addChildren(@NotNull WebdavResource target)
    {
        if (!(target instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) target;
        Container c = folder.getContainer();
        
        PipeRoot root = PipelineService.get().findPipelineRoot(c);
        if (null == root || !root.isValid())
            return null;

        String webdavURL = root.getWebdavURL();
        if (null != webdavURL && webdavURL.contains(URLEncoder.encode(PIPELINE_LINK)) && root.getContainer().equals(c))
            return PageFlowUtil.set(PIPELINE_LINK);

        return null;
    }


    public WebdavResource resolve(@NotNull WebdavResource parent, @NotNull String name)
    {
        if (!PIPELINE_LINK.equalsIgnoreCase(name))
            return null;
        if (!(parent instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) parent;
        Container c = folder.getContainer();
        if (null == c)
            return null;
        PipeRootImpl root = PipelineServiceImpl.get().getPipelineRootSetting(c);
        if (null == root)
            return null;
        return new PipelineFolderResource(folder, c, root);
    }


    private class PipelineFolderResource extends FileSystemResource
    {
        Container c;

        PipelineFolderResource(WebdavResource parent, Container c, PipeRootImpl root)
        {
            super(parent.getPath(), PIPELINE_LINK);

            this.c = c;
            _containerId = c.getId();
            _shouldIndex = root.isSearchable();
            setPolicy(SecurityPolicyManager.getPolicy(root));

            _files = new ArrayList<>();
            for (File file : root.getRootPaths())
            {
                _files.add(new FileInfo(file));
            }

            this.setSearchProperty(SearchService.PROPERTY.securableResourceId, root.getResourceId());
        }

        @Override
        public boolean canDelete(User user, boolean forDelete, List<String> msg)
        {
            return false;
        }

        @Override
        protected boolean hasAccess(User user)
        {
            return user.hasRootPermission(AdminOperationsPermission.class) || c.getPolicy().getPermissions(user).size() > 0;
        }

        @Override
        public boolean canList(User user, boolean forRead)
        {
            return hasAccess(user);
        }

        @Override
        public String getName()
        {
            return PIPELINE_LINK;
        }

        public FileSystemResource find(String name)
        {
            if (_files != null)
                return new FileSystemResource(this, name);
            return null;
        }
    }
}
