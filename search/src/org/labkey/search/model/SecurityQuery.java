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
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BitSetIterator;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchScope.SearchableContainers;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchService.SearchCategory;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MultiPhaseCPUTimer;
import org.labkey.api.util.MultiPhaseCPUTimer.InvocationTimer;
import org.labkey.api.util.TestContext;
import org.labkey.search.model.LuceneSearchServiceImpl.FIELD_NAME;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

public class SecurityQuery extends Query
{
    private final User _user;
    private final Container _currentContainer;
    private final boolean _recursive;

    private final HashMap<String, Set<String>> _categoryContainers = new HashMap<>();
    private final Map<String, Container> _containerIds;
    private final HashMap<String, Boolean> _securableResourceIds = new HashMap<>();
    private final InvocationTimer<SearchService.SEARCH_PHASE> _iTimer;

    SecurityQuery(User user, SearchScope searchScope, Container currentContainer, InvocationTimer<SearchService.SEARCH_PHASE> iTimer)
    {
        this(user, searchScope, currentContainer, iTimer, SearchService.get().getSearchCategories());
    }

    SecurityQuery(User user, SearchScope searchScope, Container currentContainer, InvocationTimer<SearchService.SEARCH_PHASE> iTimer, Collection<SearchCategory> searchCategories)
    {
        // These three are used for hashCode() & equals(). We have disabled query caching for now (see #26416), but this gets us close to being able to use it. We
        // need to add some indication that permissions haven't changed since the query was cached, for example, include in the hash a counter that SecurityManager
        // increments for every group or role assignment change.
        _user = user;
        _currentContainer = currentContainer;
        _recursive = searchScope.isRecursive();
        _iTimer = iTimer;

        // Categories that require only base container Read are resolved directly; the rest are grouped by required
        // permission so categories that share one (e.g., the three assay categories all require AssayReadPermission)
        // share a single container set.
        CategoryPermissions categoryPermissions = groupCategoriesByRequiredPermission(searchCategories);
        Map<Class<? extends Permission>, Collection<SearchCategory>> categoriesByPermission = categoryPermissions.categoriesByPermission();

        SearchableContainers searchable = searchScope.getSearchableContainers(user, currentContainer, categoriesByPermission.keySet());
        _containerIds = searchable.readable();

        for (String categoryName : categoryPermissions.baseReadCategoryNames())
            _categoryContainers.put(categoryName, _containerIds.keySet());

        categoriesByPermission.forEach((requiredPermission, categories) -> {
            Set<String> permittedContainerIds = searchable.containerIdsByPermission().get(requiredPermission);

            for (SearchCategory category : categories)
                _categoryContainers.put(category.getName(), permittedContainerIds);
        });
    }

    record CategoryPermissions(Map<Class<? extends Permission>, Collection<SearchCategory>> categoriesByPermission, Set<String> baseReadCategoryNames){}

