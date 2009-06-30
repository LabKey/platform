/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.study.StudySchema;

import java.io.IOException;
import java.io.Writer;
import java.sql.Types;
import java.util.Set;
import java.util.Map;
import java.util.Collections;

public class SpecimenDetailTable extends AbstractSpecimenTable
{
    public SpecimenDetailTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenDetail());

        addWrapColumn(_rootTable.getColumn("GlobalUniqueId"));
        
        ColumnInfo pvColumn = new ParticipantVisitColumn(
                "ParticipantVisit",
                new AliasedColumn(this, "PVParticipant", getRealTable().getColumn("PTID")),
                new AliasedColumn(this, "PVVisit", getRealTable().getColumn("VisitValue")));
        addColumn(pvColumn);
        pvColumn.setFk(new LookupForeignKey("ParticipantVisit")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ParticipantVisitTable(_schema, null);
            }
        });

        addSpecimenVisitColumn(_schema.getStudy().isDateBased());
        addWrapColumn(_rootTable.getColumn("Volume"));
        addSpecimenTypeColumns();
        addWrapColumn(_rootTable.getColumn("PrimaryVolume"));
        addWrapColumn(_rootTable.getColumn("PrimaryVolumeUnits"));

        addVialCommentsColumn(true);


        addWrapColumn(_rootTable.getColumn("LockedInRequest"));
        addWrapColumn(_rootTable.getColumn("Requestable"));

        ColumnInfo siteNameColumn = wrapColumn("SiteName", getRealTable().getColumn("CurrentLocation"));
        siteNameColumn.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return StudySchema.getInstance().getTableInfoSite();
            }
        });
        siteNameColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new SiteNameDisplayColumn(colInfo);
            }
        });
        addColumn(siteNameColumn);

        ColumnInfo siteLdmsCodeColumn = wrapColumn("SiteLdmsCode", getRealTable().getColumn("CurrentLocation"));
        siteLdmsCodeColumn.setFk(new LookupForeignKey("RowId", "LdmsLabCode")
        {
            public TableInfo getLookupTableInfo()
            {
                return StudySchema.getInstance().getTableInfoSite();
            }
        });
        addColumn(siteLdmsCodeColumn);
        addWrapColumn(_rootTable.getColumn("AtRepository"));

        ColumnInfo availableColumn = wrapColumn("Available", getRealTable().getColumn("Available"));
        availableColumn.setKeyField(true);
        addColumn(availableColumn);

        String innerSelect = "(SELECT QualityControlFlag FROM " +
                StudySchema.getInstance().getTableInfoSpecimenComment() +
                " WHERE GlobalUniqueId = " + ExprColumn.STR_TABLE_ALIAS + ".GlobalUniqueId" +
                " AND Container = ?)";

        // gross bit of SQL: this case statement ensures that we always get a 'true' or 'false' return from this
        // subselect, even though the lookup might return null:
        SQLFragment sqlFragConflicts = new SQLFragment("(CASE WHEN " + innerSelect + " = ? THEN ? ELSE ? END)");
        sqlFragConflicts.add(getContainer().getId());
        sqlFragConflicts.add(Boolean.TRUE);
        sqlFragConflicts.add(Boolean.TRUE);
        sqlFragConflicts.add(Boolean.FALSE);
        addColumn(new ExprColumn(this, "QualityControlFlag", sqlFragConflicts, Types.BOOLEAN));

        SQLFragment sqlFragQCComments = new SQLFragment("(SELECT QualityControlComments FROM " +
                StudySchema.getInstance().getTableInfoSpecimenComment() +
                " WHERE GlobalUniqueId = " + ExprColumn.STR_TABLE_ALIAS + ".GlobalUniqueId" +
                " AND Container = ?)");
        sqlFragQCComments.add(getContainer().getId());
        addColumn(new ExprColumn(this, "QualityControlComments", sqlFragQCComments, Types.VARCHAR));

        addWrapLocationColumn("ProcessingLocation", "ProcessingLocation");

        setDefaultVisibleColumns(QueryService.get().getDefaultVisibleColumns(getColumns()));
    }

    public static class SiteNameDisplayColumn extends DataColumn
    {
        private static final String NO_SITE_DISPLAY_VALUE = "In Transit";
        public SiteNameDisplayColumn(ColumnInfo siteColumn)
        {
            super(siteColumn);
        }

        private ColumnInfo getInRequestColumn()
        {
            FieldKey me = getBoundColumn().getFieldKey();
            FieldKey inRequestKey = new FieldKey(me.getParent(), "LockedInRequest");
            Map<FieldKey, ColumnInfo> requiredColumns = QueryService.get().getColumns(getBoundColumn().getParentTable(), Collections.singleton(inRequestKey));
            return requiredColumns.get(inRequestKey);
        }

        public void addQueryColumns(Set<ColumnInfo> columns)
        {
            super.addQueryColumns(columns);
            ColumnInfo inRequestCol = getInRequestColumn();
            if (inRequestCol != null)
                columns.add(inRequestCol);
        }

        private String getNoSiteText(RenderContext ctx)
        {
            ColumnInfo inRequestColumn = getInRequestColumn();
            boolean requested = false;
            if (inRequestColumn != null)
            {
                Object inRequest = inRequestColumn.getValue(ctx);
                requested = (inRequest instanceof Boolean && ((Boolean) inRequest).booleanValue()) ||
                    (inRequest instanceof Integer && ((Integer) inRequest).intValue() == 1);
                return NO_SITE_DISPLAY_VALUE + (requested ? ": Requested" : "");
            }
            else
                return NO_SITE_DISPLAY_VALUE + ": Request status unknown";
        }

        public Object getDisplayValue(RenderContext ctx)
        {
            Object value = getBoundColumn().getValue(ctx);
            if (value == null)
                return getNoSiteText(ctx);
            else
                return super.getDisplayValue(ctx);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object value = getBoundColumn().getValue(ctx);
            if (value == null)
                out.write(getNoSiteText(ctx));
            else
                super.renderGridCellContents(ctx, out);
        }
    }
}
