package org.labkey.api.exp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.labkey.api.gwt.client.model.GWTDomain;

import java.util.Map;

public class DomainKindDesign<T>
{
    private String _domainKindName;
    private GWTDomain _domainDesign;
    private T _options; //domain kind specific properties, this should be the same <T> object as DomainKind<T> implementations.
    private String[] _previewNames;

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

    public void setOptions(T options)
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

    public String[] getPreviewNames()
    {
        return _previewNames;
    }

    public void setPreviewNames(String[] previewNames)
    {
        _previewNames = previewNames;
    }

}
