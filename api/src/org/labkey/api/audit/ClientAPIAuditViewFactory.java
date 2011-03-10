/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.api.audit;

import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;

/**
 * User: jeckels
 * Date: Mar 6, 2011
 */
public class ClientAPIAuditViewFactory extends SimpleAuditViewFactory
{
    public static final String EVENT_TYPE = "Client API Actions";
    private static final ClientAPIAuditViewFactory INSTANCE = new ClientAPIAuditViewFactory();

    public static ClientAPIAuditViewFactory getInstance()
    {
        return INSTANCE;
    }

    @Override
    public String getEventType()
    {
        return EVENT_TYPE;
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("EventType", EVENT_TYPE);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, EVENT_TYPE);
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);
        view.setSort(new Sort("-Date"));

        return view;
    }
}
