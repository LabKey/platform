/*
 * Copyright (c) 2009-2015 LabKey Corporation
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

package org.labkey.study.controllers.specimen;

import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.view.AjaxCompletion;
import org.labkey.study.StudySchema;
import org.springframework.validation.BindException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: klum
 * Date: Jul 21, 2009
 */
@RequiresPermission(ReadPermission.class)
public class AutoCompleteAction extends ApiAction<AutoCompleteAction.AutoCompletionForm>
{
    @Override
    public ApiResponse execute(AutoCompletionForm form, BindException errors) throws Exception
    {
        Container container = getContainer();
        String column;
        TableInfo tinfo;
        boolean insensitiveCompare = false;
        boolean hasContainerColumn = true;
        if (SpecimenService.CompletionType.ParticipantId.name().equals(form.getType()))
        {
            tinfo = StudySchema.getInstance().getTableInfoParticipantVisit();
            column = "ParticipantId";
            insensitiveCompare = true;
        }
        else if (SpecimenService.CompletionType.SpecimenGlobalUniqueId.name().equals(form.getType()))
        {
            tinfo = StudySchema.getInstance().getTableInfoVial(container);
            column = "GlobalUniqueId";
            insensitiveCompare = true;
            hasContainerColumn = false;
        }
        else if (SpecimenService.CompletionType.VisitId.name().equals(form.getType()))
        {
            tinfo = StudySchema.getInstance().getTableInfoParticipantVisit();
            column = "SequenceNum";
        }
        else if (SpecimenService.CompletionType.LabId.name().equals(form.getType()))
        {
            tinfo = StudySchema.getInstance().getTableInfoSite(container);
            column = "Label";
            insensitiveCompare = true;
        }
        else
            throw new IllegalArgumentException("Completion type " + form.getType() + " not recognized.");

        ApiSimpleResponse response = new ApiSimpleResponse();
        if (null == tinfo)      // theoretically possible from getTableInfoVial
            return response;
        final List<AjaxCompletion> completions = new ArrayList<>();

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT DISTINCT ");
        sql.append(column);
        sql.append(" FROM ");
        sql.append(tinfo.getSchema().getName()).append(".").append(tinfo.getName());
        if (hasContainerColumn)
        {
            sql.append(" WHERE Container = ?");
            sql.add(container.getId());
        }
        sql.append(" ORDER BY ").append(column);
        tinfo.getSqlDialect().limitRows(sql, 50);

        new SqlSelector(tinfo.getSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                completions.add(new AjaxCompletion(rs.getObject(1).toString()));
            }
        });

        List<JSONObject> jsonCompletions = new ArrayList<>();
        for (AjaxCompletion completion : completions)
            jsonCompletions.add(completion.toJSON());

        response.put("completions", jsonCompletions);

        return response;
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
