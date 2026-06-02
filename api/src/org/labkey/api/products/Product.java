/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
