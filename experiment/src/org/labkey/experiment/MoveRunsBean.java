package org.labkey.experiment;

import org.labkey.api.util.ContainerTree;

/**
 * User: jeckels
 * Date: Feb 14, 2007
 */
public class MoveRunsBean
{
    private ContainerTree _containerTree;
    private String _dataRegionSelectionKey;

    public MoveRunsBean(ContainerTree containerTree, String dataRegionSelectionKey)
    {
        _containerTree = containerTree;
        _dataRegionSelectionKey = dataRegionSelectionKey;
    }

    public ContainerTree getContainerTree()
    {
        return _containerTree;
    }

    public String getDataRegionSelectionKey()
    {
        return _dataRegionSelectionKey;
    }
}
