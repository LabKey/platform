/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
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
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.experiment.SampleSetAuditProvider;
import org.labkey.experiment.api.ExpDataClassDataTableImpl;
import org.labkey.experiment.api.ExpMaterialImpl;
import org.labkey.experiment.api.ExpSampleSetImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.experiment.controllers.exp.RunInputOutputBean;
import org.labkey.experiment.samples.UploadMaterialSetForm.InsertUpdateChoice;
import org.labkey.experiment.samples.UploadMaterialSetForm.NameFormatChoice;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UploadSamplesHelper
{
    private static final Logger _log = Logger.getLogger(UploadSamplesHelper.class);
    public static final String MATERIAL_INPUT_PARENT = "MaterialInputs";
    public static final String DATA_INPUT_PARENT = "DataInputs";
    public static final String MATERIAL_OUTPUT_CHILD = "MaterialOutputs";
    public static final String DATA_OUTPUT_CHILD = "DataOutputs";
    private static final String MATERIAL_LSID_SUFFIX = "ToBeReplaced";

    UploadMaterialSetForm _form;
    private MaterialSource _materialSource;
    private ExpSampleSetImpl _sampleSet;

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
            Set<String> inputOutputColumns = new HashSet<>();
            Set<DomainProperty> alternateKeyCandidates = new HashSet<>();
            ColumnDescriptor[] columns = loader.getColumns();

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
                        cd.clazz = String.class;
                    }
                    else if (isNameHeader(cd.name))
                    {
                        cd.name = ExpMaterialTable.Column.Name.name();
                        cd.clazz = String.class;
                    }
                    else if (isDescriptionHeader(cd.name))
                    {
                        // load this column
                        cd.clazz = String.class;
                    }
                    else if (isInputOutputHeader(cd.name))
                    {
                        inputOutputColumns.add(cd.name);
                    }
                    else if (!isAliasHeader(cd.name))
                    {
                        cd.load = false;
                    }
                }
                else
                {
                    DomainProperty dp = descriptorsByName.get(cd.name);
                    if ((dp == null && _form.isCreateNewColumnsOnExistingSampleSet()) || _materialSource == null)
                    {
                        dp = domain.addProperty();
                        //todo :  name for domain?
                        dp.setName(cd.name);
                        String legalName = ColumnInfo.legalNameFromName(cd.name);
                        String propertyURI = materialSourceLsid + "#" + legalName;
                        dp.setPropertyURI(propertyURI);
                        dp.setRangeURI(PropertyType.getFromClass(cd.clazz).getTypeUri());
                        //Change name to be fully qualified string for property
                        descriptorsByName.put(dp.getName(), dp);
                        addedProperty = true;
                    }

                    if (dp != null)
                    {
                        PropertyDescriptor pd = dp.getPropertyDescriptor();

                        if (pd != null)
                        {
                            cd.name = dp.getPropertyURI();
                            cd.clazz = pd.getPropertyType().getJavaType();

                            if (pd.isLookup() && pd.getPropertyType() == PropertyType.INTEGER)
                                alternateKeyCandidates.add(dp);
                        }
                        else
                            cd.load = false;
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

            String nameExpression = null;
            boolean usingNameAsUniqueColumn = false;
            List<String> idColPropertyURIs = new ArrayList<>();
            if (_materialSource != null && _materialSource.getIdCol1() != null)
            {
                usingNameAsUniqueColumn = getIdColPropertyURIs(_materialSource, idColPropertyURIs);
            }
            else
            {
                if (NameFormatChoice.IdColumns.equals(_form.getNameFormatEnum()))
                {
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
                else
                {
                    assert NameFormatChoice.NameExpression.equals(_form.getNameFormatEnum());
                    if (_form.getNameExpression() == null || _form.getNameExpression().length() == 0)
                        throw new ExperimentException("Name expression required");

                    nameExpression = _form.getNameExpression();
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

            boolean createdSampleSet = ensureSampleSet(nameExpression, usingNameAsUniqueColumn, idColPropertyURIs, parentColPropertyURI);

            List<Map<String, Object>> maps = loadRows(loader, alternateKeyCandidates);

            Set<PropertyDescriptor> descriptors = getPropertyDescriptors(domain, maps);

            _sampleSet.createSampleNames(maps, null, null, null,
                    _form.getInsertUpdateChoiceEnum() == InsertUpdateChoice.insertIgnore,
                    _form.isAddUniqueSuffixForDuplicateNames());

            Set<String> reusedMaterialLSIDs = Collections.emptySet();
            if (!createdSampleSet)
            {
                reusedMaterialLSIDs = deleteOldPropertyValues(hasCommentHeader, maps, descriptors);
            }

            materials = insertTabDelimitedMaterial(maps, new ArrayList<>(descriptors), _materialSource, reusedMaterialLSIDs, inputOutputColumns, _form.isSkipDerivation());
            _sampleSet.onSamplesChanged(_form.getUser(), null);

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

    private List<Map<String, Object>> loadRows(DataLoader loader, final Set<DomainProperty> alternateKeyCandidates) throws ExperimentException, IOException
    {
        if (alternateKeyCandidates.isEmpty())
            return loader.load();

        // once the Sample Set is guaranteed to exist we can look for the ForeignKey information for alternateKeyCandidates
        UserSchema ssSchema = QueryService.get().getUserSchema(_form.getUser(), _form.getContainer(), SchemaKey.fromParts(SamplesSchema.SCHEMA_NAME));
        TableInfo ssTable = ssSchema.getTable(_sampleSet.getName());

        if (ssTable == null)
            throw new ExperimentException("Unable to resolve table for Sample Set: " + _sampleSet.getName());

        ColumnDescriptor[] columns = loader.getColumns();
        Map<DomainProperty, TableInfo> alternateKeys = new HashMap<>();

        // PRE-CONDITION: Type ("clazz") of ColumnDescriptor must be set prior to asking the loader to load()
        for (DomainProperty dp : alternateKeyCandidates)
        {
            ColumnInfo column = ssTable.getColumn(dp.getName());
            ForeignKey fk = column != null ? column.getFk() : null;
            if (fk != null && fk.allowImportByAlternateKey())
            {
                for (ColumnDescriptor cd : columns)
                {
                    if (cd.name.equals(dp.getPropertyURI()))
                    {
                        cd.clazz = String.class;
                        alternateKeys.put(dp, fk.getLookupTableInfo());
                        break;
                    }
                }
            }
        }

        List<Map<String, Object>> maps = loader.load();

        for (Map.Entry<DomainProperty, TableInfo> entry : alternateKeys.entrySet())
        {
            DomainProperty dp = entry.getKey();
            SimpleTranslator.RemapPostConvert remap = new SimpleTranslator.RemapPostConvert(
                    entry.getValue(), true, SimpleTranslator.RemapMissingBehavior.Error);

            for (Map<String, Object> map : maps)
            {
                Object value = map.get(dp.getPropertyURI());

                if (value instanceof String && !StringUtils.isNumeric((String) value))
                {
                    Object remapped;

                    try
                    {
                        remapped = remap.mappedValue(value);
                    }
                    catch (ConversionException ex)
                    {
                        throw new ExperimentException("Failed to convert '" + dp.getName() + "': " + ex.getMessage(), ex);
                    }

                    if (value != remapped)
                        map.put(dp.getPropertyURI(), remapped);
                }
            }
        }

        return maps;
    }

    // Remember the actual set of properties that we received, so we don't end up deleting unspecified values.
    @NotNull
    private Set<PropertyDescriptor> getPropertyDescriptors(Domain domain, List<Map<String, Object>> maps)
    {
        Set<PropertyDescriptor> descriptors = new HashSet<>();
        descriptors.add(ExperimentProperty.COMMENT.getPropertyDescriptor());

        for (Map<String, Object> map : maps)
        {
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

    private boolean ensureSampleSet(String nameExpression, boolean usingNameAsUniqueColumn, List<String> idColPropertyURIs, String parentColPropertyURI)
    {
        assert _sampleSet == null;
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
            setCols(nameExpression, usingNameAsUniqueColumn, idColPropertyURIs, parentColPropertyURI, _materialSource);
            _materialSource.setMaterialLSIDPrefix(new Lsid("Sample", String.valueOf(_form.getContainer().getRowId()) + "." + setName, "").toString());
            _sampleSet = new ExpSampleSetImpl(_materialSource);
            _sampleSet.save(_form.getUser());
            _materialSource = _sampleSet.getDataObject();
            return true;
        }
        else
        {
            // 6088: update id cols for already existing material source if none have been set
            if (_materialSource.getIdCol1() == null || (_materialSource.getParentCol() == null && parentColPropertyURI != null))
            {
                assert _materialSource.getName().equals(_form.getName());
                assert _materialSource.getLSID().equals(ExperimentServiceImpl.get().getSampleSetLsid(_form.getName(), _form.getContainer()).toString());
                setCols(nameExpression, usingNameAsUniqueColumn, idColPropertyURIs, parentColPropertyURI, _materialSource);
                _sampleSet = new ExpSampleSetImpl(_materialSource);
                _sampleSet.save(_form.getUser());
                _materialSource = _sampleSet.getDataObject();
            }
            else
            {
                _sampleSet = new ExpSampleSetImpl(_materialSource);
            }
            return false;
        }
    }

    private Set<String> deleteOldPropertyValues(boolean hasCommentHeader, List<Map<String, Object>> maps, Set<PropertyDescriptor> descriptors) throws ExperimentException
    {
        Set<String> reusedMaterialLSIDs = new HashSet<>();
        InsertUpdateChoice insertUpdate = _form.getInsertUpdateChoiceEnum();
        ListIterator<Map<String, Object>> li = maps.listIterator();
        Lsid.LsidBuilder builder = generateSampleLSID(_materialSource);
        while (li.hasNext())
        {
            Map<String, Object> map = li.next();
            String name = (String)map.get("Name");
            assert name != null : "Name should have been generated";
            String lsid = builder.setObjectId(name).toString();
            ExpMaterial material = ExperimentService.get().getExpMaterial(lsid);

            if (material == null)
            {
                if (insertUpdate == InsertUpdateChoice.updateOnly)
                    throw new ExperimentException("Can't update; material not found for '" + name + "' in folder '" + getContainer().getPath() + "'");
            }
            else
            {
                if (insertUpdate == InsertUpdateChoice.insertOnly)
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

        return reusedMaterialLSIDs;
    }

    private boolean isNameHeader(String name)
    {
        return name.equalsIgnoreCase(ExpMaterialTable.Column.Name.name());
    }

    private boolean isDescriptionHeader(String name)
    {
        return name.equalsIgnoreCase(ExpMaterialTable.Column.Description.name());
    }

    private boolean isCommentHeader(String name)
    {
        return name.equalsIgnoreCase(ExpMaterialTable.Column.Flag.name()) || name.equalsIgnoreCase("Comment");
    }

    private boolean isAliasHeader(String name)
    {
        return name.equalsIgnoreCase(ExpMaterialTable.Column.Alias.name());
    }

    public static boolean isInputOutputHeader(String name)
    {
        String[] parts = name.split("\\.|/");
        return parts[0].equalsIgnoreCase(DATA_INPUT_PARENT) || parts[0].equalsIgnoreCase(MATERIAL_INPUT_PARENT) ||
                parts[0].equalsIgnoreCase(DATA_OUTPUT_CHILD) || parts[0].equalsIgnoreCase(MATERIAL_OUTPUT_CHILD);
    }

    private boolean isReservedHeader(String name)
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

    private void setCols(String nameExpression, boolean usingNameAsUniqueColumn, List<String> idColPropertyURIs, String parentColPropertyURI, MaterialSource source)
    {
        if (nameExpression != null)
        {
            source.setNameExpression(nameExpression);
            source.setIdCol1(ExpMaterialTable.Column.Name.name());
            source.setIdCol2(null);
            source.setIdCol3(null);
        }
        else
        {
            if (usingNameAsUniqueColumn)
            {
                assert idColPropertyURIs.size() == 1 && idColPropertyURIs.get(0).equals(ExpMaterialTable.Column.Name.name()) : "Expected a single 'Name' id column";
                source.setIdCol1(ExpMaterialTable.Column.Name.name());
                source.setIdCol2(null);
                source.setIdCol3(null);
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
        }

        if (parentColPropertyURI != null)
        {
            source.setParentCol(parentColPropertyURI);
        }
    }

    public List<ExpMaterial> insertTabDelimitedMaterial(List<Map<String, Object>> rows, List<PropertyDescriptor> descriptors, MaterialSource source, Set<String> reusedMaterialLSIDs, Set<String> inputOutputColumns, boolean skipDerivation)
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

        // process any alias values
        Map<String, ExpMaterial> materialMap = new HashMap<>();
        List<String> idColumns = new ArrayList<>();
        getIdColPropertyURIs(source, idColumns);
        for (ExpMaterial material : helper._materials)
        {
            materialMap.put(material.getName(), material);
        }

        TableInfo aliasMap = ExperimentService.get().getTinfoMaterialAliasMap();
        rows.stream().filter(row -> row.containsKey("Alias")).forEach(row -> {
            String name = (String)row.get("Name");
            assert name != null : "Name should have been generated";
            ExpMaterial material = materialMap.get(name);
            if (material != null)
            {
                Object aliases = row.get("Alias");
                ExpDataClassDataTableImpl.AliasInsertHelper.handleInsertUpdate(c, _form.getUser(), material.getLSID(), aliasMap, aliases);
            }
        });

        if (!skipDerivation && (source.getParentCol() != null || !inputOutputColumns.isEmpty()))
        {
            // TODO: replace with a cache
            Map<String, List<ExpMaterialImpl>> potentialParents = new HashMap<>();

            assert rows.size() == helper._materials.size() : "Didn't find as many materials as we have rows";

            List<UploadSampleRunRecord> runRecords = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++)
            {
                Map<String, Object> row = rows.get(i);
                ExpMaterial material = helper._materials.get(i);

                if (reusedMaterialLSIDs.contains(material.getLSID()))
                {
                    // Since this entry was already in the database, we may need to delete old derivation info
                    clearSampleSourceRun(_form.getUser(), material);
                }

                Pair<RunInputOutputBean, RunInputOutputBean> inputsAndOutputs = resolveInputsAndOutputs(
                        _form.getUser(), getContainer(),
                        source, inputOutputColumns,
                        row, potentialParents, helper);

                if (inputsAndOutputs.first != null)
                {
                    // Add parent derivation run
                    Map<ExpMaterial, String> parentMaterialMap = inputsAndOutputs.first.getMaterials();
                    Map<ExpData, String> parentDataMap = inputsAndOutputs.first.getDatas();
                    record(_form.isMergeDerivations(), runRecords, parentMaterialMap, new HashMap<>(Collections.singletonMap(material, "Sample")), parentDataMap, new HashMap<>());
                }

                if (inputsAndOutputs.second != null)
                {
                    // Add child derivation run
                    Map<ExpMaterial, String> childMaterialMap = inputsAndOutputs.second.getMaterials();
                    Map<ExpData, String> childDataMap = inputsAndOutputs.second.getDatas();
                    record(_form.isMergeDerivations(), runRecords, Collections.singletonMap(material, "Sample"), childMaterialMap, Collections.emptyMap(), childDataMap);
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

    /**
     * Clear the source protocol application for this material.
     * If the run that created this material is not a sample derivation run, throw an error -- we don't
     * want to delete an assay run, for example.
     * If the run has more than the sample as an output, the material is removed as an output of the run
     * otherwise the run will be deleted.
     */
    private static void clearSampleSourceRun(User user, ExpMaterial material) throws ValidationException
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
        if (dataOutputs.isEmpty() && materialOutputs.isEmpty())
        {
            // if run has no other outputs, delete the run completely
            material.setSourceApplication(null);
            material.save(user);
            existingDerivationRun.delete(user);
        }
        else
        {
            // if the existing run has other outputs, remove the run as the source application for this sample
            // and remove it as an output from the run
            material.setSourceApplication(null);
            material.save(user);
            ExpProtocolApplication outputApp = existingDerivationRun.getOutputProtocolApplication();
            if (outputApp != null)
                outputApp.removeMaterialInput(user, material);
            existingSourceApp.removeMaterialInput(user, material);
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
                                                                         @Nullable MaterialSource source)
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

            // TODO: Avoid looking up the SampleSet and DataClass on every iteration
            String[] parts = parentColName.split("\\.|/");
            if (parts.length == 2)
            {
                if (parts[0].equalsIgnoreCase(MATERIAL_INPUT_PARENT))
                {
                    ExpMaterial sample = findMaterial(c, parts[1], parentValue);
                    if (sample != null)
                        parentMaterials.put(sample, "Sample");
                    else
                        throw new ValidationException("Sample input '" + parentValue + "' in SampleSet '" + parts[1] + "' not found");
                }
                else if (parts[0].equalsIgnoreCase(MATERIAL_OUTPUT_CHILD))
                {
                    ExpMaterial sample = findMaterial(c, parts[1], parentValue);
                    if (sample != null)
                        childMaterials.put(sample, "Sample");
                    else
                        throw new ValidationException("Sample output '" + parentValue + "' in SampleSet '" + parts[1] + "' not found");
                }
                else if (parts[0].equalsIgnoreCase(DATA_INPUT_PARENT))
                {
                    if (source != null)
                        ensureTargetColumnLookup(user, c, source, parentColName, "exp.data", parts[1]);
                    ExpData data = findData(c, user, parts[1], parentValue);
                    if (data != null)
                        parentData.put(data, data.getName());
                    else
                        throw new ValidationException("Data input '" + parentValue + "' in DataClass '" + parts[1] + "' not found");
                }
                else if (parts[0].equalsIgnoreCase(DATA_OUTPUT_CHILD))
                {
                    ExpData data = findData(c, user, parts[1], parentValue);
                    if (data != null)
                        childData.put(data, data.getName());
                    else
                        throw new ValidationException("Data output '" + parentValue + "' in DataClass '" + parts[1] + "' not found");
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

    @NotNull
    Pair<RunInputOutputBean, RunInputOutputBean> resolveInputsAndOutputs(User user, Container c, MaterialSource source,
                                                           Set<String> parentColumns,
                                                           Map<String, Object> row,
                                                           Map<String, List<ExpMaterialImpl>> potentialParents,
                                                           MaterialImportHelper helper) throws ExperimentException, ValidationException
    {
        if (source.getParentCol() != null)
        {
            // legacy magic column value method
            Map<ExpMaterial, String> parentInputMap = new HashMap<>();
            String newParent = row.get(source.getParentCol()) == null ? null : row.get(source.getParentCol()).toString();
            if (newParent != null)
            {
                if (parentInputMap.isEmpty())
                {
                    if (potentialParents.isEmpty())
                    {
                        // Map from material name to material of all materials in all sample sets visible from this location
                        potentialParents = ExperimentServiceImpl.get().getSamplesByName(_form.getContainer(), _form.getUser());
                        if (potentialParents.isEmpty())
                            potentialParents.put("NO-SAMPLES", null); // avoid re-querying if there are no samples

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

                    List<ExpMaterial> parentMaterials = resolveParentMaterials(c, newParent, potentialParents);
                    int index = 1;
                    for (ExpMaterial parentMaterial : parentMaterials)
                    {
                        parentInputMap.put(parentMaterial, "Sample" + (index == 1 ? "" : Integer.toString(index)));
                        index++;
                    }
                }
            }

            RunInputOutputBean parents = null;
            if (!parentInputMap.isEmpty())
                parents = new RunInputOutputBean(parentInputMap, Collections.emptyMap());

            return Pair.of(parents, null);
        }
        else
        {
            // support for mapping DataClass or SampleSet objects as a parent input using the column name format:
            // DataInputs/<data class name> or MaterialInputs/<sample set name>. Either / or . works as a delimiter

            // collect pairs of parent column names and parent values
            Set<Pair<String, String>> parentValues = new HashSet<>();
            for (String parentColName : parentColumns)
            {
                Object value = row.get(parentColName);
                Set<Pair<String, String>> values = parentColumnAndNamePairs(value, parentColName);
                if (values == null)
                    continue;

                parentValues.addAll(values);
            }

            return resolveInputsAndOutputs(user, c, parentValues, source);
        }
    }

    private Set<Pair<String, String>> parentColumnAndNamePairs(Object value, String parentColName)
    {
        return parentNames(value, parentColName)
                .map(o -> Pair.of(parentColName, o))
                .collect(Collectors.toSet());
    }

    public static Stream<String> parentNames(Object value, String parentColName)
    {
        if (value == null)
            return Stream.empty();

        Stream<String> values;
        if (value instanceof String)
        {
            values = Arrays.stream(((String)value).split(","));
        }
        else if (value instanceof Collection)
        {
            Collection<?> coll = (Collection)value;
            values = coll.stream().map(String::valueOf);
        }
        else
        {
            throw new IllegalStateException("Expected string or collection for '" + parentColName + "': " + value);
        }

        return values
                .map(String::trim)
                .filter(s -> !s.isEmpty());
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

    private List<ExpMaterial> resolveParentMaterials(Container c, String newParent, Map<String, List<ExpMaterialImpl>> materials) throws ValidationException, ExperimentException
    {
        List<ExpMaterial> parents = new ArrayList<>();

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
                // Couldn't find exactly one match, check if it might be of the form
                // /PROJECT/FOLDER.SAMPLE_SET_NAME.SAMPLE_NAME or just SAMPLE_SET_NAME.SAMPLE_NAME
                int dotIndex = parentName.lastIndexOf(".");
                if (dotIndex != -1)
                {
                    String sampleSetName = parentName.substring(0, dotIndex);

                    // See if there's potentially a container path prefixed
                    int i = sampleSetName.indexOf(".");
                    if (i != -1 && sampleSetName.startsWith("/"))
                    {
                        String folderPath = sampleSetName.substring(0, i);
                        Container targetedContainer = ContainerManager.getForPath(folderPath);
                        // Make sure the container exists and the user has permission to see it
                        if (targetedContainer != null && targetedContainer.hasPermission(_form.getUser(), ReadPermission.class))
                        {
                            c = targetedContainer;
                            sampleSetName = sampleSetName.substring(i + 1);
                        }
                    }

                    String sampleName = parentName.substring(dotIndex + 1);
                    parent = findMaterial(c, sampleSetName, sampleName);
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

    private static ExpMaterial findMaterial(Container c, String sampleSetName, String sampleName) throws ValidationException
    {
        // Could easily do some caching here, but probably not a significant perf issue
        ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(c, sampleSetName, true);
        if (sampleSet == null)
            throw new ValidationException("SampleSet '" + sampleSetName + "' not found");

        return sampleSet.getSample(c, sampleName);
    }

    private static ExpData findData(Container c, User user, String dataClassName, String dataName) throws ValidationException
    {
        // Could easily do some caching here, but probably not a significant perf issue
        ExpDataClass dataClass = ExperimentService.get().getDataClass(c, user, dataClassName);
        if (dataClass == null)
            throw new ValidationException("DataClass '" + dataClassName + "' not found");

        return dataClass.getData(c, dataName);
    }

    private class MaterialImportHelper implements OntologyManager.ImportHelper
    {
        private Container _container;
        private List<String> _idCols;
        private User _user;
        private MaterialSource _source;
        private final Set<String> _reusedMaterialLSIDs;
        private List<ExpMaterial> _materials = new ArrayList<>();
        private int _currentRow = 0;

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
            String name = (String)map.get("Name");
            assert name != null : "Name should have been generated";
            String lsid = generateSampleLSID(_source).setObjectId(name).toString();

            ExpMaterialImpl material;
            if (!_reusedMaterialLSIDs.contains(lsid))
            {
                material = ExperimentServiceImpl.get().createExpMaterial(_container, lsid, name);
                material.setCpasType(_source.getLSID());
                if (map.containsKey("Description"))
                    material.setDescription((String)map.get("Description"));
                material.save(_user);
            }
            else
            {
                material = ExperimentServiceImpl.get().getExpMaterial(lsid);
                assert material != null : "Could not find existing material with lsid " + lsid;
                if (map.containsKey("Description"))
                    material.setDescription((String)map.get("Description"));
                // Save it so that we reset the modified/modified by info
                material.save(_user);
            }
            _materials.add(material);

            return lsid;
        }

        public void afterBatchInsert(int rowCount) throws SQLException
        {
            List<ExpMaterial> materials = new ArrayList<>();
            for (int i = _currentRow; i < rowCount; i++)
            {
                ExpMaterial material = _materials.get(i);
                if (material != null)
                    materials.add(material);
            }

            if (!materials.isEmpty())
                ExperimentService.get().onMaterialsCreated(materials, _container, _user);
            _currentRow = rowCount;
        }


        public void updateStatistics(int currentRow) throws SQLException
        {
        }
    }
}
