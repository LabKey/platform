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

package org.labkey.experiment.samples;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpLineage;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.api.SimpleRunRecord;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.experiment.ExpDataIterators;
import org.labkey.experiment.api.ExpMaterialTableImpl;
import org.labkey.experiment.api.ExpSampleTypeImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.experiment.controllers.exp.RunInputOutputBean;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.labkey.api.exp.api.ExpRunItem.PARENT_IMPORT_ALIAS_MAP_PROP;

public abstract class UploadSamplesHelper
{
    private static final Logger _log = LogManager.getLogger(UploadSamplesHelper.class);
    private static final String MATERIAL_LSID_SUFFIX = "ToBeReplaced";

    private static final String ALIQUOT_DB_SEQ_PREFIX = "SampleAliquot";

    private static final String INVALID_ALIQUOT_PROPERTY = "An aliquot-specific property [%1$s] value has been ignored for a non-aliquot sample.";
    private static final String INVALID_NONALIQUOT_PROPERTY = "A sample property [%1$s] value has been ignored for an aliquot.";


    private static boolean isNameHeader(String name)
    {
        return name.equalsIgnoreCase(ExpMaterialTable.Column.Name.name());
    }

    private static boolean isDescriptionHeader(String name)
    {
        return name.equalsIgnoreCase(ExpMaterialTable.Column.Description.name());
    }

    private static boolean isCommentHeader(String name)
    {
        return name.equalsIgnoreCase(ExpMaterialTable.Column.Flag.name()) || name.equalsIgnoreCase("Comment");
    }

    private static boolean isAliasHeader(String name)
    {
        return name.equalsIgnoreCase(ExpMaterialTable.Column.Alias.name());
    }

    public static boolean isInputOutputHeader(String name)
    {
        if(StringUtils.isBlank(name))
            return false;

        String[] parts = name.split("\\.|/");
        return parts[0].equalsIgnoreCase(ExpData.DATA_INPUT_PARENT) || parts[0].equalsIgnoreCase(ExpMaterial.MATERIAL_INPUT_PARENT) ||
                parts[0].equalsIgnoreCase(ExpData.DATA_OUTPUT_CHILD) || parts[0].equalsIgnoreCase(ExpMaterial.MATERIAL_OUTPUT_CHILD);
    }

    private static boolean isReservedHeader(String name)
    {
        if (isNameHeader(name) || isDescriptionHeader(name) || isCommentHeader(name) || "CpasType".equalsIgnoreCase(name) || isAliasHeader(name))
            return true;
        if (isInputOutputHeader(name))
            return true;
        for (ExpMaterialTable.Column column : ExpMaterialTable.Column.values())
        {
            if (name.equalsIgnoreCase(column.name()))
                return true;
        }
        return false;
    }

    private boolean getIdColPropertyURIs(MaterialSource source, List<String> idColNames)
    {
        boolean usingNameAsUniqueColumn = false;
        if (isNameHeader(source.getIdCol1()))
        {
            idColNames.add(source.getIdCol1());
            usingNameAsUniqueColumn = true;
        }
        else
        {
            idColNames.add(source.getIdCol1());
            if (source.getIdCol2() != null)
            {
                idColNames.add(source.getIdCol2());
            }
            if (source.getIdCol3() != null)
            {
                idColNames.add(source.getIdCol3());
            }
        }
        return usingNameAsUniqueColumn;
    }


