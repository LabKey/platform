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

package org.labkey.experiment.xar;

import org.fhcrc.cpas.exp.xml.*;
import org.fhcrc.cpas.exp.xml.ExperimentRunType;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.exp.xar.Replacer;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.Pair;
import org.labkey.experiment.api.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: jeckels
 * Date: Dec 7, 2005
 */
public class XarExpander extends AbstractXarImporter
{
    private final ExperimentRun _run;
    private final Map<PredecessorStep, List<String>> _materialOutputs = new HashMap<>();
    private final Map<PredecessorStep, List<String>> _dataOutputs = new HashMap<>();
    private final List<Data> _startingData;
    private final List<ExpMaterial> _startingMaterials;
    private final Map<String, Data> _allData = new HashMap<>();
    private final Map<String, ExpMaterial> _allMaterial = new HashMap<>();

    public static final String OUTPUT_MATERIAL_PER_INSTANCE_EXPRESSION_PARAMETER = "terms.fhcrc.org#XarTemplate.OutputMaterialPerInstanceExpression";
    public static final String OUTPUT_DATA_PER_INSTANCE_EXPRESSION_PARAMETER = "terms.fhcrc.org#XarTemplate.OutputDataPerInstanceExpression";

    public XarExpander(XarSource source, PipelineJob job,
                       ExperimentRun run, List<Data> startingData, List<ExpMaterial> startingMaterials, ExperimentArchiveType experimentArchive)
    {
        super(source, job);

        _run = run;
        _startingData = startingData;
        _startingMaterials = startingMaterials;
        _experimentArchive = experimentArchive;

        for (Data data : _startingData)
        {
            _allData.put(data.getLSID(), data);
        }
        for (ExpMaterial material : _startingMaterials)
        {
            _allMaterial.put(material.getLSID(), material);
        }

    }

    private String trimString(String s)
    {
        return s == null ? null : s.trim();
    }

    private void processLogStep(ExperimentLogEntryType step,
                                XarContext parentContext,
                                ExperimentRunType xbRun, FileResolver resolver) throws SQLException, ExperimentException
    {
        XarContext context = new XarContext(parentContext);

        Integer actionSequence = step.getActionSequenceRef();

        String runProtocolLSID = _run.getProtocolLSID();
        ProtocolActionStepDetail stepProtocol = ExperimentServiceImpl.get().getProtocolActionStepDetail(runProtocolLSID, actionSequence);
        if (null == stepProtocol)
            throw new XarFormatException("Could not find protocol action set for protocl lsid " + runProtocolLSID + ", actionSequence " + actionSequence);

        ProtocolBean pbStep = new ProtocolBean(stepProtocol);

        ExperimentLogEntryType.ApplicationInstanceCollection instanceCollection = step.getApplicationInstanceCollection();
        InstanceDetailsType[] instances = null;
        if (null != instanceCollection)
            instances = instanceCollection.getInstanceDetailsArray();

        if (runProtocolLSID.equals(pbStep.stepProtocolLSID))
        {
            // Case 1: ExperimentRun (start) node
            handleStartNode(step, context, pbStep, xbRun, resolver);
        }
        else if (null != instances && instances.length > 0)
        {
            // Case 2: Instance details from the submitted XML
            handleExplicitInstances(instances, context, pbStep, step, xbRun, resolver);
        }
        else
        {
            // Case 3: generate protocolapp instances from predecessor outputs
            handleInputsFromOutputs(pbStep, step, context, xbRun, resolver);
        }
    }

