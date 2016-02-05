/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

package org.labkey.query.controllers;

import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewForm;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.QueryManager;

public class InternalViewForm extends ViewForm
{
    private int _customViewId;
    private CstmView _view;

    public CstmView getViewAndCheckPermission() throws Exception
    {
        if (_view != null)
            return _view;
        QueryManager mgr = QueryManager.get();
        CstmView view = mgr.getCustomView(getContainer(), _customViewId);
        checkEdit(getViewContext(), view);
        _view = view;
        return _view;
    }

    public int getCustomViewId()
    {
        return _customViewId;
    }

    public void setCustomViewId(int id)
    {
        _customViewId = id;
    }

    public static void checkEdit(ViewContext context, CstmView view) throws Exception
    {
        if (view == null)
        {
            throw new NotFoundException();
        }
        if (!view.getContainerId().equals(context.getContainer().getId()))
        {
            throw new UnauthorizedException();
        }
        if (view.getCustomViewOwner() == null)
        {
            if (!context.hasPermission(EditSharedViewPermission.class))
            {
                throw new UnauthorizedException();
            }
        }
        else
        {
            if (view.getCustomViewOwner().intValue() != context.getUser().getUserId())
            {
                throw new UnauthorizedException();
            }
        }
    }
}
