package org.labkey.api.exp.api;

import java.util.Map;

/* Default class for domain kind options or properties
 */
public class DomainKindOptions implements DomainKindProperties
{
    Map<String, Object> _options;

    public Map<String, Object> getOptions()
    {
        return _options;
    }

    public void setOptions(Map<String, Object> options)
    {
        _options = options;
    }

}