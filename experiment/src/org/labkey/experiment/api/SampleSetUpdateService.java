/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.experiment.api;

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.experiment.samples.UploadMaterialSetForm;
import org.labkey.experiment.samples.UploadSamplesHelper;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.labkey.experiment.samples.UploadMaterialSetForm.InsertUpdateChoice;

/**
 * User: kevink
 */
public class SampleSetUpdateService extends AbstractQueryUpdateService
{
    private ExpSampleSetImpl _ss;

    public enum Options {
        AddUniqueSuffixForDuplicateNames,
        SkipDerivation
    }

    public SampleSetUpdateService(ExpMaterialTableImpl table, ExpSampleSetImpl ss)
    {
        super(table);
        _ss = ss;
    }

    private String getMaterialLsid(Map<String, Object> row)
    {
        Object o = row.get(ExpMaterialTable.Column.LSID.name());
        if (o instanceof String)
            return (String)o;

        return null;
    }


    IntegerConverter _converter = new IntegerConverter();

    private Integer getMaterialRowId(Map<String, Object> row)
    {
        Object o = row.get(ExpMaterialTable.Column.RowId.name());
        if (o != null)
            return (Integer)(_converter.convert(Integer.class, o));

        return null;
    }

    private List<ExpMaterial> insertOrUpdate(InsertUpdateChoice insertUpdate, User user, Container container, List<Map<String, Object>> originalRows, boolean addUniqueSuffix, boolean skipDerivation)
            throws QueryUpdateServiceException, ValidationException
    {
        if (_ss == null)
            throw new IllegalArgumentException("Can't insert or update without a Sample Set.");

        List<Map<String, Object>> rows = writePostedFiles(container, originalRows);

        UploadMaterialSetForm form = new UploadMaterialSetForm();
        form.setContainer(container);
        form.setUser(user);
        form.setName(_ss.getName());
        form.setImportMoreSamples(true);
        form.setParentColumn(-1);
        form.setInsertUpdateChoice(insertUpdate.name());
        form.setCreateNewSampleSet(false);
        form.setCreateNewColumnsOnExistingSampleSet(false);
        if (addUniqueSuffix)
            form.setAddUniqueSuffixForDuplicateNames(true);
        if (skipDerivation)
            form.setSkipDerivation(true);

        translateRowIdToIdCols(rows);

        try
        {
            form.setLoader(new MapLoader(rows));

            UploadSamplesHelper helper = new UploadSamplesHelper(form, _ss.getDataObject());
            Pair<MaterialSource, List<ExpMaterial>> pair = helper.uploadMaterials();
            return pair.second;
        }
        catch (IOException e)
        {
            throw new QueryUpdateServiceException(e);
        }
        catch (ExperimentException e)
        {
            throw new ValidationException(e.getMessage());
        }
    }

