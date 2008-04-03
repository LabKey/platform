package org.labkey.biotrue.soapmodel;

public class Browse_request
{
    String session_id;
    String mod;
    String op;
    String ent;
    Integer id;

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
}
