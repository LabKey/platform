package org.labkey.api.products;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.StartupPropertyEntry;

import java.util.Arrays;
import java.util.List;

public abstract class Product implements Comparable<Product>
{
    public abstract Integer getOrderNum();

    public abstract String getProductGroupId();

    public abstract String getName();

    public abstract String getKey();

    public abstract boolean isEnabled();

    public abstract @NotNull List<String> getFeatureFlags();

    // return true if the ProductRegistry.include startup prop is set to include the product by name
    public static boolean shouldIncludeViaStartupProperty(String productName)
    {
        ModuleLoader loader = ModuleLoader.getInstance();
        StartupPropertyEntry entry = loader.getStartupPropertyEntries("ProductRegistry").stream()
                .filter(e -> "include".equals(e.getName()))
                .findFirst().orElse(null);
        return entry != null && Arrays.stream(StringUtils.split(entry.getValue(), ",")).anyMatch(val -> productName.equals(val.trim()));
    }

    @Override
    public int compareTo(@NotNull Product o)
    {
        return getName().compareToIgnoreCase(o.getName());
    }
}
