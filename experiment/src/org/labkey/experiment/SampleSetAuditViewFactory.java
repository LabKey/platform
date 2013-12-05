/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.experiment;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;

/**
 * User: bbimber
 * Date: 1/13/12
 * Time: 7:29 AM
 */
public class SampleSetAuditViewFactory extends SimpleAuditViewFactory
{
    private static final SampleSetAuditViewFactory INSTANCE = new SampleSetAuditViewFactory();
    public static final String EVENT_TYPE = "SampleSetAuditEvent";

    public static SampleSetAuditViewFactory getInstance()
    {
        return INSTANCE;
    }

    @Override
    public String getName()
    {
        return "Sample Set events";
    }

    @Override
    public String getEventType()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getDescription()
    {
        return "Summarizes events from sample set inserts or updates";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("EventType"), EVENT_TYPE, CompareType.EQUAL);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter);
        view.setSort(new Sort("-Date"));

        return view;
    }
}
