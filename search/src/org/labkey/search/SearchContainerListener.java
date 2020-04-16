/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

package org.labkey.search;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.webdav.WebdavService;
import org.labkey.search.model.DavCrawler;

import java.beans.PropertyChangeEvent;

public class SearchContainerListener extends ContainerManager.AbstractContainerListener
{
    @Override
    public void containerCreated(Container c, User user)
    {
        SearchService ss = SearchService.get();
        if (null != ss)
        {
            DavCrawler.getInstance().addPathToCrawl(WebdavService.getPath().append(c.getParsedPath()), null);
        }
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        SearchService ss = SearchService.get();
        if (null != ss)
        {
            ss.deleteContainer(c.getId());
        }
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
        SearchService ss = SearchService.get();
        if (null != ss)
        {
            ss.reindexContainerFiles(c);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent propertyChangeEvent)
    {
        SearchService ss = SearchService.get();
        if (null != ss)
        {
            ContainerManager.ContainerPropertyChangeEvent evt = (ContainerManager.ContainerPropertyChangeEvent) propertyChangeEvent;

            if (evt.property == ContainerManager.Property.Name)
            {
                ss.reindexContainerFiles(evt.container);
            }
        }
    }
}