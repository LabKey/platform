/*
 * Copyright (c) 2005-2009 LabKey Corporation
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
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.*;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.ImageUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.Container;
import org.labkey.experiment.api.ExpProtocolApplicationImpl;
import org.labkey.experiment.api.ExpRunImpl;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;
import java.awt.image.BufferedImage;

/**
 * User: migra
 * Date: Jun 15, 2005
 * Time: 12:57:02 AM
 */
public class ExperimentRunGraph
{
    private static File baseDirectory;
    private static final String dotExePath = "dot";
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
    public static RunGraphFiles generateRunGraph(ViewContext ctx, ExpRunImpl run, boolean detail, String focus) throws ExperimentException, IOException, InterruptedException
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
            if (!testDotPath())
            {
                StringBuffer sb = new StringBuffer();
                sb.append("Unable to display graph view: cannot run ");
                sb.append(dotExePath);
                sb.append(" due to system configuration error. \n<BR>");
                sb.append("For help on fixing your system configuration, please consult the Graphviz section of the <a href=\"");
                sb.append((new HelpTopic("thirdPartyCode", HelpTopic.Area.SERVER)).getHelpTopicLink());
                sb.append("\" target=\"_blank\">LabKey Server documentation on third party components</a>.<br>");
                throw new ExperimentException(sb.toString());
            }

            ActionURL url = ctx.getActionURL();
            PrintWriter out = null;
            Integer focusId = null;
            String typeCode = null;

