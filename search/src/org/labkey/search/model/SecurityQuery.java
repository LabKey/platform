/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.BitSetIterator;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.MultiPhaseCPUTimer.InvocationTimer;
import org.labkey.search.model.LuceneSearchServiceImpl.FIELD_NAME;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

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

    private final HashMap<String, Set<String>> _categoryContainers = new HashMap<>();
    private final HashMap<String, Container> _containerIds;
    private final HashMap<String, Boolean> _securableResourceIds = new HashMap<>();
    private final InvocationTimer<SearchService.SEARCH_PHASE> _iTimer;

    SecurityQuery(User user, SearchScope searchScope, Container currentContainer, InvocationTimer<SearchService.SEARCH_PHASE> iTimer)
    {
        // These three are used for hashCode() & equals(). We have disabled query caching for now (see #26416), but this gets us close to being able to use it. We
        // need to add some indication that permissions haven't changed since the query was cached, for example, include in the hash a counter that SecurityManager
        // increments for every group or role assignment change.
        _user = user;
        _currentContainer = currentContainer;
        _recursive = searchScope.isRecursive();
        _iTimer = iTimer;

        _containerIds = searchScope.getSearchableContainers(user, currentContainer);

        SearchService.get().getSearchCategories().forEach(
                category -> {
                    _categoryContainers.put(category.getName(), category.getPermittedContainerIds(user, _containerIds));
                }
        );
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
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

            private boolean isReadable(String containerId, String categories)
            {
//                return _containerIds.containsKey(containerId);
                if (StringUtils.isEmpty(categories) || !_categoryContainers.containsKey(categories))
                    return _containerIds.containsKey(containerId);
                else
                    return _categoryContainers.get(categories).contains(containerId);
            }

            @Override
            public Scorer scorer(LeafReaderContext context) throws IOException
            {
                SearchService.SEARCH_PHASE currentPhase = _iTimer.getCurrentPhase();
                _iTimer.setPhase(SearchService.SEARCH_PHASE.applySecurityFilter);

                LeafReader reader = context.reader();
                int maxDoc = reader.maxDoc();
                FixedBitSet bits = new FixedBitSet(maxDoc);

                BinaryDocValues securityContextDocValues = reader.getBinaryDocValues(FIELD_NAME.securityContext.name());

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
                            final String categories;
                            String[] parts = StringUtils.split(securityContext, "|");
                            // SecurityContext is usually just a container ID and a string of categories, but in some cases it adds a resource ID.
                            containerId = parts[0];
                            if (parts.length > 1)
                                categories = parts[1];
                            else
                                categories = null;
                            if (parts.length > 2)
                                resourceId = parts[2];
                            else
                                resourceId = null;

                            // Must have read permission on the container (always). Must also have read permissions on resource ID, if non-null.
                            if (isReadable(containerId, categories) && (null == resourceId || canReadResource(resourceId, containerId)))
                                bits.set(doc);
                        }
                    }

                    return new ConstantScoreScorer(this, score(), scoreMode, new BitSetIterator(bits, bits.approximateCardinality()));
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
            canRead = sr.hasPermission(_user, ReadPermission.class);
            _securableResourceIds.put(resourceId, canRead);
        }

        return canRead;
    }

    @Override
    public void visit(QueryVisitor visitor)
    {
        visitor.visitLeaf(this);
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

        @Override
        @NotNull
        public String getResourceId()
        {
            return _id;
        }

        @Override
        @NotNull
        public String getResourceName()
        {
            return _id;
        }

        @Override
        @NotNull
        public String getResourceDescription()
        {
            return "";
        }

        @Override
        @NotNull
        public Module getSourceModule()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public SecurableResource getParentResource()
        {
            return null;
        }

        @Override
        @NotNull
        public Container getResourceContainer()
        {
            return _container;
        }

        @Override
        @NotNull
        public List<SecurableResource> getChildResources(User user)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean mayInheritPolicy()
        {
            return false;
        }
    }
}
