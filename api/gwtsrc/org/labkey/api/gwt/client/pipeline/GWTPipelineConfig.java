/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.gwt.client.pipeline;

import java.io.Serializable;
import java.util.List;

/**
 * User: jeckels
 * Date: Jan 20, 2012
 */
public class GWTPipelineConfig implements Serializable
{
    private List<GWTPipelineTask> _tasks;

    public GWTPipelineConfig()
    {
    }

    public GWTPipelineConfig(List<GWTPipelineTask> tasks)
    {
        _tasks = tasks;
    }

    public List<GWTPipelineTask> getTasks()
    {
        return _tasks;
    }
}
