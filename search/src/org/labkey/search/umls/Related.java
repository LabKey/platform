/*
 * Copyright (c) 2010-2018 LabKey Corporation
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
import java.util.ArrayList;
import java.util.Map;

/**
 * User: matthewb
 * Date: Mar 10, 2010
 * Time: 10:46:44 AM
 */
public class Related
{
    final String _type="REL";
    public String CUI1;
    String AUI1;
    String STYPE1;
    public String REL;
    public String CUI2;
    String AUI2;
    String STYPE2;
    public String RELA;
    String RUI;
    String SRUI;
    String SAB;
    String SL;
    String RG;
    String DIR;
    String SUPPRESS;
    String CVF;


    public Related(Map<String,Object> map)
    {
        apply(map);
    }
    

    public Related(String[] args)
    {
        try
        {
            int i=0;
            CUI1=args[i++];
            AUI1=args[i++];
            STYPE1=args[i++];
            REL=args[i++];
            CUI2=args[i++];
            AUI2=args[i++];
            STYPE2=args[i++];
            RELA=args[i++];
            RUI=args[i++];
            SRUI=args[i++];
            SAB=args[i++];
            SL=args[i++];
            RG=args[i++];
            DIR=args[i++];
            SUPPRESS=args[i++];
            CVF=args[i++];
        }
        catch (ArrayIndexOutOfBoundsException x)
        {
        }
    }


   void apply(Map<String,Object> map)
    {
        CUI1=getString(map,"cui1");
        AUI1=getString(map,"aui1");
        STYPE1=getString(map,"stype1");
        REL=getString(map,"rel");
        CUI2=getString(map,"cui2");
        AUI2=getString(map,"aui2");
        STYPE2=getString(map,"stype2");
        RELA=getString(map,"rela");
        SRUI=getString(map,"srui");
        SAB=getString(map,"sab");
        SL=getString(map,"sl");
        RG=getString(map,"rg");
        DIR=getString(map,"dir");
        SUPPRESS=getString(map,"suppress");
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
        return _type + ": " + CUI1 + " " + REL + " " + CUI2;
    }

    static
    {
        ObjectFactory.Registry.register(Related.class, new BeanObjectFactory<Related>()
        {
            @Override
            public Related fromMap(Map<String, ?> m)
            {
                return new Related((Map)m);
            }

            @Override
            public Related fromMap(Related bean, Map<String, ?> m)
            {
                bean.apply((Map)m);
                return bean;
            }

            @Override
            public ArrayList<Related> handleArrayList(ResultSet rs)
            {
                return new ResultSetSelector(UmlsSchema.getScope(), rs).getArrayList(Related.class);
            }

            @Override
            public Related[] handleArray(ResultSet rs)
            {
                return new ResultSetSelector(UmlsSchema.getScope(), rs).getArray(Related.class);
            }
        });
    }
}
