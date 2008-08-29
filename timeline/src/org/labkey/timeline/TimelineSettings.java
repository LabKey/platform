/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.timeline;

/*
* User: Mark Igra
* Date: Jul 7, 2008
* Time: 4:56:33 PM
*/
public class TimelineSettings
{
    private String _schemaName;
    private String _queryName;
    private String _viewName;
    private String _startField;
    private String _endField;
    private String _titleField;
    private String _descriptionField;
    private String _iconField;
    private String _divId;
    private String _webPartTitle;
    private int _pixelHeight = 500;

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getStartField()
    {
        return _startField;
    }

    public void setStartField(String startField)
    {
        _startField = startField;
    }

    public String getEndField()
    {
        return _endField;
    }

    public void setEndField(String endField)
    {
        _endField = endField;
    }

    public String getTitleField()
    {
        return _titleField;
    }

    public void setTitleField(String titleField)
    {
        _titleField = titleField;
    }

    public String getDescriptionField()
    {
        return _descriptionField;
    }

    public void setDescriptionField(String descriptionField)
    {
        _descriptionField = descriptionField;
    }

    public String getIconField()
    {
        return _iconField;
    }

    public void setIconField(String iconField)
    {
        _iconField = iconField;
    }

    public int getPixelHeight()
    {
        return _pixelHeight;
    }

    public void setPixelHeight(int pixelHeight)
    {
        _pixelHeight = pixelHeight;
    }

    public String getDivId()
    {
        return _divId;
    }

    public void setDivId(String divId)
    {
        _divId = divId;
    }

    public String getWebPartTitle()
    {
        if (null == _webPartTitle)
        {
            if (null == getQueryName())
                return "Timeline";
            else
                return getQueryName() + " Timeline";
        }
        else
            return _webPartTitle;
    }

    public void setWebPartTitle(String webPartTitle)
    {
        _webPartTitle = webPartTitle;
    }

    public String getViewName()
    {
        return _viewName;
    }

    public void setViewName(String viewName)
    {
        _viewName = viewName;
    }
}