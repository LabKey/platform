package org.labkey.api.ontology;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.URLHelper;

public interface Concept
{
    OntologyProvider getProvider();

    Ontology getOntology();

    String getCode();

    String getLabel();

    @Nullable String getDescription();

    @Nullable URLHelper getURL();
}