    private void handleInputsFromOutputs(ProtocolBean pbStep, ExperimentLogEntryType step, XarContext context, ExperimentRunType xbRun, FileResolver fileResolver)
            throws SQLException, ExperimentException
    {
        int maxInputMaterialsPerInstance = 0;
        int instanceCnt = 0;
        int maxInputDataPerInstance = 0;

        // predecessor outputs when used as inputs
        List<String> predecessorMaterialsOutput = null;
        List<String> predecessorDataOutput = null;

        // todo:  is this test too restrictive?
        if ((null == pbStep.maxInputMaterialPerInstance) || (pbStep.maxInputMaterialPerInstance.intValue() > 0))
        {
            if ((null != pbStep.maxInputDataPerInstance) && (pbStep.maxInputDataPerInstance.intValue() > 0))
            {
                throw new XarFormatException("Step number " + step.getActionSequenceRef() + " protocol " + pbStep.stepProtocolLSID
                        + ". Can't generate multiple instance details for both Materials and Data inputs");
            }

            // Find the set of all Material outputs from predecessors
            predecessorMaterialsOutput = getMaterialOutputs(pbStep.stepProtocolLSID);
            if (null == pbStep.maxInputMaterialPerInstance)
            {
                maxInputMaterialsPerInstance = predecessorMaterialsOutput.size();
                instanceCnt = 1;
            }
            else
            {
                maxInputMaterialsPerInstance = pbStep.maxInputMaterialPerInstance.intValue();
                instanceCnt = predecessorMaterialsOutput.size() / maxInputMaterialsPerInstance;
                // check to see it divides evenly
                if ((instanceCnt * maxInputMaterialsPerInstance) != predecessorMaterialsOutput.size())
                {
                    getLog().warn("Step number " + step.getActionSequenceRef() + " protocol " + pbStep.stepProtocolLSID
                            + ". Material inputs don't divide evenly into instances.  Number of instances rounded up.");
                    instanceCnt ++;
                }
            }
        }

        if ((null == pbStep.maxInputDataPerInstance) || (pbStep.maxInputDataPerInstance.intValue() > 0))
        {
            if ((null != pbStep.maxInputMaterialPerInstance) && (pbStep.maxInputMaterialPerInstance.intValue() > 0))
            {
                throw new XarFormatException("Step number " + step.getActionSequenceRef() + " protocol " + pbStep.stepProtocolLSID
                        + ". Can't generate multiple instance details for both Materials and Data inputs");
            }
            // Find the set of all Data outputs from predecessors
            predecessorDataOutput = getDataOutputs(pbStep.stepProtocolLSID);
            if (null == pbStep.maxInputDataPerInstance)
            {
                maxInputDataPerInstance = predecessorDataOutput.size();
                instanceCnt = 1;
            }
            else
            {
                maxInputDataPerInstance = pbStep.maxInputDataPerInstance.intValue();
                //BUGBUG don't want to round up
                instanceCnt = predecessorDataOutput.size() / maxInputDataPerInstance;
                // check to see it divides evenly
                if ((instanceCnt * maxInputDataPerInstance) != predecessorDataOutput.size())
                {
                    getLog().warn("Step number " + step.getActionSequenceRef() + " protocol " + pbStep.stepProtocolLSID
                            + ". Data inputs don't divide evenly into instances. ");
                    instanceCnt ++;
                }
            }
        }

        fileResolver.reset();
        for (int i = 0; i < instanceCnt; i++)
        {
            InstanceDetailsType details = InstanceDetailsType.Factory.newInstance();
            InputOutputRefsType inputRefs = details.addNewInstanceInputs();
            details.addNewInstanceParametersApplied();

            for (int j = 0; j < maxInputMaterialsPerInstance; j++)
            {
                int k = (i * maxInputMaterialsPerInstance) + j;
                InputOutputRefsType.MaterialLSID materialLSID = inputRefs.addNewMaterialLSID();
                materialLSID.setStringValue(predecessorMaterialsOutput.get(k));
            }
            for (int j = 0; j < maxInputDataPerInstance; j++)
            {
                int k = (i * maxInputDataPerInstance) + j;
                InputOutputRefsType.DataLSID dataLSID = inputRefs.addNewDataLSID();
                dataLSID.setStringValue(predecessorDataOutput.get(k));
            }

            if (inputRefs.getDataLSIDArray().length + inputRefs.getMaterialLSIDArray().length == 0)
            {
                throw new XarFormatException("No inputs found for protocol step " + pbStep.stepProtocolLSID + ", instance " + (i + 1) + " of " + instanceCnt);
            }

            context.addSubstitution("InputInstance", Integer.toString(i));
            createProtAppInstance(pbStep, context, step, details, xbRun, fileResolver);
            fileResolver.advance();
        }
        fileResolver.verifyEmpty();
    }

