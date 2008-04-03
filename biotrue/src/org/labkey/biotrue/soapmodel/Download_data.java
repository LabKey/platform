package org.labkey.biotrue.soapmodel;

public class Download_data
{
    String session_id;
    String mod;
    String op;
    String ent;
    Integer id;
    String parent_ent;
    Integer parent_id;
    String url;

    public String getSession_id()
    {
        return session_id;
    }

    public void setSession_id(String session_id)
    {
        this.session_id = session_id;
    }

    public String getMod()
    {
        return mod;
    }

    public void setMod(String mod)
    {
        this.mod = mod;
    }

    public String getOp()
    {
        return op;
    }

    public void setOp(String op)
    {
        this.op = op;
    }

    public String getEnt()
    {
        return ent;
    }

    public void setEnt(String ent)
    {
        this.ent = ent;
    }

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public String getParent_ent()
    {
        return parent_ent;
    }

    public void setParent_ent(String parent_ent)
    {
        this.parent_ent = parent_ent;
    }

    public Integer getParent_id()
    {
        return parent_id;
    }

    public void setParent_id(Integer parent_id)
    {
        this.parent_id = parent_id;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }
}
