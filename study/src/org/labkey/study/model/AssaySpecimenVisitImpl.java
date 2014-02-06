/*
 * Copyright (c) 2014 LabKey Corporation
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
import org.labkey.api.study.AssaySpecimenVisit;

/**
 * Created by cnathe on 2/3/14.
 */
public class AssaySpecimenVisitImpl implements AssaySpecimenVisit
{
    private int _assaySpecimenId;
    private int _visitId;
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
}
