/*
 * Copyright (c) 2008-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
