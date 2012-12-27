/*
 * Copyright (c) 2010-2012 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.view.DisplayElement;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Apr 8, 2010
 * Time: 11:05:50 AM
 */

/**
 * Represents a reference to a built-in button
 */
public class BuiltInButtonConfig implements ButtonConfig
{
    private String _caption;
    private String _originalCaption;
    private String _insertAfter, _insertBefore;
    private Integer _insertPosition;
    private boolean _hidden = false;

    public BuiltInButtonConfig(String originalCaption)
    {
        this(originalCaption, originalCaption);
    }

    public BuiltInButtonConfig(String originalCaption, String newCaption)
    {
        _originalCaption = originalCaption;
        _caption = newCaption;
    }


    public String getCaption()
    {
        return _caption;
    }

    public void setCaption(String caption)
    {
        _caption = caption;
    }

    public String getInsertAfter()
    {
        return _insertAfter;
    }

    public void setInsertAfter(String insertAfter)
    {
        _insertAfter = insertAfter;
    }

    public String getInsertBefore()
    {
        return _insertBefore;
    }

    public void setInsertBefore(String insertBefore)
    {
        _insertBefore = insertBefore;
    }

    public Integer getInsertPosition()
    {
        return _insertPosition;
    }

    public void setInsertPosition(Integer insertPosition)
    {
        _insertPosition = insertPosition;
    }

    public DisplayElement createButton(RenderContext ctx, List<DisplayElement> originalButtons)
    {
        for (DisplayElement de : originalButtons)
        {
            if (de instanceof ActionButton && _originalCaption.equalsIgnoreCase(de.getCaption()))
            {
                de.setVisible(!_hidden);
                if (_caption != null && !_caption.equals(_originalCaption))
                {
                    de.setCaption(_caption);
                    return de;
                }
                else
                    return de;
            }
        }
        return null;
    }

    public void setHidden(boolean hidden)
    {
        _hidden = hidden;
    }
}
