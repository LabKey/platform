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
package org.labkey.pipeline.mule.transformers;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.AnyTypePermission;
import com.thoughtworks.xstream.security.ForbiddenClassException;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.NullPermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;
import com.thoughtworks.xstream.security.TypeHierarchyPermission;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.util.URLHelper;
import org.labkey.pipeline.api.PipelineJacksonTyping;
import org.labkey.pipeline.mule.RequeueLostJobsRequest;
import org.labkey.pipeline.mule.StatusChangeRequest;
import org.labkey.pipeline.mule.StatusRequest;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Locks down the XStream type-permission allowlist for the pipeline JMS status channel, used by the
 * {@link XmlToObject}/{@link ObjectToXml} transformers in the remote/cluster Mule configs.
 * <p>
 * The JMS endpoint is treated as a trusted-internal channel, but we still deny-by-default rather than granting
 * {@code AnyTypePermission.ANY}, which would let any class on the classpath be instantiated during deserialization
 * (a classic XStream gadget-chain RCE primitive). See https://x-stream.github.io/security.html. This is
 * defense-in-depth: only the types that legitimately appear in a {@link StatusRequest} object graph are allowed.
 * <p>
 * Gated by the same deprecated {@link PipelineJacksonTyping#FEATUREFLAG_DISABLE_JOB_TYPE_ALLOWLIST} escape hatch as the
 * JSON job channel: when set, {@link #configure} reverts to {@code AnyTypePermission.ANY}.
 */
public class PipelineXStreamSecurity
{
    private PipelineXStreamSecurity()
    {
    }

    public static void configure(XStream x)
    {
        if (!PipelineJacksonTyping.isEnforced())
        {
            // Escape hatch: the deprecated FEATUREFLAG_DISABLE_JOB_TYPE_ALLOWLIST reverts both pipeline deserialization
            // channels to their historical unrestricted behavior. Reopens the gadget-chain RCE surface; only for
            // temporarily unblocking a legitimate type the allowlist rejects.
            x.addPermission(AnyTypePermission.ANY);
            return;
        }

        // Deny everything, then carve out exactly the types reachable from a StatusRequest. NONE must be added
        // first: XStream evaluates permissions in reverse registration order, so the catch-all deny goes in last.
        x.addPermission(NoTypePermission.NONE);
        x.addPermission(NullPermission.NULL);
        x.addPermission(PrimitiveTypePermission.PRIMITIVES);

        // StatusChangeRequest, RequeueLostJobsRequest, and any future StatusRequest implementations.
        x.addPermission(new TypeHierarchyPermission(StatusRequest.class));

        x.allowTypes(new Class[]{
                String.class,
                TaskId.class,       // StatusChangeRequest._activeTaskId
                TaskId.Type.class,  // TaskId._type
                Class.class,        // TaskId._namespaceClass
        });

        // HashSet serializes as <set> which comes as java.util.Set but then resolves to HashSet, so allow the interface
        x.allowTypes(new Class[]{Set.class});
        x.addPermission(new TypeHierarchyPermission(HashSet.class));
        x.allowTypes(new String[]{"java.util.Collections$SingletonSet"});
    }

    /**
     * Verifies that the allowlist permits every type reachable from a real StatusRequest object graph (so the
     * configuration does not break legitimate JMS status traffic) while still rejecting arbitrary classes.
     */
    public static class TestCase extends Assert
    {
        private static Object roundTrip(Object o)
        {
            XStream x = new XStream();
            configure(x);
            // Serialization is not gated by permissions; deserialization is what the allowlist guards. A missing
            // entry surfaces here as a ForbiddenClassException.
            return x.fromXML(x.toXML(o));
        }

        private static StatusChangeRequest statusChangeRequest(TaskId activeTaskId, String guid)
        {
            PipelineJob job = new PipelineJob()
            {
                @Override
                public URLHelper getStatusHref()
                {
                    return null;
                }

                @Override
                public String getDescription()
                {
                    return "test job";
                }

                @Override
                public String getJobGUID()
                {
                    return guid;
                }

                @Override
                public TaskId getActiveTaskId()
                {
                    return activeTaskId;
                }
            };
            return new StatusChangeRequest(job, "RUNNING", "status info", "remote-host");
        }

        @Test
        public void statusChangeWithNamespaceClassTaskId()
        {
            // TaskId(Class) populates _namespaceClass, exercising the Class.class allowlist entry.
            Object result = roundTrip(statusChangeRequest(new TaskId(PipelineJob.class), "job-guid-1"));
            assertTrue("Expected a StatusChangeRequest", result instanceof StatusChangeRequest);
        }

        @Test
        public void statusChangeWithModuleTaskId()
        {
            // TaskId(module, Type, name, version) populates the Type enum and the double version.
            Object result = roundTrip(statusChangeRequest(new TaskId("myModule", TaskId.Type.task, "myTask", 1.0), "job-guid-2"));
            assertTrue("Expected a StatusChangeRequest", result instanceof StatusChangeRequest);
        }

        @Test
        public void statusChangeWithNullTaskId()
        {
            assertTrue(roundTrip(statusChangeRequest(null, "job-guid-3")) instanceof StatusChangeRequest);
        }

        @Test
        public void requeueWithHashSet()
        {
            RequeueLostJobsRequest req = new RequeueLostJobsRequest(new HashSet<>(List.of("location1")), new HashSet<>(List.of("job1")), "remote-host");
            assertTrue(roundTrip(req) instanceof RequeueLostJobsRequest);
        }

        @Test
        public void requeueWithCaseInsensitiveHashSet()
        {
            // PipelineModule builds the locations set as a CaseInsensitiveHashSet.
            RequeueLostJobsRequest req = new RequeueLostJobsRequest(new CaseInsensitiveHashSet("location1"), new HashSet<>(List.of("job1")), null);
            assertTrue(roundTrip(req) instanceof RequeueLostJobsRequest);
        }

        @Test
        public void requeueWithSingletonSet()
        {
            // RemoteServerStartup builds the locations set with Collections.singleton(...).
            RequeueLostJobsRequest req = new RequeueLostJobsRequest(Collections.singleton("location1"), Collections.singleton("job1"), "remote-host");
            assertTrue(roundTrip(req) instanceof RequeueLostJobsRequest);
        }

        @Test(expected = ForbiddenClassException.class)
        public void rejectsArbitraryType()
        {
            // A class outside the allowlist must be refused during deserialization.
            XStream x = new XStream();
            configure(x);
            x.fromXML("<java.io.File><path>/tmp/evil</path></java.io.File>");
        }
    }
}
