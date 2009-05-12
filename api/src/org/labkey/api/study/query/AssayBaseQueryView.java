/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ViewContext;

/**
 * User: brittp
 * Date: Jul 16, 2007
 * Time: 2:29:31 PM
 */
public abstract class AssayBaseQueryView extends QueryView
{
    protected ExpProtocol _protocol;
    protected AssayProvider _provider;

    public AssayBaseQueryView(ExpProtocol protocol, ViewContext context, QuerySettings settings)
    {
        super(AssayService.get().createSchema(context.getUser(), context.getContainer()), settings);
        _protocol = protocol;
        _provider = AssayService.get().getProvider(_protocol);
        getSettings().setAllowChooseQuery(false);
    }

    protected DataRegion createDataRegion()
    {
        DataRegion dr = super.createDataRegion();
        dr.setShowRecordSelectors(showControls());
        dr.setShadeAlternatingRows(true);
        dr.setShowBorders(true);
        return dr;
    }

    protected boolean showControls()
    {
        return true;
    }
}
