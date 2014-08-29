/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.study.assay;

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.DefaultAssaySaveHandler;
import org.labkey.api.view.ViewContext;

/**
 * User: kevink
 * Date: 8/10/14
 */
public class TsvAssaySaveHandler extends DefaultAssaySaveHandler
{
    @Override
    public void setProvider(AssayProvider provider)
    {
        assert provider instanceof TsvAssayProvider;
        super.setProvider(provider);
    }

    @Override
    public TsvAssayProvider getProvider()
    {
        return (TsvAssayProvider)super.getProvider();
    }

    // Issue 21247 and 21285: HACK for 14.2 to allow LABKEY.Experiment.saveBatch() API to work with GPAT assays.
    // In 14.2, TsvAssayProvider.createRunUploadFactory() was refactored to instantiate the more generic AssayRunUploadContextImpl instead of ModuleRunUploadForm
    // to support the LABKEY.Assay.importRun() API, but in the process the refactoring broke using the saveBatch() API for GPAT assays.
    // Workaround for 14.2 is to instantiate the ModuleRunUploadForm for TsvAssayProvider (and ModuleAssayProvider) but only for the saveBatch() API.
    @Override
    protected AssayRunUploadContext.Factory createRunUploadContext(ExpProtocol protocol, ViewContext context)
    {
        return new ModuleRunUploadForm.Factory(protocol, getProvider(), context);
    }
}
