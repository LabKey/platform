package org.labkey.search.umls;

import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.util.ResultSetUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

/**
* Created by IntelliJ IDEA.
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
            public ConceptName fromMap(Map<String, ?> m)
            {
                return new ConceptName((Map)m);
            }
            public ConceptName fromMap(ConceptName bean, Map<String, ?> m)
            {
                bean.apply((Map)m);
                return bean;
            }
            @Override
            public ConceptName[] handleArray(ResultSet rs) throws SQLException
            {
                ArrayList<ConceptName> list = new ArrayList<ConceptName>();
                while (rs.next())
                    list.add(new ConceptName(ResultSetUtil.mapRow(rs)));
                return list.toArray(new ConceptName[list.size()]);
            }
        });
    }
}