    private void handleExplicitInstances(InstanceDetailsType[] instances, XarContext context, ProtocolBean pbStep, ExperimentLogEntryType step, ExperimentRunType xbRun, FileResolver fileResolver)
            throws SQLException, ExperimentException
    {
        fileResolver.reset();
        for (int i = 0; i < instances.length; i++)
        {
            context.addSubstitution("InputInstance", Integer.toString(i));
            createProtAppInstance(pbStep, context, step, instances[i], xbRun, fileResolver);
            fileResolver.advance();
        }
        fileResolver.verifyEmpty();
    }

    private void handleStartNode(ExperimentLogEntryType step, XarContext context, ProtocolBean pbStep, ExperimentRunType xbRun, FileResolver fileResolver)
            throws SQLException, ExperimentException
    {
        InstanceDetailsType details = null;
        ExperimentLogEntryType.ApplicationInstanceCollection instanceCollection = step.getApplicationInstanceCollection();
        if (instanceCollection != null)
        {
            InstanceDetailsType[] detailsArray = instanceCollection.getInstanceDetailsArray();
            if (detailsArray.length > 1)
            {
                throw new XarFormatException("Expected to get a single InstanceDetail");
            }
            else if (detailsArray.length == 1)
            {
                details = detailsArray[0];
            }
        }

        if (details == null)
        {
            details = InstanceDetailsType.Factory.newInstance();
            InputOutputRefsType inputRefs = details.addNewInstanceInputs();
            for (ExpMaterial startingMaterial : _startingMaterials)
            {
                InputOutputRefsType.MaterialLSID materialLSID = inputRefs.addNewMaterialLSID();
                materialLSID.setStringValue(trimString(startingMaterial.getLSID()));
            }
            for (Data startingData : _startingData)
            {
                InputOutputRefsType.DataLSID dataLSID = inputRefs.addNewDataLSID();
                dataLSID.setStringValue(trimString(startingData.getLSID()));
                dataLSID.setDataFileUrl(trimString(startingData.getDataFileUrl()));
            }
        }

        if (null != step.getCommonParametersApplied())
            details.setInstanceParametersApplied(step.getCommonParametersApplied());
        context.addSubstitution("InputInstance", Integer.toString(1));
        fileResolver.reset();
        createProtAppInstance(pbStep, context, step, details, xbRun, fileResolver);
        fileResolver.advance();
        fileResolver.verifyEmpty();
    }

