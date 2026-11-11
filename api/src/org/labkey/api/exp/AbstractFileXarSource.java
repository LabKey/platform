/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.api.exp;

import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.fhcrc.cpas.exp.xml.ExperimentArchiveDocument;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.vfs.FileLike;
import org.labkey.vfs.FileSystemLike;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

/**
 * User: jeckels
 * Date: Dec 2, 2005
 */
public abstract class AbstractFileXarSource extends XarSource
{
    private static final Logger LOG = LogHelper.getLogger(AbstractFileXarSource.class, "XAR data file path validation");

    protected FileLike _xmlFile;

    protected FileLike getXmlFile()
    {
        return _xmlFile;
    }

    public AbstractFileXarSource(PipelineJob job)
    {
        super(job);
    }

    public AbstractFileXarSource(String description, Container container, User user)
    {
        this(description, container, user, null);
    }

    public AbstractFileXarSource(String description, Container container, User user, @Nullable PipelineJob job)
    {
        super(description, container, user, job);
    }

    public AbstractFileXarSource(String description, Container container, User user, @Nullable PipelineJob job, @Nullable Map<String, String> substitutions)
    {
        super(description, container, user, job, substitutions);
    }

    @Override
    public ExperimentArchiveDocument getDocument() throws XmlException, IOException
    {
        InputStream fIn = null;

        try
        {
            NetworkDrive.exists(getXmlFile());
            fIn = getXmlFile().openInputStream();
            return ExperimentArchiveDocument.Factory.parse(fIn, XmlBeansUtil.getDefaultParseOptions());
        }
        finally
        {
            if (fIn != null)
            {
                try
                {
                    fIn.close();
                }
                catch (IOException ignored) {}
            }
        }
    }

    @Override
    @Nullable
    public Path getRootPath()
    {
        return null != getXmlFile()? getXmlFile().toNioPathForRead().getParent(): null;
    }

    @Override
    public boolean shouldIgnoreDataFiles()
    {
        return false;
    }

    @Override
    public String canonicalizeDataFileURL(String dataFileURL)
    {
        dataFileURL = dataFileURL.replace("\\", "/");
        Path xarDirectory = getRootPath();
        Container container = getXarContext().getContainer();
        URI uri = FileUtil.createUri(dataFileURL);
        if (!uri.isAbsolute())
        {
            Path path = xarDirectory.resolve(dataFileURL);
            validatePathAllowed(dataFileURL, xarDirectory, path, container);
            String result = _dataFileURLs.get(FileUtil.getAbsolutePath(container, path));
            if (result != null)
            {
                return result;
            }
            return FileUtil.pathToString(FileUtil.getAbsoluteCaseSensitivePath(container, path.toUri()));
        }
        else
        {
            Path path = FileUtil.getAbsoluteCaseSensitivePath(container, uri);
            if (isDataFilePathRestricted())
            {
                validatePathAllowed(dataFileURL, xarDirectory, path, container);
            }
            else
            {
                // measuring whether the exemption is still earned: validate and record, but keep allowing as before
                try
                {
                    validatePathAllowed(dataFileURL, xarDirectory, path, container);
                }
                catch (InvalidPathException e)
                {
                    LOG.warn("Exempt XAR source referenced a data file outside the roots of {}: {}", container.getPath(), dataFileURL);
                }
            }
            return FileUtil.pathToString(path);
        }
    }

    /** False where the paths come from a pipeline job rather than a user-supplied archive; those files live wherever the job wrote them. */
    protected boolean isDataFilePathRestricted()
    {
        return true;
    }

    /** A data file may only live in the XAR's own directory or under one of the container's roots. */
    private void validatePathAllowed(String dataFileURL, @Nullable Path xarDirectory, @Nullable Path path, Container container)
    {
        if (path == null)
            throw new InvalidPathException(dataFileURL, "Path to data file could not be resolved");

        if (xarDirectory != null && path.normalize().startsWith(xarDirectory.normalize()))
            return;

        if (!FileContentService.isUnderContainerRoot(container, path))
            throw new InvalidPathException(dataFileURL, "Path to data file not allowed");
    }

