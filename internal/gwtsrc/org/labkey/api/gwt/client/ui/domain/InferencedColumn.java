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
package org.labkey.api.gwt.client.ui.domain;

import com.google.gwt.user.client.rpc.IsSerializable;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

import java.util.List;

/**
 * Represents an inferenced column of data from a file -- excel or tsv.
 *
 * Contains a guess at a property descriptor, as well as the first
 * few rows of data from the file.
 *
 * User: jgarms
 * Date: Nov 4, 2008
 */
public class InferencedColumn implements IsSerializable
{
    private GWTPropertyDescriptor propertyDescriptor;
    private List<String> data;

    public InferencedColumn() {}

    public InferencedColumn(GWTPropertyDescriptor propertyDescriptor, List<String> data)
    {
        this.propertyDescriptor = propertyDescriptor;
        this.data = data;
    }

    public GWTPropertyDescriptor getPropertyDescriptor()
    {
        return propertyDescriptor;
    }

    public List<String> getData()
    {
        return data;
    }

    public void setData(List<String> data)
    {
        this.data = data;
    }

    public void setPropertyDescriptor(GWTPropertyDescriptor propertyDescriptor)
    {
        this.propertyDescriptor = propertyDescriptor;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(propertyDescriptor.getName()).append(" (").append(propertyDescriptor.getRangeURI()).append(')');
        sb.append(": {");
        boolean needComma = false;
        for (String s : data)
        {
            if (needComma)
                sb.append(", ");
            else
                needComma = true;
            sb.append(s);
        }
        sb.append('}');
        return sb.toString();
    }
}
