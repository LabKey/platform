/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.api.assay.plate;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AbstractAssayTsvDataHandler;
import org.labkey.api.assay.AssayDataType;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.vfs.FileLike;

public class PlateMetadataDataHandler extends AbstractAssayTsvDataHandler
{
    public static final String NAMESPACE = "AssayRunTSVPlateMetadata";
    public static final AssayDataType DATA_TYPE;

    static
    {
        FileType fileType = new FileType(".json");
        fileType.setExtensionsMutuallyExclusive(false);
        DATA_TYPE = new AssayDataType(NAMESPACE, fileType, ExpDataRunInput.IMPORTED_DATA_ROLE);
    }

    @Override
    protected boolean allowEmptyData()
    {
        return false;
    }

    @Override
    protected boolean shouldAddInputMaterials()
    {
        return false;
    }

    @Override
    public DataType getDataType()
    {
        return DATA_TYPE;
    }

    @Override
    public Priority getPriority(ExpData data)
    {
        Lsid lsid = new Lsid(data.getLSID());
        if (DATA_TYPE.matches(lsid))
        {
            return Priority.HIGH;
        }
        return null;
    }

    @Override
    public void importFile(@NotNull ExpData data, @NotNull FileLike dataFile, @NotNull ViewBackgroundInfo info, @NotNull Logger log, @NotNull XarContext context)
    {
        // this is just a noop data handler, the actual parsing and importing of the plate metadata needs to happen in
        // AbstractAssayTsvDataHandler.addAssayPlateMetadata because we need to access the inserted result data to get at the
        // lsids.
    }
}
