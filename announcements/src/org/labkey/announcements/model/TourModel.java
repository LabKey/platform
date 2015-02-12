/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.announcements.model;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Entity;

import java.io.Serializable;

/**
 * Created by Marty on 1/15/2015.
 *
 * Bean class for comm.Tours
 */
public class TourModel extends Entity implements Serializable
{
    private Integer _RowId;
    private String _Title;
    private String _Description;
    private String _Json;
    private Integer _Mode;

    public TourModel()
    {
    }

    public Integer getRowId()
    {
        return _RowId;
    }

    public void setRowId(Integer id)
    {
        _RowId = id;
    }

    public String getTitle()
    {
        return _Title;
    }

    public void setTitle(String title)
    {
        _Title = title;
    }

    public String getDescription()
    {
        return _Description;
    }

    public void setDescription(String description)
    {
        _Description = description;
    }

    public String getJson()
    {
        return _Json;
    }

    public void setJson(String json)
    {
        _Json = json;
    }

    public Integer getMode()
    {
        return _Mode;
    }

    public void setMode(Integer mode)
    {
        _Mode = mode;
    }

    public JSONObject toJSON()
    {
        JSONObject tour = new JSONObject(this);
        tour.remove("json");
        tour.remove("title");
        tour.remove("description");
        tour.remove("createdBy");
        tour.remove("created");
        tour.remove("modifiedBy");
        tour.remove("modified");
        tour.remove("containerId");
        tour.remove("containerPath");
        tour.remove("entityId");

        return tour;
    }

    public String targetStepObject()
    {
        JSONObject tour = new JSONObject(this.getJson());
        JSONArray steps = tour.getJSONArray("steps");
        JSONObject out = new JSONObject();
        int i = 0;
        Object step;

        while((step = steps.get(i++)) != null)
        {
            JSONObject jsonStep = new JSONObject(step);
            Object target = jsonStep.remove("target");
            out.put(target.toString(), jsonStep.toString());
        }
        return out.toString();

    }
}
