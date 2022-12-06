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
package org.labkey.specimen;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Link;
import org.labkey.api.view.ActionURL;
import org.labkey.specimen.actions.SpecimenController;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.specimen.SpecimenCommentAuditDomainKind.COLUMN_NAME_VIAL_ID;
import static org.labkey.specimen.SpecimenCommentAuditDomainKind.SPECIMEN_COMMENT_EVENT;

/**
 * User: klum
 * Date: 7/18/13
 */
public class SpecimenCommentAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    private static final List<FieldKey> defaultVisibleColumns = List.of(
        FieldKey.fromParts(COLUMN_NAME_CREATED),
        FieldKey.fromParts(COLUMN_NAME_CREATED_BY),
        FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY),
        FieldKey.fromParts(COLUMN_NAME_PROJECT_ID),
        FieldKey.fromParts(COLUMN_NAME_CONTAINER),
        FieldKey.fromParts(COLUMN_NAME_VIAL_ID),
        FieldKey.fromParts(COLUMN_NAME_COMMENT)
    );

    public SpecimenCommentAuditProvider()
    {
        super(new SpecimenCommentAuditDomainKind());
    }

    @Override
    public String getEventName()
    {
        return SPECIMEN_COMMENT_EVENT;
    }

    @Override
    public String getLabel()
    {
        return "Specimen Comments and QC";
    }

    @Override
    public String getDescription()
    {
        return "Specimen Comments and QC";
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_VIAL_ID);
        return legacyNames;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)SpecimenCommentAuditEvent.class;
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        return new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(MutableColumnInfo col)
            {
                if (COLUMN_NAME_VIAL_ID.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo containerColumn = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
                    col.setLabel("Vial Id");

                    col.setDisplayColumnFactory(new DisplayColumnFactory()
                    {
                        @Override
                        public DisplayColumn createRenderer(final ColumnInfo colInfo)
                        {
                            return new DataColumn(colInfo)
                            {
                                @Override
                                public void addQueryColumns(Set<ColumnInfo> columns)
                                {
                                    columns.add(containerColumn);
                                    super.addQueryColumns(columns);
                                }

                                @Override
                                public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                                {
                                    Object containerId = containerColumn.getValue(ctx);
                                    String globalUniqueId = (String) getValue(ctx);
                                    if (globalUniqueId == null)
                                        return;

                                    Container container = ContainerManager.getForId(containerId.toString());
                                    if (container == null)
                                    {
                                        out.write(globalUniqueId);
                                        return;
                                    }

                                    ActionURL url = SpecimenController.getCommentURL(container, globalUniqueId);
                                    out.write(new Link.LinkBuilder(globalUniqueId).href(url).clearClasses().toString());
                                }
                            };
                        }
                    });
                }
            }
        };
    }


    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }
}
