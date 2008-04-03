package org.labkey.study.chart.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 10, 2007
 */
public class GWTPair implements IsSerializable
{
    private String _key;
    private String _value;

    public GWTPair(){}
    public GWTPair(String key, String value)
    {
        _key = key;
        _value = value;
    }

    public String getKey()
    {
        return _key;
    }

    public void setKey(String key)
    {
        _key = key;
    }

    public String getValue()
    {
        return _value;
    }

    public void setValue(String value)
    {
        _value = value;
    }
}
