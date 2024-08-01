package org.labkey.api.products;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class Product implements Comparable<Product>
{
    public abstract Integer getOrderNum();

    public abstract String getProductGroupId();

    public abstract String getName();

    public abstract String getKey();

    public abstract boolean isEnabled();

    public abstract @NotNull List<String> getFeatureFlags();

    @Override
    public int compareTo(@NotNull Product o)
    {
        return getName().compareToIgnoreCase(o.getName());
    }
}
