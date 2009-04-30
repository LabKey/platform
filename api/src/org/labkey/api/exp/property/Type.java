/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.exp.property;

import java.util.Date;

/**
* User: adam
* Date: Apr 29, 2009
* Time: 9:44:07 AM
*/
public enum Type
{
    StringType("Text (String)", "xsd:string", String.class),
    IntType("Integer", "xsd:int", Integer.class),
    DoubleType("Number (Double)", "xsd:double", Double.class),
    DateTimeType("DateTime", "xsd:dateTime", Date.class),
    BooleanType("Boolean", "xsd:boolean", Boolean.class);

    private String label;
    private String xsd;
    private Class clazz;

    Type(String label, String xsd, Class clazz)
    {
        this.label = label;
        this.xsd = xsd;
        this.clazz = clazz;
    }

    public String getLabel()
    {
        return label;
    }

    public String getXsdType()
    {
        return xsd;
    }

    public Class getJavaClass()
    {
        return clazz;
    }

    public static Type getTypeByLabel(String label)
    {
        for (Type type : values())
        {
            if (type.getLabel().equals(label))
                return type;
        }
        return null;
    }

    public static Type getTypeByXsdType(String xsd)
    {
        for (Type type : values())
        {
            if (type.getXsdType().equals(xsd))
                return type;
        }
        return null;
    }

    public static Type getTypeByClass(Class clazz)
    {
        for (Type type : values())
        {
            if (type.getJavaClass().equals(clazz))
                return type;
        }
        return null;
    }
}
