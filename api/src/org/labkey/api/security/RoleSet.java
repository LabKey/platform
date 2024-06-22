package org.labkey.api.security;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.impersonation.RoleImpersonationContextFactory;
import org.labkey.api.security.roles.CanSeeAuditLogRole;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.ProjectCreatorRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
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

    // Test Jackson serialization/deserialization of a RoleSet and a role-impersonating user
    public static class TestCase extends Assert
    {
        @Test
        public void testSerialization() throws JsonProcessingException
        {
            User adminUser = TestContext.get().getUser();

            testImpersonateRoles(adminUser, null, ProjectCreatorRole.class, CanSeeAuditLogRole.class);
            testImpersonateRoles(adminUser, JunitUtil.getTestContainer().getProject(), ReaderRole.class, EditorRole.class);
        }

        @SafeVarargs
        private void testImpersonateRoles(User adminUser, @Nullable Container project, Class<? extends Role>... roleClasses) throws JsonProcessingException
        {
            User impersonatingUser = adminUser.cloneUser();
            Set<Role> roles = Arrays.stream(roleClasses).map(RoleManager::getRole).collect(Collectors.toSet());

            // Round-trip via the pipeline ObjectMapper, which serializes based on fields, not getters() (in contrast,
            // our standard ObjectMapper's deserialization chokes because of User's *many* getters)
            ObjectMapper mapper = PipelineJob.createObjectMapper();
            ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();

            String json = writer.writeValueAsString(new RoleSet(roles));
            RoleSet reconstitutedRoleSet = mapper.readValue(json, RoleSet.class);
            assertEquals(roles, reconstitutedRoleSet.getRoles());

            RoleImpersonationContextFactory factory = new RoleImpersonationContextFactory(project, adminUser, roles, Collections.emptySet(), null);
            impersonatingUser.setImpersonationContext(factory.getImpersonationContext());

            if (null == project)
                assertEquals(roles, impersonatingUser.getSiteRoles().collect(Collectors.toSet()));
            else
                assertEquals(roles, impersonatingUser.getAssignedRoles(project).collect(Collectors.toSet()));

            json = writer.writeValueAsString(impersonatingUser);
            User reconstitutedUser = mapper.readValue(json, User.class);

            if (null == project)
                assertEquals(roles, reconstitutedUser.getSiteRoles().collect(Collectors.toSet()));
            else
                assertEquals(roles, reconstitutedUser.getAssignedRoles(project).collect(Collectors.toSet()));
        }
    }
}
