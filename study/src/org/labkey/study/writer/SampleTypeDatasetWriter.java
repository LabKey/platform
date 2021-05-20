package org.labkey.study.writer;

import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;

// DatasetWriter actually writes all datasets (crf, assay, and sample type). This is a do-nothing writer to gets the
// sample type dataset checkbox to show up in the UI.
public class SampleTypeDatasetWriter implements InternalStudyWriter
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
