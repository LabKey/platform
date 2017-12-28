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

package org.labkey.search.model;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.BitSetIterator;
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
import org.labkey.api.util.MultiPhaseCPUTimer.InvocationTimer;
import org.labkey.search.model.LuceneSearchServiceImpl.FIELD_NAME;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

/*
* User: adam
* Date: Dec 16, 2009
* Time: 1:36:39 PM
*/
class SecurityQuery extends Query
{
    private final User _user;
    private final Container _currentContainer;
    private final boolean _recursive;

    private final HashMap<String, Container> _containerIds;
    private final HashMap<String, Boolean> _securableResourceIds = new HashMap<>();
    private final InvocationTimer<SearchService.SEARCH_PHASE> _iTimer;

    SecurityQuery(User user, Container searchRoot, Container currentContainer, boolean recursive, InvocationTimer<SearchService.SEARCH_PHASE> iTimer)
    {
        // These three are used for hashCode() & equals(). We have disabled query caching for now (see #26416), but this gets us close to being able to use it. We
        // need to add some indication that permissions haven't changed since the query was cached, for example, include in the hash a counter that SecurityManager
        // increments for every group or role assignment change.
        _user = user;
        _currentContainer = currentContainer;
        _recursive = recursive;

        _iTimer = iTimer;

        if (recursive)
        {
            // Returns root plus all children (including workbooks & tabs) where user has read permissions
            List<Container> containers = ContainerManager.getAllChildren(searchRoot, user);
            _containerIds = new HashMap<>(containers.size() * 2);

            for (Container c : containers)
            {
                boolean searchable = (c.isSearchable() || c.equals(currentContainer)) && (c.isWorkbook() || c.shouldDisplay(user));

                if (searchable)
                {
                    _containerIds.put(c.getId(), c);
                }
            }
        }
        else
        {
            _containerIds = new HashMap<>();

            if (searchRoot.hasPermission(user, ReadPermission.class))
                _containerIds.put(searchRoot.getId(), searchRoot);
        }
    }


    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost)
    {
        return new ConstantScoreWeight(this, boost)
        {
            // NOT cacheable since results depend on the current user's current permissions.
            // TODO: With this in place (as of Lucene 7.2.0), we should be able to remove the global caching directive
            // in WritableIndexManagerImpl... after thorough testing! See #26416.
            @Override
            public boolean isCacheable(LeafReaderContext ctx)
            {
                return false;
            }

            @Override
            public Scorer scorer(LeafReaderContext context) throws IOException
            {
                SearchService.SEARCH_PHASE currentPhase = _iTimer.getCurrentPhase();
                _iTimer.setPhase(SearchService.SEARCH_PHASE.applySecurityFilter);

                LeafReader reader = context.reader();
                int maxDoc = reader.maxDoc();
                FixedBitSet bits = new FixedBitSet(maxDoc);

                SortedDocValues securityContextDocValues = reader.getSortedDocValues(FIELD_NAME.securityContext.name());

                try
                {
                    int doc;

                    // Can be null, if no documents (e.g., shortly after bootstrap or clear index)
                    if (null != securityContextDocValues)
                    {
                        while (NO_MORE_DOCS != (doc = securityContextDocValues.nextDoc()))
                        {
                            BytesRef bytesRef = securityContextDocValues.binaryValue();
                            String securityContext = StringUtils.trimToNull(bytesRef.utf8ToString());

                            final String containerId;
                            final String resourceId;

                            // SecurityContext is usually just a container ID, but in some cases it adds a resource ID.
                            if (securityContext.length() > 36)
                            {
                                containerId = securityContext.substring(0, 36);
                                resourceId = securityContext.substring(37);
                            }
                            else
                            {
                                containerId = securityContext;
                                resourceId = null;
                            }

                            // Must have read permission on the container (always). Must also have read permissions on resource ID, if non-null.
                            if (_containerIds.containsKey(containerId) && (null == resourceId || canReadResource(resourceId, containerId)))
                                bits.set(doc);
                        }
                    }

                    return new ConstantScoreScorer(this, score(), new BitSetIterator(bits, bits.approximateCardinality()));
                }
                finally
                {
                    _iTimer.setPhase(currentPhase);
                }
            }
        };
    }

    private boolean canReadResource(String resourceId, String containerId)
    {
        assert !resourceId.equals(containerId);

        if (_containerIds.containsKey(resourceId))
            return true;

        Boolean canRead = _securableResourceIds.get(resourceId);

        if (null == canRead)
        {
            SecurableResource sr = new _SecurableResource(resourceId, _containerIds.get(containerId));
            SecurityPolicy p = SecurityPolicyManager.getPolicy(sr);
            canRead = p.hasPermission(_user, ReadPermission.class);
            _securableResourceIds.put(resourceId, canRead);
        }

        return canRead;
    }

    @Override
    public String toString(String field)
    {
        return null;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SecurityQuery that = (SecurityQuery) o;

        if (_recursive != that._recursive) return false;
        if (!_user.equals(that._user)) return false;
        return _currentContainer.equals(that._currentContainer);
    }

    @Override
    public int hashCode()
    {
        int result = _user.hashCode();
        result = 31 * result + _currentContainer.hashCode();
        result = 31 * result + (_recursive ? 1 : 0);
        return result;
    }

    private static class _SecurableResource implements SecurableResource
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
