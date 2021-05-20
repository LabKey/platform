package org.labkey.study.writer;

import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;

// DatasetDefinitionWriter actually writes all dataset definitions (study, assay, and sample type). This is a do-nothing writer to get the
// sample type dataset definition checkbox to show up in the UI.
public class SampleTypeDatasetDefinition implements InternalStudyWriter
{
    @Override
    public String getDataType()
    {
        return StudyArchiveDataTypes.SAMPLE_TYPE_DATASET_DEFINITIONS;
    }

    @Override
    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf)
    {
    }
}
