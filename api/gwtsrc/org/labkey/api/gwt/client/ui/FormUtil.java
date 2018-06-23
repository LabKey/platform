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

package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.DOM;

/**
 * User: jeckels
 * Date: Feb 8, 2008
 */
public class FormUtil
{
    public static void setValueInForm(String value, Element element)
    {
        DOM.setElementAttribute(element, "value", value == null ? "" : value);
    }


    public static String getValueInForm(Element element)
    {
        String value = DOM.getElementAttribute(element, "value");
        if ("".equals(value))
        {
            return null;
        }
        return value;
    }
}
