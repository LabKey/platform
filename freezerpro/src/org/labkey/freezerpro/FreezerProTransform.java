package org.labkey.freezerpro;

import com.drew.lang.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.util.FileType;

import java.io.File;

/**
 * User: klum
 * Date: 11/13/13
 */
public class FreezerProTransform implements SpecimenTransform
{
    @Override
    public String getName()
    {
        return "FreezerPro";
    }

    @Override
    public boolean isEnabled(Container container)
    {
        return container.getActiveModules().contains(ModuleLoader.getInstance().getModule(FreezerProModule.class));
    }

    @Override
    public FileType getFileType()
    {
        return FreezerProTransformTask.FREEZER_PRO_FILE_TYPE;
    }

    @Override
    public void transform(@Nullable PipelineJob job, File input, File outputArchive) throws PipelineJobException
    {
        FreezerProTransformTask task = new FreezerProTransformTask(job);
        task.transform(input, outputArchive);
    }
}
