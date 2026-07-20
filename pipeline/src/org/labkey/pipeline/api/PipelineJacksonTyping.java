/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.pipeline.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.settings.AppProps;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deny-by-default type allowlist for deserializing pipeline jobs off the JMS {@code job.queue} channel.
 *
 * <p>{@link PipelineJob#createObjectMapper()} enables Jackson polymorphic default typing ({@code NON_FINAL},
 * {@code WRAPPER_ARRAY}) so a job's concrete subclass and its field graph round-trip through {@code @class}-style type
 * ids on the wire. Serialization is unaffected by any validator, but on <b>deserialization</b> an unrestricted default
 * typer lets the wire pick the instantiated class — the JSON twin of an XStream {@code AnyTypePermission} gadget-chain
 * RCE primitive (see {@link org.labkey.pipeline.mule.transformers.PipelineXStreamSecurity}, which locks down the
 * sibling {@code status.queue} channel).
 *
 * <p>A single {@code org.labkey.} prefix covers every {@link PipelineJob} subclass and LabKey domain object in the graph;
 * the remaining allowlist entries are the JDK collection/scalar and known-safe library packages that appear as job
 * fields. The final scalar value types ({@code String}, {@code Integer}, {@code Long}, {@code Double}, {@code Boolean},
 * {@code Short}, {@code Byte}, {@code Float}, {@code Character}) are pinned individually in {@code ALLOWED_EXACT}: some
 * carry a {@code java.lang.*} type id even as a bare value, and the rest surface a {@code [Ljava.lang.X;} id once boxed
 * in an {@code Object[]}. They are listed exactly rather than via a {@code java.lang.} prefix that would also re-admit
 * gadget classes like {@code Runtime}.
 */
public final class PipelineJacksonTyping
{
    private PipelineJacksonTyping()
    {
    }

    /**
     * Deprecated feature flag that reverts both pipeline deserialization channels to their historical unrestricted
     * behavior: the JSON job allowlist here, and the XStream status-channel allowlist in {@code PipelineXStreamSecurity}.
     * Off by default (i.e. the allowlists are enforced). Acts as a workaround if an allowlist rejects a legitimate type.
     */
    public static final String FEATUREFLAG_DISABLE_JOB_TYPE_ALLOWLIST = "PipelineJobDisableTypeAllowlist";

    // Package prefixes whose subtypes may be instantiated during job deserialization.
    private static final List<String> ALLOWED_PREFIXES = List.of(
            "org.labkey.",                 // every PipelineJob subclass and LabKey domain object (module convention)
            "java.util.",                  // HashMap, ArrayList, HashSet, LinkedHashMap, ... (container types)
            "java.sql.",                   // Timestamp, Time, Date (non-final; tagged under NON_FINAL)
            "java.time.",                  // java.time value types
            "java.math.",                  // BigInteger, BigDecimal
            "org.json.",                   // JsonOrgModule types registered on the base mapper
            "it.unimi.dsi.fastutil."       // fastutil collections used by the sequence-analysis job family
    );

    // Exact JDK / third-party class names that carry a type id but whose package prefix must stay off the allowlist.
    // Two sources. (1) Base classes pinned by the custom serializers registered in PipelineJob.createObjectMapper():
    // FileSerialization, PathSerialization and URISerialization call typeSer.typeId(value, File.class/Path.class/URI.class,
    // ...) and CronExpressionSerialization emits the runtime org.quartz.CronExpression, always as the base class (never a
    // subtype). (2) Final scalar value types that NON_FINAL default typing tags with a java.lang.* id: Long/Short/Byte/
    // Float/Character get one even as a bare value (they aren't the natural binding for their JSON token), while
    // String/Integer/Double/Boolean travel untyped as scalars but still surface a [Ljava.lang.X; id when boxed in an
    // Object[] (elementType strips the array to the component). None can be a gadget, so an exact allow is safe; a broad
    // java.lang. prefix is deliberately avoided so it can never re-admit Runtime/Process/etc.
    private static final Set<String> ALLOWED_EXACT = Set.of(
            "java.io.File",
            "java.nio.file.Path",
            "java.net.URI",
            "org.quartz.CronExpression",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Float",
            "java.lang.Character",
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Double",
            "java.lang.Boolean"
    );

    // Explicit denials, checked before the allowlist. None overlap ALLOWED_PREFIXES today, so this is belt-and-suspenders
    // documenting intent and guarding against a future widening of the allowlist (e.g. a broad java.lang. entry).
    private static final List<String> DENIED_PREFIXES = List.of(
            "java.lang.Runtime",
            "java.lang.Process",           // Process, ProcessBuilder, ProcessImpl
            "javax.naming.",               // JNDI (LDAP/RMI lookup gadgets)
            "javax.script.",
            "javax.management.",
            "java.rmi.",
            "com.sun.",
            "sun.",
            "org.springframework.",
            "org.apache.xalan.",
            "org.apache.commons.collections.functors.",
            "org.apache.commons.collections4.functors.",
            "org.apache.commons.beanutils.",
            "org.codehaus.groovy.runtime.",
            "bsh.",
            "clojure."
    );

    private static final PolymorphicTypeValidator VALIDATOR = new AllowlistValidator();

    public static boolean isEnforced()
    {
        return !AppProps.getInstance().isOptionalFeatureEnabled(FEATUREFLAG_DISABLE_JOB_TYPE_ALLOWLIST);
    }

    /**
     * The deny-by-default validator; always enforcing, regardless of the escape-hatch flag.
     */
    public static PolymorphicTypeValidator validator()
    {
        return VALIDATOR;
    }

    /**
     * Build the ObjectMapper used to deserialize pipeline jobs. Applies {@link #validator()} unless the deprecated
     * escape-hatch flag has been set, in which case it falls back to the historical unrestricted default typing.
     */
    public static ObjectMapper createJobDeserializationMapper()
    {
        return PipelineJob.createObjectMapper(isEnforced() ? VALIDATOR : null);
    }

    private static PolymorphicTypeValidator.Validity classify(String className)
    {
        String element = elementType(className);
        if (element.isEmpty())
            return PolymorphicTypeValidator.Validity.ALLOWED; // primitive or primitive[]

        for (String denied : DENIED_PREFIXES)
        {
            if (element.startsWith(denied))
                return PolymorphicTypeValidator.Validity.DENIED;
        }
        if (ALLOWED_EXACT.contains(element))
            return PolymorphicTypeValidator.Validity.ALLOWED;
        for (String allowed : ALLOWED_PREFIXES)
        {
            if (element.startsWith(allowed))
                return PolymorphicTypeValidator.Validity.ALLOWED;
        }
        return PolymorphicTypeValidator.Validity.DENIED;
    }

    // Strip array markers so an array type is judged by its element type. Returns "" for a primitive (or primitive
    // array) element, which is always allowed.
    private static String elementType(String className)
    {
        int depth = 0;
        while (depth < className.length() && className.charAt(depth) == '[')
            depth++;
        if (depth == 0)
            return className;
        String rest = className.substring(depth);
        if (rest.startsWith("L") && rest.endsWith(";"))
            return rest.substring(1, rest.length() - 1);
        return ""; // primitive array element descriptor (e.g. "[I")
    }

    private static class AllowlistValidator extends PolymorphicTypeValidator.Base
    {
        // validateBaseType is left at the inherited INDETERMINATE so that per-subtype checks always run — returning
        // ALLOWED here would accept every subtype of a base type without inspection.

        @Override
        public Validity validateSubClassName(MapperConfig<?> config, JavaType baseType, String subClassName)
        {
            return classify(subClassName);
        }

        @Override
        public Validity validateSubType(MapperConfig<?> config, JavaType baseType, JavaType subType)
        {
            Class<?> raw = subType.getRawClass();
            return raw == null ? Validity.DENIED : classify(raw.getName());
        }
    }

    public static class TestCase extends Assert
    {
        public static class Holder
        {
            public Object value;
        }

        @Test
        public void allowsExpectedJobTypes() throws Exception
        {
            // The Holder root (org.labkey.*), the HashMap/ArrayList (java.util.*), and the java.sql.Timestamp value all
            // carry type ids and must round-trip through the enforcing mapper without a ForbiddenClass-style rejection.
            // Long/Float/Character are final too but, unlike String/Integer/Double, NON_FINAL default typing still tags
            // them with a java.lang.* type id in an Object-typed slot, both as a direct value and as a collection element.
            ObjectMapper writeMapper = PipelineJob.createObjectMapper();
            ObjectMapper secureMapper = PipelineJob.createObjectMapper(validator());

            Map<String, Object> map = new HashMap<>();
            map.put("string", "hello");
            map.put("number", 42);
            map.put("long", 42L);
            map.put("float", 3.14f);
            map.put("char", 'x');
            map.put("timestamp", new Timestamp(1400938833L));
            map.put("list", new ArrayList<>(List.of("a", "b")));
            map.put("scalarList", new ArrayList<>(List.of(42L, 3.14f, 'x')));
            // An Object[] carries a [Ljava.lang.X; type id even for scalars that travel untyped as bare values, so
            // String[]/Integer[] exercise the component-type allow that a direct String/Integer value does not.
            map.put("stringArray", new String[]{"a", "b"});
            map.put("intArray", new Integer[]{1, 2});
            Holder holder = new Holder();
            holder.value = map;

            String json = writeMapper.writeValueAsString(holder);
            Holder result = secureMapper.readValue(json, Holder.class);

            assertTrue("Expected the map value to survive", result.value instanceof Map);
            Map<?, ?> resultMap = (Map<?, ?>) result.value;
            assertTrue("Expected the Timestamp to survive", resultMap.get("timestamp") instanceof Timestamp);
            assertTrue("Expected the Long to survive", resultMap.get("long") instanceof Long);
            assertTrue("Expected the Float to survive", resultMap.get("float") instanceof Float);
            assertTrue("Expected the Character to survive", resultMap.get("char") instanceof Character);

            List<?> scalarList = (List<?>) resultMap.get("scalarList");
            assertTrue("Expected the boxed Long element to survive", scalarList.get(0) instanceof Long);
            assertTrue("Expected the boxed Float element to survive", scalarList.get(1) instanceof Float);
            assertTrue("Expected the boxed Character element to survive", scalarList.get(2) instanceof Character);

            assertTrue("Expected the String[] to survive", resultMap.get("stringArray") instanceof String[]);
            assertTrue("Expected the Integer[] to survive", resultMap.get("intArray") instanceof Integer[]);
        }

        @Test
        public void rejectsGadgetType()
        {
            // A denied JDK gadget must be refused during deserialization before the class is resolved/instantiated.
            ObjectMapper secureMapper = PipelineJob.createObjectMapper(validator());
            String payload = "[\"java.lang.ProcessBuilder\",{\"command\":[\"/bin/sh\"]}]";
            try
            {
                secureMapper.readValue(payload, Object.class);
                fail("Expected java.lang.ProcessBuilder to be rejected by the type allowlist");
            }
            catch (JsonProcessingException expected)
            {
                // expected: PolymorphicTypeValidator denied the subtype
            }
        }

        @Test
        public void rejectsUnlistedType()
        {
            // Deny-by-default: even a benign class outside the allowlist (java.awt.Point) is refused.
            ObjectMapper secureMapper = PipelineJob.createObjectMapper(validator());
            String payload = "[\"java.awt.Point\",{\"x\":0,\"y\":0}]";
            try
            {
                secureMapper.readValue(payload, Object.class);
                fail("Expected java.awt.Point to be rejected by the deny-by-default allowlist");
            }
            catch (JsonProcessingException expected)
            {
                // expected
            }
        }

        @Test
        public void allowsFrameworkPinnedTypeIds() throws JsonMappingException
        {
            // The custom serializers in PipelineJob.createObjectMapper() (File/Path/URI/CronExpression) pin these exact
            // type ids on the wire. If the allowlist doesn't permit them, every job carrying such a field fails to
            // deserialize. Assert at the validator level so the check is independent of PipelineJobService/server state.
            PolymorphicTypeValidator v = validator();
            for (String cn : new String[]{"java.io.File", "java.nio.file.Path", "java.net.URI", "org.quartz.CronExpression"})
            {
                assertEquals(cn + " must be allowed (pinned by a registered custom serializer)",
                        PolymorphicTypeValidator.Validity.ALLOWED, v.validateSubClassName(null, null, cn));
            }
            // A gadget must still be denied, and deny takes precedence over any allow.
            assertEquals(PolymorphicTypeValidator.Validity.DENIED, v.validateSubClassName(null, null, "java.lang.ProcessBuilder"));
        }
    }
}
