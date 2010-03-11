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
            public Related fromMap(Map<String, ?> m)
            {
                return new Related((Map)m);
            }
            public Related fromMap(Related bean, Map<String, ?> m)
            {
                bean.apply((Map)m);
                return bean;
            }
            @Override
            public Related[] handleArray(ResultSet rs) throws SQLException
            {
                ArrayList<Related> list = new ArrayList<Related>();
                while (rs.next())
                    list.add(new Related(ResultSetUtil.mapRow(rs)));
                return list.toArray(new Related[list.size()]);
            }
        });
    }
}
