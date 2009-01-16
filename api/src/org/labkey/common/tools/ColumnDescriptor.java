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
package org.labkey.common.tools;

import org.apache.commons.beanutils.Converter;
import org.labkey.api.data.Container;

import java.lang.reflect.Method;
import java.util.Date;

/**
 * Used by data loaders to define their column properties
 *
 * User: jgarms
 * Date: Oct 22, 2008
 */
public class ColumnDescriptor
{
    public ColumnDescriptor()
    {
    }

    public ColumnDescriptor(String name, Class type)
    {
        this.name = name;
        this.clazz = type;
    }

    public ColumnDescriptor(String name, Class type, Object defaultValue)
    {
        this.name = name;
        this.clazz = type;
        this.missingValues = defaultValue;
    }

    public Class clazz = String.class;
    public String name = null;
    public boolean load = true;
    public boolean isProperty = false; //Load as a class property
    public Object missingValues = null;
    public Object errorValues = null;
    public Converter converter = null;
    public Method setter = null;

    // This column is a data column, with qc enabled
    public boolean qcEnabled;

    // This column is the qc indicator -- that is, .Q or .N, not the actual value.
    public boolean qcIndicator;

    // If qc is enabled, or this is a qc indicator, need to know which container to look in for possible QC states
    public Container qcContainer;

    public String toString()
    {
        return name + ":" + clazz.getSimpleName();
    }

    public String getRangeURI()
    {
        if (clazz == String.class)
            return "xsd:string";
        if (clazz == Integer.class)
            return "xsd:int";
        if (clazz == Double.class)
            return "xsd:double";
        if (clazz == Date.class)
            return "xsd:dateTime";
        if (clazz == Boolean.class)
            return "xsd:boolean";
        throw new IllegalArgumentException("Unknown class for column: " + clazz);
    }
}