    /**
     * Splits categories into those requiring only base container Read (their names are added to baseReadCategoryNames)
     * and those requiring a specific permission, which are grouped by that permission class.
     */
    static CategoryPermissions groupCategoriesByRequiredPermission(Collection<SearchCategory> categories)
    {
        MultiValuedMap<Class<? extends Permission>, SearchCategory> categoriesByPermission = new ArrayListValuedHashMap<>();
        Set<String> baseReadCategoryNames = new HashSet<>();

        for (SearchCategory category : categories)
        {
            Class<? extends Permission> requiredPermission = category.getRequiredPermission();

            if (null == requiredPermission)
                baseReadCategoryNames.add(category.getName());
            else
                categoriesByPermission.put(requiredPermission, category);
        }

        return new CategoryPermissions(categoriesByPermission.asMap(), baseReadCategoryNames);
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
        private static SearchCategory categoryRequiring(String name, Class<? extends Permission> requiredPermission)
        {
            return new SearchCategory(name, name, false)
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
            SearchCategory wiki = new SearchCategory("wiki", "Wiki Pages");

            CategoryPermissions result = SecurityQuery.groupCategoriesByRequiredPermission(List.of(wiki));

            assertEquals(Set.of("wiki"), result.baseReadCategoryNames());
            assertTrue(result.categoriesByPermission().isEmpty());
        }

        @Test
        public void testCategoriesSharingAPermissionAreGroupedTogether()
        {
            // Mirrors the real assay/assayBatch/assayRun categories, which all require the same permission.
            SearchCategory assay = categoryRequiring("assay", InsertPermission.class);
            SearchCategory assayBatch = categoryRequiring("assayBatch", InsertPermission.class);
            SearchCategory assayRun = categoryRequiring("assayRun", InsertPermission.class);

            CategoryPermissions result = SecurityQuery.groupCategoriesByRequiredPermission(List.of(assay, assayBatch, assayRun));

            assertTrue(result.baseReadCategoryNames().isEmpty());
            assertEquals(Set.of(InsertPermission.class), result.categoriesByPermission().keySet());
            assertEquals(Set.of(assay, assayBatch, assayRun), Set.copyOf(result.categoriesByPermission().get(InsertPermission.class)));
        }

        @Test
        public void testCategoriesWithDifferentPermissionsAreNotGroupedTogether()
        {
            SearchCategory data = categoryRequiring("data", InsertPermission.class);
            SearchCategory media = categoryRequiring("media", DeletePermission.class);

            CategoryPermissions result = SecurityQuery.groupCategoriesByRequiredPermission(List.of(data, media));

            assertEquals(Set.of(InsertPermission.class, DeletePermission.class), result.categoriesByPermission().keySet());
            assertEquals(List.of(data), result.categoriesByPermission().get(InsertPermission.class));
            assertEquals(List.of(media), result.categoriesByPermission().get(DeletePermission.class));
        }

        @Test
        public void testMixOfBaseReadAndPermissionRequiringCategories()
        {
            SearchCategory wiki = new SearchCategory("wiki", "Wiki Pages");
            SearchCategory data = categoryRequiring("data", InsertPermission.class);

            CategoryPermissions result = SecurityQuery.groupCategoriesByRequiredPermission(List.of(wiki, data));

            assertEquals(Set.of("wiki"), result.baseReadCategoryNames());
            assertEquals(List.of(data), result.categoriesByPermission().get(InsertPermission.class));
        }
    }

    public static class FilterTestCase extends Assert
    {
        private static final String PROJECT_NAME = "SecurityQueryTestProject";
        private static final String EMAIL = "security_query_test@test.com";
        private static final SearchCategory BASE_CATEGORY = new SearchCategory("securityQueryTestBase", "Base");
        private static final SearchCategory INSERT_CATEGORY = TestCase.categoryRequiring("securityQueryTestInsert", InsertPermission.class);
        private static final String ID_FIELD = "id";

        /** One document of each kind is indexed in every test folder */
        private enum DocKind
        {
            BASE(c -> c.getId() + "|" + BASE_CATEGORY.getName()),
            INSERT(c -> c.getId() + "|" + INSERT_CATEGORY.getName()),
            UNKNOWN_CATEGORY(c -> c.getId() + "|notARegisteredCategory"),
            NO_CATEGORY(Container::getId);

            private final Function<Container, String> _securityContext;

            DocKind(Function<Container, String> securityContext)
            {
                _securityContext = securityContext;
            }

            String getId(Container c)
            {
                return c.getName() + ":" + name();
            }
        }

        private static final Set<DocKind> ALL_KINDS = EnumSet.allOf(DocKind.class);
        private static final Set<DocKind> READ_ONLY_KINDS = EnumSet.complementOf(EnumSet.of(DocKind.INSERT));

        /** Documents in the project whose securable resource ID must also pass a read check */
        private enum ResourceDoc
        {
            READABLE_FOLDER_RESOURCE,
            RESTRICTED_FOLDER_RESOURCE,
            NON_CONTAINER_RESOURCE
        }

        private static User _admin;
        private static User _user;
        private static Container _project;
        private static Container _inherited;
        private static Container _editable;
        private static Container _restricted;
        private static Directory _directory;
        private static DirectoryReader _reader;

        @BeforeClass
        public static void setUp() throws Exception
        {
            cleanup();
            _admin = TestContext.get().getUser();
            _user = SecurityManager.addUser(new ValidEmail(EMAIL), null).getUser();

            _project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, _admin);
            MutableSecurityPolicy projectPolicy = new MutableSecurityPolicy(_project.getPolicy());
            projectPolicy.addRoleAssignment(_user, ReaderRole.class);
            SecurityPolicyManager.savePolicyForTests(projectPolicy, _admin);

            _inherited = ContainerManager.createContainer(_project, "Inherited", _admin);
            SecurityManager.setInheritPermissions(_inherited);

            _editable = ContainerManager.createContainer(_project, "Editable", _admin);
            MutableSecurityPolicy editablePolicy = new MutableSecurityPolicy(_editable);
            editablePolicy.addRoleAssignment(_user, EditorRole.class);
            SecurityPolicyManager.savePolicyForTests(editablePolicy, _admin);

