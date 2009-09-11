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
package org.labkey.study.model;

import org.labkey.api.data.Container;

/*
 * User: brittp
 * Date: Jul 15, 2008
 * Time: 2:48:55 PM
 */
public class QCState
{
    private int _rowId;
    private String _label;
    private Container _container;
    private String _description;
    private boolean _publicData;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public boolean isPublicData()
    {
        return _publicData;
    }

    public void setPublicData(boolean publicData)
    {
        _publicData = publicData;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QCState qcState = (QCState) o;

        if (_publicData != qcState._publicData) return false;
        if (_rowId != qcState._rowId) return false;
        if (_container != null ? !_container.equals(qcState._container) : qcState._container != null) return false;
        if (_description != null ? !_description.equals(qcState._description) : qcState._description != null)
            return false;
        if (_label != null ? !_label.equals(qcState._label) : qcState._label != null) return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = _rowId;
        result = 31 * result + (_label != null ? _label.hashCode() : 0);
        result = 31 * result + (_container != null ? _container.hashCode() : 0);
        result = 31 * result + (_description != null ? _description.hashCode() : 0);
        result = 31 * result + (_publicData ? 1 : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return getLabel() + ": " + getDescription();
    }
}