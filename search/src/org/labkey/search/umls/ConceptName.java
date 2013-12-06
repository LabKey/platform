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
* Time: 10:46:07 AM
*/
public class ConceptName  // MRCONSO
{
    final String _type="CONSO";
    public String CUI;
    String LAT;
    String TS;
    String LUI;
    String STT;
    String SUI;
    public String ISPREF;  // Y,N
    String AUI;
    String SAUI;
    String SCUI;
    String SDUI;
    public String SAB;
    String TTY;
    String CODE;
    public String STR;
    String SRL;
    String SUPPRESS;
    String CVF;

    ConceptName()
    {
    }

    public ConceptName(Map<String,Object> map)
    {
        apply(map);
    }

    void apply(Map<String,Object> map)
    {
        CUI=getString(map,"cui");
        LAT=getString(map,"lat");
        TS=getString(map,"ts");
        LUI=getString(map,"lui");
        STT=getString(map,"stt");
        SUI=getString(map,"sui");
        ISPREF=getString(map,"ispref");
        AUI=getString(map,"aui");
        SAUI=getString(map,"saui");
        SCUI=getString(map,"scui");
        SDUI=getString(map,"sdui");
        SAB=getString(map,"sab");
        TTY=getString(map,"tty");
        CODE=getString(map,"code");
        STR=getString(map,"str");
        SRL=getString(map,"srl");
        SUPPRESS=getString(map,"suppress");
        CVF=getString(map,"cvf");
    }

    public ConceptName(String[] args)
    {
        try
        {
            int i=0;
            CUI=args[i++];
            LAT=args[i++];
            TS=args[i++];
            LUI=args[i++];
            STT=args[i++];
            SUI=args[i++];
            ISPREF=args[i++];
            AUI=args[i++];
            SAUI=args[i++];
            SCUI=args[i++];
            SDUI=args[i++];
            SAB=args[i++];
            TTY=args[i++];
            CODE=args[i++];
            STR=args[i++];
            if (i==args.length) return;
            SRL=args[i++];
            if (i==args.length) return;
            SUPPRESS=args[i++];
            if (i==args.length) return;
            CVF=args[i++];
        }
        catch (ArrayIndexOutOfBoundsException x)
        {
        }
    }

    @Override
    public String toString()
    {
        return _type + ": " + CUI + " " + STR;
    }

    private String getString(Map map, String key)
    {
        Object v = map.get(key);
        return null==v ? null : v.toString();
    }

    static
    {
        ObjectFactory.Registry.register(ConceptName.class, new BeanObjectFactory<ConceptName>()
        {
            @Override
            public ConceptName fromMap(Map<String, ?> m)
            {
                return new ConceptName((Map)m);
            }

            @Override
            public ConceptName fromMap(ConceptName bean, Map<String, ?> m)
            {
                bean.apply((Map)m);
                return bean;
            }

            @Override
            public ArrayList<ConceptName> handleArrayList(ResultSet rs)
            {
                return new ResultSetSelector(UmlsSchema.getScope(), rs).getArrayList(ConceptName.class);
            }

            @Override
            public ConceptName[] handleArray(ResultSet rs) throws SQLException
            {
                return new ResultSetSelector(UmlsSchema.getScope(), rs).getArray(ConceptName.class);
            }
        });
    }
}