            _restricted = ContainerManager.createContainer(_project, "Restricted", _admin);
            SecurityPolicyManager.savePolicyForTests(new MutableSecurityPolicy(_restricted), _admin);

            _directory = new ByteBuffersDirectory();

            try (IndexWriter writer = new IndexWriter(_directory, new IndexWriterConfig()))
            {
                for (Container c : getAllFolders())
                    for (DocKind kind : DocKind.values())
                        addDocument(writer, kind.getId(c), kind._securityContext.apply(c));

                for (ResourceDoc doc : ResourceDoc.values())
                {
                    String resourceId = switch (doc)
                    {
                        case READABLE_FOLDER_RESOURCE -> _editable.getId();
                        case RESTRICTED_FOLDER_RESOURCE -> _restricted.getId();
                        case NON_CONTAINER_RESOURCE -> GUID.makeGUID();
                    };
                    addDocument(writer, doc.name(), DocKind.BASE._securityContext.apply(_project) + "|" + resourceId);
                }
            }

            _reader = DirectoryReader.open(_directory);
        }

        private static List<Container> getAllFolders()
        {
            return List.of(_project, _inherited, _editable, _restricted);
        }

        private static void addDocument(IndexWriter writer, String id, String securityContext) throws IOException
        {
            Document doc = new Document();
            doc.add(new StringField(ID_FIELD, id, Field.Store.YES));
            doc.add(new BinaryDocValuesField(FIELD_NAME.securityContext.name(), new BytesRef(securityContext)));
            writer.addDocument(doc);
        }

        @AfterClass
        public static void cleanup() throws Exception
        {
            if (null != _reader)
                _reader.close();
            if (null != _directory)
                _directory.close();
            _reader = null;
            _directory = null;

            Container project = ContainerManager.getForPath(PROJECT_NAME);
            if (null != project)
                ContainerManager.deleteAll(project, TestContext.get().getUser());

            User user = UserManager.getUser(new ValidEmail(EMAIL));
            if (null != user)
                UserManager.deleteUser(user.getUserId());
        }

        @Test
        public void testReaderAcrossSubfolders() throws IOException
        {
            Set<String> expected = getIds(List.of(_project, _inherited), READ_ONLY_KINDS);
            expected.addAll(getIds(List.of(_editable), ALL_KINDS));
            expected.add(ResourceDoc.READABLE_FOLDER_RESOURCE.name());

            assertEquals(expected, search(_user, SearchScope.FolderAndSubfolders, _project));
        }

        @Test
        public void testFolderScope() throws IOException
        {
            assertEquals(getIds(List.of(_editable), ALL_KINDS), search(_user, SearchScope.Folder, _editable));
            assertEquals(Set.of(), search(_user, SearchScope.Folder, _restricted));
        }

        @Test
        public void testGuest() throws IOException
        {
            assertEquals(Set.of(), search(User.guest, SearchScope.FolderAndSubfolders, _project));
        }

        @Test
        public void testSiteAdmin() throws IOException
        {
            assertTrue(_admin.hasSiteAdminPermission());
            Set<String> hits = search(_admin, SearchScope.FolderAndSubfolders, _project);

            assertTrue(hits.containsAll(getIds(getAllFolders(), ALL_KINDS)));
            assertTrue(hits.contains(ResourceDoc.RESTRICTED_FOLDER_RESOURCE.name()));
        }

        private static Set<String> getIds(Collection<Container> folders, Set<DocKind> kinds)
        {
            Set<String> ids = new HashSet<>();

            for (Container c : folders)
                for (DocKind kind : kinds)
                    ids.add(kind.getId(c));

            return ids;
        }

        private Set<String> search(User user, SearchScope scope, Container current) throws IOException
        {
            IndexSearcher searcher = new IndexSearcher(_reader);
            InvocationTimer<SearchService.SEARCH_PHASE> timer = new MultiPhaseCPUTimer<>(SearchService.SEARCH_PHASE.class, SearchService.SEARCH_PHASE.values()).getInvocationTimer();
            Query query = new SecurityQuery(user, scope, current, timer, List.of(BASE_CATEGORY, INSERT_CATEGORY));
            TopDocs topDocs = searcher.search(query, _reader.maxDoc());
            StoredFields storedFields = searcher.storedFields();
            Set<String> ids = new HashSet<>();

            for (ScoreDoc scoreDoc : topDocs.scoreDocs)
                ids.add(storedFields.document(scoreDoc.doc).get(ID_FIELD));

            return ids;
        }
    }
}
