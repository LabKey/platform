/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

package org.labkey.experiment.controllers.exp;

import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.model.GWTMaterial;
import org.labkey.api.gwt.client.model.GWTSampleSet;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ViewContext;
import org.labkey.experiment.samplechooser.client.SampleSetService;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Feb 8, 2008
 */
public class SampleSetServiceImpl extends BaseRemoteService implements SampleSetService
{
    public SampleSetServiceImpl(ViewContext context)
    {
        super(context);
    }

    public List<GWTSampleSet> getSampleSets()
    {
        List<GWTSampleSet> result = new ArrayList<>();
        for (ExpSampleSet set : ExperimentService.get().getSampleSets(getContainer(), _context.getUser(), true))
        {
            GWTSampleSet gwtSet = new GWTSampleSet(set.getName(), set.getLSID());
            gwtSet.setRowId(set.getRowId());
            List<String> columnNames = new ArrayList<>();
            for (DomainProperty propertyDescriptor : set.getType().getProperties())
            {
                columnNames.add(propertyDescriptor.getName());
            }
            gwtSet.setColumnNames(columnNames);
            result.add(gwtSet);
        }
        return result;
    }

    public List<GWTMaterial> getMaterials(GWTSampleSet gwtSet)
    {
        ExpSampleSet set = ExperimentService.get().getSampleSet(gwtSet.getRowId());
        if (set == null)
        {
            return null;
        }

        if (!set.getContainer().hasPermission(getUser(), ReadPermission.class))
        {
            return null;
        }

        List<? extends ExpMaterial> materials = set.getSamples();
        List<GWTMaterial> result = new ArrayList<>(materials.size());
        for (ExpMaterial material : materials)
        {
            GWTMaterial gwtMaterial = new GWTMaterial();
            result.add(gwtMaterial);
            gwtMaterial.setLsid(material.getLSID());
            gwtMaterial.setRowId(material.getRowId());
            gwtMaterial.setName(material.getName());
        }
        return result;
    }
}
