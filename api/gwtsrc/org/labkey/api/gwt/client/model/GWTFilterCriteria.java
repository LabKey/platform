package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
@EqualsAndHashCode
public class GWTFilterCriteria implements Serializable, IsSerializable
{
    private String name;
    private String op;
    private Integer propertyId;
    private Integer referencePropertyId;
    private Object value;

    public GWTFilterCriteria()
    {
    }

    public GWTFilterCriteria(GWTFilterCriteria fc)
    {
        setName(fc.getName());
        setOp(fc.getOp());
        setPropertyId(fc.getPropertyId());
        setReferencePropertyId(fc.getReferencePropertyId());
        setValue(fc.getValue());
    }
}

