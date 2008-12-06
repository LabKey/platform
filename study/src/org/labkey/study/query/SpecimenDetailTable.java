/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
        ColumnInfo participantColumn = new AliasedColumn(this, "ParticipantId", _rootTable.getColumn("PTID"));
        participantColumn.setFk(new QueryForeignKey(_schema, "Participant", "ParticipantId", null));
        participantColumn.setKeyField(true);
        addColumn(participantColumn);

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
        ColumnInfo primaryTypeColumn = new AliasedColumn(this, "PrimaryType", _rootTable.getColumn("PrimaryTypeId"));
        primaryTypeColumn.setFk(new LookupForeignKey("ScharpId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new PrimaryTypeTable(_schema);
            }
        });
        addColumn(primaryTypeColumn);
        ColumnInfo additiveTypeColumn = new AliasedColumn(this, "AdditiveType", _rootTable.getColumn("AdditiveTypeId"));
        additiveTypeColumn.setFk(new LookupForeignKey("ScharpId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new AdditiveTypeTable(_schema);
            }
        });
        addColumn(additiveTypeColumn);
        ColumnInfo derivativeTypeColumn = new AliasedColumn(this, "DerivativeType", _rootTable.getColumn("DerivativeTypeId"));
        derivativeTypeColumn.setFk(new LookupForeignKey("ScharpId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new DerivativeTypeTable(_schema);
            }
        });
        addColumn(derivativeTypeColumn);

        addWrapColumn(_rootTable.getColumn("LockedInRequest"));
        ColumnInfo siteNameColumn = addWrapColumn(_rootTable.getColumn("SiteName"));
        siteNameColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new SiteNameDisplayColumn(colInfo);
            }
        });
        ColumnInfo originatingSiteCol = new AliasedColumn(this, "Clinic", _rootTable.getColumn("OriginatingLocationId"));
        originatingSiteCol.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new SiteTable(_schema);
            }
        });
        addColumn(originatingSiteCol);

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
/*
        addWrapColumn(_rootTable.getColumn("fr_container"));
        addWrapColumn(_rootTable.getColumn("fr_level1"));
        addWrapColumn(_rootTable.getColumn("fr_level2"));
        addWrapColumn(_rootTable.getColumn("fr_position"));
        addWrapColumn(_rootTable.getColumn("freezer"));*/
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