    /**
     * Clear the source protocol application for this material.
     * If the run that created this material is not a sample derivation run, throw an error -- we don't
     * want to delete an assay run, for example.
     * If the run has more than the sample as an output, the material is removed as an output of the run
     * otherwise the run will be deleted.
     */
    public static void clearSampleSourceRun(User user, ExpMaterial material) throws ValidationException
    {
        ExpProtocolApplication existingSourceApp = material.getSourceApplication();
        if (existingSourceApp == null)
            return;

        ExpRun existingDerivationRun = existingSourceApp.getRun();
        if (existingDerivationRun == null)
            return;

        ExpProtocol protocol = existingDerivationRun.getProtocol();

        if (ExperimentServiceImpl.get().isSampleAliquot(protocol))
            return;

        if (!ExperimentServiceImpl.get().isSampleDerivation(protocol))
        {
            throw new ValidationException(
                    "Can't remove source run '" + existingDerivationRun.getName() + "'" +
                    " of protocol '" + protocol.getName() + "'" +
                    " for sample '" + material.getName() + "' since it is not a sample derivation run");
        }

        List<ExpData> dataOutputs = existingDerivationRun.getDataOutputs();
        List<ExpMaterial> materialOutputs = existingDerivationRun.getMaterialOutputs();
        if (dataOutputs.isEmpty() && (materialOutputs.isEmpty() || (materialOutputs.size() == 1 && materialOutputs.contains(material))))
        {
            _log.debug("Sample '" + material.getName() + "' has existing source derivation run '" + existingDerivationRun.getRowId() + "' -- run has no other outputs, deleting run");
            // if run has no other outputs, delete the run completely
            material.setSourceApplication(null);
            material.save(user);
            existingDerivationRun.delete(user);
        }
        else
        {
            _log.debug("Sample '" + material.getName() + "' has existing source derivation run '" + existingDerivationRun.getRowId() + "' -- run has other " + dataOutputs.size() + " data outputs and " + materialOutputs.size() + " material outputs, removing sample from run");
            // if the existing run has other outputs, remove the run as the source application for this sample
            // and remove it as an output from the run
            material.setSourceApplication(null);
            material.save(user);
            ExpProtocolApplication outputApp = existingDerivationRun.getOutputProtocolApplication();
            if (outputApp != null)
                outputApp.removeMaterialInput(user, material);
            existingSourceApp.removeMaterialInput(user, material);
            ExperimentService.get().queueSyncRunEdges(existingDerivationRun);
        }
    }

    /**
     * Collect the output material or data into a run record.
     * When merge is true, the outputs will be combined with
     * an existing record with the same input parents, if possible.
     */
    public static void record(boolean merge,
                              List<UploadSampleRunRecord> runRecords,
                              Map<ExpMaterial, String> parentMaterialMap,
                              Map<ExpMaterial, String> childMaterialMap,
                              Map<ExpData, String> parentDataMap,
                              Map<ExpData, String> childDataMap,
                              ExpMaterial aliquotParent,
                              ExpMaterial aliquotChild)
    {
        if (merge)
        {
            Set<ExpMaterial> parentMaterials = parentMaterialMap.keySet();
            Set<ExpData> parentDatas = parentDataMap.keySet();

            // find existing RunRecord with the same set of parents and add output children to it
            for (UploadSampleRunRecord record : runRecords)
            {
                if (record._aliquotInput != null && record._aliquotInput.equals(aliquotParent))
                {
                    record._aliquotOutputs.add(aliquotChild);
                    return;
                }
                else if ((!record.getInputMaterialMap().isEmpty() || !record.getInputDataMap().isEmpty()) && record.getInputMaterialMap().keySet().equals(parentMaterials) && record.getInputDataMap().keySet().equals(parentDatas))
                {
                    if (record._outputMaterial.isEmpty())
                        record._outputMaterial = childMaterialMap;
                    else
                        record._outputMaterial.putAll(childMaterialMap);

                    if (record._outputData.isEmpty())
                        record._outputData = childDataMap;
                    else
                        record._outputData.putAll(childDataMap);
                    return;
                }
            }
        }

        // otherwise, create new run record
        List<ExpMaterial> aliquots = null;
        if (aliquotChild != null)
        {
            aliquots = new LinkedList<>();
            aliquots.add(aliquotChild);
        }

        runRecords.add(new UploadSampleRunRecord(parentMaterialMap, childMaterialMap, parentDataMap, childDataMap, aliquotParent, aliquots));
    }

    public static class UploadSampleRunRecord implements SimpleRunRecord
    {
        private Map<ExpMaterial, String> _inputMaterial;
        Map<ExpMaterial, String> _outputMaterial;
        Map<ExpData, String> _inputData;
        Map<ExpData, String> _outputData;

        ExpMaterial _aliquotInput;
        List<ExpMaterial> _aliquotOutputs;

        public UploadSampleRunRecord(Map<ExpMaterial, String> inputMaterial, Map<ExpMaterial, String> outputMaterial,
                                     Map<ExpData, String> inputData, Map<ExpData, String> outputData,
                                     ExpMaterial aliquotInput, List<ExpMaterial> aliquotChildren)
        {
            _inputMaterial = inputMaterial;
            _outputMaterial = outputMaterial;
            _inputData = inputData;
            _outputData = outputData;
            _aliquotInput = aliquotInput;
            _aliquotOutputs = aliquotChildren;
        }

        @Override
        public Map<ExpMaterial, String> getInputMaterialMap()
        {
            return _inputMaterial;
        }

        @Override
        public Map<ExpMaterial, String> getOutputMaterialMap()
        {
            return _outputMaterial;
        }

        @Override
        public Map<ExpData, String> getInputDataMap()
        {
            return _inputData;
        }

