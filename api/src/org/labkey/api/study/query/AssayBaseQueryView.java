/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.study.query;

import org.labkey.api.data.DataRegion;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayService;

/**
 * User: brittp
 * Date: Jul 16, 2007
 * Time: 2:29:31 PM
 */
public abstract class AssayBaseQueryView extends QueryView
{
    protected ExpProtocol _protocol;
    protected AssayProvider _provider;

    public AssayBaseQueryView(ExpProtocol protocol, AssaySchema schema, QuerySettings settings)
    {
        super(schema, settings);
        _protocol = protocol;
        _provider = AssayService.get().getProvider(_protocol);
    }

    @Override
    protected void configureDataRegion(DataRegion dr)
    {
        super.configureDataRegion(dr);
        dr.setShowRecordSelectors(showControls());
    }

    protected boolean showControls()
    {
        return true;
    }
}
