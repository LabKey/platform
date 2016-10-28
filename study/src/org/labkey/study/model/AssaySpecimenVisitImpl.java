/*
 * Copyright (c) 2014-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.study.AssaySpecimenVisit;

import java.util.HashMap;
import java.util.Map;

public class AssaySpecimenVisitImpl implements AssaySpecimenVisit
{
    private int _assaySpecimenId;
    private int _visitId;
    private int _rowId;
    private Container _container;

    public AssaySpecimenVisitImpl()
    {
    }

    public AssaySpecimenVisitImpl(Container container, int assaySpecimenId, int visitId)
    {
        _container = container;
        _assaySpecimenId = assaySpecimenId;
        _visitId = visitId;
    }

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public int getAssaySpecimenId()
    {
        return _assaySpecimenId;
    }

    public void setAssaySpecimenId(int assaySpecimenId)
    {
        _assaySpecimenId = assaySpecimenId;
    }

    public int getVisitId()
    {
        return _visitId;
    }

    public void setVisitId(int visitId)
    {
        _visitId = visitId;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public Map<String, Object> serialize()
    {
        Map<String, Object> props = new HashMap<>();
        props.put("RowId", getRowId());
        props.put("VisitId", getVisitId());
        props.put("AssaySpecimenId", getAssaySpecimenId());
        props.put("Container", getContainer().getId());
        return props;
    }

    public static AssaySpecimenVisitImpl fromJSON(@NotNull JSONObject o, Container container)
    {
        // AssaySpecimenId may not be specified in JSON
        int assaySpecimenId = o.containsKey("AssaySpecimenId") ? o.getInt("AssaySpecimenId") : 0;
        AssaySpecimenVisitImpl assaySpecimenVisit = new AssaySpecimenVisitImpl(container, assaySpecimenId, o.getInt("VisitId"));

        if (o.containsKey("RowId"))
            assaySpecimenVisit.setRowId(o.getInt("RowId"));

        return assaySpecimenVisit;
    }
}
