/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.study.query;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;
import org.labkey.study.model.Vial;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

/**
 * User: brittp
 * Date: Jan 26, 2007
 * Time: 10:13:50 AM
 */
public class SpecimenEventQueryView extends BaseStudyQueryView
{
    protected SpecimenEventQueryView(UserSchema schema, QuerySettings settings, SimpleFilter filter, Sort sort)
    {
        super(schema, settings, filter, sort);
    }

    public static SpecimenEventQueryView createView(ViewContext context, Vial vial)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(context.getContainer());
        StudyQuerySchema schema = StudyQuerySchema.createSchema(study, context.getUser(), true);
        String queryName = "SpecimenEvent";
        QuerySettings qs = schema.getSettings(context, queryName, queryName);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("VialId"), vial.getRowId());
        Sort sort = new Sort("-ShipDate");
        return new SpecimenEventQueryView(schema, qs, filter, sort);
    }

}
