package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public class Activity implements Serializable
{
    private final Container _container;
    private final String _name;
    private final String _irb;
    private final String _phiStr;
    private final PHI _phi;
    private final String[] _terms;

    public Activity(Container container, String name, String irb, String phiStr, String... terms)
    {
        _container = container;
        _name = name;
        _irb = irb;
        _phiStr = phiStr;
        _phi = PHI.valueOf(phiStr);
        _terms = terms;
    }

    public Activity(Container container, String name, String irb, @NotNull PHI phi, String... terms)
    {
        _container = container;
        _name = name;
        _irb = irb;
        _phiStr = phi.name();
        _phi = phi;
        _terms = terms;
    }

    public Container getContainer()
    {
        return _container;
    }

    public String getName()
    {
        return _name;
    }

    public String getIRB()
    {
        return _irb;
    }

    public String getPhiStr()
    {
        return _phiStr;
    }

    public PHI getPHI()
    {
        return _phi;
    }

    public String[] getTerms()
    {
        return _terms;
    }
}
