/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.api.settings;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.products.Product;
import org.labkey.api.products.ProductRegistry;
import org.labkey.api.util.logging.LogHelper;

import java.util.Arrays;
import java.util.Collection;

public class ProductConfiguration extends AbstractWriteableSettingsGroup implements StartupProperty
{
    public static final String SCOPE_PRODUCT_CONFIGURATION = "ProductConfiguration";
    public static final String PROPERTY_NAME = "productKey";
    public static final String INCLUDE_NAME = "include";
    private static final Logger _logger = LogHelper.getLogger(ProductConfiguration.class, "Product Configuration properties");

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
        if (productKey == null)
            config.remove(PROPERTY_NAME);
        else
            config.storeStringValue(PROPERTY_NAME, productKey);
        config.save();
        ProductRegistry.clearProductFeatureSetCache();
    }

    private static ProductConfiguration getWritableConfig()
    {
        ProductConfiguration config = new ProductConfiguration();
        config.makeWriteable(ContainerManager.getRoot());
        return config;
    }

    @Nullable
    public String getCurrentProductKey()
    {
        return lookupStringValue(PROPERTY_NAME, null);
    }

    @Nullable
    public Product getCurrentProduct()
    {
        String productKey = getCurrentProductKey();
        _logger.debug("Current product key: {}", productKey);
        if (productKey == null)
            return null;
        return ProductRegistry.getProduct(productKey);
    }

    public boolean isProductEnabled(@NotNull String productKey, boolean defaultValue)
    {
        String currentProductKey = getCurrentProductKey();
        if (currentProductKey == null)
            return defaultValue;
        return productKey.equalsIgnoreCase(currentProductKey);
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

    public static void handleStartupProperties()
    {
        ModuleLoader.getInstance().handleStartupProperties(new LenientStartupPropertyHandler<>(SCOPE_PRODUCT_CONFIGURATION, new ProductConfiguration()) {
            @Override
            public void handle(Collection<StartupPropertyEntry> entries)
            {
                entries.forEach(entry -> {
                    if (PROPERTY_NAME.equalsIgnoreCase(entry.getName()))
                        ProductConfiguration.setProductKey(entry.getValue());
                });
            }
        });
    }

    // return true if the ProductConfiguration.include startup prop is set to include the product by name
    public static boolean shouldIncludeViaStartupProperty(String productName)
    {
        ModuleLoader loader = ModuleLoader.getInstance();
        StartupPropertyEntry entry = loader.getStartupPropertyEntries(SCOPE_PRODUCT_CONFIGURATION).stream()
                .filter(e -> INCLUDE_NAME.equals(e.getName()))
                .findFirst().orElse(null);
        return entry != null && Arrays.stream(StringUtils.split(entry.getValue(), ",")).anyMatch(val -> productName.equals(val.trim()));
    }
}
