package org.labkey.experiment;

import org.graphper.api.Graphviz;
import org.graphper.draw.ExecuteException;
import org.graphper.parser.DotParser;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.util.SvgUtil;
import org.labkey.experiment.api.ExpDataImpl;
import org.labkey.experiment.api.ExpMaterialImpl;
import org.labkey.experiment.api.ExpProtocolApplicationImpl;
import org.labkey.experiment.api.ExpRunImpl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class ExperimentRunGraph
{
    private static final int MAX_WIDTH_SMALL_FONT = 8;
    private static final int MAX_WIDTH_BIG_FONT = 3;
    private static final int MAX_SIBLINGS = 5;
    private static final int MIN_SIBLINGS = 3;

    public static String getSvg(String dot) throws ExecuteException
    {
        Graphviz graph = DotParser.parse(dot);
        String svg = graph.toSvgStr();

        // Scale down to 50% of default size. This is arbitrary but seems reasonable. Diagrams are larger than
        // the previous image-based ones, but monitors are much higher resolution than when those were scaled.
        return SvgUtil.scaleSize(svg, 0.5f);
    }

    public static String getDotGraph(Container c, ExpRunImpl run, boolean detail, String focus, String focusType)
    {
        Integer focusId = null;
        String typeCode = focusType;

        if (null != focus && !focus.isEmpty())
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
            catch (NumberFormatException | ExperimentException ignored) {}
        }

        StringWriter writer = new StringWriter();

        try (PrintWriter out = new PrintWriter(writer))
        {
            GraphCtrlProps ctrlProps = analyzeGraph(run);

            DotGraph dg = new DotGraph(out, c, ctrlProps.fUseSmallFonts);

            if (!detail)
                generateSummaryGraph(run, dg, ctrlProps);
            else
            {
                if (null != focusId)
                    dg.setFocus(focusId, typeCode);

                // add starting inputs to graph if they need grouping
                Map<? extends ExpMaterial, String> materialRoles = run.getMaterialInputs();
                List<ExpMaterial> inputMaterials = new ArrayList<>(materialRoles.keySet());
                inputMaterials.sort(new RoleAndNameComparator<>(materialRoles));
                Map<? extends ExpData, String> dataRoles = run.getDataInputs();
                List<ExpData> inputDatas = new ArrayList<>(dataRoles.keySet());
                inputDatas.sort(new RoleAndNameComparator<>(dataRoles));
                if (!run.getProtocolApplications().isEmpty())
                {
                    long groupId = run.getProtocolApplications().getFirst().getRowId();
                    addStartingInputs(inputMaterials, inputDatas, groupId, dg, run.getRowId(), ctrlProps);
                    generateDetailGraph(run, dg, ctrlProps);
                }
            }
            dg.dispose();
        }

        return writer.getBuffer().toString();
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
            if (null == countSeq)
                countSeq = Integer.valueOf(1);
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

    /**
     * Sort first by role, if present, and then by name.
     */
    private static class RoleAndNameComparator<Type extends ExpRunItem> implements Comparator<Type>
    {
        private final Map<? extends Type, String> _roles;

        private RoleAndNameComparator(Map<? extends Type, String> roles)
        {
            _roles = roles;
        }

        @Override
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
        Long groupIdPA = null;

        // We're going to remove entries as we use them, so we need our own copy
        Map<ExpData, String> runDataInputs = new HashMap<>(expRun.getDataInputs());
        Map<ExpMaterial, String> runMaterialInputs = new HashMap<>(expRun.getMaterialInputs());

        int prevseq = 0;

        for (ExpProtocolApplicationImpl protApp : expRun.getProtocolApplications())
        {
            long rowIdPA = protApp.getRowId();
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
                Long groupId = dg.getMGroupId(material.getRowId());
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
                Long groupId = dg.getDGroupId(data.getRowId());
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
            // CONSIDER: Add hidden connector between previous action sequence if it has no inputs

            for (int i = 0; i < outputMaterials.size(); i++)
            {
                ExpMaterial material = outputMaterials.get(i);
                // determine group membership for output nodes.  Either we are starting
                // a new group because we are exceeding Max siblings, or
                // we are inheriting a group from above.
                Long groupId = dg.getPAGroupId(rowIdPA);
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
                Long groupId = dg.getPAGroupId(rowIdPA);
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

    private static void addStartingInputs(List<ExpMaterial> inputMaterials, List<ExpData> inputDatas, long protAppId, DotGraph dg, long expRunId, GraphCtrlProps ctrlProps)
    {
        Long groupId = null;
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
        long runId = expRun.getRowId();
        Map<? extends ExpMaterial, String> inputMaterials = expRun.getMaterialInputs();
        Map<? extends ExpData, String> inputDatas = expRun.getDataInputs();
        List<ExpMaterial> outputMaterials = expRun.getMaterialOutputs();
        List<ExpData> outputDatas = expRun.getDataOutputs();
        Long groupId;

        int i = 0;
        for (Map.Entry<? extends ExpMaterial, String> entry : inputMaterials.entrySet())
        {
            ExpMaterial inputMaterial = entry.getKey();
            groupId=null;
            if (ctrlProps.fGroupInputs && (i >= ctrlProps.maxSiblingNodes - 1))
                groupId = 0L;
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
        for (Map.Entry<? extends ExpData, String> entry : inputDatas.entrySet())
        {
            ExpData inputData = entry.getKey();
            groupId=null;
            if (ctrlProps.fGroupInputs && (i >= ctrlProps.maxSiblingNodes - 1))
                groupId = 0L;
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
                    groupId = 1L;
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
                    groupId = 1L;
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
            Integer c = mPANodesPerSequence.get(Integer.valueOf(seq));
            if (null==c)
                return 0;
            return c.intValue();
        }
    }
}
