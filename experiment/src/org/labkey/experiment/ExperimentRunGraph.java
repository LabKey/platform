/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.api.ExpRunImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.ExpProtocolApplicationImpl;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;
import java.util.List;
import java.awt.*;
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

    public synchronized static File getFolderDirectory(int folderId) throws IOException
    {
        File result = new File(getBaseDirectory(), "Folder" + folderId);
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

    public static void generateRunGraph(ViewContext ctx, int containerId, int runId, boolean detail, String focus) throws ExperimentException, IOException, InterruptedException
    {
        File imageFile = getImageFile(containerId, runId, detail, focus);
        File mapFile = getMapFile(containerId, runId, detail, focus);

        if (!AppProps.getInstance().isDevMode() && imageFile.exists() && mapFile.exists())
        {
            return;
        }

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
            ExpRunImpl expRun = ExperimentServiceImpl.get().getExpRun(runId);
            if (null != focus)
            {
                typeCode = focus.substring(0, 1);
                focusId = Integer.parseInt(focus.substring(1));
                expRun.trimRunTree(focusId, typeCode);
            }
            StringWriter writer = new StringWriter();
            out = new PrintWriter(writer);
            GraphCtrlProps ctrlProps = analyzeGraph(expRun);

            DotGraph dg = new DotGraph(out, url, ctrlProps.fUseSmallFonts);

            if (!detail)
                generateSummaryGraph(expRun, dg, ctrlProps);
            else
            {
                if (null != focusId)
                    dg.setFocus(focusId, typeCode);

                // add starting inputs to graph if they need grouping
                List<ExpMaterial> inputMaterials = new ArrayList<ExpMaterial>(expRun.getMaterialInputs().keySet());
                List<ExpData> inputDatas = new ArrayList<ExpData>(expRun.getDataInputs().keySet());
                int groupId = expRun.getProtocolApplications()[0].getRowId();
                addStartingInputs(inputMaterials, inputDatas, groupId, dg, expRun.getRowId(), ctrlProps);
                generateDetailGraph(expRun, dg, ctrlProps);
            }
            dg.dispose();
            out.close();
            out = null;
            String dotInput = writer.getBuffer().toString();

            ProcessBuilder pb = new ProcessBuilder(dotExePath, "-Tpng", "-o" + imageFile.getName());
            pb.directory(getFolderDirectory(containerId));
            ProcessResult result = executeProcess(pb, dotInput);
            if (result._returnCode != 0)
            {
                throw new IOException("Graph generation failed with error code " + result._returnCode + " - " + result._output);
            }

            imageFile.deleteOnExit();

            pb = new ProcessBuilder(dotExePath, "-Tcmap", "-o" + mapFile.getName());
            pb.directory(getFolderDirectory(containerId));
            result = executeProcess(pb, dotInput);

            if (result._returnCode != 0)
            {
                throw new IOException("Graph generation failed with error code " + result._returnCode + " - " + result._output);
            }
            mapFile.deleteOnExit();
            
            resizeFiles(imageFile, mapFile);
        }
        finally
        {
            if (null != out)
                out.close();
        }
    }

    private static void resizeImageMap(File mapFile, double finalScale)
        throws IOException
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
                // Get rid of the \n that does the line wrapping for the image since dot writes it as a literal in the image map's tooltip
                line = line.replace("\\n", " ");
                int coordsIndex = line.indexOf("coords=\"");
                if (coordsIndex != -1)
                {
                    int openIndex = coordsIndex + "coords=\"".length();
                    int closeIndex = line.indexOf("\"", openIndex);
                    if (closeIndex != -1)
                    {
                        // Parse and scale the coordinates
                        String coordsOriginal = line.substring(openIndex, closeIndex);
                        String[] coords = coordsOriginal.split(",");
                        StringBuilder newLine = new StringBuilder();
                        newLine.append(line.substring(0, openIndex));
                        String separator = "";
                        for (String coord : coords)
                        {
                            newLine.append(separator);
                            separator = ",";
                            newLine.append((int)(Integer.parseInt(coord) * finalScale));
                        }
                        newLine.append(line.substring(closeIndex));
                        line = newLine.toString();
                    }
                }
                sb.append(line);
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

    /** Rewrite the output files so that they look nice and antialiased */
    private static void resizeFiles(File imageFile, File imageMapFile) throws IOException
    {
        double finalScale = 1;

        BufferedImage originalImage = ImageIO.read(imageFile);
        BufferedImage bufferedResizedImage = null;

        // Unfortunately these images don't anti-alias well in a single resize using Java's default
        // algorithm, but they look fine if you do it in incremental steps
        double incrementalScale = .85;
        for (int i = 0; i < 6; i++)
        {
            finalScale *= incrementalScale;
            int width = (int) (originalImage.getWidth() * incrementalScale);
            int height = (int) (originalImage.getHeight() * incrementalScale);

            // Create a new empty image buffer to render into
            bufferedResizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = bufferedResizedImage.createGraphics();

            // Set up the hints to make it look decent
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Draw the resized image
            g2d.drawImage(originalImage, 0, 0, width, height, null);
            g2d.dispose();
            originalImage = bufferedResizedImage;
        }

        FileOutputStream fOut = null;
        try
        {
            // Write it back out to disk
            fOut = new FileOutputStream(imageFile);
            ImageIO.write(bufferedResizedImage, "png", fOut);
            fOut.close();
        }
        finally
        {
            if (fOut != null) { try { fOut.close(); } catch (IOException e) {} }
        }

        // Need to rewrite the image map to change the coordinates according to the scaling factor
        resizeImageMap(imageMapFile, finalScale);
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
            Integer rowIdPA = protApp.getRowId();
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

            if (sequence != prevseq)
            {
                dg.flushPending();
                prevseq = sequence;
                countPAForSeq = 0;
                groupIdPA = null;
            }

            if (inputMaterials != null)
            {
                for (ExpMaterial material : inputMaterials)
                {
                    Integer groupId = dg.getMGroupId(material.getRowId());
                    dg.addMaterial(material, groupId, sequence, expRun.getMaterialOutputs().contains(material));

                    // check if we need to start or stop grouping at this level of PAs
                    // first, if the number of nodesat this level is less than the max,
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
            }

            if (inputDatas != null)
            {
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
            }


            if (outputMaterials != null)
            {
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
            }

            if (outputDatas != null)
            {
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
            //e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (InterruptedException e)
        {
            //e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return false;

    }


    public static File getMapFile(int containerId, int runId, boolean detail, String focus) throws IOException
    {
        return new File(getBaseFileName(containerId, runId, detail, focus) + ".map");
    }

    public static File getDotFile(int containerId, int runId, boolean detail, String focus) throws IOException
    {
        return new File(getBaseFileName(containerId, runId, detail, focus) + ".dot");
    }

    public static File getImageFile(int containerId, int runId, boolean detail, String focus) throws IOException
    {
        return new File(getBaseFileName(containerId, runId, detail, focus) + ".png");
    }

    private static String getBaseFileName(int containerId, int runId, boolean detail, String focus) throws IOException
    {
        String fileName;
        if (null != focus)
            fileName = getFolderDirectory(containerId) + File.separator + "run" + runId + "Focus" + focus;
        else
            fileName = getFolderDirectory(containerId) + File.separator + "run" + runId + (detail ? "Detail" : "");
        return fileName;
    }
}