        @Override
        public Map<ExpData, String> getOutputDataMap()
        {
            return _outputData;
        }

        @Override
        public ExpMaterial getAliquotInput()
        {
            return _aliquotInput;
        }

        @Override
        public List<ExpMaterial> getAliquotOutputs()
        {
            return _aliquotOutputs;
        }

    }

    /**
     * support for mapping DataClass or SampleSet objects as a parent input using the column name format:
     * DataInputs/<data class name> or MaterialInputs/<sample type name>. Either / or . works as a delimiter
     *
     * @param runItem the item whose parents are being modified.  If provided, existing parents of the item
     *                will be incorporated into the resolved inputs and outputs
     * @param parentNames set of (parent column name, parent value) pairs.  Parent values that are empty
     *                    indicate tha parent should be removed.
     * @throws ExperimentException
     */
    @NotNull
    public static Pair<RunInputOutputBean, RunInputOutputBean> resolveInputsAndOutputs(User user, Container c, @Nullable ExpRunItem runItem,
                                                                                       Set<Pair<String, String>> parentNames,
                                                                                       @Nullable MaterialSource source,
                                                                                       RemapCache cache,
                                                                                       Map<Integer, ExpMaterial> materialMap,
                                                                                       Map<Integer, ExpData> dataMap,
                                                                                       Map<String, ExpSampleType> sampleTypes,
                                                                                       Map<String, ExpDataClass> dataClasses,
                                                                                       @Nullable String aliquotedFrom,
                                                                                       String dataType /*sample type or source type name*/)
            throws ValidationException, ExperimentException
    {
        Map<ExpMaterial, String> parentMaterials = new LinkedHashMap<>();
        Map<ExpData, String> parentData = new LinkedHashMap<>();
        Set<String> parentDataTypesToRemove = new CaseInsensitiveHashSet();
        Set<String> parentSampleTypesToRemove = new CaseInsensitiveHashSet();

        Map<ExpMaterial, String> childMaterials = new HashMap<>();
        Map<ExpData, String> childData = new HashMap<>();
        boolean isMerge = runItem != null;

        ExpMaterial aliquotParent = null;
        boolean isAliquot = !StringUtils.isEmpty(aliquotedFrom);

        if (isAliquot)
        {
            ExpSampleType sampleType = sampleTypes.computeIfAbsent(dataType, (name) -> SampleTypeService.get().getSampleType(c, user, name));
            if (sampleType == null)
                throw new ValidationException("Invalid sample type: " + dataType);

            aliquotParent = findMaterial(c, user, sampleType, dataType, aliquotedFrom, cache, materialMap);

            if (aliquotParent == null)
            {
                String message = "Aliquot parent '" + aliquotedFrom + "' not found.";
                throw new ValidationException(message);
            }
        }

        for (Pair<String, String> pair : parentNames)
        {
            String parentColName = pair.first;
            String parentValue = pair.second;
            boolean isEmptyParent = StringUtils.isEmpty(parentValue);

            String[] parts = parentColName.split("\\.|/");
            if (parts.length == 1)
            {
                if (parts[0].equalsIgnoreCase("parent"))
                {
                    if (!isEmptyParent)
                    {
                        if (isAliquot)
                        {
                            String message = "Sample derivation parent input is not allowed for aliquots.";
                            throw new ValidationException(message);
                        }

                        ExpMaterial sample = findMaterial(c, user, null, null, parentValue, cache, materialMap);
                        if (sample != null)
                            parentMaterials.put(sample, sampleRole(sample));
                        else
                        {
                            String message = "Sample input '" + parentValue + "' not found";
                            throw new ValidationException(message);
                        }
                    }
                }
            }
            if (parts.length == 2)
            {
                String namePart = QueryKey.decodePart(parts[1]);
                if (parts[0].equalsIgnoreCase(ExpMaterial.MATERIAL_INPUT_PARENT))
                {
                    ExpSampleType sampleType = sampleTypes.computeIfAbsent(namePart, (name) -> SampleTypeService.get().getSampleType(c, user, name));
                    if (sampleType == null)
                        throw new ValidationException(String.format("Invalid import alias: parent SampleType [%1$s] does not exist or may have been deleted", namePart));

                    if (isEmptyParent)
                    {
                        if (isMerge && !isAliquot)
                            parentSampleTypesToRemove.add(namePart);
                    }
                    else
                    {
                        if (isAliquot)
                        {
                            String message = "Sample derivation parent input is not allowed for aliquots";
                            throw new ValidationException(message);
                        }

                        ExpMaterial sample = findMaterial(c, user, sampleType, namePart, parentValue, cache, materialMap);
                        if (sample != null)
                            parentMaterials.put(sample, sampleRole(sample));
                        else
                            throw new ValidationException("Sample '" + parentValue + "' not found in Sample Type '" + namePart + "'.");

                    }
                 }
                else if (parts[0].equalsIgnoreCase(ExpMaterial.MATERIAL_OUTPUT_CHILD))
                {
                    ExpSampleType sampleType = sampleTypes.computeIfAbsent(namePart, (name) -> SampleTypeService.get().getSampleType(c, user, name));
                    if (sampleType == null)
                        throw new ValidationException(String.format("Invalid import alias: child SampleType [%1$s] does not exist or may have been deleted", namePart));

                    if (!isEmptyParent)
                    {
                        ExpMaterial sample = findMaterial(c, user, sampleType, namePart, parentValue, cache, materialMap);
                        if (sample != null)
                        {
                            if (StringUtils.isEmpty(sample.getAliquotedFromLSID()))
                                childMaterials.put(sample, sampleRole(sample));
                            else
                            {
                                String message = "Sample derivation output is not allowed for aliquots.";
                                throw new ValidationException(message);
                            }
                        }
                        else
                            throw new ValidationException("Sample output '" + parentValue + "' not found in Sample Type '" + namePart + "'.");
                    }
                }
                else if (parts[0].equalsIgnoreCase(ExpData.DATA_INPUT_PARENT))
                {
                    ExpDataClass dataClass = dataClasses.computeIfAbsent(namePart, (name) -> ExperimentService.get().getDataClass(c, user, name));
                    if (dataClass == null)
                        throw new ValidationException(String.format("Invalid import alias: parent DataClass [%1$s] does not exist or may have been deleted", namePart));

                    if (isEmptyParent)
                    {
                        if (isMerge && !isAliquot)
                            parentDataTypesToRemove.add(namePart);
                    }
                    else
                    {
                        if (isAliquot)
                        {
                            String message = parentColName + " is not allowed for aliquots";
                            throw new ValidationException(message);
                        }

                        ExpData data = findData(c, user, dataClass, namePart, parentValue, cache, dataMap);
                        if (data != null)
                            parentData.put(data, dataRole(data, user));
                        else
                        {

                            if (ExpSchema.DataClassCategoryType.sources.name().equalsIgnoreCase(dataClass.getCategory()))
                                throw new ValidationException("Source '" + parentValue + "' not found in Source Type  '" + namePart + "'.");
                            else
                                throw new ValidationException("Data input '" + parentValue + "' not found in in Data Class '" + namePart + "'.");
                        }
                    }
                }
                else if (parts[0].equalsIgnoreCase(ExpData.DATA_OUTPUT_CHILD))
                {
                    ExpDataClass dataClass = dataClasses.computeIfAbsent(namePart, (name) -> ExperimentService.get().getDataClass(c, user, name));
                    if (dataClass == null)
                        throw new ValidationException(String.format("Invalid import alias: child DataClass [%1$s] does not exist or may have been deleted", namePart));

                    if (!isEmptyParent)
                    {
                        ExpData data = findData(c, user, dataClass, namePart, parentValue, cache, dataMap);
                        if (data != null)
                            childData.put(data, dataRole(data, user));
                        else
                            throw new ValidationException("Data output '" + parentValue + "' in DataClass '" + namePart + "' not found");
                    }
                }
            }
        }


        if (isMerge)
        {
            ExpLineageOptions options = new ExpLineageOptions();
            options.setChildren(false);
            options.setDepth(2); // use 2 to get the first generation of parents because the first "parent" is the run

            ExpLineage lineage = ExperimentService.get().getLineage(c, user, runItem, options);
            Pair<Set<ExpData>, Set<ExpMaterial>> currentParents = Pair.of(lineage.getDatas(), lineage.getMaterials());
            if (currentParents.first != null)
            {
                Map<ExpData, String> existingParentData = new HashMap<>();
                currentParents.first.forEach((dataParent) -> {
                    ExpDataClass dataClass = dataParent.getDataClass(user);
                    String role = dataRole(dataParent, user);
                    if (dataClass != null && !parentData.containsValue(role) && !parentDataTypesToRemove.contains(role))
                    {
                        existingParentData.put(dataParent, role);
                    }
                });
                parentData.putAll(existingParentData);
            }
            if (currentParents.second != null)
            {
                boolean isExistingAliquot = false;
                if (runItem instanceof ExpMaterial)
                {
                    ExpMaterial currentMaterial = (ExpMaterial) runItem;
                    isExistingAliquot = !StringUtils.isEmpty(currentMaterial.getAliquotedFromLSID());

                    if (isExistingAliquot && !isAliquot)
                        throw new ValidationException("AliquotedFrom is absent for aliquot " + currentMaterial.getName() + ".");
                    else if (!isExistingAliquot && isAliquot)
                        throw new ValidationException("Unable to change sample to aliquot " + currentMaterial.getName() + ".");
                    else if (isExistingAliquot)
                    {
                        if (!currentMaterial.getAliquotedFromLSID().equals(aliquotParent.getLSID())
                            && !currentMaterial.getAliquotedFromLSID().equals(aliquotParent.getName())) // for insert using merge, parent name is temporarily stored as lsid
                            throw new ValidationException("Aliquot parents cannot be updated for sample " + currentMaterial.getName() + ".");
                        else if (currentMaterial.getAliquotedFromLSID().equals(aliquotParent.getLSID())) // when AliquotedFromLSID is lsid, aliquot is already processed
                            aliquotParent = null; // already exist, not need to recreate
                    }
                }

                Map<ExpMaterial, String> existingParentMaterials = new HashMap<>();
                if (isExistingAliquot && currentParents.second.size() > 1)
                    throw new ValidationException("Invalid parents for aliquot " + runItem.getName() + ".");

                if (!isAliquot)
                {
                    for (ExpMaterial materialParent : currentParents.second)
                    {
                        ExpSampleType sampleType = materialParent.getSampleType();
                        String role = sampleRole(materialParent);
                        if (sampleType != null && !parentMaterials.containsValue(role) && !parentSampleTypesToRemove.contains(role))
                            existingParentMaterials.put(materialParent, role);
                    }
                    parentMaterials.putAll(existingParentMaterials);
                }
            }
        }

        RunInputOutputBean parents = null;

        if (!parentMaterials.isEmpty() || !parentData.isEmpty() || !parentDataTypesToRemove.isEmpty() || !parentSampleTypesToRemove.isEmpty() || aliquotParent != null)
            parents = new RunInputOutputBean(parentMaterials, parentData, aliquotParent, !parentDataTypesToRemove.isEmpty() || !parentSampleTypesToRemove.isEmpty());

        RunInputOutputBean children = null;
        if (!childMaterials.isEmpty() || !childData.isEmpty())
            children = new RunInputOutputBean(childMaterials, childData, null);

        return Pair.of(parents, children);
    }


