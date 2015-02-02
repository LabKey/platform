/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

import org.labkey.api.wiki.WikiRendererType;
import org.labkey.study.model.*;
import org.labkey.api.jsp.JspBase;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.*;

import java.util.List;

/**
 * User: brittp
 * Date: Jan 11, 2006
 * Time: 11:27:26 AM
 */
public abstract class BaseStudyPage extends JspBase
{
    private StudyImpl _study;

    protected StudyImpl getStudy()
    {
//        if (_study == null)
//            throw new IllegalStateException("init must be called before rendering this JSP.");
        return _study;
    }

    protected List<VisitImpl> getVisits(Visit.Order order)
    {
        return getStudy().getVisits(order);
    }

    protected List<DatasetDefinition> getDatasets()
    {
        return getStudy().getDatasets();
    }

    protected List<LocationImpl> getLocations()
    {
        return getStudy().getLocations();
    }

    protected List<CohortImpl> getCohorts(User user)
    {
        return getStudy().getCohorts(user);
    }

    protected List<AssaySpecimenConfigImpl> getAssaySpecimenConfigs()
    {
        return getStudy().getAssaySpecimenConfigs("AssayName");
    }

    protected List<ProductImpl> getStudyProducts(User user, String role)
    {
        return getStudy().getStudyProducts(user, role);
    }

    protected List<TreatmentImpl> getStudyTreatments(User user)
    {
        return getStudy().getStudyTreatments(user);
    }

    protected WikiRendererType[] getRendererTypes()
    {
        return WikiRendererType.values();
    }

    protected WikiRendererType currentRendererType()
    {
        return getStudy().getDescriptionWikiRendererType();
    }

    public void init(Container c)
    {
        _study = StudyManager.getInstance().getStudy(c);
    }

    public void init(StudyImpl study)
    {
        _study = study;
    }
}
