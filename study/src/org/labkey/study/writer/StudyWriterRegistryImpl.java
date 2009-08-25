package org.labkey.study.writer;

import org.labkey.api.study.ContainerWriterRegistry;
import org.labkey.api.writer.Writer;
import org.labkey.api.writer.WriterFactory;
import org.labkey.api.writer.ExportContext;
import org.labkey.api.data.Container;
import org.labkey.study.model.StudyImpl;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;

public class StudyWriterRegistryImpl implements ContainerWriterRegistry
{
    private static final StudyWriterRegistryImpl INSTANCE = new StudyWriterRegistryImpl();
    private static final Collection<WriterFactory<Container, ExportContext>> FACTORIES = new CopyOnWriteArrayList<WriterFactory<Container, ExportContext>>();

    private StudyWriterRegistryImpl()
    {
    }

    public static StudyWriterRegistryImpl get()
    {
        return INSTANCE;
    }

    public Collection<? extends Writer<StudyImpl, StudyExportContext>> getStudyWriters()
    {
        // New up the writers every time since these classes can be stateful
        return Arrays.asList(
                new VisitMapWriter(),
                new CohortWriter(),
                new QcStateWriter(),
                new DatasetWriter(),
                new AssayDatasetWriter(),
                new SpecimenArchiveWriter(),
                new QueryWriter(),
                new CustomViewWriter(),
                new ReportWriter(),
                new StudyXmlWriter()  // Note: Needs to be last of the study writers since it writes out the study.xml file (to which other writers contribute)
        );
    }

    public Collection<? extends Writer<Container, ExportContext>> getContainerWriters()
    {
        // New up the writers every time since these classes can be stateful
        Collection<Writer<Container, ExportContext>> writers = new LinkedList<Writer<Container, ExportContext>>();

        for (WriterFactory<Container, ExportContext> factory : FACTORIES)
            writers.add(factory.create());

        return writers;
    }

    public void addContainerWriterFactory(WriterFactory<Container, ExportContext> factory)
    {
        FACTORIES.add(factory);
    }
}