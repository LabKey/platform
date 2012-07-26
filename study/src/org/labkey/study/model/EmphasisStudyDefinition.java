/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
public class EmphasisStudyDefinition implements HasViewContext
{
    private String _name;
    private String _description;
    private String _srcPath;
    private String _dstPath;
    private int[] _datasets = new int[0];
    private ViewContext _context;
    private int _updateDelay;

    private int[] _groups = new int[0];
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

    public int[] getGroups()
    {
        return _groups;
    }

    public void setGroups(int[] groups)
    {
        _groups = groups;
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
}
