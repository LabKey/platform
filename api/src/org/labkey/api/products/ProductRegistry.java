/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.api.products;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.collections.ConcurrentCaseInsensitiveSortedMap;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.settings.ProductConfiguration;
import org.labkey.api.settings.ProductFeature;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ProductRegistry
{
    public static final String PRODUCT_ID_PROPERTY_NAME = "productId";
    private static final Logger _logger = LogHelper.getLogger(ProductRegistry.class, "Application product registry");
    private static final Map<String, ProductMenuProvider> _productMap = new ConcurrentCaseInsensitiveSortedMap<>();
    private static final Map<String, List<ProductMenuProvider>> _moduleProviderMap = new ConcurrentHashMap<>();
    private static final Map<String, ProductMenuProvider> _sectionMap = new ConcurrentCaseInsensitiveSortedMap<>();
    private static final ProductRegistry _instance = new ProductRegistry();
    private static final Map<String, Product>  _products = new ConcurrentHashMap<>();
    private static final Set<String> _productFeaturesCache = new HashSet<>();

    private ProductRegistry()
    {
        // private to make this a singleton
    }

    public static ProductRegistry get()
    {
        return _instance;
    }

    public static void addProduct(Product product)
    {
        _products.put(product.getKey(), product);
    }

    public static Product getProduct(String key)
    {
        return _products.get(key);
    }

    public static void clearProductFeatureSetCache()
    {
        _productFeaturesCache.clear();
    }

    @Nullable
    public String getPrimaryApplicationId(Container container)
    {
        Container project = container.getProject();
        if (project == null) // root container has no application
            return null;

        // if the container is a project or subfolder from a project with a product defined, use that
        String productId = getPrimaryProductIdForContainer(project);
        if (productId != null)
            return productId;

        // Handle the case of the application folder being a subfolder
        return getPrimaryProductIdForContainer(container);
    }

    public static Set<String> getProductFeatureSet()
    {
        if (_productFeaturesCache.isEmpty())
        {
            Set<String> productFeatures = new HashSet<>();
            String productKey = new ProductConfiguration().getCurrentProductKey();
            if (productKey != null && _products.containsKey(productKey))
                _productFeaturesCache.addAll(_products.get(productKey).getFeatureFlags());
            else
            {
                // if no product is specifically configured, we'll return the feature set
                // for the highest product that is enabled based on the modules on the server
                for (Product product : getProducts(true, false))
                {
                    if (product.isEnabled())
                    {
                        _productFeaturesCache.addAll(product.getFeatureFlags());
                        return productFeatures;
                    }
                }
            }
        }
        return _productFeaturesCache;
    }

    public static Collection<Product> getProducts()
    {
        return getProducts(false, true);
    }

    public static Collection<Product> getProducts(boolean sorted, boolean ascending)
    {
        if (!sorted)
            return _products.values();

        List<Product> orderedProducts = new ArrayList<>(_products.values());
        orderedProducts.sort((a, b) -> {
            if (a == b) return 0;
            if (null == a) return ascending ? -1 : 1;
            if (null == b) return ascending ? 1 : -1;
            return ascending ? a.getOrderNum() - b.getOrderNum() : b.getOrderNum() - a.getOrderNum();
        });
        return orderedProducts;
    }

    public static boolean isProductFeatureEnabled(ProductFeature feature)
    {
        return getProductFeatureSet().contains(feature.toString());
    }

    public boolean containsProductId(String productId)
    {
        return _productMap.containsKey(productId);
    }

    public void registerMenuItemsProvider(ProductMenuProvider provider)
    {

        if (_productMap.containsKey(provider.getProductId()))
            throw new IllegalArgumentException("Product key '" + provider.getProductId() + " already registered by module '" + _productMap.get(provider.getProductId()).getModuleName() + "'");

        _productMap.put(provider.getProductId(), provider);
        List<ProductMenuProvider> providers = _moduleProviderMap.computeIfAbsent(provider.getModuleName(), k -> new ArrayList<>());
        providers.add(provider);

        provider.getSectionNames(null).forEach(name -> _sectionMap.put(name, provider));
    }

    public void unregisterMenuItemsProvider(ProductMenuProvider provider)
    {
        if (_productMap.containsKey(provider.getProductId()))
        {
            _productMap.remove(provider.getProductId());
            List<ProductMenuProvider> providers = _moduleProviderMap.get(provider.getModuleName());
            if (providers != null)
            {
                providers.remove(provider);
                if (providers.isEmpty())
                    _moduleProviderMap.remove(provider.getModuleName());
            }
        }
        provider.getSectionNames(null).forEach(_sectionMap::remove);
    }

    @NotNull
    private List<String> getProductIdsForContainer(@NotNull Container container)
    {
        List<String> productIds = new ArrayList<>();

        Set<String> modules = container.getActiveModules().stream().map(Module::getName).collect(Collectors.toSet());
        for (String module : modules)
        {
           if (_moduleProviderMap.containsKey(module))
               productIds.addAll(_moduleProviderMap.get(module).stream().map(ProductMenuProvider::getProductId).toList());
        }

        return productIds;
    }

    public boolean supportsProductFolders(@NotNull Container container)
    {
        ProductMenuProvider provider = getPrimaryProductMenuForContainer(container);
        return provider != null && provider.supportsProductFolders();
    }

    @Nullable
    public String getPrimaryProductIdForContainer(@NotNull Container container)
    {
        List<String> productIds = getProductIdsForContainer(container);
        if (productIds.isEmpty())
            return null;
        if (productIds.size() == 1)
            return productIds.getFirst();
        ProductMenuProvider provider = getPrimaryProductMenuForContainer(container);
        return provider != null ? provider.getProductId() : null;
    }

    @Nullable
    public ProductMenuProvider getPrimaryProductMenuForContainer(@NotNull Container container)
    {
        List<String> productIds = getProductIdsForContainer(container);
        _logger.debug("Product Ids in container '{}' of type '{}' are {}", container.getPath(), container.getFolderType().getName(), StringUtils.join(productIds));
        List<ProductMenuProvider> providers = getRegisteredProducts().stream().filter(provider -> productIds.contains(provider.getProductId())).toList();
        _logger.debug("Menu providers: {}", providers.stream().map(ProductMenuProvider::getProductId).collect(Collectors.toList()));
        if (providers.isEmpty())
            return null;

        if (providers.size() == 1)
            return providers.getFirst();

        // see if there's a provider that matches the folder type (need to check this first so if LabKey LIMS or LKB is the configured product you can still show LKSM folders)
        Optional<ProductMenuProvider> optionalProvider = providers.stream().filter(provider -> provider.getFolderTypeName() != null && provider.getFolderTypeName().equals(container.getFolderType().getName())).findFirst();
        if (optionalProvider.isPresent())
        {
            _logger.debug("Found product menu provider for folder type '{}'", container.getFolderType().getName());
            return optionalProvider.get();
        }

        List<String> orderedProducts = getProducts(true, false).stream().filter(Product::isEnabled).map(Product::getProductGroupId).toList();
        _logger.debug("Products are {}", _products.keySet());
        _logger.debug("Ordered products are {}", orderedProducts);
        ProductMenuProvider highestProvider = providers.stream()
                .min(Comparator.comparing(provider -> orderedProducts.indexOf(provider.getProductId()))).orElse(null);
        _logger.debug("Highest product menu provider: {}", highestProvider.getProductId());
        // then see if there's a provider that matches the configured product
        Product product = new ProductConfiguration().getCurrentProduct();
        if (product == null)
        {
            _logger.debug("Using highest product menu provider.");
            return highestProvider;
        }

        optionalProvider = providers.stream().filter(provider -> provider.getProductId().equals(product.getProductGroupId())).findFirst();
        _logger.debug("Product from product group: {}", optionalProvider.isPresent() ? optionalProvider.get() : "null");
        // if neither of those is true (when can this happen?), use the highest provider
        return optionalProvider.orElseGet(() -> highestProvider);
    }

    @Nullable
    public MenuSection getUserMenuSection(@NotNull ViewContext context, @NotNull String productId)
    {
        ProductMenuProvider userMenuProvider = _productMap.get(productId);
        if (userMenuProvider != null)
            return new UserInfoMenuSection(context, userMenuProvider);
        return null;
    }

    @NotNull
    public List<MenuSection> getProductMenuSections(@NotNull ViewContext context, @Nullable List<String> origProductIds)
    {
        List<String> productIds = origProductIds == null ? getProductIdsForContainer(context.getContainer()) : origProductIds;

        Map<ExperimentService.DataTypeForExclusion, Set<Long>> dataTypeExclusions = ExperimentService.get().getContainerDataTypeExclusions(context.getContainer().getId());

        List<MenuSection> sections = new ArrayList<>();
        for (String productId : productIds)
        {
            if (_productMap.containsKey(productId))
            {
                sections.addAll(_productMap.get(productId).getSections(context, dataTypeExclusions));
            }
        }
        return sections;
    }

    @NotNull
    public List<MenuSection> getMenuSections(@NotNull ViewContext context, @NotNull String productId)
    {
        if (!_productMap.containsKey(productId))
        {
            return Collections.emptyList();
        }
        ProductMenuProvider provider = _productMap.get(productId);
        List<MenuSection> sections = provider.getSections(context, Collections.emptyMap());
        // always include the user menu as the last item
        sections.add(new UserInfoMenuSection(context, provider));
        return sections;
    }

    @Nullable
    public MenuSection getMenuSection(@NotNull ViewContext context, @NotNull String name)
    {
        if (_sectionMap.containsKey(name))
        {
            return _sectionMap.get(name).getSection(context, name, Collections.emptyMap() /*used by unit test only*/ );
        }
        else
        {
            _logger.warn("No product menu provider registered for menu section '{}'.", name);
            return null;
        }
    }

    @NotNull
    public List<MenuSection> getMenuSections(@NotNull ViewContext context, @NotNull List<String> sectionNames)
    {
        List<MenuSection> items = new ArrayList<>();
        sectionNames.forEach((name) -> {
            MenuSection section = getMenuSection(context, name);
            if (section != null)
                items.add(section);
            else
                _logger.warn("No section provided for menu section name '{}'.", name);
        });
        return items;
    }

    public List<ProductMenuProvider> getRegisteredProducts()
    {
        return _productMap.values().stream().filter(product -> product.getProductName() != null).collect(Collectors.toList());
    }

    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        private static final String VALID_PRODUCT_ID = "testProductId1";
        private static final String VALID_PRODUCT_ID_2 = "testProductId2";
        private static final ProductRegistry registry = ProductRegistry.get();
        private static final ProductMenuProvider _provider1 = new TestMenuProvider("testModule", VALID_PRODUCT_ID, List.of("section X", "Section B", "section b2"));
        private static final ProductMenuProvider _provider2 = new TestMenuProvider("testModule", VALID_PRODUCT_ID_2, Collections.emptyList());

        private static class TestMenuSection extends MenuSection
        {

            public TestMenuSection(@NotNull ViewContext context, @NotNull String label, @Nullable String iconClass)
            {
                super(context, label, iconClass, VALID_PRODUCT_ID);
            }

            @Override
            protected @NotNull List<MenuItem> getAllItems()
            {
                return Collections.emptyList();
            }
        }
        private static class TestMenuProvider extends ProductMenuProvider
        {
            private final String _moduleName;
            private final String _productId;
            private final Collection<String> _sectionNames;

            public TestMenuProvider(String moduleName, String productId, Collection<String> sectionNames)
            {
                _moduleName = moduleName;
                _productId = productId;
                _sectionNames = sectionNames;
            }

            @Override
            public @NotNull String getModuleName()
            {
                return _moduleName;
            }

            @Override
            public @Nullable String getFolderTypeName()
            {
                return null;
            }

            @Override
            public @NotNull String getProductId()
            {
                return _productId;
            }

            @Override
            public @NotNull ActionURL getAppURL(Container container)
            {
                return new ActionURL();
            }

            @Override
            public @NotNull Collection<String> getSectionNames(@Nullable ViewContext viewContext)
            {
                return _sectionNames;
            }

            @Override
            public @Nullable MenuSection getSection(@NotNull ViewContext context, @NotNull String sectionName, @Nullable Map<ExperimentService.DataTypeForExclusion, Set<Long>> dataTypeExclusions)
            {
                if (_sectionNames.contains(sectionName))
                    return new TestMenuSection(context, sectionName, sectionName);
                return null;
            }
        }

        @BeforeClass
        public static void setup()
        {
            registry.registerMenuItemsProvider(_provider1);
            registry.registerMenuItemsProvider(_provider2);
        }

        @AfterClass
        public static void tearDown()
        {
            registry.unregisterMenuItemsProvider(_provider1);
            registry.unregisterMenuItemsProvider(_provider2);
        }

        @Test
        public void registerDuplicateProductId()
        {
            try
            {
                registry.registerMenuItemsProvider(new TestMenuProvider("testModule", VALID_PRODUCT_ID, List.of("Section Y", "section Z")));
                fail("No exception thrown when registering a duplicate product id");
            }
            catch (IllegalArgumentException e)
            {
                // this is the expected outcome
                assertTrue(e.getMessage().contains("already registered"));
            }
        }

        @Test
        public void getMenuSectionsByProductId()
        {
            ViewContext context = HttpView.currentContext();
            assertTrue("Should get no sections when using an unregistered product id", registry.getMenuSections(context, "bogus").isEmpty());

            List<MenuSection> sections = registry.getMenuSections(context, VALID_PRODUCT_ID);
            assertEquals("Number of sections not as expected", 4, sections.size());
            assertEquals("First section not as expected or with unexpected label", "section X", sections.get(0).getLabel());
            assertEquals("Second section not as expected or with unexpected label", "Section B", sections.get(1).getLabel());
            assertEquals("Third section not as expected or with unexpected label", "section b2", sections.get(2).getLabel());
            assertEquals("Fourth section not as expected or with unexpected label", UserInfoMenuSection.NAME, sections.get(3).getLabel());

            sections = registry.getMenuSections(context, VALID_PRODUCT_ID_2);
            assertEquals("Number of menu sections not as expected when provider has no sections", 1, sections.size());
            assertEquals("User section label not as expected", UserInfoMenuSection.NAME,  sections.getFirst().getLabel());
        }

        @Test
        public void getMenuSectionsByName()
        {
            ViewContext context = HttpView.currentContext();
            assertTrue("Should get no sections when using unknown names", registry.getMenuSections(context, List.of("Unknown1", "also unknown")).isEmpty());

            List<MenuSection> sections = registry.getMenuSections(context, List.of("section X", "Unknown", "section b2"));
            assertEquals("Number of sections not as expected", 2, sections.size());
            assertEquals("First section not as expected or with unexpected label", "section X", sections.get(0).getLabel());
            assertEquals("Second section not as expected or with unexpected label", "section b2", sections.get(1).getLabel());
        }
    }

}
