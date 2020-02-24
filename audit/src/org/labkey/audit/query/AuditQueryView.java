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
package org.labkey.audit.query;

import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.MenuButton;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.Errors;

import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 8/29/13
 *
 * Most audit tables don't allow inserting, but the ClientApiAuditProvider's table allows
 * inserts from the client api. This query view disables the insert buttons in the html UI
 * while still allowing the LABKEY.Query.insertRows() api to still work.
 *
 * This query also lets Troubleshooters interact with the audit log, #39638. It ensures
 * that the DataRegion, ButtonBar, and Button read permissions checks succeed and it hides
 * the Chart/Reports and Manage Views options, since these don't work for Troubleshooters.
 *
 * @see org.labkey.api.audit.ClientApiAuditProvider#createTableInfo(org.labkey.api.query.UserSchema, org.labkey.api.data.ContainerFilter)
 */
public class AuditQueryView extends QueryView
{
    private final Set<Role> _contextualRoles;
    private final boolean _hasInsert;

    public AuditQueryView(AuditQuerySchema schema, QuerySettings settings, Errors errors, ViewContext context)
    {
        super(schema, settings, errors);
        _contextualRoles = context.getContextualRoles();
        _hasInsert = context.hasPermission(InsertPermission.class);
    }

    @Override
    public boolean showInsertNewButton()
    {
        return false;
    }

    @Override
    public boolean showImportDataButton()
    {
        return false;
    }

    @Override
    public boolean isShowReports()
    {
        return _hasInsert;
    }

    // Need at least insert permissions to use Manage Views -- don't show it to Troubleshooters
    @Override
    public void addManageViewItems(MenuButton button, Map<String, String> params)
    {
        if (_hasInsert)
            super.addManageViewItems(button, params);
    }

    // This is terrible, but DataView conjures up a ViewContext that doesn't include the contextual roles we need for
    // the Troubleshooter scenario. Copy any contextual roles into DataView's context.
    @Override
    public DataView createDataView()
    {
        DataView view = super.createDataView();
        _contextualRoles.forEach(r->view.getViewContext().addContextualRole(r.getClass()));

        return view;
    }

    // ButtonBar and the individual buttons also need the contextual roles
    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);
        _contextualRoles.stream().map(Role::getClass).forEach(clazz->{
            bar.addContextualRole(clazz);
            bar.getList().forEach(button->button.addContextualRole(clazz));
        });
    }
}
