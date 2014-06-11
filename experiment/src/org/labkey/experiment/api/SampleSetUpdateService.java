/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.Pair;
import org.labkey.experiment.samples.UploadMaterialSetForm;
import org.labkey.experiment.samples.UploadSamplesHelper;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.labkey.experiment.samples.UploadMaterialSetForm.InsertUpdateChoice;

/**
 * User: kevink
 */
class SampleSetUpdateService extends AbstractQueryUpdateService
{
    private ExpSampleSetImpl _ss;

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

    private List<ExpMaterial> insertOrUpdate(InsertUpdateChoice insertUpdate, User user, Container container, List<Map<String, Object>> originalRows)
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
                if (value instanceof MultipartFile)
                {
                    try
                    {
                        // Once we've found one, write it to disk and replace the row's value with just the File reference to it
                        MultipartFile multipartFile = (MultipartFile)value;
                        if (multipartFile.isEmpty())
                        {
                            throw new ValidationException("File " + multipartFile.getOriginalFilename() + " for field " + entry.getKey() + " has no content");
                        }
                        File dir = AssayFileWriter.ensureUploadDirectory(container, "sampleset");
                        File file = AssayFileWriter.findUniqueFileName(multipartFile.getOriginalFilename(), dir);
                        multipartFile.transferTo(file);
                        value = file;
                    }
                    catch (ExperimentException | IOException e)
                    {
                        throw new QueryUpdateServiceException(e);
                    }
                }
                else if (value instanceof SpringAttachmentFile)
                {
                    SpringAttachmentFile saf = (SpringAttachmentFile)value;
                    try
                    {
                        File dir = AssayFileWriter.ensureUploadDirectory(container, "sampleset");
                        File file = AssayFileWriter.findUniqueFileName(saf.getFilename(), dir);
                        saf.saveTo(file);
                    }
                    catch (IOException | ExperimentException e)
                    {
                        throw new QueryUpdateServiceException(e);
                    }
                }
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
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext)
            throws DuplicateKeyException, QueryUpdateServiceException, SQLException
    {
        try
        {
            List<ExpMaterial> materials = insertOrUpdate(InsertUpdateChoice.insertOnly, user, container, rows);
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
        return null;
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        try
        {
            List<ExpMaterial> materials = insertOrUpdate(InsertUpdateChoice.updateOnly, user, container, rows);
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
    }

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        int[] ids = new int[keys.size()];
        List<Map<String, Object>> result = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++)
        {
            Map<String, Object> k = keys.get(i);
            Integer rowId = getMaterialRowId(k);
            Map<String, Object> map = getMaterialMap(rowId, getMaterialLsid(k));
            if (map == null)
                throw new QueryUpdateServiceException("No Sample Set Material found for rowId or LSID");

            if (rowId == null)
                rowId = getMaterialRowId(map);
            if (rowId == null)
                throw new QueryUpdateServiceException("RowID is required to delete a Sample Set Material");

            ids[i] = rowId.intValue();
            result.add(map);
        }

        ExperimentServiceImpl.get().deleteMaterialByRowIds(user, container, ids);
        return result;
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
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        throw new IllegalStateException();
    }
}
