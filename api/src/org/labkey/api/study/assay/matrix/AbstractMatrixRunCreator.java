/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.api.study.assay.matrix;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayDataCollector;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.DefaultAssayRunCreator;
import org.labkey.api.study.assay.ParticipantVisitResolverType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractMatrixRunCreator <ProviderType extends AbstractAssayProvider> extends DefaultAssayRunCreator <ProviderType>
{
    public AbstractMatrixRunCreator(AbstractAssayProvider provider)
    {
        super((ProviderType) provider);
    }

    public abstract String getIdColumnName();
    public abstract String getSetPropertyName();
    public abstract String getRoleName();
    public abstract String getSetName();



    @Override
    public TransformResult transform(AssayRunUploadContext<ProviderType> context, ExpRun run) throws ValidationException
    {
        TransformResult result = super.transform(context, run);

        try
        {
            result = transformSetId(context, run, result);
        }
        catch (ExperimentException e)
        {
            throw new ValidationException(e.getMessage());
        }

        return result;
    }

    //TODO: may not need this
    protected TransformResult transformSetId(AssayRunUploadContext<ProviderType> context, ExpRun run, TransformResult result) throws ValidationException, ExperimentException
    {
        Map<DomainProperty, String> runProps = result.getRunProperties() != null && !result.getRunProperties().isEmpty() ? result.getRunProperties() : context.getRunProperties();
        Map.Entry<DomainProperty, String> setEntry = findSetProperty(runProps);
        if (setEntry == null || setEntry.getValue() == null)
            throw new ValidationException(getSetName() + " set required");

        Integer updateSetId = ensureSet(context, run.getFilePathRoot(), setEntry.getValue());
        if (updateSetId != null)
        {
            DefaultTransformResult ret = new DefaultTransformResult(result);

            Map<DomainProperty, String> updatedRunProps = new HashMap<>(runProps);

            // Set the update set id string value
            updatedRunProps.put(setEntry.getKey(), String.valueOf(updateSetId));

            ret.setRunProperties(updatedRunProps);

            context.setTransformResult(ret);
            result = ret;
        }

        return result;
    }

    /**
     * Ensure the run property (feature or protein seq) "set" actually exists.
     *
     * @param context AssayRunUploadContext
     * @param runPath Path under the pipeline root to look for the set, when set is a path.
     * @param idNameOrFilePath The set id, name, or file path.
     * @return The set id only if it needs to be saved back to the "set" property; otherwise null.
     * @throws ValidationException
     */
    //TODO: may not need this
    public abstract Integer ensureSet(@NotNull AssayRunUploadContext<ProviderType> context, @Nullable File runPath, @NotNull String idNameOrFilePath) throws ValidationException, ExperimentException;

    private Map.Entry<DomainProperty, String> findSetProperty(Map<DomainProperty, String> runProps)
    {
        for (Map.Entry<DomainProperty, String> entry : runProps.entrySet())
        {
            DomainProperty dp = entry.getKey();
            if (getSetPropertyName().equalsIgnoreCase(dp.getName()))
                return entry;
        }

        return null;
    }

    @Override
    protected void addInputMaterials(AssayRunUploadContext<ProviderType> context, Map<ExpMaterial, String> inputMaterials, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        // Attach the materials found in the matrix file to the run
        try
        {
            File dataFile = getPrimaryFile(context);
            try (TabLoader loader = AbstractMatrixDataHandler.createTabLoader(dataFile, getIdColumnName()))
            {
                ColumnDescriptor[] cols = loader.getColumns();

                List<String> columnNames = new ArrayList<>(cols.length);
                for (ColumnDescriptor col : cols)
                    columnNames.add(col.getColumnName());

                Map<String, Integer> samplesMap = AbstractMatrixDataHandler.ensureSamples(context.getContainer(), context.getUser(), columnNames, getIdColumnName());
                List<? extends ExpMaterial> materials = ExperimentService.get().getExpMaterials(samplesMap.values());
                for (ExpMaterial material : materials)
                {
                    // TODO: Check if there is some other role that might be useful (well id)
                    inputMaterials.put(material, getRoleName());
                }
            }
        }
        catch (IOException e)
        {
            throw new ExperimentException("Failed to read from data file", e);
        }
    }

    private File getPrimaryFile(AssayRunUploadContext context) throws IOException, ExperimentException
    {
        Map<String, File> files = context.getUploadedData();
        assert files.containsKey(AssayDataCollector.PRIMARY_FILE);
        return files.get(AssayDataCollector.PRIMARY_FILE);
    }
}
