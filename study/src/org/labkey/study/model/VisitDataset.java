/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.study.model;

import org.labkey.api.data.Container;

/**
 * User: brittp
 * Date: Jan 7, 2006
 * Time: 3:53:35 PM
 */
public class VisitDataset
{
    private int _datasetId;
    private int _visitId;
    private boolean _isRequired = false;
    private Container _container;

    public VisitDataset()
    {
    }

    public VisitDataset(Container container, int datasetId, int visitId, boolean isRequired)
    {
        _datasetId = datasetId;
        _visitId = visitId;
        _isRequired = isRequired;
        _container = container;
    }

    public boolean isRequired()
    {
        return _isRequired;
    }

    public int getVisitRowId()
    {
        return _visitId;
    }

    public int getDatasetId()
    {
        return _datasetId;
    }

    public Container getContainer()
    {
        return _container;
    }
}
