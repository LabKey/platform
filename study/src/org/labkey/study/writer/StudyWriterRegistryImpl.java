package org.labkey.study.writer;

import org.labkey.api.study.ExternalStudyWriter;
import org.labkey.api.study.ExternalStudyWriterFactory;
import org.labkey.api.study.StudyWriterRegistry;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.CopyOnWriteArrayList;

public class StudyWriterRegistryImpl implements StudyWriterRegistry
{
    private static final StudyWriterRegistryImpl INSTANCE = new StudyWriterRegistryImpl();
    private static final Collection<ExternalStudyWriterFactory> FACTORIES = new CopyOnWriteArrayList<ExternalStudyWriterFactory>();

    private StudyWriterRegistryImpl()
    {
    }

    public static StudyWriterRegistryImpl get()
    {
        return INSTANCE;
    }

    // These writers are defined and registered by other modules.  They have no knowledge of study internals, other
    // than being able to write elements into study.xml.
    public Collection<ExternalStudyWriter> getRegisteredStudyWriters()
    {
        // New up the writers every time since these classes can be stateful
        Collection<ExternalStudyWriter> writers = new LinkedList<ExternalStudyWriter>();

        for (ExternalStudyWriterFactory factory : FACTORIES)
            writers.add(factory.create());

        return writers;
    }

    public void addStudyWriterFactory(ExternalStudyWriterFactory factory)
    {
        FACTORIES.add(factory);
    }

    // These writers are internal to study.  They have access to study internals.
    public Collection<InternalStudyWriter> getInternalStudyWriters()
    {
        // New up the writers every time since these classes can be stateful
        return Arrays.asList(
            new VisitMapWriter(),
            new CohortWriter(),
            new QcStateWriter(),
            new DatasetWriter(),
            new AssayDatasetWriter(),
            new SpecimenArchiveWriter(),
            new StudyXmlWriter()  // Note: Must be the last study writer since it writes out the study.xml file (to which other writers contribute)
        );
    }
}