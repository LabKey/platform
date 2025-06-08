package org.labkey.api.ontology;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Path;

import java.util.HashMap;
import java.util.Map;


public interface ConceptPath
{
    @JsonIgnore
    OntologyProvider getProvider();

    Path getPath();               // short version

    @Nullable String getCode();

    @JsonIgnore
    Concept getConcept();

    boolean hasChildren();

    String getLabel();

    default Map<String, Object> toJSONMap()
    {
        Map<String, Object> valMap = new HashMap<>();
        valMap.put("path", this.getPath().toString());
        valMap.put("label", this.getLabel());
        valMap.put("code", this.getCode());
        valMap.put("hasChildren", this.hasChildren());
        return valMap;
    }
}
