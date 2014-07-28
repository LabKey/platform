package org.labkey.di.steps;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.di.pipeline.TaskrefTaskImpl;

import java.util.Arrays;
import java.util.List;

/**
 * User: tgaluhn
 * Date: 7/25/2014
 */
public class TestTaskrefTask extends TaskrefTaskImpl
{
    private static final String SETTING_1 = "setting1";

    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        settings.put(SETTING_1, "test");
        logger.info("Log from test task");
        return new RecordedActionSet(makeRecordedAction());
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return Arrays.asList(SETTING_1);
    }
}
