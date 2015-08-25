/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:23:37 PM
*/
@RequiresPermission(InsertPermission.class)
public class TemplateAction extends BaseAssayAction<ProtocolIdForm>
{
    ExpProtocol _protocol;
    public ModelAndView getView(ProtocolIdForm rowIdForm, BindException errors) throws Exception
    {
        _protocol = rowIdForm.getProtocol();
        AssayProvider provider = AssayService.get().getProvider(_protocol);
        Domain runDataDomain = provider.getResultsDomain(_protocol);
        Map<String, String> colNameToPdname = new CaseInsensitiveHashMap<>();
        DataRegion dr = createDataRegionForInsert(OntologyManager.getTinfoObject(), "ObjectURI", runDataDomain.getProperties(), colNameToPdname);
        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause("0 = 1", new Object[]{});

        // CONSIDER: Export template using FieldKey instead
        // reset all captions to the property descriptor names, since names are expected by the importer
        for (DisplayColumn dc : dr.getDisplayColumns())
            dc.setCaption(colNameToPdname.get(dc.getName()));

        dr.removeColumns(provider.getTableMetadata(_protocol).getResultRowIdFieldKey().toString());

        RenderContext ctx = new RenderContext(getViewContext());
        ctx.setContainer(getContainer());
        ctx.setBaseFilter(filter);

        Results rs = dr.getResultSet(ctx);
        ExcelWriter xl = new ExcelWriter(rs, dr.getDisplayColumns());
        xl.write(getViewContext().getResponse());
        return null;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        throw new UnsupportedOperationException("Not Yet Implemented");
    }
}
