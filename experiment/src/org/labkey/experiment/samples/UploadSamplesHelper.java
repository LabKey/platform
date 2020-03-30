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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.CoerceDataIterator;
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
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleSetService;
import org.labkey.api.exp.api.SimpleRunRecord;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.experiment.ExpDataIterators;
import org.labkey.experiment.api.ExpMaterialTableImpl;
import org.labkey.experiment.api.ExpSampleSetImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.experiment.controllers.exp.RunInputOutputBean;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public abstract class UploadSamplesHelper
{
    private static final Logger _log = Logger.getLogger(UploadSamplesHelper.class);
    private static final String MATERIAL_LSID_SUFFIX = "ToBeReplaced";


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
                              Map<ExpData, String> childDataMap)
    {
        if (merge)
        {
            Set<ExpMaterial> parentMaterials = parentMaterialMap.keySet();
            Set<ExpData> parentDatas = parentDataMap.keySet();

            // find existing RunRecord with the same set of parents and add output children to it
            for (UploadSampleRunRecord record : runRecords)
            {
                if (record.getInputMaterialMap().keySet().equals(parentMaterials) && record.getInputDataMap().keySet().equals(parentDatas))
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
        runRecords.add(new UploadSampleRunRecord(parentMaterialMap, childMaterialMap, parentDataMap, childDataMap));
    }

    public static class UploadSampleRunRecord implements SimpleRunRecord
    {
        private Map<ExpMaterial, String> _inputMaterial;
        Map<ExpMaterial, String> _outputMaterial;
        Map<ExpData, String> _inputData;
        Map<ExpData, String> _outputData;

        public UploadSampleRunRecord(Map<ExpMaterial, String> inputMaterial, Map<ExpMaterial, String> outputMaterial,
                                     Map<ExpData, String> inputData, Map<ExpData, String> outputData)
        {
            _inputMaterial = inputMaterial;
            _outputMaterial = outputMaterial;
            _inputData = inputData;
            _outputData = outputData;
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
    }

    /**
     * support for mapping DataClass or SampleSet objects as a parent input using the column name format:
     * DataInputs/<data class name> or MaterialInputs/<sample set name>. Either / or . works as a delimiter
     *
     * @param parentNames - set of (parent column name, parent value) pairs
     * @throws ExperimentException
     */
    @NotNull
    public static Pair<RunInputOutputBean, RunInputOutputBean> resolveInputsAndOutputs(User user, Container c,
                                                                         Set<Pair<String, String>> parentNames,
                                                                         @Nullable MaterialSource source,
                                                                         RemapCache cache,
                                                                         Map<Integer, ExpMaterial> materialMap,
                                                                         Map<Integer, ExpData> dataMap)
            throws ExperimentException, ValidationException
    {
        Map<ExpMaterial, String> parentMaterials = new HashMap<>();
        Map<ExpData, String> parentData = new HashMap<>();

        Map<ExpMaterial, String> childMaterials = new HashMap<>();
        Map<ExpData, String> childData = new HashMap<>();

        for (Pair<String, String> pair : parentNames)
        {
            String parentColName = pair.first;
            String parentValue = pair.second;

            String[] parts = parentColName.split("\\.|/");
            if (parts.length == 1)
            {
                if (parts[0].equalsIgnoreCase("parent"))
                {
                    ExpMaterial sample = findMaterial(c, user, null, parentValue, cache, materialMap);
                    if (sample != null)
                        parentMaterials.put(sample, sampleRole(sample));
                    else
                    {
                        String message = "Sample input '" + parentValue + "'";
                        if (parts.length > 1)
                            message += " in SampleSet '" + parts[1] + "'";
                        message += " not found";
                        throw new ValidationException(message);
                    }
                }
            }
            if (parts.length == 2)
            {
                String namePart = QueryKey.decodePart(parts[1]);
                if (parts[0].equalsIgnoreCase(ExpMaterial.MATERIAL_INPUT_PARENT))
                {
                    if (!findMaterialSource(c, user, namePart))
                        throw new ValidationException(String.format("Invalid import alias: parent SampleSet [%1$s] does not exist or may have been deleted", namePart));

                    ExpMaterial sample = findMaterial(c, user, namePart, parentValue, cache, materialMap);
                    if (sample != null)
                        parentMaterials.put(sample, sampleRole(sample));
                    else
                        throw new ValidationException("Sample input '" + parentValue + "' in SampleSet '" + namePart + "' not found");
                }
                else if (parts[0].equalsIgnoreCase(ExpMaterial.MATERIAL_OUTPUT_CHILD))
                {
                    ExpMaterial sample = findMaterial(c, user, namePart, parentValue, cache, materialMap);
                    if (sample != null)
                        childMaterials.put(sample, sampleRole(sample));
                    else
                        throw new ValidationException("Sample output '" + parentValue + "' in SampleSet '" + namePart + "' not found");
                }
                else if (parts[0].equalsIgnoreCase(ExpData.DATA_INPUT_PARENT))
                {
                    if (source != null)
                        ensureTargetColumnLookup(user, c, source, parentColName, "exp.data", namePart);
                    ExpData data = findData(c, user, namePart, parentValue, cache, dataMap);
                    if (data != null)
                        parentData.put(data, dataRole(data, user));
                    else
                        throw new ValidationException("Data input '" + parentValue + "' in DataClass '" + namePart + "' not found");
                }
                else if (parts[0].equalsIgnoreCase(ExpData.DATA_OUTPUT_CHILD))
                {
                    ExpData data = findData(c, user, namePart, parentValue, cache, dataMap);
                    if (data != null)
                        childData.put(data, dataRole(data, user));
                    else
                        throw new ValidationException("Data output '" + parentValue + "' in DataClass '" + namePart + "' not found");
                }
            }
        }

        RunInputOutputBean parents = null;
        if (!parentMaterials.isEmpty() || !parentData.isEmpty())
            parents = new RunInputOutputBean(parentMaterials, parentData);

        RunInputOutputBean children = null;
        if (!childMaterials.isEmpty() || !childData.isEmpty())
            children = new RunInputOutputBean(childMaterials, childData);

        return Pair.of(parents, children);
    }

    public static String sampleRole(ExpMaterial material)
    {
        ExpSampleSet ss = material.getSampleSet();
        return ss != null ? ss.getName() : "Sample";
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

    // CONSIDER: This method shouldn't update the domain to make the property into a lookup..
    private static void ensureTargetColumnLookup(User user, Container c, MaterialSource source, String propName, String schemaName, String queryName) throws ExperimentException
    {
        Domain domain = PropertyService.get().getDomain(c, source.getLSID());
        if (domain != null)
        {
            DomainProperty prop = domain.getPropertyByName(propName);
            if (prop != null && prop.getLookup() == null)
            {
                prop.setLookup(new Lookup(c, schemaName, queryName));
                prop.setHidden(true);
                domain.save(user);
            }
        }
    }


    private static ExpMaterial findMaterial(Container c, User user, String sampleSetName, String sampleName, RemapCache cache, Map<Integer, ExpMaterial> materialCache)
            throws ValidationException
    {
        return ExperimentService.get().findExpMaterial(c, user, sampleSetName, sampleName, cache, materialCache);
    }

    private static ExpData findData(Container c, User user, @NotNull String dataClassName, String dataName, RemapCache cache, Map<Integer, ExpData> dataCache)
            throws ValidationException
    {
        return ExperimentService.get().findExpData(c, user, dataClassName, dataName, cache, dataCache);
    }


    private static boolean findMaterialSource(Container c, User user, String parentName)
    {
        return SampleSetService.get().getSampleSet(c, user, parentName) != null;
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

        final ExpSampleSetImpl sampleset;
        final DataIteratorBuilder builder;
        final Lsid.LsidBuilder lsidBuilder;
        final ExpMaterialTableImpl materialTable;

        public PrepareDataIteratorBuilder(ExpSampleSetImpl sampleset, TableInfo materialTable, DataIteratorBuilder in)
        {
            this.sampleset = sampleset;
            this.builder = in;
            this.lsidBuilder = generateSampleLSID(sampleset.getDataObject());
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
            DataIterator c = LoggingDataIterator.wrap(new CoerceDataIterator(source, context, sampleset.getTinfo(), false));

            // auto gen a sequence number for genId - reserve BATCH_SIZE numbers at a time so we don't select the next sequence value for every row
            SimpleTranslator addGenId = new SimpleTranslator(c, context);
            addGenId.setDebugName("add genId");
            addGenId.selectAll(Sets.newCaseInsensitiveHashSet("genId"));

            ColumnInfo genIdCol = new BaseColumnInfo(FieldKey.fromParts("genId"), JdbcType.INTEGER);
            final int batchSize = context.getInsertOption().batch ? BATCH_SIZE : 1;
            addGenId.addSequenceColumn(genIdCol, sampleset.getContainer(), ExpSampleSetImpl.SEQUENCE_PREFIX, sampleset.getRowId(), batchSize);
            DataIterator dataIterator = LoggingDataIterator.wrap(addGenId);

            // Table Counters
            DataIteratorBuilder dib = ExpDataIterators.CounterDataIteratorBuilder.create(DataIteratorBuilder.wrap(dataIterator), sampleset.getContainer(), materialTable, ExpSampleSet.SEQUENCE_PREFIX, sampleset.getRowId());
            dataIterator = dib.getDataIterator(context);

            // sampleset.createSampleNames() + generate lsid
            // TODO does not handle insertIgnore
            DataIterator names = new _GenerateNamesDataIterator(sampleset, DataIteratorUtil.wrapMap(dataIterator, false), context);

            return LoggingDataIterator.wrap(names);
        }
    }


    static class _GenerateNamesDataIterator extends SimpleTranslator
    {
        final ExpSampleSetImpl sampleset;
        final NameGenerator nameGen;
        final NameGenerator.State nameState;
        final Lsid.LsidBuilder lsidBuilder;
        boolean first = true;

        String generatedName = null;
        String generatedLsid = null;

        _GenerateNamesDataIterator(ExpSampleSetImpl sampleset, MapDataIterator source, DataIteratorContext context)
        {
            super(source, context);
            this.sampleset = sampleset;
            nameGen = sampleset.getNameGenerator();
            nameState = nameGen.createState(true);
            lsidBuilder = generateSampleLSID(sampleset.getDataObject());
            CaseInsensitiveHashSet skip = new CaseInsensitiveHashSet();
            skip.addAll("name","lsid");
            selectAll(skip);

            addColumn(new BaseColumnInfo("name",JdbcType.VARCHAR), (Supplier)() -> generatedName);
            addColumn(new BaseColumnInfo("lsid",JdbcType.VARCHAR), (Supplier)() -> generatedLsid);
            // Ensure we have a cpasType column and it is of the right value
            addColumn(new BaseColumnInfo("cpasType",JdbcType.VARCHAR), new SimpleTranslator.ConstantColumn(sampleset.getLSID()));
        }

        void onFirst()
        {
            first = false;
        }

        @Override
        protected void processNextInput()
        {
            Map<String,Object> map = ((MapDataIterator)getInput()).getMap();
            try
            {
                generatedName = nameGen.generateName(nameState, map);
                generatedLsid = lsidBuilder.setObjectId(generatedName).toString();
            }
            catch (NameGenerator.DuplicateNameException dup)
            {
                addRowError("Duplicate name '" + dup.getName() + "' on row " + dup.getRowNumber());
            }
            catch (NameGenerator.NameGenerationException e)
            {
                // Failed to generate a name due to some part of the expression not in the row
                if (sampleset.hasNameExpression())
                    addRowError("Failed to generate name for Sample on row " + e.getRowNumber());
                else if (sampleset.hasNameAsIdCol())
                    addRowError("Name is required for Sample on row " + e.getRowNumber());
                else
                    addRowError("All id columns are required for Sample on row " + e.getRowNumber());
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
}
