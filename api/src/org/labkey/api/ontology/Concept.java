package org.labkey.api.ontology;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.URLHelper;

public interface Concept
{
    @JsonIgnore
    OntologyProvider getProvider();

    @JsonIgnore
    Ontology getOntology();

    String getCode();

    String getLabel();

    @Nullable String getDescription();

    @Nullable URLHelper getURL();
}