    private void createProtAppInstance(ProtocolBean pbStep,
                                       XarContext parentContext,
                                       ExperimentLogEntryType step,
                                       InstanceDetailsType details,
                                       ExperimentRunType xbRun, FileResolver fileResolver) throws SQLException, ExperimentException
    {
        XarContext context = new XarContext(parentContext);

        InputOutputRefsType.MaterialLSID[] inputMaterialLSIDs = details.getInstanceInputs().getMaterialLSIDArray();
        InputOutputRefsType.DataLSID[] inputDataLSIDs = details.getInstanceInputs().getDataLSIDArray();

        // apply template rules to input refs
        for (InputOutputRefsType.MaterialLSID inputMaterialLSID : inputMaterialLSIDs)
        {
            String declaredType = (inputMaterialLSID.isSetCpasType() ? inputMaterialLSID.getCpasType() : "Material");
            checkMaterialCpasType(declaredType);
            String lsid = LsidUtils.resolveLsidFromTemplate(inputMaterialLSID.getStringValue(), context, declaredType, "Material");
            inputMaterialLSID.setStringValue(lsid);

            if (pbStep.stepProtocolLSID.equals(_run.getProtocolLSID()))
            {
                addMaterialObject(pbStep.stepProtocolLSID, step.getActionSequenceRef(), lsid);
            }
        }
        for (InputOutputRefsType.DataLSID inputDataLSID : inputDataLSIDs)
        {
            String declaredType = (inputDataLSID.isSetCpasType() ? inputDataLSID.getCpasType() : ExpData.DEFAULT_CPAS_TYPE);
            checkDataCpasType(declaredType);
            String lsid = LsidUtils.resolveLsidFromTemplate(inputDataLSID.getStringValue(), context, declaredType, new AutoFileLSIDReplacer(inputDataLSID.getDataFileUrl(), getContainer(), _xarSource));
            inputDataLSID.setStringValue(lsid);

            if (pbStep.stepProtocolLSID.equals(_run.getProtocolLSID()))
            {
                addDataObject(pbStep.stepProtocolLSID, step.getActionSequenceRef(), lsid);
            }

            inputDataLSID.setDataFileUrl(null);
        }

        // special template values for 1  and only one input
        if (inputMaterialLSIDs.length + inputDataLSIDs.length == 1)
        {
            if (inputMaterialLSIDs.length == 1)
                addInputMaterialValuesToContext(inputMaterialLSIDs[0].getStringValue(), context);
            else
                addInputDataValuesToSubstitutionMap(inputDataLSIDs[0].getStringValue(), context);
        }

        // create a Protocolapp node as if it were in the original doc
        ProtocolApplicationBaseType xbProtApp = xbRun.getProtocolApplications().addNewProtocolApplication();

        // wire up parameters by creating a name-based map of param values
        // First get the map with declarations and defaults specified in protocol
        xbProtApp.addNewProtocolApplicationParameters();
        ExpProtocol protocol = _xarSource.getProtocol(pbStep.stepProtocolLSID, "Protocol application step");
        Map<String, ProtocolParameter> mEffectiveParameters = new HashMap<>(protocol.getProtocolParameters());

        //add the parameter values specified for all instances of this step.
        SimpleValueCollectionType xbCommonParams = step.getCommonParametersApplied();
        if (null != xbCommonParams)
            addSimpleValuesToMap(xbCommonParams, mEffectiveParameters);

        // then add the param values specified for this instance of the step
        SimpleValueCollectionType xbInstanceParams = details.getInstanceParametersApplied();
        if (null != xbInstanceParams)
            addSimpleValuesToMap(xbInstanceParams, mEffectiveParameters);

        // now that all the values are consolidated, write them back into the xbean
        for (ProtocolParameter param : mEffectiveParameters.values())
        {
            SimpleValueType val = xbProtApp.getProtocolApplicationParameters().addNewSimpleVal();
            convertParameterToXBean(val, param);
        }

        String outputLSID = LsidUtils.resolveLsidFromTemplate(pbStep.getProtocolApplicationLSIDTemplate(mEffectiveParameters), context, pbStep.protAppType, "ProtocolApplication", fileResolver);
        String outputName = LsidUtils.resolveNameFromTemplate(pbStep.getProtocolApplicationNameTemplate(mEffectiveParameters), context, fileResolver);

        xbProtApp.setAbout(outputLSID);
        xbProtApp.setName(outputName);
        xbProtApp.setCpasType(pbStep.protAppType);
        xbProtApp.setProtocolLSID(pbStep.stepProtocolLSID);
        xbProtApp.setActivityDate(step.getStepCompleted());
        xbProtApp.setActionSequence(step.getActionSequenceRef());
        if (null != trimString(details.getComments()))
            xbProtApp.setComments(trimString(details.getComments()));
        else
            xbProtApp.setComments("");

        //wire up pbStep.inputs
        xbProtApp.addNewInputRefs();
        xbProtApp.getInputRefs().setMaterialLSIDArray(inputMaterialLSIDs);
        xbProtApp.getInputRefs().setDataLSIDArray(inputDataLSIDs);

        // generate output nodes
        xbProtApp.addNewOutputMaterials();
        xbProtApp.addNewOutputDataObjects();

        createAllMaterialOutputs(xbProtApp, pbStep, context, mEffectiveParameters, fileResolver);
        createAllDataOutputs(xbProtApp, pbStep, context, mEffectiveParameters, fileResolver);

            //add an empty properties node
        xbProtApp.addNewProperties();
    }

