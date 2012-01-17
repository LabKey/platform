package org.labkey.api.security;

/**
* User: adam
* Date: 1/14/12
* Time: 12:10 PM
*/
public enum PrincipalType
{
    USER('u', "User"),
    GROUP('g', "Group"),
    ROLE('r', "Role"),
    MODULE('m', "Module Group");

    private final char _typeChar;
    private final String _description;

    private PrincipalType(char type, String description)
    {
        _typeChar = type;
        _description = description;
    }

    public char getTypeChar()
    {
        return _typeChar;
    }

    public String getDescription()
    {
        return _description;
    }

    public static PrincipalType forChar(char type)
    {
        switch (type)
        {
            case 'u': return USER;
            case 'g': return GROUP;
            case 'r': return ROLE;
            case 'm': return MODULE;
            default : return null;
        }
    }
}
