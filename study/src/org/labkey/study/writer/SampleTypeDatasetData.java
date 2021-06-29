package org.labkey.study.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;

// DatasetDataWriter actually writes all dataset data (study, assay, and sample type).  This is a do-nothing writer to get the
// sample type dataset data checkbox to show up in the UI.
public class SampleTypeDatasetData implements InternalStudyWriter
{
    @Override
    public @Nullable String getDataType()
    {
        return StudyArchiveDataTypes.SAMPLE_TYPE_DATASET_DATA;
    }

    @Override
    public void write(StudyImpl object, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
    }

    @Override
    public boolean includeWithTemplate()
    {
        return false;
    }
}
