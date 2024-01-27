/*
 * Copyright (c) 2014-2019 LabKey Corporation
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerDisplayColumn;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.JavaScriptDisplayColumn;
import org.labkey.api.data.PHI;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.StudySchema;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashSet;

public class StudySnapshotTable extends FilteredTable<StudyQuerySchema>
{
    @Override
    protected ContainerFilter getDefaultContainerFilter()
    {
        return ContainerFilter.Type.CurrentWithUser.create(getUserSchema());
    }

    public StudySnapshotTable(StudyQuerySchema schema, ContainerFilter cf)
    {
        super(StudySchema.getInstance().getTableInfoStudySnapshot(), schema, cf);

        setDescription("Contains a row for each Ancillary, Published, or Specimen study that was created from the study in this folder." +
                " Only users with administrator permissions will see any data.");

        var rowIdColumn = addWrapColumn(_rootTable.getColumn("RowId"));
        rowIdColumn.setHidden(true);
        rowIdColumn.setUserEditable(false);
        rowIdColumn.setKeyField(true);

        var source = new AliasedColumn(this, "Source", _rootTable.getColumn("source"));
        source.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);
        addColumn(source);

        addColumn(new AliasedColumn(this, "Type", _rootTable.getColumn(FieldKey.fromParts("type"))));

        var destination = new AliasedColumn(this, "Destination", _rootTable.getColumn("destination"));
        final User user = schema.getUser();
        destination.setDisplayColumnFactory(colInfo -> new ContainerDisplayColumn(colInfo, false){
            @Override
            public String renderURL(RenderContext ctx)
            {
                Object o = getValue(ctx);
                Container c = ContainerManager.getForId(String.valueOf(o));
                if (c != null)
                {
                    if (c.hasPermission(user, ReadPermission.class))
                        return c.getStartURL(user).getLocalURIString();
                }
                return null;
            }
        });
        addColumn(destination);

        var createdBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("CreatedBy")));
        UserIdForeignKey.initColumn(createdBy);

        var modifiedBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ModifiedBy")));
        UserIdForeignKey.initColumn(modifiedBy);

        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Created")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Modified")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Refresh")));

        var settingsColumn = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Settings")));
        settingsColumn.setDisplayColumnFactory(colInfo -> new DataColumn(colInfo){

            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                Object value = getValue(ctx);
                Object jsonValue = JsonUtil.DEFAULT_MAPPER.readValue(String.valueOf(value), Object.class);

                out.write(PageFlowUtil.filter(JsonUtil.DEFAULT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonValue), true, true));
          }
        });

        ComplianceService complianceService = ComplianceService.get();
        PHI maxAllowedPhi = complianceService.getMaxAllowedPhi(getContainer(), schema.getUser());

        AliasedColumn republishCol = new AliasedColumn("Republish", wrapColumn(_rootTable.getColumn("RowId")));
        republishCol.setDisplayColumnFactory(colInfo -> {
            Collection<String> dependencies = new HashSet<>();
            dependencies.add("clientapi/ext3");
            dependencies.add("reports/rowExpander.js");
            dependencies.add("FileUploadField.js");
            dependencies.add("study/StudyWizard.js");

            String availableStudyName = ContainerManager.getAvailableChildContainerName(getContainer(), "New Study");
            String onClickJavaScript = "LABKEY.study.openRepublishStudyWizard(${RowId:jsString}, " + PageFlowUtil.jsString(availableStudyName) + ", " + PageFlowUtil.jsString(maxAllowedPhi.name()) + ");";

            return new JavaScriptDisplayColumn(colInfo, dependencies, onClickJavaScript, "labkey-text-link")
            {
                @NotNull
                @Override
                public HtmlString getFormattedHtml(RenderContext ctx)
                {
                    return HtmlString.of("Republish");
                }

                @Override
                public void renderTitle(RenderContext ctx, Writer out)
                {
                    // no title
                }

                @Override
                public boolean isSortable()
                {
                    return false;
                }

                @Override
                public boolean isFilterable()
                {
                    return false;
                }
            };
        });
        addColumn(republishCol);
    }

    @Override
    protected SimpleFilter.FilterClause getContainerFilterClause(ContainerFilter filter, FieldKey fieldKey)
    {
        return filter.createFilterClause(getSchema(), fieldKey, AdminPermission.class, null);
    }

    @Override
    protected String getContainerFilterColumn()
    {
        return "Source";
    }
}