    /** For rows that have a RowId but lack values for the Name or ID columns, try to fill in the Name or ID columns */
    private void translateRowIdToIdCols(List<Map<String, Object>> rows)
    {
        for (Map<String, Object> row : rows)
        {
            Object rowIdObject = row.get("RowId");
            if (rowIdObject != null)
            {
                // See if we already have values for all of the Id columns
                boolean foundAllIdCols = true;
                if (_ss.hasNameAsIdCol())
                {
                    if (row.get("Name") == null)
                        foundAllIdCols = false;
                }
                else
                {
                    for (DomainProperty prop : _ss.getIdCols())
                    {
                        if (row.get(prop.getName()) == null)
                            foundAllIdCols = false;
                    }
                }
                if (!foundAllIdCols)
                {
                    try
                    {
                        int rowId = Integer.parseInt(rowIdObject.toString());

                        // Look up the values from the current row in the DB
                        Map<String, Object> oldRowValues = new TableSelector(getQueryTable(), new SimpleFilter(FieldKey.fromParts("RowId"), rowId), null).getMap();
                        if (oldRowValues != null)
                        {
                            // Stick them into the row
                            if (_ss.hasNameAsIdCol())
                            {
                                if (row.get("Name") == null)
                                    row.put("Name", oldRowValues.get("Name"));
                            }
                            else
                            {
                                for (DomainProperty prop : _ss.getIdCols())
                                {
                                    if (row.get(prop.getName()) == null)
                                        row.put(prop.getName(), oldRowValues.get(prop.getName()));
                                }
                            }
                        }
                    }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    /** Write any files that were posted into a sampleset subdirectory */
    private List<Map<String, Object>> writePostedFiles(Container container, List<Map<String, Object>> originalRows)
            throws QueryUpdateServiceException, ValidationException
    {
        List<Map<String, Object>> rows = new ArrayList<>(originalRows.size());
        // Iterate through all of the values in all of the rows, looking for MultipartFiles
        for (Map<String, Object> originalRow : originalRows)
        {
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            for (Map.Entry<String, Object> entry : originalRow.entrySet())
            {
                Object value = entry.getValue();
                value = saveFile(container, entry.getKey(), value, "sampleset");
                row.put(entry.getKey(), value);
            }
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> getMaterialMap(Integer rowId, String lsid)
            throws QueryUpdateServiceException, SQLException
    {
        Filter filter;
        if (rowId != null)
            filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.RowId), rowId);
        else if (lsid != null)
            filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.LSID), lsid);
        else
            throw new QueryUpdateServiceException("Either RowId or LSID is required to get Sample Set Material.");

        return new TableSelector(getQueryTable(), filter, null).getMap();
    }

    @Override
    public List<Map<String, Object>> getRows(User user, Container container, List<Map<String, Object>> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = new ArrayList<>(keys.size());
        for (Map<String, Object> k : keys)
        {
            result.add(getMaterialMap(getMaterialRowId(k), getMaterialLsid(k)));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
            throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        try
        {
            boolean addUniqueSuffix = false;
            boolean skipDerivation = false;
            if (configParameters != null)
            {
                if (configParameters.containsKey(Options.AddUniqueSuffixForDuplicateNames))
                    addUniqueSuffix = true;
                if (configParameters.containsKey(Options.SkipDerivation))
                    skipDerivation = true;
            }

            List<ExpMaterial> materials = insertOrUpdate(InsertUpdateChoice.insertOnly, user, container, rows, addUniqueSuffix, skipDerivation);
            List<Map<String, Object>> result = new ArrayList<>(materials.size());
            for (ExpMaterial material : materials)
            {
                result.add(getMaterialMap(material.getRowId(), material.getLSID()));
            }
            return result;
        }
        catch (ValidationException vex)
        {
            errors.addRowError(vex);
        }
        catch (RuntimeValidationException vex)
        {
            errors.addRowError(vex.getValidationException());
        }
        return null;
    }

    @Override
    public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws SQLException
    {
        DataIterator iterator = rows.getDataIterator(getDataIteratorContext(errors, InsertOption.MERGE, configParameters));
        List<Map<String, Object>> maps = iterator.stream().collect(Collectors.toList());
        try
        {
            return insertOrUpdate(InsertUpdateChoice.insertOrUpdate, user, container, maps, false, false).size();
        }
        catch (ValidationException vex)
        {
            errors.addRowError(vex);
            return 0;
        }
        catch (QueryUpdateServiceException e)
        {
            throw new UnexpectedException(e);
        }
    }

    @Override
    protected int truncateRows(User user, Container container) throws QueryUpdateServiceException, SQLException
    {
        List<ExpMaterialImpl> samples = _ss.getSamples(container);
        for (ExpMaterialImpl sample : samples)
        {
            sample.delete(user);
        }
        return samples.size();
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        try
        {
            List<ExpMaterial> materials = insertOrUpdate(InsertUpdateChoice.updateOnly, user, container, rows, false, false);
            List<Map<String, Object>> result = new ArrayList<>(materials.size());
            for (ExpMaterial material : materials)
            {
                result.add(getMaterialMap(material.getRowId(), material.getLSID()));
            }
            return result;
        }
        catch (ValidationException vex)
        {
            throw new BatchValidationException(Arrays.asList(vex), extraScriptContext);
        }
        catch (RuntimeValidationException vex)
        {
            throw new BatchValidationException(Arrays.asList(vex.getValidationException()), extraScriptContext);
        }
    }

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        List<Integer> ids = new LinkedList<>();
        List<Map<String, Object>> result = new ArrayList<>(keys.size());

        for (Map<String, Object> k : keys)
        {
            Integer rowId = getMaterialRowId(k);
            Map<String, Object> map = getMaterialMap(rowId, getMaterialLsid(k));
            if (map == null)
                throw new QueryUpdateServiceException("No Sample Set Material found for rowId or LSID");

            if (rowId == null)
                rowId = getMaterialRowId(map);
            if (rowId == null)
                throw new QueryUpdateServiceException("RowID is required to delete a Sample Set Material");

            ids.add(rowId);
            result.add(map);
        }

        ExperimentServiceImpl.get().deleteMaterialByRowIds(user, container, ids);
        return result;
    }


    @Override
    public int loadRows(User user, Container container, DataIteratorBuilder rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext) throws SQLException
    {
        return importRows(user, container, rows, context.getErrors(), context.getConfigParameters(), extraScriptContext);
    }



    /* don't need to implement these since we override insertRows() etc. */

    @Override
    protected Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        throw new IllegalStateException();
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new IllegalStateException();
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new IllegalStateException();
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
    {
        throw new IllegalStateException();
    }
}
