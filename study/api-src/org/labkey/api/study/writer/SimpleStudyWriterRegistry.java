package org.labkey.api.study.writer;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SimpleStudyWriterRegistry
{
    private static final Collection<Supplier<Collection<SimpleStudyWriter>>> WRITER_SUPPLIERS = new CopyOnWriteArrayList<>();

    public static void registerSimpleStudyWriterProvider(Supplier<Collection<SimpleStudyWriter>> provider)
    {
        WRITER_SUPPLIERS.add(provider);
    }

    public static Collection<SimpleStudyWriter> getSimpleStudyWriters()
    {
        // New up the writers every time since these classes can be stateful
        return WRITER_SUPPLIERS.stream()
            .map(Supplier::get)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }
}
