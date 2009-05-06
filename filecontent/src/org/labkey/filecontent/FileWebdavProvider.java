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

package org.labkey.filecontent;

import org.labkey.api.webdav.*;
import org.labkey.api.data.Container;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.util.PageFlowUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Oct 22, 2008
 * Time: 8:20:29 AM
 */
public class FileWebdavProvider implements WebdavService.Provider
{
    static final String FILEMODULE_LINK = "@files";
    
    @Nullable
    public Set<String> addChildren(@NotNull WebdavResolver.Resource target)
    {
        if (!(target instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) target;
        Container c = folder.getContainer();

        AttachmentDirectory[] dirs = AttachmentService.get().getRegisteredDirectories(c);
        if (null != dirs)
            for (AttachmentDirectory dir : dirs)
            {
                if (!StringUtils.isEmpty(dir.getLabel()))
                {
                    return PageFlowUtil.set(FILEMODULE_LINK);
                }
            }
        return null;
    }


    public WebdavResolver.Resource resolve(@NotNull WebdavResolver.Resource parent, @NotNull String name)
    {
        if (!FILEMODULE_LINK.equalsIgnoreCase(name))
            return null;
        if (!(parent instanceof WebdavResolverImpl.WebFolderResource))
            return null;
        WebdavResolverImpl.WebFolderResource folder = (WebdavResolverImpl.WebFolderResource) parent;
        Container c = folder.getContainer();
        
        return new _FilesetsFolder(c, parent.getPath());
    }


    class _FilesetsFolder extends AbstractCollectionResource
    {
        Container _c;
        HashMap<String,AttachmentDirectory> _map = new HashMap<String, AttachmentDirectory>();
        ArrayList<String> _names = new ArrayList<String>();
        
        _FilesetsFolder(Container c, String folder)
        {
            super(folder, FILEMODULE_LINK);
            _c = c;
            _policy = _c.getPolicy();
            
            AttachmentDirectory[] dirs = AttachmentService.get().getRegisteredDirectories(_c);
            if (dirs != null)
                for (AttachmentDirectory dir : dirs)
                {
                    if (StringUtils.isEmpty(dir.getLabel()))
                        continue;
                    _map.put(dir.getLabel(), dir);
                    _names.add(dir.getLabel());
                }
            Collections.sort(_names, String.CASE_INSENSITIVE_ORDER);
        }

        public boolean exists()
        {
            return true;
        }

        public long getCreated()
        {
            return Long.MIN_VALUE;
        }

        public long getLastModified()
        {
            return Long.MIN_VALUE;
        }

        @NotNull
        public List<String> listNames()
        {
            return Collections.unmodifiableList(_names);
        }

        public WebdavResolver.Resource find(String name)
        {
            AttachmentDirectory dir = _map.get(name);
            String path = c(getPath(),name);
            WebdavResolver.Resource r;
            r = AttachmentService.get().getAttachmentResource(path, dir);
            return r;
        }
    }
}
