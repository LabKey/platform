/*
 * Copyright (c) 2016-2026 LabKey Corporation
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

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
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
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.BitSetIterator;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.MultiPhaseCPUTimer.InvocationTimer;
import org.labkey.search.model.LuceneSearchServiceImpl.FIELD_NAME;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

public class SecurityQuery extends Query
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

        // Categories that require only base container Read (already guaranteed for every container above) are
        // resolved directly; the rest are grouped by required permission so multiple categories that require the
        // same permission (e.g., the three assay categories all require AssayReadPermission) share a single
        // O(containers) assembly pass below instead of each redoing it.
        Set<String> baseReadCategoryNames = new HashSet<>();
        MultiValuedMap<Class<? extends Permission>, SearchService.SearchCategory> categoriesByPermission =
            groupCategoriesByRequiredPermission(SearchService.get().getSearchCategories(), baseReadCategoryNames);

        for (String categoryName : baseReadCategoryNames)
            _categoryContainers.put(categoryName, _containerIds.keySet());

        // Containers that inherit their policy (e.g., workbooks, which typically don't have their own explicit
        // policy) share the exact same SecurityPolicy object as their nearest ancestor with one. Role resolution
        // (SecurityManager.getPermissions()) is therefore identical for every container backed by the same policy,
        // so compute it once per distinct policy instead of once per container per category. A user's full granted
        // permission set can be large (100+ for a site admin), but categories only ever ask about a handful of
        // permission classes, so retain just those instead of holding the full set for every distinct policy.
        Set<Class<? extends Permission>> requiredPermissions = categoriesByPermission.keySet();
        HashMap<String, Set<Class<? extends Permission>>> permissionsByPolicy = new HashMap<>();

        if (!requiredPermissions.isEmpty())
        {
            for (Container c : _containerIds.values())
            {
                permissionsByPolicy.computeIfAbsent(c.getPolicy().getResourceId(), id -> {
                    Set<Class<? extends Permission>> permitted = new HashSet<>(requiredPermissions);
                    permitted.retainAll(SecurityManager.getPermissions(c, user, null));
                    return permitted;
                });
            }
        }

        categoriesByPermission.asMap().forEach((requiredPermission, categories) -> {
            Set<String> permittedContainerIds = new HashSet<>();

            for (var entry : _containerIds.entrySet())
            {
                if (permissionsByPolicy.get(entry.getValue().getPolicy().getResourceId()).contains(requiredPermission))
                    permittedContainerIds.add(entry.getKey());
            }

            for (SearchService.SearchCategory category : categories)
                _categoryContainers.put(category.getName(), permittedContainerIds);
        });
    }

    /**
     * Splits categories into those requiring only base container Read (their names are added to baseReadCategoryNames)
     * and those requiring a specific permission, which are grouped by that permission class.
     */
    static MultiValuedMap<Class<? extends Permission>, SearchService.SearchCategory> groupCategoriesByRequiredPermission(
            Collection<SearchService.SearchCategory> categories, Set<String> baseReadCategoryNames)
    {
        MultiValuedMap<Class<? extends Permission>, SearchService.SearchCategory> categoriesByPermission = new ArrayListValuedHashMap<>();

        for (SearchService.SearchCategory category : categories)
        {
            Class<? extends Permission> requiredPermission = category.getRequiredPermission();

            if (null == requiredPermission)
                baseReadCategoryNames.add(category.getName());
            else
                categoriesByPermission.put(requiredPermission, category);
        }

        return categoriesByPermission;
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
                if (StringUtils.isEmpty(categories) || !_categoryContainers.containsKey(categories))
                    return _containerIds.containsKey(containerId);
                else
                    return _categoryContainers.get(categories).contains(containerId);
            }

            @Override
            public ScorerSupplier scorerSupplier(LeafReaderContext context)
            {
                return new ScorerSupplier()
                {
                    private final LeafReader _reader;
                    private final @Nullable BinaryDocValues _securityContextDocValues;

                    {
                        SearchService.SEARCH_PHASE currentPhase = _iTimer.getCurrentPhase();

                        try
                        {
                            _iTimer.setPhase(SearchService.SEARCH_PHASE.applySecurityFilter);
                            _reader = context.reader();
                            _securityContextDocValues = _reader.getBinaryDocValues(FIELD_NAME.securityContext.name());
                        }
                        catch (IOException e)
                        {
                            throw new RuntimeException(e);
                        }
                        finally
                        {
                            _iTimer.setPhase(currentPhase);
                        }
                    }

                    @Override
                    public Scorer get(long leadCost) throws IOException
                    {
                        SearchService.SEARCH_PHASE currentPhase = _iTimer.getCurrentPhase();

                        try
                        {
                            _iTimer.setPhase(SearchService.SEARCH_PHASE.applySecurityFilter);
                            int maxDoc = _reader.maxDoc();
                            FixedBitSet bits = new FixedBitSet(maxDoc);
                            int doc;

                            // Can be null, if no documents (e.g., shortly after bootstrap or clear index)
                            if (null != _securityContextDocValues)
                            {
                                while (NO_MORE_DOCS != (doc = _securityContextDocValues.nextDoc()))
                                {
                                    BytesRef bytesRef = _securityContextDocValues.binaryValue();
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

                            return new ConstantScoreScorer(score(), scoreMode, new BitSetIterator(bits, bits.approximateCardinality()));
                        }
                        finally
                        {
                            _iTimer.setPhase(currentPhase);
                        }
                    }

                    @Override
                    public long cost()
                    {
                        return null == _securityContextDocValues ? 0 : _securityContextDocValues.cost();
                    }
                };
            }
        };
    }

    private boolean canReadResource(String resourceId, String containerId)
    {
        if (resourceId.equalsIgnoreCase(containerId))
            throw new IllegalStateException("ResourceId was specified when it equals ContainerId: " + resourceId);

        if (_containerIds.containsKey(resourceId))
            return true;

        Boolean canRead = _securableResourceIds.get(resourceId);

        if (null == canRead)
        {
            // If resourceId represents a Container, then check permissions on it; this is important to ensure that
            // permission inheritance is respected. If it's not a Container, then just fake up a SecurableResource.
            // This is likely specific to Data Finder, which wants search to check permissions on both the cube
            // container and the study container, not a resource that lives within a container. Issue 53420.
            Container container = ContainerManager.getForId(resourceId);
            SecurableResource sr = null != container ? container : new _SecurableResource(resourceId, _containerIds.get(containerId));
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

    public static class TestCase extends Assert
    {
        private static SearchService.SearchCategory categoryRequiring(String name, Class<? extends Permission> requiredPermission)
        {
            return new SearchService.SearchCategory(name, name, false)
            {
                @Override
                public Class<? extends Permission> getRequiredPermission()
                {
                    return requiredPermission;
                }
            };
        }

        @Test
        public void testCategoryWithNoRequiredPermissionGoesToBaseRead()
        {
            SearchService.SearchCategory wiki = new SearchService.SearchCategory("wiki", "Wiki Pages");
            Set<String> baseReadCategoryNames = new HashSet<>();

            MultiValuedMap<Class<? extends Permission>, SearchService.SearchCategory> categoriesByPermission =
                SecurityQuery.groupCategoriesByRequiredPermission(List.of(wiki), baseReadCategoryNames);

            assertEquals(Set.of("wiki"), baseReadCategoryNames);
            assertTrue(categoriesByPermission.isEmpty());
        }

        @Test
        public void testCategoriesSharingAPermissionAreGroupedTogether()
        {
            // Mirrors the real assay/assayBatch/assayRun categories, which all require the same permission.
            SearchService.SearchCategory assay = categoryRequiring("assay", InsertPermission.class);
            SearchService.SearchCategory assayBatch = categoryRequiring("assayBatch", InsertPermission.class);
            SearchService.SearchCategory assayRun = categoryRequiring("assayRun", InsertPermission.class);
            Set<String> baseReadCategoryNames = new HashSet<>();

            MultiValuedMap<Class<? extends Permission>, SearchService.SearchCategory> categoriesByPermission =
                SecurityQuery.groupCategoriesByRequiredPermission(List.of(assay, assayBatch, assayRun), baseReadCategoryNames);

            assertTrue(baseReadCategoryNames.isEmpty());
            assertEquals(Set.of(InsertPermission.class), categoriesByPermission.keySet());
            assertEquals(Set.of(assay, assayBatch, assayRun), Set.copyOf(categoriesByPermission.get(InsertPermission.class)));
        }

        @Test
        public void testCategoriesWithDifferentPermissionsAreNotGroupedTogether()
        {
            SearchService.SearchCategory data = categoryRequiring("data", InsertPermission.class);
            SearchService.SearchCategory media = categoryRequiring("media", DeletePermission.class);
            Set<String> baseReadCategoryNames = new HashSet<>();

            MultiValuedMap<Class<? extends Permission>, SearchService.SearchCategory> categoriesByPermission =
                SecurityQuery.groupCategoriesByRequiredPermission(List.of(data, media), baseReadCategoryNames);

            assertEquals(Set.of(InsertPermission.class, DeletePermission.class), categoriesByPermission.keySet());
            assertEquals(List.of(data), categoriesByPermission.get(InsertPermission.class));
            assertEquals(List.of(media), categoriesByPermission.get(DeletePermission.class));
        }

        @Test
        public void testMixOfBaseReadAndPermissionRequiringCategories()
        {
            SearchService.SearchCategory wiki = new SearchService.SearchCategory("wiki", "Wiki Pages");
            SearchService.SearchCategory data = categoryRequiring("data", InsertPermission.class);
            Set<String> baseReadCategoryNames = new HashSet<>();

            MultiValuedMap<Class<? extends Permission>, SearchService.SearchCategory> categoriesByPermission =
                SecurityQuery.groupCategoriesByRequiredPermission(List.of(wiki, data), baseReadCategoryNames);

            assertEquals(Set.of("wiki"), baseReadCategoryNames);
            assertEquals(List.of(data), categoriesByPermission.get(InsertPermission.class));
        }
    }
}
