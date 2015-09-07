/*
 * Copyright (c) 2011-2015 LabKey Corporation
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

import org.json.JSONObject;
import org.labkey.api.data.AbstractParticipantCategory;
import org.labkey.api.data.AbstractParticipantGroup;
import org.labkey.api.study.ParticipantCategory;


/**
 * User: klum
 * Date: Jun 8, 2011
 * Time: 2:49:27 PM
 */

/**
 * Represents a category of participants in related groups.
 */
public class ParticipantCategoryImpl extends AbstractParticipantCategory<String>
{
    private String _type;
    private boolean _autoUpdate;

    // properties used when the type is a query
    private String _queryName;
    private String _schemaName;
    private String _viewName;

    // properties used when the type is a cohort (column in a dataset)
    private int _datasetId;
    private String _groupProperty;

    public ParticipantCategoryImpl()
    {
    }

    public AbstractParticipantGroup<String>[] createGroups()
    {
        return new ParticipantGroup[0];
    }

    public ParticipantCategoryImpl(ParticipantCategoryImpl cat)
    {
        super(cat);
    }

    @Override
    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        if (Type.valueOf(type) == null)
            throw new IllegalArgumentException("Invalid ParticipantCategory type");

        _type = type;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getViewName()
    {
        return _viewName;
    }

    public void setViewName(String viewName)
    {
        _viewName = viewName;
    }

    public int getDatasetId()
    {
        return _datasetId;
    }

    public void setDatasetId(int datasetId)
    {
        _datasetId = datasetId;
    }

    public boolean isAutoUpdate()
    {
        return _autoUpdate;
    }

    public void setAutoUpdate(boolean autoUpdate)
    {
        _autoUpdate = autoUpdate;
    }

    public String getGroupProperty()
    {
        return _groupProperty;
    }

    public void setGroupProperty(String groupProperty)
    {
        _groupProperty = groupProperty;
    }

    // syntactic sugar
    public ParticipantGroup[] getGroups()
    {
        return (ParticipantGroup[]) super.getGroups();
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject json = super.toJSON();
        json.put("type", getType());
        json.put("autoUpdate", isAutoUpdate());

        // take care of Study Participant category fields here
        if (ParticipantCategory.Type.query.equals(ParticipantCategory.Type.valueOf(getType())))
        {
            json.put("queryName", getQueryName());
            json.put("schemaName", getSchemaName());
            json.put("viewName", getViewName());
        }

        if (ParticipantCategory.Type.cohort.equals(ParticipantCategory.Type.valueOf(getType())))
        {
            json.put("datasetId", getDatasetId());
            json.put("groupProperty", getGroupProperty());
        }

        return json;
    }

    @Override
    public void copy(AbstractParticipantCategory copy)
    {
        super.copy(copy);
        ParticipantCategoryImpl cat = (ParticipantCategoryImpl) copy;
        _queryName = cat._queryName;
        _schemaName = cat._schemaName;
        _viewName = cat._viewName;
        _datasetId = cat._datasetId;
        _groupProperty = cat._groupProperty;
        _type = cat._type;
        _autoUpdate = cat._autoUpdate;
    }
}
