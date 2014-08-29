/*
 * Copyright (c) 2014 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerDisplayColumn;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.StudySchema;

import java.io.IOException;
import java.io.Writer;

/**
 * Created by klum on 8/8/2014.
 */
public class StudySnapshotTable extends FilteredTable<StudyQuerySchema>
{
    public StudySnapshotTable(StudyQuerySchema schema)
    {
        super(StudySchema.getInstance().getTableInfoStudySnapshot(), schema, ContainerFilter.Type.CurrentWithUser.create(schema.getUser()));

        setDescription("Contains a row for each Ancillary or Published Study that was created from the study in this folder." +
                " Only users with administrator permissions will see any data.");

        ColumnInfo rowIdColumn = addWrapColumn(_rootTable.getColumn("RowId"));
        rowIdColumn.setHidden(true);
        rowIdColumn.setUserEditable(false);
        rowIdColumn.setKeyField(true);

        ColumnInfo source = new AliasedColumn(this, "Source", _rootTable.getColumn("source"));
        ContainerForeignKey.initColumn(source, getUserSchema());
        addColumn(source);

        ColumnInfo destination = new AliasedColumn(this, "Destination", _rootTable.getColumn("destination"));
        ContainerForeignKey.initColumn(destination, getUserSchema());
        final User user = schema.getUser();
        destination.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ContainerDisplayColumn(colInfo, false, true){
                    @Override
                    public String renderURL(RenderContext ctx)
                    {
                        Object o = getValue(ctx);
                        Container c = ContainerManager.getForId(String.valueOf(o));
                        if (c != null)
                        {
                            if (!c.hasPermission(user, ReadPermission.class))
                                return null;
                        }
                        return super.renderURL(ctx);
                    }
                };
            }
        });
        addColumn(destination);

        ColumnInfo createdBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("CreatedBy")));
        UserIdForeignKey.initColumn(createdBy);

        ColumnInfo modifiedBy = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ModifiedBy")));
        UserIdForeignKey.initColumn(modifiedBy);

        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Created")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Modified")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Refresh")));

        ColumnInfo settingsColumn = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Settings")));
        settingsColumn.setDisplayColumnFactory(new DisplayColumnFactory(){
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DataColumn(colInfo){

                    @Override
                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        Object value = getValue(ctx);
                        ObjectMapper jsonMapper = new ObjectMapper();
                        Object jsonValue = jsonMapper.readValue(String.valueOf(value), Object.class);

                        out.write(PageFlowUtil.filter(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonValue), true, true));
                  }
                };
            }
        });
    }

    @Override
    protected SimpleFilter.FilterClause getContainerFilterClause(ContainerFilter filter, FieldKey fieldKey)
    {
        return filter.createFilterClause(getSchema(), fieldKey, getContainer(), AdminPermission.class, null);
    }

    @Override
    protected String getContainerFilterColumn()
    {
        return "Source";
    }
}
