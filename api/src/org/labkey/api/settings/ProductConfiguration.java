package org.labkey.api.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerManager;

public class ProductConfiguration extends AbstractWriteableSettingsGroup implements StartupProperty
{
    public static final String PROPERTY_NAME = "productKey";

    @Override
    protected String getGroupName()
    {
        return "ProductConfiguration";
    }

    @Override
    protected String getType()
    {
        return "Product Configuration";
    }

    public static void setProductKey(String productKey)
    {
        ProductConfiguration config = getWritableConfig();
        config.storeStringValue(PROPERTY_NAME, productKey);
        config.save();
    }

    private static ProductConfiguration getWritableConfig()
    {
        ProductConfiguration config = new ProductConfiguration();
        config.makeWriteable(ContainerManager.getRoot());
        return config;
    }

    @Nullable
    public String getCurrentProduct()
    {
        return lookupStringValue(PROPERTY_NAME, null);
    }

    public boolean isProductEnabled(@NotNull String productKey, boolean defaultValue)
    {
        String currentProduct = getCurrentProduct();
        if (currentProduct == null)
            return defaultValue;
        return productKey.equalsIgnoreCase(currentProduct);
    }


    @Nullable
    @Override
    public String getPropertyName()
    {
        return PROPERTY_NAME;
    }

    @Override
    public String getDescription()
    {
        return "The key for the product tier that is enabled.";
    }
}
