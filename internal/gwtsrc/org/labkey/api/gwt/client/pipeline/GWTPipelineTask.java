/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

/**
 * User: jeckels
 * Date: Jan 20, 2012
 */
public class GWTPipelineTask implements Serializable
{
    private String _taskId;
    private String _name;
    private String _groupName;

    public GWTPipelineTask()
    {
    }

    public GWTPipelineTask(String taskId, String name, String groupName)
    {
        _taskId = taskId;
        _name = name;
        _groupName = groupName;
    }

    public String getTaskId()
    {
        return _taskId;
    }

    public String getName()
    {
        return _name;
    }

    public String getGroupName()
    {
        return _groupName;
    }
}