    private void convertParameterToXBean(SimpleValueType val, AbstractParameter param)
    {
        val.setName(param.getName());
        val.setOntologyEntryURI(param.getOntologyEntryURI());
        val.setValueType(SimpleTypeNames.Enum.forString(param.getValueType()));
        if (param.getValue() == null)
        {
            val.setStringValue(null);
        }
        else if (val.getValueType() == SimpleTypeNames.DATE_TIME)
            val.setStringValue(DateUtil.formatDateTime((Date) param.getValue(), "yyyy-MM-dd'T'HH:mm:ss"));
        else
        {
            val.setStringValue(param.getValue().toString());
        }
    }

    private void createAllMaterialOutputs(ProtocolApplicationBaseType xbProtApp,
                                            ProtocolBean pbStep,
                                            XarContext parentContext,
                                            Map<String, ProtocolParameter> params, FileResolver fileResolver) throws XarFormatException
    {
        XarContext context = new XarContext(parentContext);

        Integer i = getOutputCountByParameter(params.get(OUTPUT_MATERIAL_PER_INSTANCE_EXPRESSION_PARAMETER), context, fileResolver);
        if (i == null)
        {
            i = pbStep.outputMaterialPerInstance;
        }


        // note: a null (xsi:nil=true) value for outputXPerInstance means "undetermined"
        // for these protocolApps,  let the custom handler create the outputs
        if (null != i)
        {
            for (int k = 0; k < i.intValue(); k++)
            {
                context.addSubstitution("OutputInstance", Integer.toString(k));
                createMaterialOutput(xbProtApp, pbStep, context, params, fileResolver);
                fileResolver.advance();
            }
        }
    }

    private Integer getOutputCountByParameter(ProtocolParameter param, XarContext context, FileResolver fileResolver)
            throws XarFormatException
    {
        Integer result = null;
        if (param != null)
        {
            String intString = LsidUtils.resolveNameFromTemplate(param.getStringValue(), context, fileResolver);
            try
            {
                result = new Integer(intString);
            }
            catch (NumberFormatException e)
            {
                throw new XarFormatException("The " + param.getOntologyEntryURI() + " value of " + param.getStringValue() + " resolved to " + intString + " which is not a valid integer");
            }
        }
        return result;
    }

    protected void createMaterialOutput(ProtocolApplicationBaseType xbProtApp,
                                        ProtocolBean pbStep,
                                        XarContext context,
                                        Map<String, ProtocolParameter> params, FileResolver fileResolver) throws XarFormatException
    {
        String outputType = "Material";
        if (null != pbStep.outputMaterialType)
            outputType = pbStep.outputMaterialType;

        String outputName = LsidUtils.resolveNameFromTemplate(pbStep.getOutputMaterialNameTemplate(params), context, fileResolver);
        String outputLSID = LsidUtils.resolveLsidFromTemplate(pbStep.getOutputMaterialLSIDTemplate(params), context, outputType, "Material", fileResolver);
        if (outputLSID == null)
        {
            throw new XarFormatException("Could not determine Material Output LSID for protocol with LSID " + pbStep.stepProtocolLSID);
        }

        MaterialBaseType xbMaterial = xbProtApp.getOutputMaterials().addNewMaterial();
        xbMaterial.setAbout(outputLSID);
        xbMaterial.setName(outputName);
        xbMaterial.setCpasType(outputType);

        Material material = new Material();
        material.setName(outputName);
        material.setLSID(outputLSID);
        _allMaterial.put(material.getLSID(), new ExpMaterialImpl(material));

        addMaterialObject(pbStep.stepProtocolLSID, xbProtApp.getActionSequence(), xbMaterial.getAbout());
    }

