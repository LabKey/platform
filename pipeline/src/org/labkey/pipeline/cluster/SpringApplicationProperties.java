package org.labkey.pipeline.cluster;

import org.labkey.api.pipeline.PipelineJobService;

/**
 * User: jeckels
 * Date: Apr 29, 2008
 */
public class SpringApplicationProperties implements PipelineJobService.ApplicationProperties
{
    private String _toolsDirectory;

    public String getToolsDirectory()
    {
        return _toolsDirectory;
    }

    public void setToolsDirectory(String toolsDirectory)
    {
        _toolsDirectory = toolsDirectory;
    }
}