    public static FileLike getLogFileFor(FileLike f) throws IOException
    {
        FileLike xarDirectory = f.getParent();
        if (!xarDirectory.exists())
        {
            throw new IOException("Xar file parent directory does not exist");
        }

        String xarShortName = f.getName();
        int index = xarShortName.toLowerCase().lastIndexOf(".xml");
        if (index == -1)
        {
            index = xarShortName.toLowerCase().lastIndexOf(".xar");
        }

        if (index != -1)
        {
            xarShortName = xarShortName.substring(0, index);
        }

        return xarDirectory.resolveChild(xarShortName + LOG_FILE_NAME_SUFFIX);
    }

    public static class TestCase extends Assert
    {
        private static final String PROJECT_NAME = "XarDataFilePathTestProject";
        private static final String TYPED_ROOT = "XarDataFilePathTestRootType";

        private static Container _project;
        private static PipeRoot _pipeRoot;

        @BeforeClass
        public static void setUp()
        {
            cleanUp();
            _project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, TestContext.get().getUser());
            _pipeRoot = PipelineService.get().getPipelineRootSetting(_project);
            assertNotNull("No pipeline root for " + PROJECT_NAME, _pipeRoot);
        }

        @AfterClass
        public static void cleanUp()
        {
            Container c = ContainerManager.getForPath(PROJECT_NAME);
            if (c != null)
                ContainerManager.deleteAll(c, TestContext.get().getUser());
        }

        private XarSource createSource() throws IOException
        {
            Path xarDirectory = _pipeRoot.resolveToNioPath("xarPathTest");
            assertNotNull("Could not resolve a XAR directory under the pipeline root", xarDirectory);
            Files.createDirectories(xarDirectory);

            AbstractFileXarSource source = new AbstractFileXarSource("XAR data file path test", _project, TestContext.get().getUser())
            {
                @Override
                public FileLike getLogFilePath()
                {
                    throw new UnsupportedOperationException();
                }
            };
            source._xmlFile = FileSystemLike.wrapFile(xarDirectory.resolve("test.xar.xml"));
            return source;
        }

        @Test
        public void allowsPathsUnderTheXarDirectory() throws Exception
        {
            XarSource source = createSource();
            assertNotNull(source.getCanonicalDataFileURL("sample.tsv"));
            assertNotNull(source.getCanonicalDataFileURL(source.getRootPath().resolve("sample.tsv").toUri().toString()));
        }

        @Test
        public void allowsAbsolutePathUnderThePipelineRoot() throws Exception
        {
            XarSource source = createSource();
            Path underRoot = _pipeRoot.resolveToNioPath("elsewhere.tsv");
            assertNotNull(underRoot);
            assertNotNull(source.getCanonicalDataFileURL(underRoot.toUri().toString()));
        }

        @Test
        public void rejectsAbsolutePathOutsideTheRoots() throws Exception
        {
            XarSource source = createSource();
            assertThrows(InvalidPathException.class, () -> source.getCanonicalDataFileURL("file:///etc/hosts"));
        }

        @Test
        public void allowsAbsolutePathUnderANonPrimaryPipelineRoot() throws Exception
        {
            java.nio.file.Path typedRoot = Files.createTempDirectory("xarTypedPipelineRoot");
            String dataFileURL = typedRoot.resolve("sequences.fasta").toUri().toString();
            User user = TestContext.get().getUser();
            try
            {
                assertThrows("A path outside every configured root must be rejected",
                        InvalidPathException.class, () -> createSource().getCanonicalDataFileURL(dataFileURL));

                // a module's own root type, e.g. the MS2 FASTA sequence root, is still a root of this container
                PipelineService.get().setPipelineRoot(user, _project, TYPED_ROOT, false, typedRoot.toUri());
                assertNotNull("A path under a configured non-primary pipeline root must be accepted",
                        createSource().getCanonicalDataFileURL(dataFileURL));
            }
            finally
            {
                PipelineService.get().setPipelineRoot(user, _project, TYPED_ROOT, false);
                Files.deleteIfExists(typedRoot);
            }
        }

        @Test
        public void rejectsRelativePathEscapingTheRoots() throws Exception
        {
            XarSource source = createSource();
            assertThrows(InvalidPathException.class, () -> source.getCanonicalDataFileURL("../../../../../../../../etc/hosts"));
        }
    }
}
