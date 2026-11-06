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
package org.labkey.core.metrics;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.usageMetrics.UsageMetricsProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Reports how the server was deployed (Docker image, quick start, etc.); GH Issue 1769 */
public class DeploymentMetrics
{
    /** Set by LabKey's Docker images */
    public static final String CHANNEL_ENV = "LABKEY_DEPLOYMENT_CHANNEL";
    public static final String UNKNOWN_CHANNEL = "unknown";

    private static final int MAX_LENGTH = 100;
    private static final Path PID1_CGROUP = Path.of("/proc/1/cgroup");
    private static final Pattern CONTAINER_CGROUP = Pattern.compile("/(docker|kubepods|containerd|libpod|lxc)\\b");

    private static final String CHANNEL = getChannel(System::getenv);
    private static final boolean CONTAINER = isContainer(System::getenv, Files::exists, readPid1Cgroup());

    public static UsageMetricsProvider getMetricsProvider()
    {
        return metrics -> metrics.group("deployment", "How the server was deployed: channel (docker, ce-docker, or unknown) and whether it's running in a container")
            .put("channel", () -> CHANNEL)
            .put("container", () -> CONTAINER);
    }

    static @NotNull String getChannel(Function<String, String> env)
    {
        String channel = StringUtils.truncate(StringUtils.trimToNull(env.apply(CHANNEL_ENV)), MAX_LENGTH);
        return channel == null ? UNKNOWN_CHANNEL : channel.toLowerCase();
    }


    /** Independent of the channel, so customer-built images around the distribution still count */
    static boolean isContainer(Function<String, String> env, Predicate<Path> exists, @Nullable String pid1Cgroup)
    {
        return exists.test(Path.of("/.dockerenv"))
            || exists.test(Path.of("/run/.containerenv"))
            // "container" is set by Podman, systemd-nspawn, and LXC
            || Stream.of("KUBERNETES_SERVICE_HOST", "ECS_CONTAINER_METADATA_URI_V4", "container").anyMatch(name -> StringUtils.isNotBlank(env.apply(name)))
            // Only populated under cgroup v1; v2 shows "0::/" inside a container
            || (pid1Cgroup != null && CONTAINER_CGROUP.matcher(pid1Cgroup).find());
    }

    private static @Nullable String readPid1Cgroup()
    {
        try
        {
            return Files.isReadable(PID1_CGROUP) ? Files.readString(PID1_CGROUP) : null;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    public static class TestCase extends Assert
    {
        private static final Predicate<Path> NO_FILES = _ -> false;

        @Test
        public void testChannel()
        {
            assertEquals(UNKNOWN_CHANNEL, getChannel(Map.<String, String>of()::get));
            assertEquals(UNKNOWN_CHANNEL, getChannel(Map.of(CHANNEL_ENV, "  ")::get));
            assertEquals("ce-docker", getChannel(Map.of(CHANNEL_ENV, " CE-Docker ")::get));
            assertEquals(MAX_LENGTH, getChannel(Map.of(CHANNEL_ENV, "x".repeat(500))::get).length());
        }

        @Test
        public void testContainer()
        {
            Function<String, String> noEnv = Map.<String, String>of()::get;
            assertFalse(isContainer(noEnv, NO_FILES, null));
            assertFalse(isContainer(noEnv, NO_FILES, "0::/init.scope\n"));
            assertFalse(isContainer(noEnv, NO_FILES, "12:cpu,cpuacct:/user.slice\n"));

            assertTrue(isContainer(noEnv, path -> path.equals(Path.of("/.dockerenv")), null));
            assertTrue(isContainer(noEnv, path -> path.equals(Path.of("/run/.containerenv")), null));
            assertTrue(isContainer(Map.of("KUBERNETES_SERVICE_HOST", "10.0.0.1")::get, NO_FILES, null));
            assertTrue(isContainer(Map.of("ECS_CONTAINER_METADATA_URI_V4", "http://169.254.170.2/v4/abc")::get, NO_FILES, null));
            assertTrue(isContainer(Map.of("container", "podman")::get, NO_FILES, null));
            assertTrue(isContainer(noEnv, NO_FILES, "12:cpu,cpuacct:/docker/0123abcd\n"));
            assertTrue(isContainer(noEnv, NO_FILES, "11:memory:/kubepods/burstable/pod42/abc\n"));
        }
    }
}
