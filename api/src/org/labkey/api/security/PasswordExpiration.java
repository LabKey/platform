package org.labkey.api.security;

/**
 * User: adam
 * Date: Jan 14, 2010
 * Time: 1:33:38 PM
 */
public enum PasswordExpiration
{
    Never(null, "Never"), ThreeMonths(3, "Every three months"), SixMonths(6, "Every six months"), OneYear(12, "Every 12 months");

    private final Integer _months;
    private final String _description;

    private PasswordExpiration(Integer months, String description)
    {
        _months = months;
        _description = description;
    }

    public Integer getMonths()
    {
        return _months;
    }

    public String getDescription()
    {
        return _description;
    }
}
