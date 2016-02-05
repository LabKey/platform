/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.query.view;

import org.labkey.api.data.Container;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.data.views.ProviderType;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.view.ViewContext;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.QueryManager;

import java.sql.SQLException;
import java.util.Map;

/**
 * User: klum
 * Date: Apr 4, 2012
 */
public class QueryDataViewProvider extends AbstractQueryDataViewProvider
{
    public static final DataViewProvider.Type TYPE = new ProviderType("queries", "Custom Views", true);

    public DataViewProvider.Type getType()
    {
        return TYPE;
    }

    @Override
    protected boolean includeView(ViewContext context, CustomView view)
    {
        return !ReportUtil.isInherited(view, context.getContainer());
    }

    @Override
    public EditInfo getEditInfo()
    {
        return new EditInfoImpl();
    }

    public static class EditInfoImpl implements DataViewProvider.EditInfo
    {
        private static final Actions[] _actions = {
                Actions.delete
        };

        @Override
        public String[] getEditableProperties(Container container, User user)
        {
            return new String[0];
        }

        @Override
        public void validateProperties(Container container, User user, String id, Map<String, Object> props) throws ValidationException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateProperties(ViewContext context, String id, Map<String, Object> props) throws Exception
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Actions[] getAllowableActions(Container container, User user)
        {
            return _actions;
        }

        @Override
        public void deleteView(Container container, User user, String id) throws ValidationException
        {
            try
            {
                CstmView view = QueryManager.get().getCustomView(container, id);
                if (view != null)
                {
                    if (view.isShared())
                    {
                        if (!container.hasPermission(user, EditSharedViewPermission.class))
                            throw new ValidationException("The specified view is shared, you must be in the Editor role to be allowed to delete a shared view.");
                    }
                    QueryManager.get().delete(view);
                }
            }
            catch (SQLException e)
            {
                throw new ValidationException(e.getMessage());
            }
        }
    }
}
