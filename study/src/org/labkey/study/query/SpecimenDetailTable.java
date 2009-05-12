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

import org.labkey.study.StudySchema;
import org.labkey.api.data.*;
import org.labkey.api.query.*;

import java.io.Writer;
import java.io.IOException;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.sql.Types;

public class SpecimenDetailTable extends BaseStudyTable
{
    public SpecimenDetailTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenDetail());

        ColumnInfo rowIdColumn = addWrapColumn(_rootTable.getColumn("RowId"));
        rowIdColumn.setKeyField(true);
        addWrapColumn(_rootTable.getColumn("Container"));
        addWrapColumn(_rootTable.getColumn("SpecimenHash")).setIsHidden(true);
        addWrapColumn(_rootTable.getColumn("GlobalUniqueId"));
        addWrapParticipantColumn("PTID").setKeyField(true);

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

        AliasedColumn visitColumn;
        ColumnInfo visitDescriptionColumn = addWrapColumn(_rootTable.getColumn("VisitDescription"));
        if (_schema.getStudy().isDateBased())
        {
            //consider:  use SequenceNumMin for visit-based studies too (in visit-based studies VisitValue == SequenceNumMin)
            // could change to visitrowid but that changes datatype and displays rowid
            // instead of sequencenum when label is null
            visitColumn = new AliasedColumn(this, "Visit", _rootTable.getColumn("SequenceNumMin"));
            visitColumn.setCaption("Timepoint");
            visitDescriptionColumn.setIsHidden(true);
        }
        else
        {
            visitColumn = new AliasedColumn(this, "Visit", _rootTable.getColumn("VisitValue"));
        }
        visitColumn.setFk(new LookupForeignKey(null, (String) null, "SequenceNumMin", null)
        {
            public TableInfo getLookupTableInfo()
            {
                return new VisitTable(_schema);
            }
        });
        visitColumn.setKeyField(true);
        addColumn(visitColumn);
        addWrapColumn(_rootTable.getColumn("Volume"));
        addWrapColumn(_rootTable.getColumn("VolumeUnits"));
        addWrapTypeColumn("PrimaryType", "PrimaryTypeId");
        addWrapTypeColumn("DerivativeType", "DerivativeTypeId");
        addWrapTypeColumn("AdditiveType", "AdditiveTypeId");
        addWrapTypeColumn("DerivativeType2", "DerivativeTypeId2");
        addWrapColumn(_rootTable.getColumn("PrimaryVolume"));
        addWrapColumn(_rootTable.getColumn("PrimaryVolumeUnits"));
        addWrapColumn(_rootTable.getColumn("FrozenTime"));
        addWrapColumn(_rootTable.getColumn("ProcessingTime"));

        addWrapColumn(_rootTable.getColumn("LockedInRequest"));
        ColumnInfo siteNameColumn = addWrapColumn(_rootTable.getColumn("SiteName"));
        siteNameColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new SiteNameDisplayColumn(colInfo);
            }
        });
        addWrapLocationColumn("Clinic", "OriginatingLocationId");

        ColumnInfo commentsColumn = new AliasedColumn(this, "Comments", _rootTable.getColumn("GlobalUniqueId"));
        commentsColumn.setFk(new LookupForeignKey("GlobalUniqueId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new SpecimenCommentTable(_schema);
            }
        });
        commentsColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new CommentDisplayColumn(colInfo);
            }
        });
        addColumn(commentsColumn);

        addWrapColumn(_rootTable.getColumn("SiteLdmsCode"));
        addWrapColumn(_rootTable.getColumn("DrawTimestamp"));
        addWrapColumn(_rootTable.getColumn("AtRepository"));
        ColumnInfo availableColumn = addWrapColumn(_rootTable.getColumn("Available"));
        availableColumn.setKeyField(true);
        addWrapColumn(_rootTable.getColumn("SalReceiptDate"));
        addWrapColumn(_rootTable.getColumn("ClassId"));
        addWrapColumn(_rootTable.getColumn("ProtocolNumber"));
        addWrapColumn(_rootTable.getColumn("SubAdditiveDerivative"));

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

        setDefaultVisibleColumns(QueryService.get().getDefaultVisibleColumns(getColumns()));
        
        // add the vial count columns from specimen summary
        String sqlVialCount = "( SELECT a.VialCount FROM " + StudySchema.getInstance().getTableInfoSpecimenSummary() +
                " a WHERE a.SpecimenHash = " + ExprColumn.STR_TABLE_ALIAS + ".SpecimenHash)";
        ColumnInfo colVialCount = new ExprColumn(this, "VialCount", new SQLFragment(sqlVialCount), Types.INTEGER);
        addColumn(colVialCount);

        String sqlLockedInRequest = "( SELECT a.LockedInRequestCount FROM " + StudySchema.getInstance().getTableInfoSpecimenSummary() +
                " a WHERE a.SpecimenHash = " + ExprColumn.STR_TABLE_ALIAS + ".SpecimenHash)";
        ColumnInfo colLockedInRequest = new ExprColumn(this, "LockedInRequestCount", new SQLFragment(sqlLockedInRequest), Types.INTEGER);
        addColumn(colLockedInRequest);

        String sqlAtRepositoryCount = "( SELECT a.AtRepositoryCount FROM " + StudySchema.getInstance().getTableInfoSpecimenSummary() +
                " a WHERE a.SpecimenHash = " + ExprColumn.STR_TABLE_ALIAS + ".SpecimenHash)";
        ColumnInfo colAtRepositoryCount = new ExprColumn(this, "AtRepositoryCount", new SQLFragment(sqlAtRepositoryCount), Types.INTEGER);
        addColumn(colAtRepositoryCount);

        String sqlAvailableCount = "( SELECT a.AvailableCount FROM " + StudySchema.getInstance().getTableInfoSpecimenSummary() +
                " a WHERE a.SpecimenHash = " + ExprColumn.STR_TABLE_ALIAS + ".SpecimenHash)";
        ColumnInfo colAvailableCount = new ExprColumn(this, "AvailableCount", new SQLFragment(sqlAvailableCount), Types.INTEGER);
        addColumn(colAvailableCount);
    }

    public static class CommentDisplayColumn extends DataColumn
    {
        public CommentDisplayColumn(ColumnInfo commentColumn)
        {
            super(commentColumn);
        }

        public Object getDisplayValue(RenderContext ctx)
        {
            Object value = getDisplayColumn().getValue(ctx);
            if (value == null)
                return "";
            else
                return value;
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object value = getDisplayColumn().getValue(ctx);
            if (value != null  && value instanceof String)
                out.write((String) value);
        }
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
            return getColumnInfo().getParentTable().getColumn("LockedInRequest");
        }

        public void addQueryColumns(Set<ColumnInfo> columns)
        {
            super.addQueryColumns(columns);
            columns.add(getInRequestColumn());
        }

        private String getNoSiteText(RenderContext ctx)
        {
            Object inRequest = getInRequestColumn().getValue(ctx);
            boolean requested = (inRequest instanceof Boolean && ((Boolean) inRequest).booleanValue()) ||
                (inRequest instanceof Integer && ((Integer) inRequest).intValue() == 1);
            return NO_SITE_DISPLAY_VALUE + (requested ? ": Requested" : "");
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
