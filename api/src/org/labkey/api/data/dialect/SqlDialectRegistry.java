/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.data.dialect;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SqlDialectRegistry
{
    private static final List<SqlDialectFactory> FACTORIES = new CopyOnWriteArrayList<>();
    private static final List<Consumer<SqlDialectFactory>> FACTORY_MODIFIERS = new CopyOnWriteArrayList<>();

    public static void register(SqlDialectFactory factory)
    {
        FACTORIES.add(factory);
    }

    public static void register(Consumer<SqlDialectFactory> factoryModifier)
    {
        FACTORY_MODIFIERS.add(factoryModifier);
    }

    // Should be called once, just before creating the first dialect. See #33518.
    static List<SqlDialectFactory> getFactories()
    {
        assert !FACTORIES.isEmpty();

        // Apply every modifier to every factory, then clear out FACTORIES to ensure this is called only once
        FACTORIES.forEach(f->{FACTORY_MODIFIERS.forEach(fm->fm.accept(f));});
        List<SqlDialectFactory> ret = new LinkedList<>(FACTORIES);
        FACTORIES.clear();

        return ret;
    }
}
