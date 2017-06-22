/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.study.specimen;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.study.assay.query.AssayAuditProvider;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.query.SpecimenQueryView;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 7/18/13
 */
public class SpecimenCommentAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String SPECIMEN_COMMENT_EVENT = "SpecimenCommentEvent";

    public static final String COLUMN_NAME_VIAL_ID = "VialId";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_PROJECT_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_VIAL_ID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new SpecimenCommentAuditDomainKind();
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
    public TableInfo createTableInfo(UserSchema userSchema)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(ColumnInfo col)
            {
                if (COLUMN_NAME_VIAL_ID.equalsIgnoreCase(col.getName()))
                {
                    final ColumnInfo containerColumn = getColumn(FieldKey.fromParts(COLUMN_NAME_CONTAINER));
                    col.setLabel("Vial Id");

                    col.setDisplayColumnFactory(new DisplayColumnFactory()
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
                                    out.write(globalUniqueId);
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
            }
        };

        return table;
    }


    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }


    public static class SpecimenCommentAuditEvent extends AuditTypeEvent
    {
        private String _vialId;

        public SpecimenCommentAuditEvent()
        {
            super();
        }

        public SpecimenCommentAuditEvent(String container, String comment)
        {
            super(AssayAuditProvider.ASSAY_PUBLISH_AUDIT_EVENT, container, comment);
        }

        public String getVialId()
        {
            return _vialId;
        }

        public void setVialId(String vialId)
        {
            _vialId = vialId;
        }

        @Override
        public Map<String, Object> getAuditLogMessageElements()
        {
            Map<String, Object> elements = new LinkedHashMap<>();
            elements.put("vialId", getVialId());
            elements.putAll(super.getAuditLogMessageElements());
            return elements;
        }
    }

    public static class SpecimenCommentAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "SpecimenCommentAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public SpecimenCommentAuditDomainKind()
        {
            super(SPECIMEN_COMMENT_EVENT);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_VIAL_ID, PropertyType.STRING));
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return _fields;
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }
}
