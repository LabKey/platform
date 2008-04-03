package org.labkey.biotrue.soapmodel;

public class Download_response
{
    Download_data data;
    String error;

    public Download_data getData()
    {
        return data;
    }

    public void setData(Download_data data)
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
