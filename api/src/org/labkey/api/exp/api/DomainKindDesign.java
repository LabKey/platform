package org.labkey.api.exp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.labkey.api.gwt.client.model.GWTDomain;

import java.util.Map;

public class DomainKindDesign
{
    private String _domainKindName;
    private GWTDomain _domainDesign;
    private DomainKindProperties _options; //domain kind specific properties

    public GWTDomain getDomainDesign()
    {
        return _domainDesign;
    }

    public void setDomainDesign(GWTDomain domainDesign)
    {
        _domainDesign = domainDesign;
    }

    public Map<String, Object> getOptions()
    {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(_options, Map.class);
    }

    public void setOptions(DomainKindProperties options)
    {
        _options = options;
    }

    public String getDomainKindName()
    {
        return _domainKindName;
    }

    public void setDomainKindName(String domainKindName)
    {
        _domainKindName = domainKindName;
    }
}
