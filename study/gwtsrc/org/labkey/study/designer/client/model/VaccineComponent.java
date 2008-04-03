package org.labkey.study.designer.client.model;

import org.labkey.api.gwt.client.util.StringUtils;
import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Dec 17, 2006
 * Time: 11:20:36 PM
 */
public abstract class VaccineComponent implements IsSerializable
{
    private String name;
    private String dose;

    public String toString()
    {
        return StringUtils.trimToEmpty(name) + "(" + StringUtils.trimToEmpty(dose) + ")";
    }

    public boolean equals(Object o)
    {
        if (null == o)
            return false;

        VaccineComponent vc = (VaccineComponent) o;
        return StringUtils.equals(name, vc.name) && StringUtils.equals(dose, vc.dose);
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDose()
    {
        return dose;
    }

    public void setDose(String dose)
    {
        this.dose = dose;
    }
}
