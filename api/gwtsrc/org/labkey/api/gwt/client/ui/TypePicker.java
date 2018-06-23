/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import com.google.gwt.user.client.ui.ListBox;

/**
 * User: matthewb
 * Date: Apr 23, 2007
 * Time: 5:16:30 PM
 */
public class TypePicker extends ListBox
{
    public TypePicker(boolean allowFileLinkProperties, boolean allowAttachmentProperties)
    {
        addItem(PropertyType.xsdString);
        addItem(PropertyType.expMultiLine);
        addItem(PropertyType.xsdBoolean);
        addItem(PropertyType.xsdInt);
        addItem(PropertyType.xsdDouble);
        addItem(PropertyType.xsdDateTime);
        if (allowFileLinkProperties)
            addItem(PropertyType.expFileLink);
        if (allowAttachmentProperties)
            addItem(PropertyType.expAttachment);
    }

    public TypePicker(String rangeURI, boolean allowFileLinkProperties, boolean allowAttachmentProperties)
    {
        this(allowFileLinkProperties, allowAttachmentProperties);
        setRangeURI(rangeURI);
    }

    public void setRangeURI(String uri)
    {
        PropertyType t = PropertyType.fromName(uri);
        String rangeURI = null==t ? "" : t.toString();

        int select = 0;
        if (rangeURI != null)
        {
            for (int i=0 ; i<getItemCount(); i++)
            {
                if (rangeURI.equalsIgnoreCase(getValue(i)))
                {
                    select = i;
                    break;
                }
            }
        }
        setSelectedIndex(select);
    }

    public String getRangeURI()
    {
        return getValue(getSelectedIndex());
    }

    public static String getDisplayString(String type)
    {
        PropertyType t = PropertyType.fromName(type);
        return null == t ? type : t.getDisplay();
    }

    private void addItem(PropertyType t)
    {
        addItem(t.getDisplay(), t.toString());
    }

}
