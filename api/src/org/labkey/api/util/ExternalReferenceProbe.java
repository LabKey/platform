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
package org.labkey.api.util;

import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test helper that plays the attacker-controlled host at the far end of an XML external reference: a recorded request
 * proves the XML machinery resolved that reference over the network, the XXE (CWE-611) behavior {@link XmlBeansUtil}
 * exists to prevent.
 *
 * <p>A real loopback server rather than a {@code ProxySelector}, which is global JVM state visible to every other
 * thread in a running server.
 *
 * <p>The probe only sees references it hosts, so a test proving "nothing external at all" must point every reference
 * in its fixture at {@link #url}. Not thread-safe across tests: start and close one per test.
 *
 * <p>Test-only, but here so that other modules can use it for their own testing.
 */
public class ExternalReferenceProbe implements AutoCloseable
{
    /** A DTD body that is well-formed enough for a parser to accept once it has been fetched. */
    public static final String DTD_BODY = "<!ELEMENT r ANY>";
    /** An entity replacement body that is visible in the parsed document if expansion happened. */
    public static final String ENTITY_BODY = "external-content-was-fetched";

    private final HttpServer _server;
    private final List<String> _contacted = new CopyOnWriteArrayList<>();
    private final Map<String, String> _bodies = new ConcurrentHashMap<>();

    private ExternalReferenceProbe(HttpServer server)
    {
        _server = server;
    }

    public static ExternalReferenceProbe start() throws IOException
    {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ExternalReferenceProbe probe = new ExternalReferenceProbe(server);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            probe._contacted.add(path);
            byte[] body = probe._bodies.getOrDefault(path, "").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody())
            {
                out.write(body);
            }
        });
        server.start();
        return probe;
    }

    /**
     * Register {@code body} at {@code path} and return the absolute URL to embed in the XML under test.
     * @param path must start with "/", e.g. "/external.dtd"
     */
    public String url(@NotNull String path, @NotNull String body)
    {
        _bodies.put(path, body);
        return "http://" + _server.getAddress().getHostString() + ":" + _server.getAddress().getPort() + path;
    }

    /** Paths the XML machinery actually requested, in order. */
    public @NotNull List<String> contactedPaths()
    {
        return List.copyOf(_contacted);
    }

    public boolean wasContacted()
    {
        return !_contacted.isEmpty();
    }

    public void assertNotContacted(String message)
    {
        Assert.assertTrue(message + " -- external reference(s) were resolved over the network: " + _contacted,
            _contacted.isEmpty());
    }

    @Override
    public void close()
    {
        _server.stop(0);
    }
}
