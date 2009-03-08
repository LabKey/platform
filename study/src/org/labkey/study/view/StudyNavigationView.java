/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.study.view;

import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GroovyView;
import org.labkey.api.view.NavTree;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.Cohort;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import java.io.PrintWriter;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jul 28, 2008
 * Time: 1:20:35 PM
 */
public class StudyNavigationView extends GroovyView<NavTree>
{
    Study _study;
    StudyManager _manager;
    ActionURL _base;
    
    public StudyNavigationView(Study study)
    {
        super("/org/labkey/study/view/moduleNav.gm");
        _study = study;
        _manager = StudyManager.getInstance();
        _base = new ActionURL(StudyController.BeginAction.class, _study.getContainer());
    }

    protected NavTree getNavigation()
    {
        User user = getViewContext().getUser();
        NavTree ntRoot = new NavTree();

        //
        // DATASETS
        //
        
//        NavTree ntDatasets = new NavTree("Datasets", new ActionURL("project","getWebPart",_study.getContainer()).addParameter("webpart.name","Datasets"));
        NavTree ntDatasets = new NavTree("Datasets", new ActionURL(StudyController.DatasetsAction.class,_study.getContainer()));
        ntRoot.addChild(ntDatasets);
        DataSetDefinition defs[] = _study.getDataSets();
//        for (DataSetDefinition def : defs)
//        {
//            ntDatasets.addChild(def.getDisplayString(), _base.clone().setAction(StudyController.DatasetAction.class).addParameter("datasetId",def.getDataSetId()));
//        }

        //
        // PARTICIPANTS
        //

        DataSetDefinition dem = null;
        for (DataSetDefinition def : defs)
        {
            if (def.isDemographicData())
            {
                dem = def;
                break;
            }
        }

        if (null != dem)
        {
            NavTree ntParticipants = new NavTree("Participants");
            ntRoot.addChild(ntParticipants);
            ntParticipants.addChild("all", _base.clone().setAction(StudyController.DatasetAction.class).addParameter("datasetId",dem.getDataSetId()));
            Cohort[] cohorts = _study.getCohorts(user);
            for (Cohort cohort : cohorts)
            {
                NavTree ntCohort = new NavTree(cohort.getDisplayString(), _base.clone().setAction(StudyController.DatasetAction.class).addParameter("datasetId",dem.getDataSetId()).addParameter("cohortId",cohort.getRowId()));
                ntParticipants.addChild(ntCohort);
            }
        }

        //
        //  SPECIMENS
        //
        
        //NavTree ntSpecimens = new NavTree("Samples", new ActionURL("project","getWebPart",_study.getContainer()).addParameter("webpart.name","Specimens"));
        NavTree ntSpecimens = new NavTree("Samples", new ActionURL(StudyController.SamplesAction.class,_study.getContainer()));

        ntRoot.addChild(ntSpecimens);
        
        //
        // ADMIN
        //

        ntRoot.addChild("Study Settings", _base.clone().setAction(StudyController.ManageStudyAction.class));
        
        return ntRoot;        
    }

    public void renderView(NavTree bean, PrintWriter out) throws Exception
    {
        NavTree root = getNavigation();
        setModelBean(root);
        super.renderView(root, out);
    }
}
