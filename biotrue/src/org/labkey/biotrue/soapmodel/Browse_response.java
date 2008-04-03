package org.labkey.biotrue.soapmodel;

public class Browse_response
{
    Browse_data data;
    String error;

    public Browse_data getData()
    {
        return data;
    }

    public void setData(Browse_data data)
    {
        this.data = data;
    }

    public String getError()
    {
        return error;
    }

    public void setError(String error)
    {
        this.error = error;
    }
}
