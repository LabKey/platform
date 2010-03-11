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
            public SemanticType fromMap(Map<String, ?> m)
            {
                return new SemanticType((Map)m);
            }
            public SemanticType fromMap(SemanticType bean, Map<String, ?> m)
            {
                bean.apply((Map)m);
                return bean;
            }
            @Override
            public SemanticType[] handleArray(ResultSet rs) throws SQLException
            {
                ArrayList<SemanticType> list = new ArrayList<SemanticType>();
                while (rs.next())
                    list.add(new SemanticType(ResultSetUtil.mapRow(rs)));
                return list.toArray(new SemanticType[list.size()]);
            }
        });
    }
}