    private void addMaterialObject(String protocolLSID, int actionSequence, String materialLSID)
    {
        PredecessorStep step = new PredecessorStep(protocolLSID, actionSequence);
        List<String> outputs = _materialOutputs.get(step);
        if (outputs == null)
        {
            outputs = new ArrayList<>();
            _materialOutputs.put(step, outputs);
        }
        outputs.add(materialLSID);
    }

    private void createAllDataOutputs(ProtocolApplicationBaseType xbProtApp,
                                        ProtocolBean pbStep,
                                        XarContext parentContext,
                                        Map<String, ProtocolParameter> effectiveParameters, FileResolver fileResolver) throws XarFormatException
    {
        XarContext context = new XarContext(parentContext);

        Integer i = getOutputCountByParameter(effectiveParameters.get(OUTPUT_DATA_PER_INSTANCE_EXPRESSION_PARAMETER), context, fileResolver);
        if (i == null)
        {
            i = pbStep.outputDataPerInstance;
        }

        // note: a null (xsi:nil=true) value for outputXPerInstance means "undetermined"
        // for these protocolApps,  let the custom handler create the outputs
        if (null != i)
        {
            for (int k = 0; k < i.intValue(); k++)
            {
                context.addSubstitution("OutputInstance", Integer.toString(k));
                createDataOutput(xbProtApp, pbStep, context, effectiveParameters, fileResolver);
                fileResolver.advance();
            }
        }
    }

    private void createDataOutput(ProtocolApplicationBaseType xbProtApp,
                                  ProtocolBean pbStep,
                                  XarContext context,
                                  Map<String, ProtocolParameter> effectiveParameters, FileResolver fileResolver) throws XarFormatException
    {
        String outputType = ExpData.DEFAULT_CPAS_TYPE;
        if (null != pbStep.outputDataType)
            outputType = pbStep.outputDataType;

        String outputUrl = null;
        if (null != pbStep.getOutputDataDirTemplate(effectiveParameters))
            outputUrl = LsidUtils.resolveNameFromTemplate(pbStep.getOutputDataDirTemplate(effectiveParameters), context, fileResolver) + "\\";
        String filename = LsidUtils.resolveNameFromTemplate(pbStep.getOutputDataFileTemplate(effectiveParameters), context, fileResolver);
        if (filename != null)
        {
            outputUrl = outputUrl == null ? filename : outputUrl + filename;
        }

        String outputName = LsidUtils.resolveNameFromTemplate(pbStep.getOutputDataNameTemplate(effectiveParameters), context, fileResolver);
        String outputLSID = LsidUtils.resolveLsidFromTemplate(pbStep.getOutputDataLSIDTemplate(effectiveParameters), context, outputType, new Replacer.CompoundReplacer(fileResolver, new AutoFileLSIDReplacer(outputUrl, getContainer(), _xarSource)));
        DataBaseType xbData = xbProtApp.getOutputDataObjects().addNewData();
        if (outputLSID == null)
        {
            throw new XarFormatException("Could not determine Data Output LSID for protocol with LSID " + pbStep.stepProtocolLSID);
        }
        xbData.setAbout(outputLSID);
        xbData.setName(outputName);
        xbData.setCpasType(outputType);
        xbData.setDataFileUrl(outputUrl);

        Data data = new Data();
        data.setName(outputName);
        data.setLSID(outputLSID);
        _allData.put(data.getLSID(), data);

        addDataObject(pbStep.stepProtocolLSID, xbProtApp.getActionSequence(), xbData.getAbout());
    }