    public static String sampleRole(ExpMaterial material)
    {
        ExpSampleType st = material.getSampleType();
        return st != null ? st.getName() : "Sample";
    }

    public static String dataRole(ExpData data, User user)
    {
        ExpDataClass dc = data.getDataClass(user);
        return dc != null ? dc.getName() : ExpDataRunInput.DEFAULT_ROLE;
    }

    public static Lsid.LsidBuilder generateSampleLSID(MaterialSource source)
    {
        return new Lsid.LsidBuilder(source.getMaterialLSIDPrefix() + MATERIAL_LSID_SUFFIX);
    }


    private static ExpMaterial findMaterial(Container c, User user, ExpSampleType sampleType, String sampleTypeName, String sampleName, RemapCache cache, Map<Integer, ExpMaterial> materialCache)
            throws ValidationException
    {
        return ExperimentService.get().findExpMaterial(c, user, sampleType, sampleTypeName, sampleName, cache, materialCache);
    }

    private static ExpData findData(Container c, User user, @NotNull ExpDataClass dataClass, @NotNull String dataClassName, String dataName, RemapCache cache, Map<Integer, ExpData> dataCache)
            throws ValidationException
    {
        return ExperimentService.get().findExpData(c, user, dataClass, dataClassName, dataName, cache, dataCache);
    }

