/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.DocIdBitSet;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/*
* User: adam
* Date: Dec 16, 2009
* Time: 1:36:39 PM
*/
class SecurityFilter extends Filter
{
    private static final Set<String> SECURITY_FIELDS = PageFlowUtil.set(LuceneSearchServiceImpl.FIELD_NAME.container.name(), LuceneSearchServiceImpl.FIELD_NAME.resourceId.name());

    private final User user;
    private final HashMap<String, Container> containerIds;
    private final HashMap<String, Boolean> securableResourceIds = new HashMap<>();

    SecurityFilter(User user, Container searchRoot, Container currentContainer, boolean recursive)
    {
        this.user = user;

        if (recursive)
        {
            List<Container> containers = ContainerManager.getAllChildren(searchRoot, user);
            containerIds = new HashMap<>(containers.size());

            for (Container c : containers)
            {
                if ((c.isSearchable() || (c.equals(currentContainer)) && (c.shouldDisplay(user) || c.isWorkbook())))
                    containerIds.put(c.getId(), c);
            }
        }
        else
        {
            containerIds = new HashMap<>();
            containerIds.put(searchRoot.getId(), searchRoot);
        }
    }


    @Override
    public DocIdSet getDocIdSet(AtomicReaderContext context, Bits acceptDocs) throws IOException
    {
        IndexReader reader = context.reader();
        int max = reader.maxDoc();
        BitSet bits = new BitSet(max);

        for (int i = 0; i < max; i++)
        {
            // Must check acceptDocs to filter out documents that are deleted or previously filtered.
            // This is not really documented, but it looks like null == acceptDocs means accept everything (no deleted/filtered docs).
            if (null != acceptDocs && !acceptDocs.get(i))
                continue;

            Document doc = reader.document(i, SECURITY_FIELDS);

            String id = doc.get(LuceneSearchServiceImpl.FIELD_NAME.container.name());
            String resourceId = doc.get(LuceneSearchServiceImpl.FIELD_NAME.resourceId.name());

            if (null == id || !containerIds.containsKey(id))
                continue;
            
            if (null != resourceId && !resourceId.equals(id))
            {
                if (!containerIds.containsKey(resourceId))
                {
                    Boolean canRead = securableResourceIds.get(resourceId);
                    if (null == canRead)
                    {
                        SecurableResource sr = new _SecurableResource(resourceId, containerIds.get(id));
                        SecurityPolicy p = SecurityPolicyManager.getPolicy(sr);
                        canRead = p.hasPermission(user, ReadPermission.class);
                        securableResourceIds.put(resourceId, canRead);
                    }
                    if (!canRead.booleanValue())
                        continue;
                }
            }
            
            bits.set(i);
        }

        return new DocIdBitSet(bits);
    }


    static class _SecurableResource implements SecurableResource
    {
        final String _id;
        final Container _container;
        
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
        public Set<Class<? extends Permission>> getRelevantPermissions()
        {
            throw new UnsupportedOperationException();
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
