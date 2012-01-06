/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.files;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

/**
 * User: klum
 * Date: Apr 16, 2010
 * Time: 11:04:41 AM
 */
public class FilesTbarBtnOption
{
    String _id;
    boolean _hideText;
    boolean _hideIcon;
    int _position;

    public FilesTbarBtnOption(String id, int position, boolean hideText, boolean hideIcon)
    {
        _id = id;
        _position = position;
        _hideText = hideText;
        _hideIcon = hideIcon;
    }

    public boolean isHideText()
    {
        return _hideText;
    }

    public void setHideText(boolean hideText)
    {
        _hideText = hideText;
    }

    public boolean isHideIcon()
    {
        return _hideIcon;
    }

    public void setHideIcon(boolean hideIcon)
    {
        _hideIcon = hideIcon;
    }

    public int getPosition()
    {
        return _position;
    }

    public void setPosition(int position)
    {
        _position = position;
    }

    public String getId()
    {
        return _id;
    }

    public void setId(String id)
    {
        _id = id;
    }

    public static FilesTbarBtnOption fromJSON(JSONObject o)
    {
        if (o != null)
        {
            String id = o.getString("id");
            int position = o.getInt("position");
            boolean hideText = o.optBoolean("hideText", false);
            boolean hideIcon = o.optBoolean("hideIcon", false);

            if (!StringUtils.isEmpty(id))
                return new FilesTbarBtnOption(id, position, hideText, hideIcon);
        }
        return null;
    }

    public JSONObject toJSON()
    {
        JSONObject o = new JSONObject();

        o.put("id", _id);
        o.put("position", _position);
        o.put("hideText", _hideText);
        o.put("hideIcon", _hideIcon);

        return o;
    }
}
