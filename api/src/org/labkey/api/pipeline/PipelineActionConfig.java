/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jan 9, 2010
 * Time: 7:45:00 PM
 */
public class PipelineActionConfig
{
    private String _id;
    private displayState _state;

    public enum displayState {
        enabled,
        disabled,
        toolbar,
        admin,
    }

    public PipelineActionConfig(String id, String state)
    {
        _id = id;
        _state = displayState.valueOf(state);
    }

    public String getId()
    {
        return _id;
    }

    public void setId(String id)
    {
        _id = id;
    }

    public displayState getState()
    {
        return _state;
    }

    public void setState(displayState state)
    {
        _state = state;
    }

    public JSONObject toJSON()
    {
        JSONObject o = new JSONObject();

        o.put("id", _id == null ? "" : _id);
        o.put("display", _state == null ? "" : _state);

        return o;
    }
}
