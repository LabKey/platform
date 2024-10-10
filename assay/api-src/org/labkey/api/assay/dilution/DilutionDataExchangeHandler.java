/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.assay.dilution;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.assay.plate.PlateBasedDataExchangeHandler;
import org.labkey.api.assay.plate.AbstractPlateBasedAssayProvider;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.assay.AssayService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.vfs.FileLike;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: Apr 14, 2009
 * Time: 10:30:43 AM
 */
public class DilutionDataExchangeHandler extends PlateBasedDataExchangeHandler
{
    @Override
    public Pair<FileLike, Set<FileLike>> createTransformationRunInfo(AssayRunUploadContext<? extends AssayProvider> context, ExpRun run, FileLike scriptDir, Map<DomainProperty, String> runProperties, Map<DomainProperty, String> batchProperties) throws Exception
    {
        DilutionRunUploadForm<DilutionAssayProvider> form = (DilutionRunUploadForm)context;

        Map<String, Map<DomainProperty, String>>props = form.getSampleProperties();

        DilutionAssayProvider provider = form.getProvider();
        Plate template = provider.getPlate(form.getContainer(), form.getProtocol());

        // add in the specimen information, the data will be serialized to a tsv and the file
        // location will be added to the run properties file.

        addSampleProperties(SAMPLE_DATA_PROP_NAME, GROUP_COLUMN_NAME, props, template, WellGroup.Type.SPECIMEN);

        return super.createTransformationRunInfo(context, run, scriptDir, runProperties, batchProperties);
    }

    @Override
    public void createSampleData(@NotNull ExpProtocol protocol, ViewContext viewContext, FileLike scriptDir) throws Exception
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider instanceof AbstractPlateBasedAssayProvider)
        {
            AbstractPlateBasedAssayProvider plateProvider = (AbstractPlateBasedAssayProvider)provider;
            Plate template = plateProvider.getPlate(viewContext.getContainer(), protocol);
            List<? extends DomainProperty> props = plateProvider.getSampleWellGroupDomain(protocol).getProperties();

            Map<String, Map<DomainProperty, String>>specimens = createTestSampleProperties(props, template, WellGroup.Type.SPECIMEN);

            addSampleProperties(SAMPLE_DATA_PROP_NAME, GROUP_COLUMN_NAME, specimens, template, WellGroup.Type.SPECIMEN);
        }
        super.createSampleData(protocol, viewContext, scriptDir);
    }
}
