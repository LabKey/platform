package org.labkey.study.writer;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyImpl;

// DatasetDefinitionWriter actually writes all dataset definitions (study, assay, and sample type). This is a do-nothing writer to get the
// study dataset definition checkbox to show up in the UI.
public class StudyDatasetDefinition implements InternalStudyWriter
{
    @Override
    public @Nullable String getDataType()
    {
        return StudyArchiveDataTypes.STUDY_DATASETS_DEFINITIONS;
    }

    @Override
    public void write(StudyImpl object, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
    }
}
