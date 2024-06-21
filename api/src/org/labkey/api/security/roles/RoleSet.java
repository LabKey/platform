package org.labkey.api.security.roles;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

// Wrapper that holds a set of Roles that need to be serialized/deserialized via JSON. I had hoped to instead create a
// custom serializer/deserializer for our singleton Roles, but Jackson doesn't seem to support this pattern.
@JsonSerialize(using = RoleSet.RoleSetSerializer.class)
@JsonDeserialize(using = RoleSet.RoleSetDeserializer.class)
public class RoleSet
{
    private final Set<Role> _roles;

    public RoleSet(Collection<Role> roles)
    {
        _roles = Set.copyOf(roles);
    }

    public Set<Role> getRoles()
    {
        return _roles;
    }

    public boolean isEmpty()
    {
        return _roles.isEmpty();
    }

    public boolean contains(Role role)
    {
        return _roles.contains(role);
    }

    public Stream<Role> stream()
    {
        return _roles.stream();
    }

    public static class RoleSetSerializer extends StdSerializer<RoleSet>
    {
        @SuppressWarnings("unused")
        public RoleSetSerializer()
        {
            super(RoleSet.class);
        }

        @Override
        public void serialize(RoleSet roleSet, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException
        {
            jsonGenerator.writeFieldName("roles");
            String[] roleNames = roleSet.getRoles().stream().map(Role::getUniqueName).toArray(String[]::new);
            jsonGenerator.writeArray(roleNames, 0, roleNames.length);
        }

        @Override
        public void serializeWithType(RoleSet roleSet, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException
        {
            WritableTypeId typeIdDef = typeSer.writeTypePrefix(gen, typeSer.typeId(roleSet, JsonToken.START_OBJECT));
            serialize(roleSet, gen, serializers);
            typeSer.writeTypeSuffix(gen, typeIdDef);
        }
    }

    public static class RoleSetDeserializer extends StdDeserializer<RoleSet>
    {
        @SuppressWarnings("unused")
        public RoleSetDeserializer()
        {
            super(RoleSet.class);
        }

        @Override
        public RoleSet deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException
        {
            Set<Role> roles = new HashSet<>();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            for (JsonNode jsonNode : node.get("roles"))
            {
                String roleName = jsonNode.textValue();
                Role role = RoleManager.getRole(roleName);
                if (role != null)
                    roles.add(role);
            }
            return new RoleSet(roles);
        }
    }
}