    /* this might be generally useful
     * See SimpleTranslator.selectAll(@NotNull Set<String> skipColumns) for similar functionality, but SampleTranslator
     * copies data, this is straight pass through.
     */
    static class DropColumnsDataIterator extends WrapperDataIterator
    {
        int[] indexMap;
        int columnCount = 0;

        DropColumnsDataIterator(DataIterator di, Set<String> drop)
        {
            super(di);
            int inputColumnCount = di.getColumnCount();
            indexMap = new int[inputColumnCount+1];
            for (int inIndex = 0 ; inIndex <= inputColumnCount ; inIndex++ )
            {
                String name = di.getColumnInfo(inIndex).getName();
                if (!drop.contains(name))
                {
                    indexMap[++columnCount] = inIndex;
                }
            }
        }

        @Override
        public int getColumnCount()
        {
            return columnCount;
        }

        @Override
        public Object get(int i)
        {
            return super.get(indexMap[i]);
        }

        @Override
        public ColumnInfo getColumnInfo(int i)
        {
            return super.getColumnInfo(indexMap[i]);
        }

        @Override
        public Object getConstantValue(int i)
        {
            return super.getConstantValue(indexMap[i]);
        }

        @Override
        public Supplier<Object> getSupplier(int i)
        {
            return super.getSupplier(indexMap[i]);
        }
    }


