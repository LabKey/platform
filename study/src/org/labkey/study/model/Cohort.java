package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.util.PageFlowUtil;

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
public class Cohort extends AbstractStudyEntity<Cohort> implements Extensible
{
    private int _rowId = 0;
    private String _lsid;

    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        assert _rowId == 0 : "Attempt to redefine rowId";
        _rowId = rowId;
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
        if (!PageFlowUtil.nullSafeEquals(getContainer(), cohort.getContainer())) return false;
        if (_label != null ? !_label.equals(cohort._label) : cohort._label != null) return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = _rowId;
        result = 31 * result + (_label != null ? _label.hashCode() : 0);
        Container c = getContainer();
        if (c != null)
            result = 31 * result + c.hashCode();
        return result;
    }
}
