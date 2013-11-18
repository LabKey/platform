package org.labkey.study.pipeline;

import com.drew.lang.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudyModule;

import java.io.File;

/**
 * User: klum
 * Date: 11/13/13
 */
public class SampleMindedTransform implements SpecimenTransform
{
    @Override
    public String getName()
    {
        return "SampleMinded";
    }

    @Override
    public boolean isEnabled(Container container)
    {
        return container.getActiveModules().contains(ModuleLoader.getInstance().getModule(StudyModule.class));
    }

    @Override
    public FileType getFileType()
    {
        return SampleMindedTransformTask.SAMPLE_MINDED_FILE_TYPE;
    }

    @Override
    public void transform(@Nullable PipelineJob job, File input, File outputArchive) throws PipelineJobException
    {
        SampleMindedTransformTask task = new SampleMindedTransformTask(job);
        task.transform(input, outputArchive);
    }

    @Override
    public void postTransform(@Nullable PipelineJob job, File input, File outputArchive) throws PipelineJobException
    {
        String filename = input.getName();
        String base = FileUtil.getBaseName(filename);
        String ext = FileUtil.getExtension(filename);
        if (base.endsWith("_data"))
            base = base.substring(0,base.length()-"_data".length());
        File notdone = new File(input.getParentFile(), base + "_notdone." + ext);
        File skipvis = new File(input.getParentFile(), base + "_skipvis." + ext);

        if (notdone.exists())
        {
            SampleMindedTransformTask.importNotDone(notdone, job);
        }
        if (skipvis.exists())
        {
            SampleMindedTransformTask.importSkipVisit(skipvis, job);
        }
    }

    @Override
    public ActionURL getManageAction(Container c, User user)
    {
        return null;
    }
}