    /* TODO validate/compare functionality of CoerceDataIterator and loadRows() */

    public static class PrepareDataIteratorBuilder implements DataIteratorBuilder
    {
        private static final int BATCH_SIZE = 100;

        final ExpSampleTypeImpl sampletype;
        final DataIteratorBuilder builder;
        final Lsid.LsidBuilder lsidBuilder;
        final ExpMaterialTableImpl materialTable;

        public PrepareDataIteratorBuilder(@NotNull ExpSampleTypeImpl sampletype, TableInfo materialTable, DataIteratorBuilder in, Container container)
        {
            this.sampletype = sampletype;
            this.builder = in;
            this.lsidBuilder = generateSampleLSID(sampletype.getDataObject());
            this.materialTable = materialTable instanceof ExpMaterialTableImpl ? (ExpMaterialTableImpl) materialTable : null;       // TODO: should we throw exception if not
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator source = LoggingDataIterator.wrap(builder.getDataIterator(context));

            // drop columns
            var drop = new CaseInsensitiveHashSet();
            for (int i=1 ; i<=source.getColumnCount() ; i++)
            {
                String name = source.getColumnInfo(i).getName();
                if (isReservedHeader(name))
                {
                    // Allow 'Name' and 'Comment' to be loaded by the TabLoader.
                    // Skip over other reserved names 'RowId', 'Run', etc.
                    if (isCommentHeader(name))
                        continue;
                    if (isNameHeader(name))
                        continue;
                    if (isDescriptionHeader(name))
                        continue;
                    if (isInputOutputHeader(name))
                        continue;
                    if (isAliasHeader(name))
                        continue;
                    drop.add(name);
                }
            }
            if (!drop.isEmpty())
                source = new DropColumnsDataIterator(source, drop);

//            CoerceDataIterator to handle the lookup/alternatekeys functionality of loadRows(),
//            TODO check if this covers all the functionality, in particular how is alternateKeyCandidates used?
            DataIterator c = LoggingDataIterator.wrap(new _SamplesCoerceDataIterator(source, context, sampletype, materialTable));

            // auto gen a sequence number for genId - reserve BATCH_SIZE numbers at a time so we don't select the next sequence value for every row
            SimpleTranslator addGenId = new SimpleTranslator(c, context);
            addGenId.setDebugName("add genId");
            Set<String> idColNames = Sets.newCaseInsensitiveHashSet("genId");
            materialTable.getColumns().stream().filter(ColumnInfo::isUniqueIdField).forEach(columnInfo -> {
                idColNames.add(columnInfo.getName());
            });
            addGenId.selectAll(idColNames);

            ColumnInfo genIdCol = new BaseColumnInfo(FieldKey.fromParts("genId"), JdbcType.INTEGER);
            final int batchSize = context.getInsertOption().batch ? BATCH_SIZE : 1;
            addGenId.addSequenceColumn(genIdCol, sampletype.getContainer(), ExpSampleTypeImpl.SEQUENCE_PREFIX, sampletype.getRowId(), batchSize);
            addGenId.addUniqueIdDbSequenceColumns(ContainerManager.getRoot(), materialTable);
            DataIterator dataIterator = LoggingDataIterator.wrap(addGenId);

            // Table Counters
            DataIteratorBuilder dib = ExpDataIterators.CounterDataIteratorBuilder.create(DataIteratorBuilder.wrap(dataIterator), sampletype.getContainer(), materialTable, ExpSampleType.SEQUENCE_PREFIX, sampletype.getRowId());
            dataIterator = dib.getDataIterator(context);

            // sampleset.createSampleNames() + generate lsid
            // TODO does not handle insertIgnore
            DataIterator names = new _GenerateNamesDataIterator(sampletype, DataIteratorUtil.wrapMap(dataIterator, false), context, batchSize);

            return LoggingDataIterator.wrap(names);
        }
    }


