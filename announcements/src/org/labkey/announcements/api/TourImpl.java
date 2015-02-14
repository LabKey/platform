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
package org.labkey.announcements.api;

import org.json.JSONObject;
import org.labkey.announcements.model.TourModel;
import org.labkey.api.announcements.api.Tour;

/**
 * Created by Marty on 1/15/2015.
 */
public class TourImpl implements Tour
{
    TourModel _model;

    public TourImpl(TourModel model)
    {
        _model = model;
    }

    public Integer getRowId()
    {
        return _model.getRowId();
    }

    public void setRowId(Integer id)
    {
        _model.setRowId(id);
    }

    public String getTitle()
    {
        return _model.getTitle();
    }

    public void setTitle(String title)
    {
        _model.setTitle(title);
    }

    public String getDescription()
    {
        return _model.getDescription();
    }

    public void setDescription(String description)
    {
        _model.setDescription(description);
    }

    public String getJson()
    {
        return _model.getJson();
    }

    public void setJson(String json)
    {
        _model.setJson(json);
    }

    public Integer getMode()
    {
        return _model.getMode();
    }

    public void setMode(Integer mode)
    {
        _model.setMode(mode);
    }

    public JSONObject abbrevDef()
    {
        return _model.abbrevDef();
    }
}
