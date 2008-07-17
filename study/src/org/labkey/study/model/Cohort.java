package org.labkey.study.model;

import org.labkey.api.data.*;

/**
 * Copyright (c) 2008 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * User: brittp
 * Created: Jan 15, 2008 4:27:38 PM
 */
public class Cohort
{
    private int _rowId;
    private String _label;
    private Container _container;
    private String _lsid;

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

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public boolean isInUse()
    {
        return StudyManager.getInstance().isCohortInUse(this);
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Cohort cohort = (Cohort) o;

        if (_rowId != cohort._rowId) return false;
        if (!_container.equals(cohort._container)) return false;
        if (_label != null ? !_label.equals(cohort._label) : cohort._label != null) return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = _rowId;
        result = 31 * result + (_label != null ? _label.hashCode() : 0);
        result = 31 * result + _container.hashCode();
        return result;
    }
}
