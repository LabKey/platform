package org.labkey.api.products;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ConcurrentCaseInsensitiveSortedMap;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ProductRegistry
{
    private static Logger _logger = Logger.getLogger(ProductRegistry.class);
    private static Map<String, ProductMenuProvider> _productMap = new ConcurrentCaseInsensitiveSortedMap<>();
    private static Map<String, ProductMenuProvider> _sectionMap = new ConcurrentCaseInsensitiveSortedMap<>();
    private static ProductRegistry _instance = new ProductRegistry();

    private ProductRegistry()
    {
        // private to make this a singleton
    }

    public static ProductRegistry get()
    {
        return _instance;
    }

    public boolean containsProductId(String productId)
    {
        return _productMap.containsKey(productId);
    }

    public void registerMenuItemsProvider(ProductMenuProvider provider)
    {

        if (_productMap.containsKey(provider.getProductId()))
            throw new IllegalArgumentException("Product key '" + provider.getProductId() + " already registered by module '" + _productMap.get(provider.getProductId()).getModuleName() + "'");
        Collection<String> sectionNames = provider.getSectionNames();
        Set<String> alreadyRegistered = sectionNames.stream().filter((name) -> _sectionMap.containsKey(name)).collect(Collectors.toSet());
        if (!alreadyRegistered.isEmpty()) {
            String message = alreadyRegistered.stream()
                    .map((cat) -> "'" + cat + "' registered by product '" + _sectionMap.get(cat).getProductId() + "' in module '" +  _sectionMap.get(cat).getModuleName() + "'")
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("Product menu sections already registered: " + message);
        }
        _productMap.put(provider.getProductId(), provider);
        provider.getSectionNames().forEach(name -> _sectionMap.put(name, provider));
    }

    public List<MenuSection> getMenuSections(@NotNull ViewContext context, @NotNull String productId, @Nullable Integer itemLimit)
    {
        if (!_productMap.containsKey(productId))
        {
            return Collections.emptyList();
        }
        ProductMenuProvider provider = _productMap.get(productId);
        List<MenuSection> sections = provider.getSections(context, itemLimit);
        // always include the user menu as the last item
        sections.add(new UserInfoMenuSection(context, provider));
        return sections;
    }

    public List<MenuSection> getMenuSections(@NotNull ViewContext context, @NotNull List<String> sectionNames, @Nullable Integer itemLimit)
    {
        List<MenuSection> items = new ArrayList<>();
        sectionNames.forEach((name) -> {
            if (_sectionMap.containsKey(name))
            {
                items.addAll(_sectionMap.get(name).getSections(context, sectionNames, itemLimit));
            }
            else
            {
                _logger.error("No product menu provider registered for menu section '" + name + "'.");
            }
        });
        return items;
    }

}
