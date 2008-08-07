/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.http.client.URL;
import org.labkey.api.gwt.client.util.PropertyUtil;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class ImageButton extends PushButton implements ClickListener
{
    private List _clickListeners = new ArrayList();
    private boolean _enabled = true;
    private String _text;

    private static Image getUpFaceImage(String text)
    {
        return new Image(PropertyUtil.getContextPath() + "/" + URL.encodeComponent(text) + ".button");
    }

    public ImageButton(String text, ClickListener listener)
    {
        this(text);
        addClickListener(listener);
    }

    public ImageButton(String text)
    {
        super(getUpFaceImage(text));

        addClickListener(this);

        _text = text;
        DOM.setAttribute(getElement(), "id", "button_" + text);

        super.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                if (_enabled)
                {
                    Iterator i = new ArrayList(_clickListeners).iterator();
                    while (i.hasNext())
                    {
                        ((ClickListener)i.next()).onClick(sender);
                    }
                }
            }
        });
    }

    public void addClickListener(ClickListener listener)
    {
        _clickListeners.add(listener);
    }

    public void removeClickListener(ClickListener listener)
    {
        _clickListeners.remove(listener);
    }

//    public void setEnabled(boolean enabled)
//    {
//        if (_enabled != enabled)
//        {
//            _enabled = enabled;
//            if (_enabled)
//            {
//                setUrl(PropertyUtil.getContextPath() + "/" + URL.encodeComponent(_text) + ".button");
//            }
//            else
//            {
//                setUrl(PropertyUtil.getContextPath() + "/" + URL.encodeComponent(_text) + ".button?style=disabled");
//            }
//        }
//    }
    
    public String getText()
    {
        return _text;
    }

    public void setText(String text)
    {
        if (_text.equals(text))
            return;

        _text = text;        
        getUpFace().setImage(getUpFaceImage(text));
    }

    /** to make life simple, just override onClick instead of registering a listener */
    public void onClick(Widget sender)
    {
    }
}
