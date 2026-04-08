/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.experiment;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DotRunner;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.api.ExpRunImpl;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.Cleaner;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// TODO: This entire class can be eliminated once we're happy with the new graph-support Java implementation
public class FileBasedExperimentRunGraph extends ExperimentRunGraph
{
    private static File baseDirectory;
    private static final Logger _log = LogManager.getLogger(FileBasedExperimentRunGraph.class);

    /**
     * It's safe for lots of threads to be reading but only one should be creating or deleting at a time.
     * We could make separate locks for each folder but leaving it with one global lock for now.
     */
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    public synchronized static File getBaseDirectory() throws IOException
    {
        if (baseDirectory == null)
        {
            File tempDir = FileUtil.appendName(FileUtil.getTempDirectory(), "ExperimentRunGraphs");
            if (tempDir.exists())
            {
                FileUtil.deleteDirectoryContents(tempDir);
            }
            else
            {
                if (!FileUtil.mkdirs(tempDir))
                {
                    throw new IOException("Unable to create temporary directory for experiment run graphs: " + tempDir.getPath());
                }
            }
            baseDirectory = tempDir;
        }
        return baseDirectory;
    }

    private synchronized static File getFolderDirectory(Container container) throws IOException
    {
        File result = FileUtil.appendName(getBaseDirectory(), "Folder" + container.getRowId());
        FileUtil.mkdirs(result);
        for (int i = 0; i < 5; i++)
        {
            if (result.isDirectory())
            {
                return result;
            }
            else
            {
                try
                {
                    Thread.sleep(1);
                }
                catch (InterruptedException e) {}
                FileUtil.mkdirs(result);
            }
        }
        if (!result.isDirectory())
        {
            throw new IOException("Failed to create directory " + result);
        }
        return result;
    }

    /**
     * Creates a run graph, given the configuration parameters. Note that this creates a lock on the directory
     * that contains the files, which must be cleared by calling release() on the resulting RunGraphFiles object. 
     */
    public static RunGraphFiles generateRunGraph(ViewContext ctx, ExpRunImpl run, boolean detail, String focus, String focusType) throws ExperimentException, IOException, InterruptedException
    {
        boolean success = false;

        File imageFile = new File(getBaseFileName(run, detail, focus) + ".png");
        File mapFile = new File(getBaseFileName(run, detail, focus) + ".map");

        // First acquire a read lock so we know that another thread won't be deleting these files out from under us
        Lock readLock = LOCK.readLock();
        readLock.lock();

        try
        {
            if (!AppProps.getInstance().isDevMode() && imageFile.exists() && mapFile.exists())
            {
                success = true;
                return new RunGraphFiles(mapFile, imageFile, readLock);
            }
        }
        finally
        {
            // If we found useful files, don't release the lock because the caller will want to read them
            if (!success)
            {
                readLock.unlock();
            }
        }

        // We need to create files to open up a write lock
        Lock writeLock = LOCK.writeLock();
        writeLock.lock();

        try
        {
            testDotPath();

            String dotInput = getDotGraph(ctx.getContainer(), run, detail, focus, focusType);
            DotRunner runner = new DotRunner(getFolderDirectory(run.getContainer()), dotInput);
            runner.addCmapOutput(mapFile);
            runner.addPngOutput(imageFile);
            runner.execute();

            mapFile.deleteOnExit();
            imageFile.deleteOnExit();

            BufferedImage originalImage = ImageIO.read(imageFile);
            if (originalImage == null)
            {
                throw new IOException("Unable to read image file " + imageFile.getAbsolutePath() + " of size " + imageFile.length() + " - disk may be full?");
            }

            try (FileOutputStream fOut = new FileOutputStream(imageFile))
            {
                // Write it back out to disk
                double scale = ImageUtil.resizeImage(originalImage, fOut, .85, 6, BufferedImage.TYPE_INT_RGB);

                // Need to rewrite the image map to change the coordinates according to the scaling factor
                resizeImageMap(mapFile, scale);
            }

            // Start the procedure of downgrade our lock from write to read so that the caller can use the files
            readLock.lock();
            return new RunGraphFiles(mapFile, imageFile, readLock);
        }
        catch (UnsatisfiedLinkError | NoClassDefFoundError e)
        {
            throw new ConfigurationException("Unable to resize image, likely a problem with missing Java Runtime libraries not being available", e);
        }
        finally
        {
            writeLock.unlock();
        }
    }

