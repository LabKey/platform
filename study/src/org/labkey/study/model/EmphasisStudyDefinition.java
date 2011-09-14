/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.ParticipantGroupController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Aug 25, 2011
 * Time: 3:51:31 PM
 */
public class EmphasisStudyDefinition implements CustomApiForm, HasViewContext
{
    private String _name;
    private String _description;
    private String _srcPath;
    private String _dstPath;
    private int[] _datasets;
    private ViewContext _context;
    private int _updateDelay;

    private ParticipantGroupController.ParticipantCategorySpecification[] _categories = new ParticipantGroupController.ParticipantCategorySpecification[0];
    private boolean _copyParticipantGroups;

    @Override
    public void setViewContext(ViewContext context)
    {
        _context = context;
    }

    @Override
    public ViewContext getViewContext()
    {
        return _context;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getSrcPath()
    {
        return _srcPath;
    }

    public void setSrcPath(String srcPath)
    {
        _srcPath = srcPath;
    }

    public String getDstPath()
    {
        return _dstPath;
    }

    public void setDstPath(String dstPath)
    {
        _dstPath = dstPath;
    }

    public int[] getDatasets()
    {
        return _datasets;
    }

    public void setDatasets(int[] datasets)
    {
        _datasets = datasets;
    }

    public ParticipantGroupController.ParticipantCategorySpecification[] getCategories()
    {
        return _categories;
    }

    public void setCategories(ParticipantGroupController.ParticipantCategorySpecification[] categories)
    {
        _categories = categories;
    }

    public int getUpdateDelay()
    {
        return _updateDelay;
    }

    public void setUpdateDelay(int updateDelay)
    {
        _updateDelay = updateDelay;
    }

    public boolean isCopyParticipantGroups()
    {
        return _copyParticipantGroups;
    }

    public void setCopyParticipantGroups(boolean copyParticipantGroups)
    {
        _copyParticipantGroups = copyParticipantGroups;
    }

    @Override
    public void bindProperties(Map<String, Object> props)
    {
        setName((String)props.get("name"));
        setDescription((String)props.get("description"));
        setSrcPath((String)props.get("srcPath"));
        setDstPath((String)props.get("dstPath"));
        setCopyParticipantGroups((Boolean)props.get("copyParticipantGroups"));

        Object datasetsJSON = props.get("datasets");
        if (datasetsJSON instanceof JSONArray)
        {
            JSONArray datasets = (JSONArray)datasetsJSON;
            _datasets = new int[datasets.length()];

            for (int i=0; i < datasets.length(); i++)
            {
                _datasets[i] = datasets.getInt(i);
            }
        }

        Object dataRefresh = props.get("dataRefresh");
        if (dataRefresh instanceof JSONObject)
        {
            JSONObject refresh = (JSONObject)dataRefresh;

            if (refresh.getBoolean("autoRefresh"))
                _updateDelay = refresh.getInt("updateDelay");
        }

        Object categories = props.get("categories");
        if (categories != null)
        {
            List<ParticipantGroupController.ParticipantCategorySpecification> categorySpecs = new ArrayList<ParticipantGroupController.ParticipantCategorySpecification>();
            for (JSONObject categoryInfo : ((JSONArray)categories).toJSONObjectArray())
            {
                ParticipantGroupController.ParticipantCategorySpecification spec = new ParticipantGroupController.ParticipantCategorySpecification();

                spec.fromJSON(categoryInfo);
                categorySpecs.add(spec);
            }
            setCategories(categorySpecs.toArray(new ParticipantGroupController.ParticipantCategorySpecification[categorySpecs.size()]));
        }
    }
}
