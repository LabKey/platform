package org.labkey.study.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;

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
}
