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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ObjectFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

public class VisitTagMapEntry
{
    private final String _visitTag;
    private final int _visitId;
    private final Integer _cohortId;
    private final Integer _rowId;

    public VisitTagMapEntry(String visitTag, int visitId, @Nullable Integer cohortId, Integer rowId)
    {
        _visitTag = visitTag;
        _visitId = visitId;
        _cohortId = cohortId;
        _rowId = rowId;
    }

    private VisitTagMapEntry(Map m)
    {
        _visitTag = (String)m.get("visitTag");
        _visitId = (Integer)m.get("visitId");
        _cohortId = (Integer)m.get("cohortId");
        _rowId = (Integer)m.get("rowId");
    }

    public String getVisitTag()
    {
        return _visitTag;
    }
    public int getVisitId()
    {
        return _visitId;
    }
    public Integer getCohortId()
    {
        return _cohortId;
    }

    static
    {
        ObjectFactory.Registry.register(VisitTagMapEntry.class, new VisitTagMapEntryFactory());
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    // UNDONE: should have BaseObjectFactory to implement handle in terms of fromMap()
    private static class VisitTagMapEntryFactory implements ObjectFactory<VisitTagMapEntry>
    {
        public VisitTagMapEntry fromMap(Map<String, ?> m)
        {
            return new VisitTagMapEntry(m);
        }

        public VisitTagMapEntry fromMap(VisitTagMapEntry bean, Map<String, ?> m)
        {
            throw new UnsupportedOperationException();
        }

        public Map<String, Object> toMap(VisitTagMapEntry bean, Map m)
        {
            throw new java.lang.UnsupportedOperationException();
        }

        @Override
        public ArrayList<VisitTagMapEntry> handleArrayList(ResultSet rs) throws SQLException
        {
            throw new java.lang.UnsupportedOperationException();
        }

        public VisitTagMapEntry[] handleArray(ResultSet rs) throws SQLException
        {
            throw new java.lang.UnsupportedOperationException();
        }

        public VisitTagMapEntry handle(ResultSet rs) throws SQLException
        {
            throw new java.lang.UnsupportedOperationException();
        }
    }
}