    private void addDataObject(String protocolLSID, int actionSequence, String dataLSID)
    {
        PredecessorStep step = new PredecessorStep(protocolLSID, actionSequence);
        List<String> outputs = _dataOutputs.get(step);
        if (outputs == null)
        {
            outputs = new ArrayList<>();
            _dataOutputs.put(step, outputs);
        }
        outputs.add(dataLSID);
    }

    private void addInputMaterialValuesToContext(String materialLSID, XarContext context)
    {
        ExpMaterial materialRow = _allMaterial.get(materialLSID);

        String inputName = materialRow.getName();
        String inputLSID = materialRow.getLSID();
        context.addSubstitution("InputName", inputName);
        LsidUtils.addLsidPartsToMap(inputLSID, context);
    }

    private void addInputDataValuesToSubstitutionMap(String lsid, XarContext context) throws XarFormatException
    {
        Data data = _allData.get(lsid);

        String inputName = data.getName();
        String inputLSID = data.getLSID();
        String inputDir = data.getDataFileUrl();
        context.addSubstitution("InputName", inputName);
        LsidUtils.addLsidPartsToMap(inputLSID, context);

        if (null != inputDir)
        {
            try
            {
                File f = new File(new URI(inputDir)).getParentFile();

                File xarDir = _xarSource.getRoot();

                inputDir = FileUtil.relativizeUnix(xarDir, f, true);

                context.addSubstitution("InputDir", inputDir);
            }
            catch (IOException e)
            {
                throw new XarFormatException(e);
            }
            catch (URISyntaxException e)
            {
                throw new XarFormatException(e);
            }
        }
    }

    private void addSimpleValuesToMap(SimpleValueCollectionType xbValues,
                                      Map<String, ProtocolParameter> mValues) throws XarFormatException
    {
        for (SimpleValueType value : xbValues.getSimpleValArray())
        {
            String ontologyEntryURI = trimString(value.getOntologyEntryURI());

            if (!mValues.containsKey(ontologyEntryURI))
                throw new XarFormatException("Parameter not declared :  " + ontologyEntryURI);

            ProtocolParameter param = new ProtocolParameter();
            param.setXMLBeanValue(value, getLog());
            mValues.put(param.getOntologyEntryURI(), param);
        }
    }


    public void expandSteps(ExperimentLogEntryType[] steps,
                            XarContext context,
                            ExperimentRunType xbRun) throws SQLException, ExperimentException
    {
        FileResolver resolver = new FileResolver(_xarSource.getRoot());
        for (ExperimentLogEntryType step : steps)
        {
            if (null == step.getStepCompleted())
                step.setStepCompleted(Calendar.getInstance());

            processLogStep(step, context, xbRun, resolver);
        }
    }

    private List<String> getMaterialOutputs(String protocolStepLSID) throws SQLException
    {
        return getOutputs(protocolStepLSID, _materialOutputs);
    }

    private List<String> getOutputs(String protocolStepLSID, Map<PredecessorStep, List<String>> outputs)
    {
        List<String> result = new ArrayList<>();
        for (ProtocolActionPredecessor predecessor : ExperimentServiceImpl.get().getProtocolActionPredecessors(_run.getProtocolLSID(), protocolStepLSID))
        {
            PredecessorStep step = new PredecessorStep(predecessor.getPredecessorChildLSID(), predecessor.getPredecessorSequence());
            List<String> stepOutputs = outputs.get(step);
            if (stepOutputs != null)
            {
                result.addAll(stepOutputs);
            }
        }
        return result;
    }

    private List<String> getDataOutputs(String protocolStepLSID) throws SQLException
    {
        return getOutputs(protocolStepLSID, _dataOutputs);
    }

    private static class PredecessorStep extends Pair<String, Integer>
    {
        public PredecessorStep(String lsid, int sequence)
        {
            super(lsid, new Integer(sequence));
        }

        public String getLSID()
        {
            return first;
        }

