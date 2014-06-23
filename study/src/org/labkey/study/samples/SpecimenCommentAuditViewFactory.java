/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

package org.labkey.study.samples;

import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.query.SpecimenQueryView;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * IntKey1 is the dataset row id
 * IntKey2 is 0 if no details are available
 * Key1 is the UploadLog path (if set)
 *
 * User: jgarms
 */
public class SpecimenCommentAuditViewFactory extends SimpleAuditViewFactory
{
    public static final String SPECIMEN_COMMENT_EVENT = "SpecimenCommentEvent";

    private static final SpecimenCommentAuditViewFactory INSTANCE = new SpecimenCommentAuditViewFactory();

    static final Logger LOG = Logger.getLogger(SpecimenCommentAuditViewFactory.class);

    private SpecimenCommentAuditViewFactory() {}

    public static SpecimenCommentAuditViewFactory getInstance()
    {
        return INSTANCE;
    }

    public String getName()
    {
        return "Specimen Comments and QC";
    }

    public String getEventType()
    {
        return SPECIMEN_COMMENT_EVENT;
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
        view.setSort(new Sort("-Date"));

        return view;
    }

    @Override
    public void setupTable(final FilteredTable table, UserSchema schema)
    {
        super.setupTable(table, schema);

        final ColumnInfo containerColumn = table.getColumn("ContainerId");
        final ColumnInfo vialIdColumn = table.getColumn("Key1");
        vialIdColumn.setLabel("Vial Id");

        vialIdColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(final ColumnInfo colInfo)
            {
                return new DataColumn(colInfo)
                {
                    public void addQueryColumns(Set<ColumnInfo> columns)
                    {
                        columns.add(containerColumn);
                        super.addQueryColumns(columns);
                    }

                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        Object containerId = containerColumn.getValue(ctx);
                        String globalUniqueId = (String) getValue(ctx);
                        if (globalUniqueId == null)
                            return;

                        Container container = ContainerManager.getForId(containerId.toString());
                        if (container == null)
                        {
                            return;
                        }

                        ActionURL url = SpecimenController.getSamplesURL(container);
                        url.addParameter(SpecimenController.SampleViewTypeForm.PARAMS.showVials, true);
                        url.addParameter(SpecimenController.SampleViewTypeForm.PARAMS.viewMode, SpecimenQueryView.Mode.COMMENTS.name());
                        url.addParameter("SpecimenDetail.GlobalUniqueId~eq", globalUniqueId);

                        out.write("<a href=\"");
                        out.write(PageFlowUtil.filter(url.getLocalURIString()));
                        out.write("\">");
                        out.write(PageFlowUtil.filter(globalUniqueId));
                        out.write("</a>");
                    }
                };
            }
        });
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("ProjectId"));
        columns.add(FieldKey.fromParts("ContainerId"));
        columns.add(FieldKey.fromParts("Key1"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }
}
