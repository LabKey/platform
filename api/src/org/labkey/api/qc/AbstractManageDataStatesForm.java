/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.qc;

import org.labkey.api.action.ReturnUrlForm;

public class AbstractManageDataStatesForm extends ReturnUrlForm
{
    private int[] _ids;
    private String[] _labels;
    private String[] _descriptions;
    private int[] _publicData;
    private int[] _newIds;
    private String[] _newLabels;
    private String[] _newDescriptions;
    private int[] _newPublicData;
    private boolean _reshowPage;
    private boolean _blankQCStatePublic;

    public int[] getIds()
    {
        return _ids;
    }

    public void setIds(int[] ids)
    {
        _ids = ids;
    }

    public String[] getLabels()
    {
        return _labels;
    }

    public void setLabels(String[] labels)
    {
        _labels = labels;
    }

    public String[] getDescriptions()
    {
        return _descriptions;
    }

    public void setDescriptions(String[] descriptions)
    {
        _descriptions = descriptions;
    }

    public int[] getPublicData()
    {
        return _publicData;
    }

    public void setPublicData(int[] publicData)
    {
        _publicData = publicData;
    }

    public int[] getNewIds()
    {
        return _newIds;
    }

    public void setNewIds(int[] newIds)
    {
        _newIds = newIds;
    }

    public String[] getNewLabels()
    {
        return _newLabels;
    }

    public void setNewLabels(String[] newLabels)
    {
        _newLabels = newLabels;
    }

    public String[] getNewDescriptions()
    {
        return _newDescriptions;
    }

    public void setNewDescriptions(String[] newDescriptions)
    {
        _newDescriptions = newDescriptions;
    }

    public int[] getNewPublicData()
    {
        return _newPublicData;
    }

    public void setNewPublicData(int[] newPublicData)
    {
        _newPublicData = newPublicData;
    }

    public boolean isReshowPage()
    {
        return _reshowPage;
    }

    public void setReshowPage(boolean reshowPage)
    {
        _reshowPage = reshowPage;
    }

    public boolean isBlankQCStatePublic()
    {
        return _blankQCStatePublic;
    }

    public void setBlankQCStatePublic(boolean blankQCStatePublic)
    {
        _blankQCStatePublic = blankQCStatePublic;
    }
}
