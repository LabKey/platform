/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.api.reader;

import org.apache.commons.beanutils.Converter;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.property.Type;

import java.lang.reflect.Method;

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
    private boolean qcEnabled;

    // This column is the qc indicator -- that is, .Q or .N, not the actual value.
    private boolean qcIndicator;

    // If qc is enabled, or this is a qc indicator, need to know which container to look in for possible QC states
    private Container qcContainer;

    public String toString()
    {
        return name + ":" + clazz.getSimpleName();
    }

    public String getRangeURI()
    {
        Type type = Type.getTypeByClass(clazz);

        if (null == type)
            throw new IllegalArgumentException("Unknown class for column: " + clazz);

        return type.getXsdType();
    }

    public boolean isQcEnabled()
    {
        return qcEnabled;
    }

    public boolean isQcIndicator()
    {
        return qcIndicator;
    }

    public Container getQcContainer()
    {
        return qcContainer;
    }

    public void setQcEnabled(@NotNull Container qcContainer)
    {
        qcEnabled = true;
        this.qcContainer = qcContainer;
    }

    public void setQcIndicator(@NotNull Container qcContainer)
    {
        qcIndicator = true;
        this.qcContainer = qcContainer;
    }
}
