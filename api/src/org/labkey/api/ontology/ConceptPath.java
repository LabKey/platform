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


@JsonSerialize(using = ConceptPath.ConceptPathSerializer.class)
public interface ConceptPath
{
    @JsonIgnore
    OntologyProvider getProvider();

    Path getPath();               // short version

    @Nullable String getCode();

    @JsonIgnore
    Concept getConcept();

    class ConceptPathSerializer extends JsonSerializer<ConceptPath>
    {
        @Override
        public void serialize(ConceptPath value, JsonGenerator gen, SerializerProvider serializers) throws IOException
        {
            gen.writeStartObject();
            gen.writeStringField("path", value.getPath().toString());
            gen.writeStringField("code", value.getCode());

            Concept concept = value.getConcept();
            if (concept != null)
            {
                gen.writeStringField("label", concept.getLabel());
                gen.writeBooleanField("hasChildren", concept.hasChildren());
            }
            gen.writeEndObject();
        }
    }
}