            try
            {
                if (null != focus)
                {
                    typeCode = focus.substring(0, 1);
                    focusId = Integer.parseInt(focus.substring(1));
                    run.trimRunTree(focusId, typeCode);
                }
                StringWriter writer = new StringWriter();
                out = new PrintWriter(writer);
                GraphCtrlProps ctrlProps = analyzeGraph(run);

                DotGraph dg = new DotGraph(out, url, ctrlProps.fUseSmallFonts);

                if (!detail)
                    generateSummaryGraph(run, dg, ctrlProps);
                else
                {
                    if (null != focusId)
                        dg.setFocus(focusId, typeCode);

                    // add starting inputs to graph if they need grouping
                    Map<ExpMaterial, String> materialRoles = run.getMaterialInputs();
                    List<ExpMaterial> inputMaterials = new ArrayList<ExpMaterial>(materialRoles.keySet());
                    Collections.sort(inputMaterials, new RoleAndNameComparator<ExpMaterial>(materialRoles));
                    Map<ExpData, String> dataRoles = run.getDataInputs();
                    List<ExpData> inputDatas = new ArrayList<ExpData>(dataRoles.keySet());
                    Collections.sort(inputDatas, new RoleAndNameComparator<ExpData>(dataRoles));
                    if (run.getProtocolApplications().length > 0)
                    {
                        int groupId = run.getProtocolApplications()[0].getRowId();
                        addStartingInputs(inputMaterials, inputDatas, groupId, dg, run.getRowId(), ctrlProps);
                        generateDetailGraph(run, dg, ctrlProps);
                    }
                }
                dg.dispose();
                out.close();
                out = null;
                String dotInput = writer.getBuffer().toString();

                ProcessBuilder pb = new ProcessBuilder(dotExePath, "-Tcmap", "-o" + mapFile.getName(), "-Tpng", "-o" + imageFile.getName());
                pb.directory(getFolderDirectory(run.getContainer()));
                ProcessResult result = executeProcess(pb, dotInput);

                if (result._returnCode != 0)
                {
                    throw new IOException("Graph generation failed with error code " + result._returnCode + " - " + result._output);
                }
                mapFile.deleteOnExit();
                imageFile.deleteOnExit();

                FileOutputStream fOut = null;
                try
                {
                    BufferedImage originalImage = ImageIO.read(imageFile);

                    // Write it back out to disk
                    fOut = new FileOutputStream(imageFile);
                    double scale = ImageUtil.resizeImage(originalImage, fOut, .85, 6);

                    // Need to rewrite the image map to change the coordinates according to the scaling factor
                    resizeImageMap(mapFile, scale);

                    fOut.close();
                }
                finally
                {
                    if (fOut != null) { try { fOut.close(); } catch (IOException e) {} }
                }

                // Start the procedure of downgrade our lock from write to read so that the caller can use the files 
                readLock.lock();
                return new RunGraphFiles(mapFile, imageFile, readLock);
            }
            finally
            {
                if (null != out)
                    out.close();
            }
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
        FileInputStream fIn = null;
        try
        {
            // Read in the original file, line by line
            fIn = new FileInputStream(mapFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fIn));
            String line;
            while((line = reader.readLine()) != null)
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
                            newLine.append((int)(Integer.parseInt(coord.trim()) * finalScale));
                        }
                        newLine.append(line.substring(closeIndex));
                        line = newLine.toString();
                    }
                }
                sb.append(line);
                sb.append("\n");
            }
        }
        finally
        {
            if (fIn != null) { try { fIn.close(); } catch (IOException e) {} }
        }

        // Write the file back to the disk
        FileOutputStream mapOut = null;
        try
        {
            mapOut = new FileOutputStream(mapFile);
            OutputStreamWriter mapWriter = new OutputStreamWriter(mapOut);
            mapWriter.write(sb.toString());
            mapWriter.flush();
        }
        finally
        {
            if (mapOut != null) { try { mapOut.close(); } catch (IOException e) {} }
        }
    }

    private static ProcessResult executeProcess(ProcessBuilder pb, String stdIn) throws IOException, InterruptedException
    {
        StringBuilder sb = new StringBuilder();
        pb.redirectErrorStream(true);
        Process p = pb.start();
        PrintWriter writer = null;
        BufferedReader procReader = null;
        try
        {
            writer = new PrintWriter(p.getOutputStream());
            writer.write(stdIn);
            writer.close();
            writer = null;
            procReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = procReader.readLine()) != null)
            {
                sb.append(line);
                sb.append("\n");
            }
        }
        finally
        {
            if (procReader != null)
            {
                try { procReader.close(); } catch (IOException eio) {}
            }
            if (writer != null)
            {
                writer.close();
            }
        }
        int returnCode = p.waitFor();
        return new ProcessResult(returnCode, sb.toString());
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

    private static class ProcessResult
    {
        private final int _returnCode;
        private final String _output;

        public ProcessResult(int returnCode, String output)
        {
            super();
            _returnCode = returnCode;
            _output = output;
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
        Map<ExpData, String> runDataInputs = new HashMap<ExpData, String>(expRun.getDataInputs());
        Map<ExpMaterial, String> runMaterialInputs = new HashMap<ExpMaterial, String>(expRun.getMaterialInputs());

        int prevseq = 0;
        boolean firstApp = true;
        for (ExpProtocolApplicationImpl protApp : expRun.getProtocolApplications())
        {
            int rowIdPA = protApp.getRowId();
            String namePA = protApp.getName();
            int sequence = protApp.getActionSequence();

            ExpProtocol.ApplicationType cpasTypePA = protApp.getApplicationType();
            if (cpasTypePA == ExpProtocol.ApplicationType.ExperimentRun)
            {
                assert firstApp;
                firstApp = false;
                continue;
            }
            if (cpasTypePA == ExpProtocol.ApplicationType.ExperimentRunOutput)
            {
                continue;
            }
            
            List<ExpMaterial> inputMaterials = protApp.getInputMaterials();
            List<ExpData> inputDatas = protApp.getInputDatas();
            List<ExpMaterial> outputMaterials = protApp.getOutputMaterials();
            List<ExpData> outputDatas = protApp.getOutputDatas();

            Collections.sort(inputMaterials, new RoleAndNameComparator<ExpMaterial>(runMaterialInputs));
            Collections.sort(inputDatas, new RoleAndNameComparator<ExpData>(runDataInputs));

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
                if (cpasTypePA == ExpProtocol.ApplicationType.ExperimentRunOutput)
                    dg.addOutputNode(groupId, rowIdPA, namePA, sequence);
                else
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

                if (cpasTypePA == ExpProtocol.ApplicationType.ExperimentRunOutput)
                    dg.addOutputNode(groupId, rowIdPA, namePA, sequence);
                else
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
            mPANodesPerSequence = new TreeMap<Integer, Integer>();
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

        ExpProtocolApplication [] aSteps = exp.getProtocolApplications();
        for (int i = 0; i < aSteps.length; i++)
        {
            int curS = aSteps[i].getActionSequence();

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
            curMI += Math.min(aSteps[i].getInputMaterials().size(), maxSiblingsPerParent);
            curDI += Math.min(aSteps[i].getInputDatas().size(), maxSiblingsPerParent);
            curMO += Math.min(aSteps[i].getOutputMaterials().size(), maxSiblingsPerParent);
            curDO += Math.min(aSteps[i].getOutputDatas().size(), maxSiblingsPerParent);
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
            for (int i = iMaxLevelStart; i <= (Math.min(iMaxLevelEnd, iMaxLevelStart + maxSiblingsPerParent - 1)); i++)
            {
                curMI += Math.min(aSteps[i].getInputMaterials().size(), maxSiblingsPerParent);
                curDI += Math.min(aSteps[i].getInputDatas().size(), maxSiblingsPerParent);
                curMO += Math.min(aSteps[i].getOutputMaterials().size(), maxSiblingsPerParent);
                curDO += Math.min(aSteps[i].getOutputDatas().size(), maxSiblingsPerParent);
            }
            maxMD = Math.max(curMO + curDO, curMI + curDI);
        }

        ctrlProps.maxSiblingNodes = maxSiblingsPerParent;
        if (exp.getMaterialInputs().size() + exp.getDataInputs().size() > ctrlProps.maxNodesWidth)
            ctrlProps.fGroupInputs = true;

        return ctrlProps;
    }

    private static boolean testDotPath()
    {
        try
        {
            File testVersion = new File(getBaseDirectory(), "dottest.txt");
            if (testVersion.exists())
                return true;
            ProcessBuilder pb = new ProcessBuilder(dotExePath, "-V", "-o" + testVersion.getName());
            pb = pb.directory(getBaseDirectory());
            Process p = pb.start();
            int err = p.waitFor();
            if (err == 0)
                return true;

        }
        catch (IOException e)
        {
            return false;
        }
        catch (InterruptedException e)
        {
            return false;
        }
        return false;

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
