/*
 * Copyright (c) 2010-2012 LabKey Corporation
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
package org.labkey.api.study.actions;

import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.PlateBasedAssayProvider;
import org.labkey.api.study.assay.PlateSamplePropertyHelper;

/**
 * User: brittp
 * Date: Aug 23, 2010 3:23:03 PM
 */
public interface PlateUploadForm<ProviderType extends PlateBasedAssayProvider> extends AssayRunUploadContext<ProviderType>
{
    public PlateSamplePropertyHelper getSamplePropertyHelper();

    public void setSamplePropertyHelper(PlateSamplePropertyHelper helper);
}
