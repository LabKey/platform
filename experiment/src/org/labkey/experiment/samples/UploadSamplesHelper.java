/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpProtocolOutput;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SimpleRunRecord;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.experiment.SampleSetAuditProvider;
import org.labkey.experiment.api.ExpMaterialImpl;
import org.labkey.experiment.api.ExpSampleSetImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.experiment.samples.UploadMaterialSetForm.InsertUpdateChoice;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class UploadSamplesHelper
{
    private static final Logger _log = Logger.getLogger(UploadSamplesHelper.class);
    public static final String MATERIAL_INPUT_PARENT = "MaterialInputs";
    public static final String DATA_INPUT_PARENT = "DataInputs";

    UploadMaterialSetForm _form;
    private MaterialSource _materialSource;

    public UploadSamplesHelper(UploadMaterialSetForm form)
    {
        this(form, null);
    }

    public UploadSamplesHelper(UploadMaterialSetForm form, MaterialSource materialSource)
    {
        _form = form;
        _materialSource = materialSource;
    }

    public Map<Integer, String> getIdFieldOptions(boolean allowBlank)
    {
        if (_form.getData() != null)
        {
            try
            {
                ColumnDescriptor[] cds = _form.getLoader().getColumns();
                Map<Integer, String> ret = new LinkedHashMap<>();
                if (allowBlank)
                {
                    ret.put(-1, "");
                }
                for (int i = 0; i < cds.length; i++)
                {
                    ret.put(i, cds[i].name);
                }
                return ret;
            }
            catch (IOException e)
            {
                _log.error("Failed to initialize columns from loader.", e);
            }
        }
        return Collections.singletonMap(0, "<Please paste data>");
    }

    public Container getContainer()
    {
        return _form.getContainer();
    }

    // TODO: This function is way to long and difficult to read. Break it out.
    public Pair<MaterialSource, List<ExpMaterial>> uploadMaterials() throws ExperimentException, ValidationException, IOException
    {
        List<ExpMaterial> materials;
        DataLoader loader = _form.getLoader();
        // Look at more rows than normal when inferring types for sample set columns
        // This isn't a big perf hit because the full TSV is already in memory
        String materialSourceLsid;
        if (_materialSource == null)
        {
            materialSourceLsid = ExperimentService.get().getSampleSetLsid(_form.getName(), _form.getContainer()).toString();
            _materialSource = ExperimentServiceImpl.get().getMaterialSource(materialSourceLsid);
            if (_materialSource == null && !_form.isCreateNewSampleSet())
                throw new ExperimentException("Can't create new Sample Set '" + _form.getName() + "'");
        }
        else
        {
            materialSourceLsid = _materialSource.getLSID();
        }

        if (_form.isCreateNewSampleSet() && _form.getName().length() > ExperimentServiceImpl.get().getTinfoMaterialSource().getColumn("Name").getScale())
        {
            throw new ExperimentException("Sample set names are limited to " + ExperimentServiceImpl.get().getTinfoMaterialSource().getColumn("Name").getScale() + " characters");
        }

        try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction())
        {
            ColumnDescriptor[] columns = loader.getColumns();

            Domain domain = PropertyService.get().getDomain(getContainer(), materialSourceLsid);
            if (domain == null)
            {
                if (!_form.isCreateNewSampleSet())
                    throw new ExperimentException("Can't create new domain for Sample Set '" + _form.getName() + "'");
                domain = PropertyService.get().createDomain(getContainer(), materialSourceLsid, _form.getName());
            }
            Map<String, DomainProperty> descriptorsByName = domain.createImportMap(true);

            boolean hasCommentHeader = false;
            boolean addedProperty = false;
            for (ColumnDescriptor cd : columns)
            {
                if (isReservedHeader(cd.name))
                {
                    // Allow 'Name' and 'Comment' to be loaded by the TabLoader.
                    // Skip over other reserved names 'RowId', 'Run', etc.
                    if (isCommentHeader(cd.name))
                    {
                        hasCommentHeader = true;
                        cd.name = ExperimentProperty.COMMENT.getPropertyDescriptor().getPropertyURI();
                    }
                    else if (isNameHeader(cd.name))
                    {
                        cd.name = ExpMaterialTable.Column.Name.name();
                    }
                    else
                    {
                        cd.load = false;
                    }
                }
                else
                {
                    DomainProperty pd = descriptorsByName.get(cd.name);
                    if ((pd == null && _form.isCreateNewColumnsOnExistingSampleSet()) || _materialSource == null)
                    {
                        pd = domain.addProperty();
                        //todo :  name for domain?
                        pd.setName(cd.name);
                        String legalName = ColumnInfo.legalNameFromName(cd.name);
                        String propertyURI = materialSourceLsid + "#" + legalName;
                        pd.setPropertyURI(propertyURI);
                        pd.setRangeURI(PropertyType.getFromClass(cd.clazz).getTypeUri());
                        //Change name to be fully qualified string for property
                        descriptorsByName.put(pd.getName(), pd);
                        addedProperty = true;
                    }

                    if (pd != null)
                    {
                        cd.name = pd.getPropertyURI();
                        cd.clazz = pd.getPropertyDescriptor().getPropertyType().getJavaType();
                    }
                    else
                        cd.load = false;
                }
            }

            if (addedProperty)
            {
                if (_materialSource != null && !_form.isCreateNewColumnsOnExistingSampleSet())
                    throw new ExperimentException("Can't create new columns on existing sample set.");
                // Need to save the domain - it has at least one new property
                domain.save(_form.getUser());
            }

            boolean usingNameAsUniqueColumn = false;
            List<String> idColPropertyURIs = new ArrayList<>();
            if (_materialSource != null && _materialSource.getIdCol1() != null)
            {
                usingNameAsUniqueColumn = getIdColPropertyURIs(_materialSource, idColPropertyURIs);
            }
            else
            {
                idColPropertyURIs = new ArrayList<>();
                if (_form.getIdColumn1() < 0 || _form.getIdColumn1() >= columns.length)
                    throw new ExperimentException("An id column must be be selected to uniquely identify each sample (idColumn1 was " + _form.getIdColumn1() + ")");

                if (isNameHeader(columns[_form.getIdColumn1()].name))
                {
                    idColPropertyURIs.add(columns[_form.getIdColumn1()].name);
                    usingNameAsUniqueColumn = true;
                }
                else
                {
                    idColPropertyURIs.add(columns[_form.getIdColumn1()].name);
                    if (_form.getIdColumn2() >= 0)
                    {
                        if (_form.getIdColumn2() >= columns.length)
                            throw new ExperimentException("idColumn2 out of bounds: " + _form.getIdColumn2());
                        idColPropertyURIs.add(columns[_form.getIdColumn2()].name);
                    }
                    if (_form.getIdColumn3() >= 0)
                    {
                        if (_form.getIdColumn3() >= columns.length)
                            throw new ExperimentException("idColumn3 out of bounds: " + _form.getIdColumn3());
                        idColPropertyURIs.add(columns[_form.getIdColumn3()].name);
                    }
                }
            }
            String parentColPropertyURI;
            if (_materialSource != null && _materialSource.getParentCol() != null)
            {
                parentColPropertyURI = _materialSource.getParentCol();
            }
            else if (_form.getParentColumn() >= 0)
            {
                parentColPropertyURI = columns[_form.getParentColumn()].name;
            }
            else
            {
                parentColPropertyURI = null;
            }

            List<Map<String, Object>> maps = loader.load();

            if (maps.size() > 0)
            {
                for (String uri : idColPropertyURIs)
                {
                    if (!maps.get(0).containsKey(uri))
                    {
                        throw new ExperimentException("Id Columns must match:  Missing uri " + uri + " in row 0");
                    }
                    int i = 0;
                    ListIterator<Map<String, Object>> iter = maps.listIterator();
                    while (iter.hasNext())
                    {
                        i++;
                        Map<String, Object> map = iter.next();
                        if (map.get(uri) == null)
                        {
                            if (uri.contains("#"))
                            {
                                uri = uri.substring(uri.indexOf("#") + 1);
                            }
                            throw new ExperimentException("All rows must contain values for all Id columns: Missing " + uri +
                                    " in row:  " + (i + 1));
                        }
                    }
                }
            }

            Set<PropertyDescriptor> descriptors = getPropertyDescriptors(domain, idColPropertyURIs, maps);


            Set<String> reusedMaterialLSIDs = new HashSet<>();
            if (_materialSource == null)
            {
                assert _form.isCreateNewSampleSet();
                _materialSource = new MaterialSource();
                String setName = PageFlowUtil.encode(_form.getName());
                _materialSource.setContainer(_form.getContainer());
                _materialSource.setDescription("Samples uploaded by " + _form.getUser().getEmail());
                Lsid lsid = ExperimentServiceImpl.get().getSampleSetLsid(_form.getName(), _form.getContainer());
                _materialSource.setLSID(lsid.toString());
                _materialSource.setName(_form.getName());
                setCols(usingNameAsUniqueColumn, idColPropertyURIs, parentColPropertyURI, _materialSource);
                _materialSource.setMaterialLSIDPrefix(new Lsid("Sample", String.valueOf(_form.getContainer().getRowId()) + "." + setName, "").toString());
                ExpSampleSetImpl sampleSet = new ExpSampleSetImpl(_materialSource);
                sampleSet.save(_form.getUser());
                _materialSource = sampleSet.getDataObject();
            }
            else
            {
                // 6088: update id cols for already existing material source if none have been set
                if (_materialSource.getIdCol1() == null || (_materialSource.getParentCol() == null && parentColPropertyURI != null))
                {
                    assert _materialSource.getName().equals(_form.getName());
                    assert _materialSource.getLSID().equals(ExperimentServiceImpl.get().getSampleSetLsid(_form.getName(), _form.getContainer()).toString());
                    setCols(usingNameAsUniqueColumn, idColPropertyURIs, parentColPropertyURI, _materialSource);
                    ExpSampleSetImpl sampleSet = new ExpSampleSetImpl(_materialSource);
                    sampleSet.save(_form.getUser());
                    _materialSource = sampleSet.getDataObject();
                }

                if (maps.size() > 0)
                {
                    Set<String> uploadedPropertyURIs = maps.get(0).keySet();
                    if (!uploadedPropertyURIs.containsAll(idColPropertyURIs))
                    {
                        throw new ExperimentException("Your upload must contain the original id columns");
                    }
                }

                UploadMaterialSetForm.InsertUpdateChoice insertUpdate = _form.getInsertUpdateChoiceEnum();
                ListIterator<Map<String, Object>> li = maps.listIterator();
                while (li.hasNext())
                {
                    Map<String, Object> map = li.next();
                    String name = decideName(map, idColPropertyURIs);
                    Lsid l = new Lsid(_materialSource.getMaterialLSIDPrefix() + "ToBeReplaced");
                    l.setObjectId(name);
                    String lsid = l.toString();
                    ExpMaterial material = ExperimentService.get().getExpMaterial(lsid);

                    if (material == null)
                    {
                        if (insertUpdate == InsertUpdateChoice.updateOnly)
                            throw new ExperimentException("Can't update; material not found for '" + name + "' in folder '" + getContainer().getPath() + "'");
                    }
                    else
                    {
                        if (insertUpdate == UploadMaterialSetForm.InsertUpdateChoice.insertOnly)
                            throw new ExperimentException("Can't insert; material already exists for '" + name + "' in folder '" + material.getContainer().getPath() + "'");

                        if (insertUpdate == InsertUpdateChoice.insertIgnore)
                        {
                            li.remove();
                            continue;
                        }

                        // 13483 : Better handling of SQLException
                        if (!material.getContainer().equals(getContainer()))
                            throw new ExperimentException("A material with LSID " + lsid + " is already loaded into the folder " + material.getContainer().getPath());

                        // 8309 : preserve comment property on existing materials
                        // 10164 : Deleting flag/comment doesn't clear flag/comment after reupload
                        String oldComment = material.getComment();
                        Object newCommentObj = map.get(ExperimentProperty.COMMENT.getPropertyDescriptor().getPropertyURI());
                        String newComment = null == newCommentObj ? null : String.valueOf(newCommentObj);
                        if (StringUtils.isEmpty(newComment) && !hasCommentHeader && oldComment != null)
                        {
                            Map<String, Object> newMap = new HashMap<>(map);
                            newMap.put(ExperimentProperty.COMMENT.getPropertyDescriptor().getPropertyURI(), oldComment);
                            li.set(newMap);
                        }

                        for (PropertyDescriptor descriptor : descriptors)
                        {
                            // Delete values that we have received new versions of
                            OntologyManager.deleteProperty(material.getLSID(), descriptor.getPropertyURI(), material.getContainer(), descriptor.getContainer());
                        }
                        reusedMaterialLSIDs.add(lsid);
                    }
                }
            }
            materials = insertTabDelimitedMaterial(maps, new ArrayList<>(descriptors), _materialSource, reusedMaterialLSIDs);
            new ExpSampleSetImpl(_materialSource).onSamplesChanged(_form.getUser(), null);

            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (ConversionException e)
        {
            throw new ExperimentException(e.getMessage(), e);
        }

        return new Pair<>(_materialSource, materials);
    }

    // Remember the actual set of properties that we received, so we don't end up deleting unspecified values.
    // Check for duplicate names and remove any rows that are duplicates if we are ignoring dupes.
    @NotNull
    private Set<PropertyDescriptor> getPropertyDescriptors(Domain domain, List<String> idColPropertyURIs, List<Map<String, Object>> maps) throws ExperimentException
    {
        InsertUpdateChoice insertUpdate = _form.getInsertUpdateChoiceEnum();

        Set<PropertyDescriptor> descriptors = new HashSet<>();
        descriptors.add(ExperimentProperty.COMMENT.getPropertyDescriptor());

        Set<String> newNames = new CaseInsensitiveHashSet();
        ListIterator<Map<String, Object>> li = maps.listIterator();
        while (li.hasNext())
        {
            Map<String, Object> map = li.next();

            // Check for duplicate names
            String name = decideName(map, idColPropertyURIs);
            if (!newNames.add(name))
            {
                // Issue 23384: SampleSet: import should ignore duplicate rows when ignore duplicates is selected
                if (insertUpdate == InsertUpdateChoice.insertIgnore)
                {
                    li.remove();
                    continue;
                }
                else
                    throw new ExperimentException("Duplicate material: " + name);
            }

            for (Map.Entry<String, Object> entry : map.entrySet())
            {
                DomainProperty prop = domain.getPropertyByURI(entry.getKey());
                if (prop != null)
                {
                    descriptors.add(prop.getPropertyDescriptor());
                }

            }
        }
        return descriptors;
    }

    private boolean isNameHeader(String name)
    {
        return name.equalsIgnoreCase(ExpMaterialTable.Column.Name.name());
    }

    private boolean isCommentHeader(String name)
    {
        return name.equalsIgnoreCase(ExpMaterialTable.Column.Flag.name()) || name.equalsIgnoreCase("Comment");
    }

    private boolean isReservedHeader(String name)
    {
        if (isNameHeader(name) || isCommentHeader(name) || "CpasType".equalsIgnoreCase(name))
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

    private void setCols(boolean usingNameAsUniqueColumn, List<String> idColPropertyURIs, String parentColPropertyURI, MaterialSource source)
    {
        if (usingNameAsUniqueColumn)
        {
            assert idColPropertyURIs.size() == 1 && idColPropertyURIs.get(0).equals(ExpMaterialTable.Column.Name.name()) : "Expected a single 'Name' id column";
            source.setIdCol1(ExpMaterialTable.Column.Name.name());
        }
        else
        {
            assert idColPropertyURIs.size() <= 3 : "Found " + idColPropertyURIs.size() + " id cols but 3 is the limit";
            source.setIdCol1(idColPropertyURIs.get(0));
            if (idColPropertyURIs.size() > 1)
            {
                source.setIdCol2(idColPropertyURIs.get(1));
                if (idColPropertyURIs.size() > 2)
                {
                    source.setIdCol3(idColPropertyURIs.get(2));
                }
            }
        }
        if (parentColPropertyURI != null)
        {
            source.setParentCol(parentColPropertyURI);
        }
    }

    protected String decideName(Map<String, Object> rowMap, List<String> idCols)
    {
        StringBuilder ret = new StringBuilder(String.valueOf(rowMap.get(idCols.get(0))));
        for (int i = 1; i < idCols.size(); i++)
        {
            String col = idCols.get(i);
            ret.append("-");
            Object value = rowMap.get(col);
            if (value != null)
            {
                ret.append(value);
            }
        }
        return ret.toString();
    }

    public List<ExpMaterial> insertTabDelimitedMaterial(List<Map<String, Object>> rows, List<PropertyDescriptor> descriptors, MaterialSource source, Set<String> reusedMaterialLSIDs)
            throws SQLException, ValidationException, ExperimentException
    {
        long start = System.currentTimeMillis();
        _log.info("starting sample insert");

        if (rows.size() == 0)
            return Collections.emptyList();

        Container c = getContainer();

        //Parent object is the MaterialSet type
        int ownerObjectId = OntologyManager.ensureObject(source.getContainer(), source.getLSID());
        MaterialImportHelper helper = new MaterialImportHelper(c, source, _form.getUser(), reusedMaterialLSIDs);

        OntologyManager.insertTabDelimited(c, _form.getUser(), ownerObjectId, helper, descriptors, rows, true);

        if (source.getParentCol() != null)
        {
            Map<String, PropertyDescriptor> descriptorMap = new HashMap<>();
            Map<String, List<ExpMaterialImpl>> potentialParents = null;

            for (PropertyDescriptor descriptor : descriptors)
                descriptorMap.put(descriptor.getPropertyURI(), descriptor);

            assert rows.size() == helper._materials.size() : "Didn't find as many materials as we have rows";

            List<SimpleRunRecord> runRecords = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++)
            {
                Map<String, Object> row = rows.get(i);
                ExpMaterial material = helper._materials.get(i);

                if (reusedMaterialLSIDs.contains(material.getLSID()))
                {
                    // Since this entry was already in the database, we may need to delete old derivation info
                    ExpProtocolApplication existingSourceApp = material.getSourceApplication();
                    if (existingSourceApp != null)
                    {
                        ExpRun existingDerivationRun = existingSourceApp.getRun();
                        if (existingDerivationRun != null)
                        {
                            material.setSourceApplication(null);
                            material.save(_form.getUser());
                            existingDerivationRun.delete(_form.getUser());
                        }
                    }
                }

                Map<ExpProtocolOutput, String> parentInputMap = resolveParentInputs(_form.getUser(), getContainer(), source, descriptorMap.get(source.getParentCol()),
                        row, potentialParents, helper);

                if (!parentInputMap.isEmpty())
                {
                    // Need to create a new derivation run
                    Map<ExpMaterial, String> parentMaterialMap = new HashMap<>();
                    Map<ExpData, String> parentDataMap = new HashMap<>();

                    for (Map.Entry<ExpProtocolOutput, String> entry : parentInputMap.entrySet())
                    {
                        if (entry.getKey() instanceof ExpMaterial)
                        {
                            parentMaterialMap.put((ExpMaterial)entry.getKey(), entry.getValue());
                        }
                        else if (entry.getKey() instanceof ExpData)
                        {
                            parentDataMap.put((ExpData)entry.getKey(), entry.getValue());
                        }
                    }
                    runRecords.add(new UploadSampleRunRecord(parentMaterialMap, Collections.singletonMap(material, "Sample"), parentDataMap, Collections.emptyMap()));
                    //ExperimentService.get().deriveSamples(parentMap, Collections.singletonMap(material, "Sample"), new ViewBackgroundInfo(_form.getContainer(),  _form.getUser(), null), _log);
                }
            }

            if (!runRecords.isEmpty())
            {
                ExperimentService.get().deriveSamplesBulk(runRecords, new ViewBackgroundInfo(_form.getContainer(), _form.getUser(), null), _log);
            }
        }

        SampleSetAuditProvider.SampleSetAuditEvent event = new SampleSetAuditProvider.SampleSetAuditEvent(getContainer().getId(), "Samples inserted or updated in: " + _form.getName());

        event.setSourceLsid(source.getLSID());
        event.setSampleSetName(_form.getName());
        event.setInsertUpdateChoice(_form.getInsertUpdateChoice());

        AuditLogService.get().addEvent(_form.getUser(), event);
        _log.info("finished inserting samples : time elapsed " + (System.currentTimeMillis() - start));

        return helper._materials;
    }

    public static class UploadSampleRunRecord implements SimpleRunRecord
    {
        Map<ExpMaterial, String> _inputMaterial;
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
     * @param parentColName - the name of the parent column
     * @param parentValue - value of the parent column, this would typically be the name of the data or sample
     * @throws ExperimentException
     */
    public static void resolveParentInputs(User user, Container c, String parentColName, String parentValue, @Nullable MaterialSource source,
                                           Map<ExpProtocolOutput, String> parentMap) throws ExperimentException
    {
        // support for mapping DataClass or SampleSet objects as a parent input using the column name format:
        // DataInputs/<data class name> or MaterialInputs/<sample set name>. Either / or . works as a delimiter
        //
        String[] parts = parentColName.split("\\.|/");
        if (parts.length == 2)
        {
            if (parts[0].equalsIgnoreCase(MATERIAL_INPUT_PARENT))
            {
                ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(c, parts[1]);
                if (sampleSet != null)
                {
                    ExpMaterial sample = sampleSet.getSample(c, parentValue);
                    if (sample != null)
                        parentMap.put(sample, "Sample");
                }
            }
            else if (parts[0].equalsIgnoreCase(DATA_INPUT_PARENT))
            {
                if (source != null)
                    ensureTargetColumnLookup(user, c, source, parentColName, "exp.data", parts[1]);
                ExpDataClass dataClass = ExperimentService.get().getDataClass(c, user, parts[1]);
                if (dataClass != null)
                {
                    ExpData data = dataClass.getData(c, parentValue);
                    if (data != null)
                    {
                        parentMap.put(data, data.getName());
                    }
                }
            }
        }
    }

    Map<ExpProtocolOutput, String> resolveParentInputs(User user, Container c, MaterialSource source, PropertyDescriptor sourceDescriptor,
                                                       Map<String, Object> row,
                                                       Map<String, List<ExpMaterialImpl>> potentialParents,
                                                       MaterialImportHelper helper) throws ExperimentException, ValidationException
    {
        Map<ExpProtocolOutput, String> parentInputMap = new HashMap<>();
        String newParent = row.get(source.getParentCol()) == null ? null : row.get(source.getParentCol()).toString();

        if (newParent != null)
        {
            String parentColName = sourceDescriptor != null ? sourceDescriptor.getName() : null;
            if (parentColName != null)
            {
                // support for mapping DataClass or SampleSet objects as a parent input using the column name format:
                // DataInputs/<data class name> or MaterialInputs/<sample set name>. Either / or . works as a delimiter
                //
                resolveParentInputs(user, c, parentColName, newParent, source, parentInputMap);
            }

            // legacy magic column value method (only current way to map to multiple parents)
            if (parentInputMap.isEmpty())
            {
                if (potentialParents == null)
                {
                    // Map from material name to material of all materials in all sample sets visible from this location
                    potentialParents = ExperimentServiceImpl.get().getSamplesByName(_form.getContainer(), _form.getUser());

                    // We need to make sure that the set of potential parents points to the same object as our list of recently
                    // inserted materials. This is important because if creating the sample derivation run changes the material
                    // at all (setting its source run, for example), we need to be sure that we don't later save a different
                    // instance of the same material that doesn't have the edit.
                    for (ExpMaterial material : helper._materials)
                    {
                        List<ExpMaterialImpl> possibleDuplicates = potentialParents.get(material.getName());
                        assert possibleDuplicates != null && !possibleDuplicates.isEmpty() : "There should be at least one material with the same name";
                        if (possibleDuplicates != null)
                        {
                            for (int i = 0; i < possibleDuplicates.size(); i++)
                            {
                                if (possibleDuplicates.get(i).getRowId() == material.getRowId())
                                {
                                    possibleDuplicates.set(i, (ExpMaterialImpl) material);
                                }
                            }
                        }
                    }
                }

                List<ExpProtocolOutput> parentMaterials = resolveParentMaterials(newParent, potentialParents);
                int index = 1;
                for (ExpProtocolOutput parentMaterial : parentMaterials)
                {
                    parentInputMap.put(parentMaterial, "Sample" + (index == 1 ? "" : Integer.toString(index)));
                    index++;
                }
            }
        }
        return parentInputMap;
    }

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

    private List<ExpProtocolOutput> resolveParentMaterials(String newParent, Map<String, List<ExpMaterialImpl>> materials) throws ValidationException, ExperimentException
    {
        List<ExpProtocolOutput> parents = new ArrayList<>();

        String[] parentNames = newParent.split(",");
        for (String parentName : parentNames)
        {
            parentName = parentName.trim();
            List<? extends ExpMaterial> potentialParents = materials.get(parentName);
            if (potentialParents != null && potentialParents.size() == 1)
            {
                parents.add(potentialParents.get(0));
            }
            else
            {
                ExpMaterial parent = null;
                // Couldn't find exactly one match, check if it might be of the form <SAMPLE_SET_NAME>.<SAMPLE_NAME>
                int dotIndex = parentName.indexOf(".");
                if (dotIndex != -1)
                {
                    String sampleSetName = parentName.substring(0, dotIndex);
                    String sampleName = parentName.substring(dotIndex + 1);
                    parent = findParent(sampleSetName, sampleName);
                }
                if (parent != null)
                {
                    parents.add(parent);
                }
                else if (potentialParents == null)
                {
                    throw new ValidationException("Could not find parent material with name '" + parentName + "'.");
                }
                else if (potentialParents.size() > 1)
                {
                    throw new ExperimentException("More than one match for parent material '" + parentName + "' was found. Please prefix the name with the desired sample set, in the format 'SAMPLE_SET.SAMPLE'.");
                }
            }
        }
        return parents;
    }

    private ExpMaterial findParent(String sampleSetName, String sampleName)
    {
        // Could easily do some caching here, but probably not a significant perf issue
        for (ExpSampleSet sampleSet : ExperimentService.get().getSampleSets(getContainer(), _form.getUser(), true))
        {
            // Look for a sample set with the right name
            if (sampleSetName.equals(sampleSet.getName()))
            {
                for (ExpMaterial sample : sampleSet.getSamples())
                {
                    // Look for a sample with the right name
                    if (sample.getName().equals(sampleName))
                    {
                        return sample;
                    }
                }
            }
        }
        return null;
    }

    private class MaterialImportHelper implements OntologyManager.ImportHelper
    {
        private Container _container;
        private List<String> _idCols;
        private User _user;
        private MaterialSource _source;
        private final Set<String> _reusedMaterialLSIDs;
        private List<ExpMaterial> _materials = new ArrayList<>();

        MaterialImportHelper(Container container, MaterialSource source, User user, Set<String> reusedMaterialLSIDs)
        {
            _container = container;
            _idCols = new ArrayList<>();
            getIdColPropertyURIs(source, _idCols);
            _source = source;
            _user = user;
            _reusedMaterialLSIDs = reusedMaterialLSIDs;
        }

        public String beforeImportObject(Map<String, Object> map) throws SQLException
        {
            String name = decideName(map, _idCols);
            Lsid l = new Lsid(_source.getMaterialLSIDPrefix() + "ToBeReplaced");
            l.setObjectId(name);
            String lsid = l.toString();

            ExpMaterialImpl material;
            if (!_reusedMaterialLSIDs.contains(lsid))
            {
                material = ExperimentServiceImpl.get().createExpMaterial(_container, lsid, name);
                material.setCpasType(_source.getLSID());
                material.save(_user);
            }
            else
            {
                material = ExperimentServiceImpl.get().getExpMaterial(lsid);
                assert material != null : "Could not find existing material with lsid " + lsid;
                // Save it so that we reset the modified/modified by info
                material.save(_user);
            }
            _materials.add(material);

            return lsid;
        }

        public void afterBatchInsert(int currentRow) throws SQLException
        {
        }


        public void updateStatistics(int currentRow) throws SQLException
        {
        }
    }


}
