/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.assay;

import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.DefaultAssaySaveHandler;

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
}
