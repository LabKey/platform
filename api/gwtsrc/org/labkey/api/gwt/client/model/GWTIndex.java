/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * User: kevink
 * Date: 11/9/15
 */
public class GWTIndex implements IsSerializable, Serializable
{
    private List<String> _columnNames;
    private boolean _unique;

    public GWTIndex() { }

    public GWTIndex(List<String> columnNames, boolean unique)
    {
        _columnNames = columnNames;
        _unique = unique;
    }

    public GWTIndex(GWTIndex other)
    {
        setColumnNames(new ArrayList<String>(other.getColumnNames()));
        setUnique(other.isUnique());
    }

    public GWTIndex copy()
    {
        return new GWTIndex(this);
    }


    public List<String> getColumnNames()
    {
        return _columnNames;
    }

    public void setColumnNames(List<String> columnNames)
    {
        _columnNames = columnNames;
    }

    public boolean isUnique()
    {
        return _unique;
    }

    public void setUnique(boolean unique)
    {
        _unique = unique;
    }
}
