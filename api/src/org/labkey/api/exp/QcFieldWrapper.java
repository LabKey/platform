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
package org.labkey.api.exp;

/**
 * For fields with associated QC, this wrapper holds both an actual value
 * (which may be null) and a QC value (which also may be null).
 *
 * User: jgarms
 * Date: Jan 15, 2009
 */
public class QcFieldWrapper
{
    private Object value;
    private String qcValue;

    public Object getValue()
    {
        return value;
    }

    public void setValue(Object value)
    {
        this.value = value;
    }

    public String getQcValue()
    {
        return qcValue;
    }

    public void setQcValue(String qcValue)
    {
        if ("".equals(qcValue))
            qcValue = null;
        this.qcValue = qcValue;
    }

    @Override
    public String toString()
    {
        return "QcFieldWrapper: value=" + value + ", qcValue=" + qcValue;
    }

    public boolean isEmpty()
    {
        return value == null && qcValue == null;
    }
}
