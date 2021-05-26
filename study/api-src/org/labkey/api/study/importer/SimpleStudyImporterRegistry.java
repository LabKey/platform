package org.labkey.api.study.importer;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SimpleStudyImporterRegistry
{
    private static final Collection<Supplier<Collection<SimpleStudyImporter>>> IMPORTER_SUPPLIERS = new CopyOnWriteArrayList<>();

    public static void registerSimpleStudyImporterProvider(Supplier<Collection<SimpleStudyImporter>> provider)
    {
        IMPORTER_SUPPLIERS.add(provider);
    }

    public static Collection<SimpleStudyImporter> getSimpleStudyImporters()
    {
        // New up the writers every time since these classes can be stateful
        return IMPORTER_SUPPLIERS.stream()
            .map(Supplier::get)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }
}
