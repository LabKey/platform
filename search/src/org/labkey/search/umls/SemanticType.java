/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.search.umls;

import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.ResultSetSelector;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

/**
 * User: matthewb
 * Date: Mar 10, 2010
 * Time: 10:46:28 AM
 */
public class SemanticType // MRSTY
{
    final String _type="STY";
    String CUI;
    String TUI;
    String STN;
    String STY;
    String ATUI;
    String CVF;

    SemanticType()
    {
    }

    public SemanticType(String[] args)
    {
        try
        {
            int i=0;
            CUI=args[i++];
            TUI=args[i++];
            STN=args[i++];
            if (i==args.length) return;
            STY=args[i++];
            if (i==args.length) return;
            ATUI=args[i++];
            if (i==args.length) return;
            CVF=args[i++];
        }
        catch (ArrayIndexOutOfBoundsException x)
        {
        }
    }

    public SemanticType(Map<String,Object> map)
    {
        apply(map);
    }

    void apply(Map<String,Object> map)
    {
        CUI=getString(map,"cui");
        TUI=getString(map,"tui");
        STN=getString(map,"stn");
        STY=getString(map,"sty");
        ATUI=getString(map,"atui");
        CVF=getString(map,"cvf");
    }

    private String getString(Map map, String key)
    {
        Object v = map.get(key);
        return null==v ? null : v.toString();
    }

    @Override
    public String toString()
    {
        return _type + ": " + CUI + " " + STN + " " + STY;
    }


   static
    {
        ObjectFactory.Registry.register(SemanticType.class, new BeanObjectFactory<SemanticType>()
        {
            @Override
            public SemanticType fromMap(Map<String, ?> m)
            {
                return new SemanticType((Map)m);
            }

            @Override
            public SemanticType fromMap(SemanticType bean, Map<String, ?> m)
            {
                bean.apply((Map)m);
                return bean;
            }

            @Override
            public ArrayList<SemanticType> handleArrayList(ResultSet rs) throws SQLException
            {
                return new ResultSetSelector(UmlsSchema.getScope(), rs).getArrayList(SemanticType.class);
            }

            @Override
            public SemanticType[] handleArray(ResultSet rs) throws SQLException
            {
                return new ResultSetSelector(UmlsSchema.getScope(), rs).getArray(SemanticType.class);
            }
        });
    }
}
