/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

package org.labkey.study.controllers.samples;

import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.api.view.NavTree;
import org.labkey.study.StudySchema;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jul 21, 2009
 */
@RequiresPermissionClass(ReadPermission.class)
public class AutoCompleteAction extends ApiAction<AutoCompleteAction.AutoCompletionForm>
{
    @Override
    public ApiResponse execute(AutoCompletionForm form, BindException errors) throws Exception
    {
        String column;
        TableInfo tinfo;
        boolean insensitiveCompare = false;
        if (SpecimenService.CompletionType.ParticipantId.name().equals(form.getType()))
        {
            tinfo = StudySchema.getInstance().getTableInfoParticipantVisit();
            column = "ParticipantId";
            insensitiveCompare = true;
        }
        else if (SpecimenService.CompletionType.SpecimenGlobalUniqueId.name().equals(form.getType()))
        {
            tinfo = StudySchema.getInstance().getTableInfoVial();
            column = "GlobalUniqueId";
            insensitiveCompare = true;
        }
        else if (SpecimenService.CompletionType.VisitId.name().equals(form.getType()))
        {
            tinfo = StudySchema.getInstance().getTableInfoParticipantVisit();
            column = "SequenceNum";
        }
        else if (SpecimenService.CompletionType.LabId.name().equals(form.getType()))
        {
            tinfo = StudySchema.getInstance().getTableInfoSite();
            column = "Label";
            insensitiveCompare = true;
        }
        else
            throw new IllegalArgumentException("Completion type " + form.getType() + " not recognized.");

        List<AjaxCompletion> completions = new ArrayList<AjaxCompletion>();
        ResultSet rs = null;
        try
        {
            SQLFragment sql = new SQLFragment();
            sql.append("SELECT DISTINCT ");
            sql.append(column);
            sql.append(" FROM ");
            sql.append(tinfo.getSchema().getName()).append(".").append(tinfo.getName());
            sql.append(" WHERE Container = ?");
            sql.add(getViewContext().getContainer().getId());
            sql.append(" ORDER BY ").append(column);
            tinfo.getSqlDialect().limitRows(sql, 50);
            rs = Table.executeQuery(tinfo.getSchema(), sql);
            while (rs.next())
                completions.add(new AjaxCompletion(rs.getObject(1).toString()));

            ApiSimpleResponse response = new ApiSimpleResponse();
            List<JSONObject> jsonCompletions = new ArrayList<JSONObject>();
            for (AjaxCompletion completion : completions)
                jsonCompletions.add(completion.toJSON());

            response.put("completions", jsonCompletions);
            return response;
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) {}
        }
    }

    public static class AutoCompletionForm
    {
        private String _prefix;
        private String _type;

        public String getPrefix()
        {
            return _prefix;
        }

        public void setPrefix(String prefix)
        {
            _prefix = prefix;
        }

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }
    }
}
