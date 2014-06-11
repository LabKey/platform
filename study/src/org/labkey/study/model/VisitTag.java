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

import org.labkey.api.data.ObjectFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

public class VisitTag
{
    private final String _name;
    private final String _caption;
    private final String _description;
    private final boolean _singleUse;

    public VisitTag(String name, String caption, String description, boolean singleUse)
    {
        _name = name;
        _caption = caption;
        _description = description;
        _singleUse = singleUse;
    }

    private VisitTag(Map m)
    {
        _name = (String)m.get("name");
        _caption = (String)m.get("caption");
        _description = (String)m.get("description");
        if (null != m.get("singleUse"))
            _singleUse = (boolean)m.get("singleUse");
        else
            _singleUse = false;
    }

    public String getName()
    {
        return _name;
    }
    public String getCaption()
    {
        return _caption;
    }
    public String getDescription()
    {
        return _description;
    }
    public boolean isSingleUse()
    {
        return _singleUse;
    }

    static
    {
        ObjectFactory.Registry.register(VisitTag.class, new VisitTagFactory());
    }

    // UNDONE: should have BaseObjectFactory to implement handle in terms of fromMap()
    private static class VisitTagFactory implements ObjectFactory<VisitTag>
    {
        public VisitTag fromMap(Map<String, ?> m)
        {
            return new VisitTag(m);
        }

        public VisitTag fromMap(VisitTag bean, Map<String, ?> m)
        {
            throw new UnsupportedOperationException();
        }

        public Map<String, Object> toMap(VisitTag bean, Map m)
        {
            throw new java.lang.UnsupportedOperationException();
        }

        @Override
        public ArrayList<VisitTag> handleArrayList(ResultSet rs) throws SQLException
        {
            throw new java.lang.UnsupportedOperationException();
        }

        public VisitTag[] handleArray(ResultSet rs) throws SQLException
        {
            throw new java.lang.UnsupportedOperationException();
        }

        public VisitTag handle(ResultSet rs) throws SQLException
        {
            throw new java.lang.UnsupportedOperationException();
        }
    }
}
