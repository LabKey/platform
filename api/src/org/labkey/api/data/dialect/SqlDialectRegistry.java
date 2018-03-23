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