    static class _GenerateNamesDataIterator extends SimpleTranslator
    {
        final ExpSampleTypeImpl sampletype;
        final NameGenerator nameGen;
        final NameGenerator aliquotNameGen;
        final NameGenerator.State nameState;
        final Lsid.LsidBuilder lsidBuilder;
        final Container _container;
        final int _batchSize;
        boolean first = true;
        Map<String, String> importAliasMap = null;

        String generatedName = null;
        String generatedLsid = null;

        _GenerateNamesDataIterator(ExpSampleTypeImpl sampletype, MapDataIterator source, DataIteratorContext context, int batchSize)
        {
            super(source, context);
            this.sampletype = sampletype;
            try
            {
                this.importAliasMap = sampletype.getImportAliasMap();
            }
            catch (IOException e)
            {
                // do nothing
            }
            nameGen = sampletype.getNameGenerator();
            aliquotNameGen = sampletype.getAliquotNameGenerator();
            nameState = nameGen.createState(true);
            lsidBuilder = generateSampleLSID(sampletype.getDataObject());
            _container = sampletype.getContainer();
            _batchSize = batchSize;
            CaseInsensitiveHashSet skip = new CaseInsensitiveHashSet();
            skip.addAll("name","lsid", "rootmateriallsid");
            selectAll(skip);

            addColumn(new BaseColumnInfo("name",JdbcType.VARCHAR), (Supplier)() -> generatedName);
            addColumn(new BaseColumnInfo("lsid",JdbcType.VARCHAR), (Supplier)() -> generatedLsid);
            // Ensure we have a cpasType column and it is of the right value
            addColumn(new BaseColumnInfo("cpasType",JdbcType.VARCHAR), new SimpleTranslator.ConstantColumn(sampletype.getLSID()));
        }

        void onFirst()
        {
            first = false;
        }

        @Override
        protected void processNextInput()
        {
            Map<String,Object> map = ((MapDataIterator)getInput()).getMap();

            String aliquotedFrom = null;
            Object aliquotedFromObj = map.get("AliquotedFrom");
            if (aliquotedFromObj != null)
            {
                if (aliquotedFromObj instanceof String)
                {
                    aliquotedFrom = (String) aliquotedFromObj;
                }
                else if (aliquotedFromObj instanceof Number)
                {
                    aliquotedFrom = aliquotedFromObj.toString();
                }
            }

            boolean isAliquot = !StringUtils.isEmpty(aliquotedFrom);

            try
            {
                Supplier<Map<String, Object>> extraPropsFn = () -> {
                    if (importAliasMap != null)
                        return Map.of(PARENT_IMPORT_ALIAS_MAP_PROP, importAliasMap);
                    else
                        return Collections.emptyMap();
                };

                generatedName = nameGen.generateName(nameState, map, null, null, extraPropsFn, isAliquot ? aliquotNameGen.getParsedNameExpression() : null);;

                generatedLsid = lsidBuilder.setObjectId(generatedName).toString();
            }
            catch (NameGenerator.DuplicateNameException dup)
            {
                addRowError("Duplicate name '" + dup.getName() + "' on row " + dup.getRowNumber());
            }
            catch (NameGenerator.NameGenerationException e)
            {
                // Failed to generate a name due to some part of the expression not in the row
                if (isAliquot)
                {
                    addRowError("Failed to generate name for aliquot on row " + e.getRowNumber() + " using aliquot naming pattern " + sampletype.getAliquotNameExpression() + ". Check the syntax of the aliquot naming pattern and the data values for the aliquot.");
                }
                else
                {
                    if (sampletype.hasNameExpression())
                        addRowError("Failed to generate name for sample on row " + e.getRowNumber() + " using naming pattern " + sampletype.getNameExpression() + ". Check the syntax of the naming pattern and the data values for the sample.");
                    else if (sampletype.hasNameAsIdCol())
                        addRowError("Name is required for sample on row " + e.getRowNumber());
                    else
                        addRowError("All id columns are required for sample on row " + e.getRowNumber());
                }
            }
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            // consider add  onFirst() as callback from SimpleTranslator
            if (first)
                onFirst();

            // calls processNextInput()
            return super.next();
        }

        @Override
        public void close() throws IOException
        {
            super.close();
            if (null != nameState)
                nameState.close();
        }
    }

    static class _SamplesCoerceDataIterator extends SimpleTranslator
    {
        private final ExpSampleTypeImpl _sampleType;

        public _SamplesCoerceDataIterator(DataIterator source, DataIteratorContext context, ExpSampleTypeImpl sampleType, ExpMaterialTableImpl materialTable)
        {
            super(source, context);
            _sampleType = sampleType;
            setDebugName("Coerce before trigger script - samples");
            init(materialTable, context.getInsertOption().useImportAliases);
        }

