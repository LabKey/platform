/*
 * Copyright (c) 2009-2015 LabKey Corporation
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

package org.labkey.search.model;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.BitDocIdSet;
import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.MultiPhaseCPUTimer;
import org.labkey.search.model.LuceneSearchServiceImpl.FIELD_NAME;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/*
* User: adam
* Date: Dec 16, 2009
* Time: 1:36:39 PM
*/
class SecurityFilter extends Filter
{
    private final User _user;
    private final HashMap<String, Container> _containerIds;
    private final HashMap<String, Boolean> _securableResourceIds = new HashMap<>();
    private final MultiPhaseCPUTimer.InvocationTimer<SearchService.SEARCH_PHASE> _iTimer;

    SecurityFilter(User user, Container searchRoot, Container currentContainer, boolean recursive, MultiPhaseCPUTimer.InvocationTimer<SearchService.SEARCH_PHASE> iTimer)
    {
        _user = user;
        _iTimer = iTimer;

        if (recursive)
        {
            // Returns root plus all children (including workbooks & tabs) where user has read permissions
            List<Container> containers = ContainerManager.getAllChildren(searchRoot, user);
            _containerIds = new HashMap<>(containers.size() * 2);

            for (Container c : containers)
            {
                boolean searchable = c.isSearchable() || c.equals(currentContainer);

                if (searchable)
                {
                    boolean shouldDisplay = c.isWorkbook() || c.shouldDisplay(user);

                    if (shouldDisplay)
                    {
                        _containerIds.put(c.getId(), c);
                    }
                }
            }
        }
        else
        {
            _containerIds = new HashMap<>();
            _containerIds.put(searchRoot.getId(), searchRoot);
        }
    }


    @Override
    public DocIdSet getDocIdSet(LeafReaderContext context, Bits acceptDocs) throws IOException
    {
        SearchService.SEARCH_PHASE currentPhase = _iTimer.getCurrentPhase();
        _iTimer.setPhase(SearchService.SEARCH_PHASE.applySecurityFilter);

        LeafReader reader = context.reader();
        int max = reader.maxDoc();
        BitSet bits = new FixedBitSet(max);

        SortedDocValues containerDocValues = reader.getSortedDocValues(FIELD_NAME.container.name());
        SortedDocValues resourceDocValues = reader.getSortedDocValues(FIELD_NAME.resourceId.name());
        BytesRef bytesRef;

        try
        {
            for (int i = 0; i < max; i++)
            {
                // Must check acceptDocs to filter out documents that are deleted or previously filtered.
                // This is not really documented, but it looks like null == acceptDocs means accept everything (no deleted/filtered docs).
                if (null != acceptDocs && !acceptDocs.get(i))
                    continue;

                bytesRef = containerDocValues.get(i);
                String containerId = StringUtils.trimToNull(bytesRef.utf8ToString());

                if (!_containerIds.containsKey(containerId))
                    continue;

                // Can be null, if no documents have a resource ID (e.g., shortly after bootstrap)
                if (null != resourceDocValues)
                {
                    bytesRef = resourceDocValues.get(i);
                    String resourceId = StringUtils.trimToNull(bytesRef.utf8ToString());

                    if (null != resourceId && !resourceId.equals(containerId))
                    {
                        if (!_containerIds.containsKey(resourceId))
                        {
                            Boolean canRead = _securableResourceIds.get(resourceId);
                            if (null == canRead)
                            {
                                SecurableResource sr = new _SecurableResource(resourceId, _containerIds.get(containerId));
                                SecurityPolicy p = SecurityPolicyManager.getPolicy(sr);
                                canRead = p.hasPermission(_user, ReadPermission.class);
                                _securableResourceIds.put(resourceId, canRead);
                            }
                            if (!canRead)
                                continue;
                        }
                    }
                }

                bits.set(i);
            }
            return new BitDocIdSet(bits);
        }
        finally
        {
            _iTimer.setPhase(currentPhase);
        }
    }

    @Override
    public String toString(String field)
    {
        return null;
    }


    static class _SecurableResource implements SecurableResource
    {
        private final String _id;
        private final Container _container;
        
        _SecurableResource(String resourceId, Container c)
        {
            _id = resourceId;
            _container = c;
        }

        @NotNull
        public String getResourceId()
        {
            return _id;
        }

        @NotNull
        public String getResourceName()
        {
            return _id;
        }

        @NotNull
        public String getResourceDescription()
        {
            return "";
        }

        @NotNull
        public Module getSourceModule()
        {
            throw new UnsupportedOperationException();
        }

        public SecurableResource getParentResource()
        {
            return null;
        }

        @NotNull
        public Container getResourceContainer()
        {
            return _container;
        }

        @NotNull
        public List<SecurableResource> getChildResources(User user)
        {
            throw new UnsupportedOperationException();
        }

        public boolean mayInheritPolicy()
        {
            return false;
        }
    }
}
