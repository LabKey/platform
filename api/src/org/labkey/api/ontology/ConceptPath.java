package org.labkey.api.ontology;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.MapSerializer;
import com.fasterxml.jackson.databind.ser.std.StringSerializer;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Path;

import java.io.IOException;
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
}
