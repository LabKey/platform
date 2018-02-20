/*
 * Copyright (c) 2005-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpProtocolOutput;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DotRunner;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.api.ExpDataImpl;
import org.labkey.experiment.api.ExpMaterialImpl;
import org.labkey.experiment.api.ExpProtocolApplicationImpl;
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * User: migra
 * Date: Jun 15, 2005
 * Time: 12:57:02 AM
 */
public class ExperimentRunGraph
{
    private static File baseDirectory;
    private static Logger _log = Logger.getLogger(ExperimentRunGraph.class);
    private static int MAX_WIDTH_SMALL_FONT = 8;
    private static int MAX_WIDTH_BIG_FONT = 3;
    private static int MAX_SIBLINGS = 5;
    private static int MIN_SIBLINGS = 3;

    /**
     * It's safe for lots of threads to be reading but only one should be creating or deleting at a time.
     * We could make separate locks for each folder but leaving it with one global lock for now.
     */
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    public synchronized static File getBaseDirectory() throws IOException
    {
        if (baseDirectory == null)
        {
            File tempFile = File.createTempFile("Exp", ".dir");
            tempFile.delete();
            File tempDir = new File(tempFile.getParentFile(), "ExperimentRunGraphs");
            if (tempDir.exists())
            {
                FileUtil.deleteDirectoryContents(tempDir);
            }
            else
            {
                if (!tempDir.mkdirs())
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
        File result = new File(getBaseDirectory(), "Folder" + container.getRowId());
        result.mkdirs();
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
                result.mkdirs();
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

            Integer focusId = null;
            String typeCode = focusType;

            if (null != focus && focus.length() > 0)
            {
                if (!Character.isDigit(focus.charAt(0)))
                {
                    typeCode = focus.substring(0, 1);
                    focus = focus.substring(1);
                }
                try
                {
                    focusId = Integer.parseInt(focus);
                    run.trimRunTree(focusId, typeCode);
                }
                catch (NumberFormatException ignored) {}
            }

            StringWriter writer = new StringWriter();

            try (PrintWriter out = new PrintWriter(writer))
            {
                GraphCtrlProps ctrlProps = analyzeGraph(run);

                DotGraph dg = new DotGraph(out, ctx.getContainer(), ctrlProps.fUseSmallFonts);

                if (!detail)
                    generateSummaryGraph(run, dg, ctrlProps);
                else
                {
                    if (null != focusId)
                        dg.setFocus(focusId, typeCode);

                    // add starting inputs to graph if they need grouping
                    Map<ExpMaterial, String> materialRoles = run.getMaterialInputs();
                    List<ExpMaterial> inputMaterials = new ArrayList<>(materialRoles.keySet());
                    inputMaterials.sort(new RoleAndNameComparator<>(materialRoles));
                    Map<ExpData, String> dataRoles = run.getDataInputs();
                    List<ExpData> inputDatas = new ArrayList<>(dataRoles.keySet());
                    inputDatas.sort(new RoleAndNameComparator<>(dataRoles));
                    if (!run.getProtocolApplications().isEmpty())
                    {
                        int groupId = run.getProtocolApplications().get(0).getRowId();
                        addStartingInputs(inputMaterials, inputDatas, groupId, dg, run.getRowId(), ctrlProps);
                        generateDetailGraph(run, dg, ctrlProps);
                    }
                }
                dg.dispose();
            }

            String dotInput = writer.getBuffer().toString();
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

    /**
     * Clears out the cache of files for this container. Must be called after any operation that changes the way a graph
     * would be generated. Typically this includes deleting or inserting any run in the container, because that
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

    /**
     * Sort first by role, if present, and then by name.
     */
    private static class RoleAndNameComparator<Type extends ExpProtocolOutput> implements Comparator<Type>
    {
        private final Map<Type, String> _roles;

        private RoleAndNameComparator(Map<Type, String> roles)
        {
            _roles = roles;
        }

        public int compare(Type o1, Type o2)
        {
            String role1 = _roles.get(o1);
            String role2 = _roles.get(o2);
            if (role1 != null && role2 != null)
            {
                return role1.compareTo(role2);
            }
            else if (role1 == null && role2 != null)
            {
                return -1;
            }
            else if (role1 != null)
            {
                return 1;
            }
            return o1.getName().compareTo(o2.getName());
        }
    }

    private static void generateDetailGraph(ExpRunImpl expRun, DotGraph dg, GraphCtrlProps ctrlProps)
    {
        int countPAForSeq = 0;
        Integer groupIdPA = null;

        // We're going to remove entries as we use them, so we need our own copy
        Map<ExpData, String> runDataInputs = new HashMap<>(expRun.getDataInputs());
        Map<ExpMaterial, String> runMaterialInputs = new HashMap<>(expRun.getMaterialInputs());

        int prevseq = 0;

        for (ExpProtocolApplicationImpl protApp : expRun.getProtocolApplications())
        {
            int rowIdPA = protApp.getRowId();
            String namePA = protApp.getName();
            int sequence = protApp.getActionSequence();

            ExpProtocol.ApplicationType cpasTypePA = protApp.getApplicationType();
            if (cpasTypePA == ExpProtocol.ApplicationType.ExperimentRun || cpasTypePA == ExpProtocol.ApplicationType.ExperimentRunOutput)
            {
                continue;
            }
            
            List<ExpMaterialImpl> inputMaterials = protApp.getInputMaterials();
            List<ExpDataImpl> inputDatas = protApp.getInputDatas();
            List<ExpMaterialImpl> outputMaterials = protApp.getOutputMaterials();
            List<ExpDataImpl> outputDatas = protApp.getOutputDatas();

            inputMaterials.sort(new RoleAndNameComparator<>(runMaterialInputs));
            inputDatas.sort(new RoleAndNameComparator<>(runDataInputs));

            if (sequence != prevseq)
            {
                dg.flushPending();
                prevseq = sequence;
                countPAForSeq = 0;
                groupIdPA = null;
            }

            for (ExpMaterial material : inputMaterials)
            {
                Integer groupId = dg.getMGroupId(material.getRowId());
                dg.addMaterial(material, groupId, sequence, expRun.getMaterialOutputs().contains(material));

                // check if we need to start or stop grouping at this level of PAs
                // first, if the number of nodes at this level is less than the max,
                // don't group even if input is grouped
                // else if the parent (input) is not grouped but we're at the width limit, start a new group
                if (ctrlProps.getPACountForSequence(sequence) <= ctrlProps.maxSiblingNodes)
                    groupId = null;
                else if ((null==groupId) && (countPAForSeq >= ctrlProps.maxSiblingNodes - 1))
                {
                    if (null==groupIdPA)
                        groupIdPA = rowIdPA;
                    groupId = groupIdPA;
                }

                dg.addProtApp(groupId, rowIdPA, namePA, sequence);
                // We only want to show the label once, so remove it from the map
                String label = runMaterialInputs.remove(material);
                dg.connectMaterialToProtocolApp(material.getRowId(), rowIdPA, label);
            }

            for (ExpData data : inputDatas)
            {
                Integer groupId = dg.getDGroupId(data.getRowId());
                dg.addData(data, groupId, sequence, expRun.getDataOutputs().contains(data));

                // same check as above
                if (ctrlProps.getPACountForSequence(sequence) <= ctrlProps.maxSiblingNodes)
                    groupId = null;
                else if ((null==groupId) && (countPAForSeq >= ctrlProps.maxSiblingNodes - 1))
                {
                    if (null==groupIdPA)
                        groupIdPA = rowIdPA;
                    groupId = groupIdPA;
                }

                dg.addProtApp(groupId, rowIdPA, namePA, sequence);
                // We only want to show the label once, so remove it from the map
                String label = runDataInputs.remove(data);
                dg.connectDataToProtocolApp(data.getRowId(), rowIdPA, label);
            }


            for (int i = 0; i < outputMaterials.size(); i++)
            {
                ExpMaterial material = outputMaterials.get(i);
                // determine group membership for output nodes.  Either we are starting
                // a new group because we are exceeding Max siblings, or
                // we are inheriting a group from above.
                Integer groupId = dg.getPAGroupId(rowIdPA);
                if ((null == groupId) &&
                        (outputMaterials.size() > ctrlProps.maxSiblingNodes) && (i >= ctrlProps.maxSiblingNodes - 1))
                    groupId = rowIdPA;

                dg.addProtApp(groupId, rowIdPA, namePA, sequence);
                dg.addMaterial(material, groupId, sequence, expRun.getMaterialOutputs().contains(material));
                dg.connectProtocolAppToMaterial(rowIdPA, material.getRowId());
            }

            for (int i = 0; i < outputDatas.size(); i++)
            {
                ExpData data = outputDatas.get(i);
                Integer groupId = dg.getPAGroupId(rowIdPA);
                if ((null == groupId) &&
                        (outputDatas.size() > ctrlProps.maxSiblingNodes) && (i >= ctrlProps.maxSiblingNodes - 1))
                    groupId = rowIdPA;

                dg.addProtApp(groupId, rowIdPA, namePA, sequence);
                dg.addData(data, groupId, sequence, expRun.getDataOutputs().contains(data));
                dg.connectProtocolAppToData(rowIdPA, data.getRowId());

            }
            countPAForSeq++;
        }
    }

    private static void addStartingInputs(List<ExpMaterial> inputMaterials, List<ExpData> inputDatas, int protAppId, DotGraph dg, int expRunId, GraphCtrlProps ctrlProps)
    {
        Integer groupId = null;
        for (int i=0;i<inputMaterials.size();i++)
        {
            // check if we need to group
            if (ctrlProps.fGroupInputs && i >= ctrlProps.maxSiblingNodes - 1)
                groupId = protAppId;
            dg.addStartingMaterial(inputMaterials.get(i), groupId, 0, expRunId);
        }
        groupId = null;
        for (int i=0;i<inputDatas.size();i++)
        {
            if (ctrlProps.fGroupInputs && i >= ctrlProps.maxSiblingNodes - 1)
                groupId = protAppId;
            dg.addStartingData(inputDatas.get(i), groupId, 0, expRunId);
        }
    }

    private static void generateSummaryGraph(ExpRunImpl expRun, DotGraph dg, GraphCtrlProps ctrlProps)
    {
        int runId = expRun.getRowId();
        Map<ExpMaterial, String> inputMaterials = expRun.getMaterialInputs();
        Map<ExpData, String> inputDatas = expRun.getDataInputs();
        List<ExpMaterial> outputMaterials = expRun.getMaterialOutputs();
        List<ExpData> outputDatas = expRun.getDataOutputs();
        Integer groupId;

        int i = 0;
        for (Map.Entry<ExpMaterial, String> entry : inputMaterials.entrySet())
        {
            ExpMaterial inputMaterial = entry.getKey();
            groupId=null;
            if (ctrlProps.fGroupInputs && (i >= ctrlProps.maxSiblingNodes - 1))
                groupId = 0;
            dg.addStartingMaterial(inputMaterial, groupId, null, runId);
            dg.addExpRun(runId, expRun.getName());
            dg.connectMaterialToRun(inputMaterial.getRowId(), runId, entry.getValue());
            ExpRun producingRun = inputMaterial.getRun();
            if (producingRun != null && producingRun.getRowId() != runId)
            {
                dg.addLinkedRun(producingRun.getRowId(), producingRun.getName());
                dg.connectRunToMaterial(producingRun.getRowId(), inputMaterial.getRowId());
            }
            i++;
        }
        i = 0;
        for (Map.Entry<ExpData, String> entry : inputDatas.entrySet())
        {
            ExpData inputData = entry.getKey();
            groupId=null;
            if (ctrlProps.fGroupInputs && (i >= ctrlProps.maxSiblingNodes - 1))
                groupId = 0;
            dg.addStartingData(inputData, groupId, null, runId);
            dg.addExpRun(runId, expRun.getName());
            dg.connectDataToRun(inputData.getRowId(), runId, entry.getValue());
            ExpRun producingRun = inputData.getRun();
            if (producingRun != null && producingRun.getRowId() != runId)
            {
                dg.addLinkedRun(producingRun.getRowId(), producingRun.getName());
                dg.connectRunToData(producingRun.getRowId(), inputData.getRowId());
            }
            i++;
        }
        if (outputMaterials != null)
        {
            i = 0;
            for (ExpMaterial material : outputMaterials)
            {
                groupId = null;
                if ((outputMaterials.size() > ctrlProps.maxSiblingNodes) && (i >= ctrlProps.maxSiblingNodes - 1))
                    groupId = 1;
                dg.addExpRun(runId, expRun.getName());
                dg.addMaterial(material, groupId, null, expRun.getMaterialOutputs().contains(material));
                dg.connectRunToMaterial(runId, material.getRowId());
                for (ExpRun successorRun : material.getSuccessorRuns())
                {
                    dg.addLinkedRun(successorRun.getRowId(), successorRun.getName());
                    dg.connectMaterialToRun(material.getRowId(), successorRun.getRowId(), null);
                }
                i++;
            }
        }

        if (outputDatas != null)
        {
            i = 0;
            for (ExpData data : outputDatas)
            {
                groupId = null;
                if ((outputDatas.size() > ctrlProps.maxSiblingNodes) && (i >= ctrlProps.maxSiblingNodes - 1))
                    groupId = 1;
                dg.addExpRun(runId, expRun.getName());
                dg.addData(data, groupId, null, expRun.getDataOutputs().contains(data));
                dg.connectRunToData(runId, data.getRowId());
                for (ExpRun successorRun : data.getSuccessorRuns())
                {
                    dg.addLinkedRun(successorRun.getRowId(), successorRun.getName());
                    dg.connectDataToRun(data.getRowId(), successorRun.getRowId(), null);
                }
                i++;
            }
        }
    }

    private static class GraphCtrlProps
    {
        int maxSiblingNodes;
        int maxNodesWidth;
        boolean fGroupInputs=false;
        boolean fUseSmallFonts;
        SortedMap<Integer, Integer> mPANodesPerSequence;

        public GraphCtrlProps()
        {
            mPANodesPerSequence = new TreeMap<>();
        }
        public int getPACountForSequence(int seq)
        {
            Integer c = mPANodesPerSequence.get(new Integer(seq));
            if (null==c)
                return 0;
            return c.intValue();
        }

    }


    private static GraphCtrlProps analyzeGraph(ExpRunImpl exp)
    {
        int maxSiblingsPerParent = MAX_SIBLINGS;
        int maxMD = MIN_SIBLINGS;
        int iMaxLevelStart = 0;
        int iMaxLevelEnd = 0;
        int curMI = 0;
        int curDI = 0;
        int curMO = 0;
        int curDO = 0;
        int prevS = 0;
        int iLevelStart = 0;
        int iLevelEnd = 0;
        GraphCtrlProps ctrlProps = new GraphCtrlProps();

        int i = 0;
        List<ExpProtocolApplicationImpl> steps = exp.getProtocolApplications();
        for (ExpProtocolApplicationImpl step : steps)
        {
            int curS = step.getActionSequence();

            Integer countSeq = ctrlProps.mPANodesPerSequence.get(curS);
            if (null==countSeq)
                countSeq=new Integer(1);
            else
                countSeq++;
            ctrlProps.mPANodesPerSequence.put(curS, countSeq);

            if (curS != prevS)
            {
                if (curMI + curDI > maxMD)
                {
                    maxMD = curMI + curDI;
                    iMaxLevelStart = iLevelStart;
                    iMaxLevelEnd = iLevelEnd;
                }
                if (curMO + curDO > maxMD)
                {
                    maxMD = curMO + curDO;
                    iMaxLevelStart = iLevelStart;
                    iMaxLevelEnd = iLevelEnd;
                }
                prevS = curS;
                curMI = 0;
                curDI = 0;
                curMO = 0;
                curDO = 0;
                iLevelStart = i;
            }
            iLevelEnd = i;
            curMI += Math.min(step.getInputMaterials().size(), maxSiblingsPerParent);
            curDI += Math.min(step.getInputDatas().size(), maxSiblingsPerParent);
            curMO += Math.min(step.getOutputMaterials().size(), maxSiblingsPerParent);
            curDO += Math.min(step.getOutputDatas().size(), maxSiblingsPerParent);
            i++;
        }

        if (maxMD > MAX_WIDTH_BIG_FONT)
        {
            ctrlProps.fUseSmallFonts = true;
            ctrlProps.maxNodesWidth = MAX_WIDTH_SMALL_FONT;
        }
        else
        {
            ctrlProps.fUseSmallFonts  = false;
            ctrlProps.maxNodesWidth = MAX_WIDTH_BIG_FONT;
        }

        // try to adjust the number of siblings to fit the levels within the max width
        while ((maxMD > ctrlProps.maxNodesWidth) && (maxSiblingsPerParent > MIN_SIBLINGS))
        {
            curMI = 0;
            curDI = 0;
            curMO = 0;
            curDO = 0;
            maxSiblingsPerParent--;
            for (i = iMaxLevelStart; i <= (Math.min(iMaxLevelEnd, iMaxLevelStart + maxSiblingsPerParent - 1)); i++)
            {
                ExpProtocolApplication step = steps.get(i);
                curMI += Math.min(step.getInputMaterials().size(), maxSiblingsPerParent);
                curDI += Math.min(step.getInputDatas().size(), maxSiblingsPerParent);
                curMO += Math.min(step.getOutputMaterials().size(), maxSiblingsPerParent);
                curDO += Math.min(step.getOutputDatas().size(), maxSiblingsPerParent);
            }
            maxMD = Math.max(curMO + curDO, curMI + curDI);
        }

        ctrlProps.maxSiblingNodes = maxSiblingsPerParent;
        if (exp.getMaterialInputs().size() + exp.getDataInputs().size() > ctrlProps.maxNodesWidth)
            ctrlProps.fGroupInputs = true;

        return ctrlProps;
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
            throw new ExperimentException(DotRunner.getConfigurationErrorHtml(e));
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

    /**
     * Results for run graph generation. Must be released once the files have been consumed by the caller.
     */
    public static class RunGraphFiles
    {
        private final File _mapFile;
        private final File _imageFile;
        private Lock _lock;
        private final Throwable _allocation = new Throwable();

        public RunGraphFiles(File mapFile, File imageFile, Lock lock)
        {
            _mapFile = mapFile;
            _imageFile = imageFile;
            _lock = lock;
        }

        public File getMapFile()
        {
            return _mapFile;
        }

        public File getImageFile()
        {
            return _imageFile;
        }

        protected void finalize() throws Throwable
        {
            super.finalize();
            if (_lock != null)
            {
                _log.error("Lock was not released. Creation was at:", _allocation);
                release();
            }
        }

        /**
         * Release the lock on the files.
         */
        public void release()
        {
            if (_lock != null)
            {
                _lock.unlock();
                _lock = null;
            }
        }
    }
}
