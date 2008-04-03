package org.labkey.api.security;

import java.security.Principal;
import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Sep 20, 2006
 * Time: 1:28:26 PM
 */
public abstract class UserPrincipal implements Principal, Serializable
{
    private String _name;
    private int _userId = 0;

    public static final String typeProject = "g";
    public static final String typeModule = "m";
    public static final String typeUser = "u";

    private String _type;

    protected UserPrincipal(String type)
    {
        _type = type;
    }

    protected UserPrincipal(String name, int id, String type)
    {
        this(type);
        _name = name;
        _userId = id;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public int getUserId()
    {
        return _userId;
    }

    public void setUserId(int userId)
    {
        _userId = userId;
    }

    public String getType()
    {
        return _type;
    }

    protected void setType(String type)
    {
        if (type.length() != 1 || !"gum".contains(type))
            throw new IllegalArgumentException("Unrecognized type specified.  Must be one of 'u', 'g', or 'm'.");
        _type = type;
    }
}