/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Assume;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.settings.AppProps;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class JunitUtil
{
    public static String getAttributeValue(Node elem, String attr)
    {
        Node node = elem.getAttributes().getNamedItem(attr);
        if (null == node)
            return null;
        return node.getNodeValue();
    }


    public static Document tidyAsDocument(String html) throws Exception
    {
        ArrayList<String> errors = new ArrayList<>();
        String tidy = TidyUtil.convertHtmlToXml(html, errors);

        DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
        fact.setValidating(false);
        fact.setCoalescing(true);
        fact.setIgnoringComments(true);
        fact.setNamespaceAware(false);
        DocumentBuilder builder = fact.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(tidy)));
        return doc;
    }

    /**
     * Returns the container called "_junit" to be used by test cases.
     */
    public static Path getTestContainerPath()
    {
        return ContainerManager.getSharedContainer().getParsedPath().append("_junit", true);
    }

    public static Container getTestContainer()
    {
        return ContainerManager.ensureContainer(getTestContainerPath());
    }

    public static void deleteTestContainer()
    {
        Container junit = ContainerManager.getForPath(getTestContainerPath());

        // Could be null, if junit tests have never been run before
        if (null != junit)
            ContainerManager.deleteAll(junit, TestContext.get().getUser());
    }

    /**
     * Simulate race conditions by invoking a runnable in parallel on the specified number of threads (using a CyclicBarrier
     * to synchronize the start of execution) and waiting until all runnables complete, and then repeating that process for
     * the specified number of races. When all races are complete, shutdown the thread pool and wait for termination.
     *
     * @param runnable Task to run
     * @param threads Number of threads to use in parallel during each race
     * @param races Number of successive races to invoke
     * @param timeoutSeconds Maximum allowed timeout while awaiting termination
     */
    public static void createRaces(final Runnable runnable, final int threads, final int races, final int timeoutSeconds) throws Throwable
    {
        final CyclicBarrier barrier = new CyclicBarrier(threads);
        final AtomicInteger iterations = new AtomicInteger(0);
        final AtomicBoolean failFast = new AtomicBoolean(false);

        Runnable runnableWrapper = () -> {
            for (int i = 0; i < races; i++)
            {
                try
                {
                    if (failFast.get())
                        return;

                    barrier.await();
                }
                catch (InterruptedException | BrokenBarrierException e)
                {
                    return;
                }

                try
                {
                    runnable.run();
                }
                catch (Throwable t)
                {
                    // Game over if any runnable throws... shut everybody down, otherwise the other threads will hang
                    // waiting for a barrier condition that can never be met.
                    failFast.set(true);
                    barrier.reset();
                    throw t;
                }

                iterations.incrementAndGet();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Collection<Future> futures = new LinkedList<>();

        for (int i = 0; i < threads; i++)
            futures.add(pool.submit(runnableWrapper));

        pool.shutdown();
        pool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);

        for (Future future : futures)
        {
            try
            {
                if (future.isDone())
                    future.get();
                else
                    future.cancel(true);
            }
            catch (ExecutionException e)
            {
                throw e.getCause();
            }
        }

        final int expected = threads * races;

        if (iterations.intValue() != expected)
            throw new IllegalStateException("Did not execute runnable the expected number of times: " + iterations + " vs. " + expected);
    }


    private static final java.nio.file.Path SAMPLE_DATA_PATH = Paths.get("test", "sampledata");
    private static final Object MAP_LOCK = new Object();

    private static Map<String, File> _sampleDataDirectories = null;

    /**
     * Retrieves sample data files from the specified module or, if no module is specified, the testAutomation data
     * directory. Similar to TestFileUtils.getSampleData() used in Selenium tests.
     *
     * @param module Module that contains the sample data. If null, testAutomation repository will be searched.
     * @param relativePath e.g. "lists/ListDemo.lists.zip" or "OConnor_Test.folder.zip"
     * @return File object to the specified file or directory, if it exists on this server.
     * @throws AssertionError in dev mode if file is not found.
     * @throws org.junit.AssumptionViolatedException in production mode if file is not found.
     */
    public static File getSampleData(@Nullable Module module, String relativePath) throws IOException
    {
        String projectRoot = AppProps.getInstance().getProjectRoot();

        if (AppProps.getInstance().isDevMode())
            Assert.assertNotNull("Project root not found. Can't load sample data.", projectRoot);
        else
            Assume.assumeNotNull("Project root not found. Skipping test in production mode.", projectRoot);

        final File sampleDataDir;

        if (null == module)
        {
            sampleDataDir = new File(projectRoot, "server/testAutomation/data");
        }
        else
        {
            String sourcePath = StringUtils.trimToNull(module.getSourcePath());

            if (null != sourcePath)
            {
                sampleDataDir = new File(sourcePath, "test/sampledata");
            }
            else
            {
                // Modules might have null sourcePath on TeamCity, so crawl for test/sampledata directories and populate
                // a map the first time, then stash the map for future lookups.
                String name = module.getExplodedPath().getName();

                synchronized (MAP_LOCK)
                {
                    if (null == _sampleDataDirectories)
                    {
                        Map<String, File> map = new CaseInsensitiveHashMap<>();

                        java.nio.file.Path path = Paths.get("server", "modules");
                        Files.walk(Paths.get(projectRoot).resolve(path), 2)
                            .filter(Files::isDirectory)
                            .map(p -> p.resolve(SAMPLE_DATA_PATH))
                            .filter(p -> Files.isDirectory(p))
                            .forEach(p -> map.put(p.getName(p.getNameCount() - 3).toString(), p.toFile()));

                        _sampleDataDirectories = Collections.unmodifiableMap(map);
                    }

                    sampleDataDir = _sampleDataDirectories.get(name);
                }
            }
        }

        File file = new File(sampleDataDir, relativePath);

        String message = null;
        if (null != module)
        {
            if (sampleDataDir == null || !sampleDataDir.exists())
            {
                message = "Sample data directory not found for [" + module.getName() + "] module.";
            }
        }
        if (!file.exists())
        {
            message = "No sample data found at [" + file.getAbsolutePath() + "].";
        }

        if (AppProps.getInstance().isDevMode())
            Assert.assertTrue(message, message != null);
        else
            Assume.assumeTrue(message + " Skipping test in production mode.", message != null);

        return file;
    }
}