    /**
     * Shrink all the coordinates in an image map by a fixed ratio
     */
    private static void resizeImageMap(File mapFile, double finalScale) throws IOException
    {
        StringBuilder sb = new StringBuilder();

        try (FileInputStream fIn = new FileInputStream(mapFile))
        {
            // Read in the original file, line by line
            BufferedReader reader = new BufferedReader(new InputStreamReader(fIn));
            String line;
            while ((line = reader.readLine()) != null)
            {
                int coordsIndex = line.indexOf("coords=\"");
                if (coordsIndex != -1)
                {
                    int openIndex = coordsIndex + "coords=\"".length();
                    int closeIndex = line.indexOf("\"", openIndex);
                    if (closeIndex != -1)
                    {
                        // Parse and scale the coordinates
                        String coordsOriginal = line.substring(openIndex, closeIndex);
                        String[] coords = coordsOriginal.split(",|(\\s)");
                        StringBuilder newLine = new StringBuilder();
                        newLine.append(line.substring(0, openIndex));
                        String separator = "";
                        for (String coord : coords)
                        {
                            newLine.append(separator);
                            separator = ",";
                            newLine.append((int) (Integer.parseInt(coord.trim()) * finalScale));
                        }
                        newLine.append(line.substring(closeIndex));
                        line = newLine.toString();
                    }
                }
                sb.append(line);
                sb.append("\n");
            }
        }

        // Write the file back to the disk
        try (FileOutputStream mapOut = new FileOutputStream(mapFile))
        {
            OutputStreamWriter mapWriter = new OutputStreamWriter(mapOut);
            mapWriter.write(sb.toString());
            mapWriter.flush();
        }
    }

    private static class CacheClearer implements Runnable
    {
        private final Container _container;

        public CacheClearer(Container container)
        {
            _container = container;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheClearer that = (CacheClearer) o;
            return Objects.equals(_container, that._container);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(_container);
        }

        @Override
        public void run()
        {
            clearCache(_container);
        }
    }

    public static Runnable getCacheClearingCommitTask(Container c)
    {
        return new CacheClearer(c);
    }

    /**
     * Clears out the cache of files for this container. Must be called after any operation that changes the way a graph
     * would be generated. Typically, this includes deleting or inserting any run in the container, because that
     * can change the connections between the runs, which is reflected in the graphs.
     */
    public static void clearCache(Container container)
    {
        Lock deleteLock = LOCK.writeLock();
        deleteLock.lock();
        try
        {
            FileUtil.deleteDir(getFolderDirectory(container));
        }
        catch (IOException e)
        {
            // Non-fatal
            _log.error("Failed to clear cached experiment run graphs for container " + container, e);
        }
        finally
        {
            deleteLock.unlock();
        }
    }

    private static void testDotPath() throws ExperimentException
    {
        File dir;

        try
        {
            dir = getBaseDirectory();
        }
        catch (IOException e)
        {
            throw new ExperimentException(DotRunner.getConfigurationError(e));
        }

        DotRunner.testDotPath(dir);
    }


    private static String getBaseFileName(ExpRun run, boolean detail, String focus) throws IOException
    {
        String fileName;
        if (null != focus)
            fileName = getFolderDirectory(run.getContainer()) + File.separator + "run" + run.getRowId() + "Focus" + focus;
        else
            fileName = getFolderDirectory(run.getContainer()) + File.separator + "run" + run.getRowId() + (detail ? "Detail" : "");
        return fileName;
    }

    private static final Cleaner CLEANER = Cleaner.create();

    private static class FileLockState implements Runnable
    {
        private final Throwable _allocation;
        private Lock _lock;

        private FileLockState(Throwable allocation, Lock lock)
        {
            _allocation = allocation;
            _lock = lock;
        }

        @Override
        public void run()
        {
            if (_lock != null)
            {
                _log.error("Lock was not released. Creation was at:", _allocation);
                _lock.unlock();
                _lock = null;
            }
        }

        private void release()
        {
            if (_lock != null)
            {
                _lock.unlock();
                _lock = null;
            }
        }
    }

    /**
     * Results for run graph generation. Must be released once the files have been consumed by the caller.
     */
    public static class RunGraphFiles
    {
        private final File _mapFile;
        private final File _imageFile;
        private final FileLockState _state;
        private final Cleaner.Cleanable _cleanable;

        public RunGraphFiles(File mapFile, File imageFile, Lock lock)
        {
            _mapFile = mapFile;
            _imageFile = imageFile;
            _state = new FileLockState(new Throwable(), lock);
            _cleanable = CLEANER.register(this, _state);
        }

        public File getMapFile()
        {
            return _mapFile;
        }

        public File getImageFile()
        {
            return _imageFile;
        }

        /**
         * Release the lock on the files.
         */
        public void release()
        {
            _state.release();
            _cleanable.clean();
        }
    }
}
