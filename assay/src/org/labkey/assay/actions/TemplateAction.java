/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.assay.actions;

import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.actions.BaseAssayAction;
import org.labkey.api.assay.actions.ProtocolIdForm;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
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
    @Override
    public ModelAndView getView(ProtocolIdForm rowIdForm, BindException errors) throws Exception
    {
        ExpProtocol protocol = rowIdForm.getProtocol();
        AssayProvider provider = AssayService.get().getProvider(protocol);
        Domain runDataDomain = provider.getResultsDomain(protocol);
        Map<String, String> colNameToPdname = new CaseInsensitiveHashMap<>();
        DataRegion dr = createDataRegionForInsert(OntologyManager.getTinfoObject(), "ObjectURI", runDataDomain.getProperties(), colNameToPdname);
        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause("0 = 1", new Object[]{});

        // CONSIDER: Export template using FieldKey instead
        // reset all captions to the property descriptor names, since names are expected by the importer
        for (DisplayColumn dc : dr.getDisplayColumns())
            dc.setCaption(colNameToPdname.get(dc.getName()));

        dr.removeColumns(provider.getTableMetadata(protocol).getResultRowIdFieldKey().toString());

        RenderContext ctx = new RenderContext(getViewContext());
        ctx.setContainer(getContainer());
        ctx.setBaseFilter(filter);

        ExcelWriter xl = new ExcelWriter(()->dr.getResults(ctx), dr.getDisplayColumns(), ExcelWriter.ExcelDocumentType.xlsx);
        xl.renderWorkbook(getViewContext().getResponse());
        return null;
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        throw new UnsupportedOperationException("Not Yet Implemented");
    }
}