        public int getSequence()
        {
            return second.intValue();
        }
    }

    private static class ProtocolBean
    {
        String stepProtocolLSID;
        String protAppType;
        String outputMaterialType;
        Integer maxInputMaterialPerInstance;
        Integer outputMaterialPerInstance;
        Integer maxInputDataPerInstance;
        Integer outputDataPerInstance;
        String outputDataType;

        private Map<String, Object> _properties;

        ProtocolBean(ProtocolActionStepDetail stepProtocol) throws SQLException
        {
            loadProtocolElementProperties(stepProtocol);

            // overlay properties in the props table
            _properties = OntologyManager.getProperties(stepProtocol.getContainer(), stepProtocolLSID);
        }

        private void loadProtocolElementProperties(ProtocolActionStepDetail stepProtocol)
        {
            stepProtocolLSID = stepProtocol.getLSID();
            protAppType = stepProtocol.getApplicationType();
            if (protAppType == null)
            {
                protAppType = "ProtocolApplication";
            }
            outputMaterialType = stepProtocol.getOutputMaterialType();
            maxInputMaterialPerInstance = stepProtocol.getMaxInputMaterialPerInstance();
            outputMaterialPerInstance = stepProtocol.getOutputMaterialPerInstance();
            maxInputDataPerInstance = stepProtocol.getMaxInputDataPerInstance();
            outputDataPerInstance = stepProtocol.getOutputDataPerInstance();
            outputDataType = stepProtocol.getOutputDataType();
        }

        private String getProperty(String ontologyURI, Map<String, ProtocolParameter> params)
        {
            if (params != null)
            {
                for (ProtocolParameter param : params.values())
                {
                    if (param.getOntologyEntryURI().equals(ontologyURI))
                    {
                        if (param.getStringValue() != null)
                        {
                            return param.getStringValue();
                        }
                    }
                }
            }

            return getProperty(ontologyURI);
        }


        private String getProperty(String ontologyURI)
        {
            if (_properties == null)
            {
                return null;
            }
            return (String) _properties.get(ontologyURI);
        }

        protected String getProtocolApplicationLSIDTemplate(Map<String, ProtocolParameter> effectiveParameters)
        {
            return getProperty("terms.fhcrc.org#XarTemplate.ApplicationLSID", effectiveParameters);
        }

        protected String getProtocolApplicationNameTemplate(Map<String, ProtocolParameter> effectiveParameters)
        {
            return getProperty("terms.fhcrc.org#XarTemplate.ApplicationName", effectiveParameters);
        }

        protected String getOutputDataDirTemplate(Map<String, ProtocolParameter> effectiveParameters)
        {
            return getProperty("terms.fhcrc.org#XarTemplate.OutputDataDir", effectiveParameters);
        }

        protected String getOutputDataFileTemplate(Map<String, ProtocolParameter> effectiveParameters)
        {
            return getProperty("terms.fhcrc.org#XarTemplate.OutputDataFile", effectiveParameters);
        }

        protected String getOutputMaterialLSIDTemplate(Map<String, ProtocolParameter> effectiveParameters)
        {
            return getProperty("terms.fhcrc.org#XarTemplate.OutputMaterialLSID", effectiveParameters);
        }

        protected String getOutputMaterialNameTemplate(Map<String, ProtocolParameter> effectiveParameters)
        {
            return getProperty("terms.fhcrc.org#XarTemplate.OutputMaterialName", effectiveParameters);
        }

        protected String getOutputDataLSIDTemplate(Map<String, ProtocolParameter> effectiveParameters)
        {
            return getProperty("terms.fhcrc.org#XarTemplate.OutputDataLSID", effectiveParameters);
        }

        protected String getOutputDataNameTemplate(Map<String, ProtocolParameter> effectiveParameters)
        {
            return getProperty("terms.fhcrc.org#XarTemplate.OutputDataName", effectiveParameters);
        }
    }
}