        void init(TableInfo target, boolean useImportAliases)
        {
            Map<String,ColumnInfo> targetMap = DataIteratorUtil.createTableMap(target, useImportAliases);
            Set<String> seen = new CaseInsensitiveHashSet();
            DataIterator di = getInput();
            int count = di.getColumnCount();

            Map<String, Boolean> propertyFields = new CaseInsensitiveHashMap<>();
            for (DomainProperty dp : _sampleType.getDomain().getProperties())
            {
                propertyFields.put(dp.getName(), ExpSchema.DerivationDataScopeType.ChildOnly.name().equalsIgnoreCase(dp.getDerivationDataScope()));
            }

            int derivationDataColInd = -1;
            for (int i=1 ; i<=count ; i++)
            {
                ColumnInfo from = di.getColumnInfo(i);
                if (from != null)
                {
                    if ("AliquotedFrom".equalsIgnoreCase(from.getName()))
                    {
                        derivationDataColInd = i;
                        break;
                    }
                }
            }
            for (int i=1 ; i<=count ; i++)
            {
                ColumnInfo from = di.getColumnInfo(i);
                ColumnInfo to = targetMap.get(from.getName());

                if (null != to)
                {
                    String name = to.getName();
                    boolean isPropertyField = propertyFields.containsKey(name);
                    seen.add(to.getName());

                    String ignoredAliquotPropValue = String.format(INVALID_ALIQUOT_PROPERTY, name);
                    String ignoredMetaPropValue = String.format(INVALID_NONALIQUOT_PROPERTY, name);
                    if (to.getPropertyType() == PropertyType.ATTACHMENT || to.getPropertyType() == PropertyType.FILE_LINK)
                    {
                        if (isPropertyField)
                        {
                            ColumnInfo clone = new BaseColumnInfo(to);
                            addColumn(clone, new DerivationScopedColumn(i, derivationDataColInd, propertyFields.get(name), ignoredAliquotPropValue, ignoredMetaPropValue));
                        }
                        else
                            addColumn(to, i);
                    }
                    else if (to.getFk() instanceof MultiValuedForeignKey)
                    {
                        // pass-through multi-value columns -- converting will stringify a collection
                        if (isPropertyField)
                        {
                            var col = new BaseColumnInfo(getInput().getColumnInfo(i));
                            col.setName(name);
                            addColumn(col, new DerivationScopedColumn(i, derivationDataColInd, propertyFields.get(name), ignoredAliquotPropValue, ignoredMetaPropValue));
                        }
                        else
                            addColumn(to.getName(), i);
                    }
                    else
                    {
                        if (isPropertyField)
                        {
                            _addConvertColumn(name, i, to.getJdbcType(), to.getFk(), derivationDataColInd, propertyFields.get(name));
                        }
                        else
                            addConvertColumn(to.getName(), i, to.getJdbcType(), to.getFk(), true);
                    }
                }
                else
                {
                    if (derivationDataColInd == i && _context.getInsertOption().mergeRows)
                    {
                        addColumn("AliquotedFromLSID", i); // temporarily populate sample name as lsid for merge, used to differentiate insert vs update for merge
                    }

                    addColumn(i);
                }
            }
        }

        private void _addConvertColumn(String name, int fromIndex, JdbcType toType, ForeignKey toFk, int derivationDataColInd, boolean isAliquotField)
        {
            var col = new BaseColumnInfo(getInput().getColumnInfo(fromIndex));
            col.setName(name);
            col.setJdbcType(toType);
            if (toFk != null)
                col.setFk(toFk);

            _addConvertColumn(col, fromIndex, derivationDataColInd, isAliquotField);
        }

        private void _addConvertColumn(ColumnInfo col, int fromIndex, int derivationDataColInd, boolean isAliquotField)
        {
            SimpleConvertColumn c = createConvertColumn(col, fromIndex, true);
            c = new DerivationScopedConvertColumn(fromIndex, c, derivationDataColInd, isAliquotField, String.format(INVALID_ALIQUOT_PROPERTY, col.getName()), String.format(INVALID_NONALIQUOT_PROPERTY, col.getName()));

            addColumn(col, c);
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            return super.next();
        }

        @Override
        public Object get(int i)
        {
            return super.get(i);
        }

        @Override
        protected Object addConversionException(String fieldName, Object value, JdbcType target, Exception x)
        {
            return value;
        }
    }
}
