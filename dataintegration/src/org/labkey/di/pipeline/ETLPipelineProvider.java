package org.labkey.di.pipeline;

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.view.ViewContext;
import org.labkey.di.DataIntegrationModule;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class ETLPipelineProvider extends PipelineProvider
{
    public static final String NAME = "ETL";

    public ETLPipelineProvider(DataIntegrationModule module)
    {
        super(NAME, module);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {

    }
}
