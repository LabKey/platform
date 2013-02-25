package org.labkey.di.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class TransformJob extends PipelineJob
{
    public TransformJob(@Nullable String provider, ViewBackgroundInfo info, PipeRoot root)
    {
        super(provider, info, root);
    }

    @Override
    public URLHelper getStatusHref()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDescription()
    {
        throw new UnsupportedOperationException();
    }
}
