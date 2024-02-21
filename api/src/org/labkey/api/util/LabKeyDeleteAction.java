/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.util;

import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.appender.rolling.action.AbstractPathAction;
import org.apache.logging.log4j.core.appender.rolling.action.DeletingVisitor;
import org.apache.logging.log4j.core.appender.rolling.action.PathCondition;
import org.apache.logging.log4j.core.appender.rolling.action.PathSortByModificationTime;
import org.apache.logging.log4j.core.appender.rolling.action.PathSorter;
import org.apache.logging.log4j.core.appender.rolling.action.PathWithAttributes;
import org.apache.logging.log4j.core.appender.rolling.action.SortingVisitor;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;

import java.io.IOException;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A modified version of log4j2 DeleteAction that doesn't require any conditions; files to delete are determined
 * by Java code.
 */
@Plugin(name = "LabKeyDelete", category = Core.CATEGORY_NAME, printObject = true)
public class LabKeyDeleteAction extends AbstractPathAction
{
    private final PathSorter pathSorter;
    private final boolean testMode;

    /**
     * Creates a new DeleteAction that starts scanning for files to delete from the specified base path.
     *
     * @param basePath base path from where to start scanning for files to delete.
     * @param followSymbolicLinks whether to follow symbolic links. Default is false.
     * @param maxDepth The maxDepth parameter is the maximum number of levels of directories to visit. A value of 0
     *            means that only the starting file is visited, unless denied by the security manager. A value of
     *            MAX_VALUE may be used to indicate that all levels should be visited.
     * @param testMode if true, files are not deleted but instead a message is printed to the <a
     *            href="http://logging.apache.org/log4j/2.x/manual/configuration.html#StatusMessages">status logger</a>
     *            at INFO level. Users can use this to do a dry run to test if their configuration works as expected.
     * @param sorter sorts
     * @param pathConditions an array of path filters (if more than one, they all need to accept a path before it is
     *            deleted).
     */
    LabKeyDeleteAction(
            final String basePath,
            final boolean followSymbolicLinks,
            final int maxDepth,
            final boolean testMode,
            final PathSorter sorter,
            final PathCondition[] pathConditions,
            final StrSubstitutor subst) {
        super(basePath, followSymbolicLinks, maxDepth, pathConditions, subst);
        this.testMode = testMode;
        this.pathSorter = Objects.requireNonNull(sorter, "sorter");
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.logging.log4j.core.appender.rolling.action.AbstractPathAction#execute()
     */
    @Override
    public boolean execute() throws IOException {
        return executeDelete();
    }

    private boolean executeDelete() throws IOException {
        final List<PathWithAttributes> selectedForDeletion = selectFiles();
        if (selectedForDeletion == null) {
            LOGGER.trace("Null list returned (no files to delete)");
            return true;
        }
        deleteSelectedFiles(selectedForDeletion);
        return true;
    }

    private List<PathWithAttributes> selectFiles() throws IOException {
        final List<PathWithAttributes> sortedPaths = getSortedPaths();
        trace("Sorted paths:", sortedPaths);
        return selectFilesToDelete(getBasePath(), sortedPaths);
    }

    private List<PathWithAttributes> selectFilesToDelete(Path basePath, List<PathWithAttributes> sortedPaths)
    {
        return List.of(); // TODO: Fill in logic from ErrorLogRotator
    }

    private void deleteSelectedFiles(final List<PathWithAttributes> selectedForDeletion) throws IOException {
        trace("Paths selected for deletion:", selectedForDeletion);
        for (final PathWithAttributes pathWithAttributes : selectedForDeletion) {
            final Path path = pathWithAttributes == null ? null : pathWithAttributes.getPath();
            if (isTestMode()) {
                LOGGER.info("Deleting {} (TEST MODE: file not actually deleted)", path);
            } else {
                delete(path);
            }
        }
    }

    /**
     * Deletes the specified file.
     *
     * @param path the file to delete
     * @throws IOException if a problem occurred deleting the file
     */
    protected void delete(final Path path) throws IOException {
        LOGGER.trace("Deleting {}", path);
        Files.deleteIfExists(path);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.logging.log4j.core.appender.rolling.action.AbstractPathAction#execute(FileVisitor)
     */
    @Override
    public boolean execute(final FileVisitor<Path> visitor) throws IOException {
        final List<PathWithAttributes> sortedPaths = getSortedPaths();
        trace("Sorted paths:", sortedPaths);

        for (final PathWithAttributes element : sortedPaths) {
            try {
                visitor.visitFile(element.getPath(), element.getAttributes());
            } catch (final IOException ioex) {
                LOGGER.error("Error in post-rollover Delete when visiting {}", element.getPath(), ioex);
                visitor.visitFileFailed(element.getPath(), ioex);
            }
        }
        // TODO return (visitor.success || ignoreProcessingFailure)
        return true; // do not abort rollover even if processing failed
    }

    private void trace(final String label, final List<PathWithAttributes> sortedPaths) {
        LOGGER.trace(label);
        for (final PathWithAttributes pathWithAttributes : sortedPaths) {
            LOGGER.trace(pathWithAttributes);
        }
    }

    /**
     * Returns a sorted list of all files up to maxDepth under the basePath.
     *
     * @return a sorted list of files
     * @throws IOException
     */
    List<PathWithAttributes> getSortedPaths() throws IOException {
        final SortingVisitor sort = new SortingVisitor(pathSorter);
        super.execute(sort);
        final List<PathWithAttributes> sortedPaths = sort.getSortedPaths();
        return sortedPaths;
    }

    /**
     * Returns {@code true} if files are not deleted even when all conditions accept a path, {@code false} otherwise.
     *
     * @return {@code true} if files are not deleted even when all conditions accept a path, {@code false} otherwise
     */
    public boolean isTestMode() {
        return testMode;
    }

    @Override
    protected FileVisitor<Path> createFileVisitor(final Path visitorBaseDir, final List<PathCondition> conditions) {
        return new DeletingVisitor(visitorBaseDir, conditions, testMode);
    }

    /**
     * Create a DeleteAction.
     *
     * @param basePath base path from where to start scanning for files to delete.
     * @param followLinks whether to follow symbolic links. Default is false.
     * @param maxDepth The maxDepth parameter is the maximum number of levels of directories to visit. A value of 0
     *            means that only the starting file is visited, unless denied by the security manager. A value of
     *            MAX_VALUE may be used to indicate that all levels should be visited.
     * @param testMode if true, files are not deleted but instead a message is printed to the <a
     *            href="http://logging.apache.org/log4j/2.x/manual/configuration.html#StatusMessages">status logger</a>
     *            at INFO level. Users can use this to do a dry run to test if their configuration works as expected.
     *            Default is false.
     * @param sorterParameter a plugin implementing the {@link PathSorter} interface
     * @param pathConditions an array of path conditions (if more than one, they all need to accept a path before it is
     *            deleted).
     * @param config The Configuration.
     * @return A DeleteAction.
     */
    @PluginFactory
    public static LabKeyDeleteAction createDeleteAction(
            // @formatter:off
            @PluginAttribute("basePath") final String basePath,
            @PluginAttribute(value = "followLinks") final boolean followLinks,
            @PluginAttribute(value = "maxDepth", defaultInt = 1) final int maxDepth,
            @PluginAttribute(value = "testMode") final boolean testMode,
            @PluginElement("PathSorter") final PathSorter sorterParameter,
            @PluginElement("PathConditions") final PathCondition[] pathConditions,
            @PluginConfiguration final Configuration config) {
        // @formatter:on
        final PathSorter sorter = sorterParameter == null ? new PathSortByModificationTime(true) : sorterParameter;
        return new LabKeyDeleteAction(
                basePath,
                followLinks,
                maxDepth,
                testMode,
                sorter,
                pathConditions,
                config.getStrSubstitutor());
    }
}
