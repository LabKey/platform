package org.labkey.api.ontology;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Path;

public interface ConceptPath
{
    OntologyProvider getProvider();

    Path getPath();               // short version

    @Nullable String getCode();

    Concept getConcept();
}
