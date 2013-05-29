/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.assay.nab.view;

import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.nab.NabUrls;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.actions.AssayHeaderView;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.ArrayList;
import java.util.List;

/**
* Created by IntelliJ IDEA.
* User: klum
* Date: 5/15/13
*/
public class RunDetailsHeaderView extends AssayHeaderView
{
    private int _runId;
    private Container _container;

    public RunDetailsHeaderView(Container container, ExpProtocol protocol, AssayProvider provider, int runId)
    {
        super(protocol, provider, true, true, null);
        _container = container;
        _runId = runId;
    }

    @Override
    public List<NavTree> getLinks()
    {
        List<NavTree> links = new ArrayList<NavTree>();

        links.add(new NavTree("View Runs", PageFlowUtil.addLastFilterParameter(PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getViewContext().getContainer(), _protocol, _containerFilter))));
        links.add(new NavTree("View Results", PageFlowUtil.addLastFilterParameter(PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(getViewContext().getContainer(), _protocol, _containerFilter))));

        if (getViewContext().hasPermission(InsertPermission.class))
        {
            links.add(new NavTree(AbstractAssayProvider.IMPORT_DATA_LINK_NAME, _provider.getImportURL(_container, _protocol)));

            if (getViewContext().hasPermission(DeletePermission.class))
            {
                ActionURL rerunURL = getProvider().getImportURL(_container, getProtocol());
                if (rerunURL != null)
                {
                    rerunURL.addParameter("reRunId", _runId);
                    links.add(new NavTree("Delete and Re-import", rerunURL));
                }
            }
        }

        NavTree changeCurveMenu = new NavTree("Change Curve Type");
        for (DilutionCurve.FitType type : DilutionCurve.FitType.values())
        {
            ActionURL changeCurveURL = getViewContext().cloneActionURL();
            changeCurveURL.replaceParameter("fitType", type.name());
            changeCurveMenu.addChild(type.getLabel(), changeCurveURL);
        }
        links.add(changeCurveMenu);

        ActionURL downloadURL = PageFlowUtil.urlProvider(NabUrls.class).urlDownloadDatafile(_container).addParameter("rowId", _runId);
        links.add(new NavTree("Download Datafile", downloadURL));
        links.add(new NavTree("Print", getViewContext().cloneActionURL().addParameter("_print", "true")));
        return links;
    }
}
