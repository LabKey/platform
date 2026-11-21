/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.specimen.SpecimenMigrationService;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.study.query.AbstractStudyTable;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class BaseStudyTable extends AbstractStudyTable<StudyQuerySchema>
{
    public BaseStudyTable(StudyQuerySchema schema, TableInfo realTable, ContainerFilter cf)
    {
        super(schema, realTable, cf);
    }

    public BaseStudyTable(StudyQuerySchema schema, TableInfo realTable, ContainerFilter cf, boolean includeSourceStudyData)
    {
        super(schema, realTable, cf, includeSourceStudyData);
    }


    protected ColumnInfo addSpecimenVisitColumn(TimepointType timepointType, boolean isProvisioned)
    {
        var aliasVisitColumn = new AliasedColumn(this, "SequenceNum", _rootTable.getColumn("VisitValue"));
        return addSpecimenVisitColumn(timepointType, aliasVisitColumn, isProvisioned);
    }

    protected ColumnInfo addSpecimenVisitColumn(TimepointType timepointType, MutableColumnInfo aliasVisitColumn, boolean isProvisioned)
    {
        MutableColumnInfo visitColumn;
        var visitDescriptionColumn = addWrapColumn(_rootTable.getColumn("VisitDescription"));

        // add the sequenceNum column so we have it for later queries
        // Make it visible by default since it's useful in scenarios like specimen lookups from assay data
        addColumn(aliasVisitColumn);

        if (timepointType == TimepointType.DATE || timepointType == TimepointType.CONTINUOUS)
        {
            //consider:  use SequenceNumMin for visit-based studies too (in visit-based studies VisitValue == SequenceNumMin)
            // could change to visitrowid but that changes datatype and displays rowid
            // instead of sequencenum when label is null
            visitColumn = addColumn(new ParticipantVisitColumn(this, isProvisioned ? getContainer() : null));
            visitColumn.setLabel("Timepoint");

            visitDescriptionColumn.setHidden(true);
        }
        else    // if (timepointType == TimepointType.VISIT)     // only other choice
        {
            visitColumn = addColumn(new ParticipantVisitColumn(this, isProvisioned ? getContainer() : null));
        }

        LookupForeignKey visitFK = new LookupForeignKey(getContainerFilter(), "RowId", null)
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                VisitTable visitTable = new VisitTable(_userSchema, getLookupContainerFilter());
                if (_userSchema.allowSetContainerFilter())
                    visitTable.setContainerFilter(getLookupContainerFilter());
                return visitTable;
            }
        };
        visitFK.addJoin(FieldKey.fromParts("Container"), "Folder", false);
        visitColumn.setFk(visitFK);
        // Don't setKeyField. Use addQueryFieldKeys where needed

        visitColumn.setDisplayColumnFactory(col -> new VisitDisplayColumn(col, FieldKey.fromParts("SequenceNum")));

        return visitColumn;
    }

    public static class VisitDisplayColumn extends DataColumn
    {
        private final FieldKey _seqNumMinFieldKey;
        private Map<Integer, VisitImpl> _visits;

        public VisitDisplayColumn(ColumnInfo col, FieldKey seqNumMinFieldKey)
        {
            super(col);
            _seqNumMinFieldKey = seqNumMinFieldKey;
        }

        @Override
        @NotNull
        public HtmlString getFormattedHtml(RenderContext ctx)
        {
            Object value = getDisplayValue(ctx);
            if (value == null)
                return super.getFormattedHtml(ctx);
            return HtmlString.of(value);
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            Object value = ctx.get(getDisplayColumn().getFieldKey());
            if (value == null)
            {
                value = ctx.get(_seqNumMinFieldKey);
            }
            return value;
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            keys.add(_seqNumMinFieldKey);
        }

        @Override
        protected String getHoverContent(RenderContext ctx)
        {
            Study study = StudyManager.getInstance().getStudy(ctx.getContainer());
            if (study != null && getColumnInfo().getValue(ctx) != null)
            {
                VisitImpl visit = getVisits(study).get(Integer.parseInt(getColumnInfo().getValue(ctx).toString()));
                if (visit != null && (visit.getDescription() != null || visit.getLabel() != null))
                    return PageFlowUtil.filter(visit.getDescription() != null ? visit.getDescription() : visit.getLabel());
            }

            return null;
        }

        /** Fetch the visits all at once, not once per row */
        private Map<Integer, VisitImpl> getVisits(Study study)
        {
            if (_visits == null)
            {
                _visits = new HashMap<>();
                for (VisitImpl visit : StudyManager.getInstance().getVisits(study, Visit.Order.SEQUENCE_NUM))
                {
                    _visits.put(visit.getRowId(), visit);
                }
            }
            return _visits;
        }

        @Override
        protected String getHoverTitle(RenderContext ctx)
        {
            return "Visit Description";
        }
    }

    private static class ParticipantVisitColumn extends ExprColumn
    {
        Container container;

        public ParticipantVisitColumn(TableInfo parent, @Nullable Container container)
        {
            super(parent, "Visit", null, JdbcType.INTEGER);
            this.container = container;
        }

        @Override
        public SQLFragment getValueSql(String parentAlias)
        {
            String pvAlias = getSqlDialect().truncate(parentAlias + "$PV", 0);
            return new SQLFragment().appendDottedIdentifiers(pvAlias, "VisitRowId");
        }


        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            String pvAlias = getSqlDialect().truncate(parentAlias + "$PV", 0);
            SQLFragment join = new SQLFragment();

            join.unsafeAppend(" LEFT OUTER JOIN ").append(StudySchema.getInstance().getTableInfoParticipantVisit(), pvAlias).unsafeAppend(" ON\n");
            join.appendIdentifier(parentAlias).unsafeAppend(".ParticipantSequenceNum = ").appendIdentifier(pvAlias).unsafeAppend(".ParticipantSequenceNum AND\n");
            if (null == container)
                join.appendIdentifier(parentAlias).unsafeAppend(".Container = ").appendIdentifier(pvAlias).unsafeAppend(".Container");
            else
                join.appendValue(container, getSqlDialect())
                        .unsafeAppend(" = ").appendIdentifier(pvAlias).unsafeAppend(".Container");

            map.put(pvAlias, join);
        }
    }

    protected void addVialCommentsColumn(final boolean joinBackToSpecimens)
    {
        var commentsColumn = new AliasedColumn(this, "VialComments", _rootTable.getColumn("GlobalUniqueId"));
        LookupForeignKey commentsFK = new LookupForeignKey(getContainerFilter(), "GlobalUniqueId", null)
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                SpecimenMigrationService sms = SpecimenMigrationService.get();
                return sms != null ? sms.createSpecimenCommentTable(_userSchema, getLookupContainerFilter(), joinBackToSpecimens) : null;
            }
        };
        commentsFK.addJoin(FieldKey.fromParts("Container"), "Folder", false);
        commentsColumn.setFk(commentsFK);
        commentsColumn.setDescription("");
        commentsColumn.setDisplayColumnFactory(CommentDisplayColumn::new);
        commentsColumn.setUserEditable(false);
        addColumn(commentsColumn);
    }

    protected void addSpecimenCommentColumns(StudyQuerySchema schema, boolean isSpecimenDetail)
    {
        DatasetDefinition defPtid = null;
        DatasetDefinition defPtidVisit = null;
        final String alias;
        final boolean includeVialComments;
        final boolean hidden;
        if (isSpecimenDetail)
        {
            alias = "Comments";
            includeVialComments = true;
            hidden = false;
        }
        else
        {
            alias = SpecimenCommentColumn.COLUMN_NAME;
            includeVialComments = false;
            hidden = true;
        }

        StudyImpl study = StudyManager.getInstance().getStudy(_userSchema.getContainer());
        if (study.getParticipantCommentDatasetId() != null)
            defPtid = StudyManager.getInstance().getDatasetDefinition(study, study.getParticipantCommentDatasetId());
        if (study.getParticipantVisitCommentDatasetId() != null)
            defPtidVisit = StudyManager.getInstance().getDatasetDefinition(study, study.getParticipantVisitCommentDatasetId());

        boolean validParticipantCommentTable = defPtid != null && defPtid.canRead(schema.getUser()) && defPtid.isDemographicData();
        TableInfo participantCommentTable = validParticipantCommentTable ? defPtid.getTableInfo(schema.getUser()) : null;
        boolean validParticipantVisitCommentTable = defPtidVisit != null && defPtidVisit.canRead(schema.getUser()) && !defPtidVisit.isDemographicData();
        TableInfo participantVisitCommentTable = validParticipantVisitCommentTable ? defPtidVisit.getTableInfo(schema.getUser()) : null;

        addColumn(new SpecimenCommentColumn(this, participantCommentTable, study.getParticipantCommentProperty(),
                participantVisitCommentTable, study.getParticipantVisitCommentProperty(), alias, includeVialComments, hidden));
    }

    public static class SpecimenCommentColumn extends ExprColumn
    {
        public static final String COLUMN_NAME = "SpecimenComment";
        protected static final String VIAL_COMMENT_ALIAS = "VialComment";
        protected static final String PARTICIPANT_COMMENT_JOIN = "ParticipantCommentJoin$";
        protected static final String PARTICIPANT_COMMENT_ALIAS = "ParticipantComment";
        protected static final String PARTICIPANTVISIT_COMMENT_JOIN = "ParticipantVisitCommentJoin$";
        protected static final String PARTICIPANTVISIT_COMMENT_ALIAS = "ParticipantVisitComment";
        protected static final String SPECIMEN_COMMENT_JOIN = "SpecimenCommentJoin$";

        private final TableInfo _ptidCommentTable;
        private final TableInfo _ptidVisitCommentTable;
        private final boolean _includeVialComments;
        private final Container _container;

        public SpecimenCommentColumn(BaseStudyTable parent, TableInfo ptidCommentTable, String ptidCommentProperty,
                                     TableInfo ptidVisitCommentTable, String ptidVisitCommentProperty, String name,
                                     boolean includeVialComments, boolean hidden)
        {
            super(parent, name, new SQLFragment(), JdbcType.VARCHAR);
            setHidden(hidden);
            _container = parent.getContainer();
            _ptidCommentTable = ptidCommentTable;
            _ptidVisitCommentTable = ptidVisitCommentTable;
            _includeVialComments = includeVialComments;
            SQLFragment sql = new SQLFragment();
            String ptidCommentAlias = null;
            String ptidVisitCommentAlias = null;

            String subjectNoun = StudyService.get().getSubjectNounSingular(_container);

            if (ptidCommentProperty != null && _ptidCommentTable != null)
            {
                if (_ptidCommentTable.getColumn(ptidCommentProperty) != null)
                    ptidCommentAlias = ColumnInfo.legalNameFromName(ptidCommentProperty);
            }

            if (ptidVisitCommentProperty != null && _ptidVisitCommentTable != null)
            {
                if (_ptidVisitCommentTable.getColumn(ptidVisitCommentProperty) != null)
                    ptidVisitCommentAlias = ColumnInfo.legalNameFromName(ptidVisitCommentProperty);
            }

            List<Pair<SQLFragment, SQLFragment>> commentFields = new ArrayList<>();

            if (_includeVialComments)
            {
                SQLFragment field = new SQLFragment().appendIdentifier(ExprColumn.STR_TABLE_ALIAS).unsafeAppend("$" + SPECIMEN_COMMENT_JOIN + ".Comment");
                parent.addColumn(new ExprColumn(parent, VIAL_COMMENT_ALIAS, field, JdbcType.VARCHAR)
                {
                    @Override
                    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
                    {
                        super.declareJoins(parentAlias, map);
                        specimenCommentJoin(parentAlias, map);
                    }
                }).setHidden(true);
                commentFields.add(new Pair<>(field, new SQLFragment().appendStringLiteral("Vial: ", getSqlDialect())));
            }
            if (ptidCommentTable != null && ptidCommentAlias != null)
            {
                SQLFragment field = new SQLFragment().appendIdentifier(ExprColumn.STR_TABLE_ALIAS).unsafeAppend("$" + PARTICIPANT_COMMENT_JOIN + ".").appendIdentifier(ptidCommentAlias);
                parent.addColumn(new ExprColumn(parent, PARTICIPANT_COMMENT_ALIAS, field, JdbcType.VARCHAR)
                {
                    @Override
                    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
                    {
                        super.declareJoins(parentAlias, map);
                        participantCommentJoin(parentAlias, map);
                    }
                }).setHidden(true);
                commentFields.add(new Pair<>(field, new SQLFragment().appendStringLiteral(subjectNoun + ": ", getSqlDialect())));
            }
            if (ptidVisitCommentTable != null && ptidVisitCommentAlias != null)
            {
                SQLFragment field = new SQLFragment().appendIdentifier(ExprColumn.STR_TABLE_ALIAS).unsafeAppend("$" + PARTICIPANTVISIT_COMMENT_JOIN + ".").appendIdentifier(ptidVisitCommentAlias);
                parent.addColumn(new ExprColumn(parent, PARTICIPANTVISIT_COMMENT_ALIAS, field, JdbcType.VARCHAR)
                {
                    @Override
                    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
                    {
                        super.declareJoins(parentAlias, map);
                        participantVisitCommentJoin(parentAlias, map);
                    }
                }).setHidden(true);
                commentFields.add(new Pair<>(field, new SQLFragment().appendStringLiteral(subjectNoun + "/Visit: ", getSqlDialect())));
            }

            SQLFragment sb = new SQLFragment();
            if (!commentFields.isEmpty())
            {
                switch (commentFields.size())
                {
                    case 1 ->
                    {
                        sb.unsafeAppend("CASE");
                        appendCommentCaseSQL(sb, commentFields);
                        sb.unsafeAppend(" ELSE NULL END");
                    }
                    case 2 ->
                    {
                        sb.unsafeAppend("CASE");
                        appendCommentCaseSQL(sb, commentFields);
                        appendCommentCaseSQL(sb, Collections.singletonList(commentFields.get(0)));
                        appendCommentCaseSQL(sb, Collections.singletonList(commentFields.get(1)));
                        sb.unsafeAppend(" ELSE NULL END");
                    }
                    case 3 ->
                    {
                        sb.unsafeAppend("CASE");
                        appendCommentCaseSQL(sb, commentFields);
                        appendCommentCaseSQL(sb, Arrays.asList(commentFields.get(0), commentFields.get(1)));
                        appendCommentCaseSQL(sb, Arrays.asList(commentFields.get(0), commentFields.get(2)));
                        appendCommentCaseSQL(sb, Arrays.asList(commentFields.get(1), commentFields.get(2)));
                        appendCommentCaseSQL(sb, Collections.singletonList(commentFields.get(0)));
                        appendCommentCaseSQL(sb, Collections.singletonList(commentFields.get(1)));
                        appendCommentCaseSQL(sb, Collections.singletonList(commentFields.get(2)));
                        sb.unsafeAppend(" ELSE NULL END");
                    }
                }
            }
            sql.unsafeAppend("(");
            if (!sb.isEmpty())
                sql.append(sb);
            else
                sql.unsafeAppend("' '");
            sql.unsafeAppend(")");
            setValueSQL(sql);

            // Address issue #20328. Seems like this should be set automatically, since we're essentially wrapping a column that
            // we know shouldn't be faceted (based on inputType, e.g.), but that information isn't getting propagated.
            setFacetingBehaviorType(FacetingBehaviorType.ALWAYS_OFF);
        }

        private void appendCommentCaseSQL(SQLFragment sb, List<Pair<SQLFragment, SQLFragment>> fields)
        {
            var and = SQLFragment.sep(" WHEN ", "AND ", "THEN ");
            for (Pair<SQLFragment, SQLFragment> field : fields)
                sb.append(and.next()).append(field.first).unsafeAppend(" IS NOT NULL ");
            sb.append(and.last());

            SQLFragment[] castFields = new SQLFragment[fields.size()];
            SQLFragment sep = SQLFragment.unsafe("''");

            for (int i = 0; i < fields.size(); i++)
            {
                castFields[i] = SQLFragment.unsafe("CAST((").append(getSqlDialect().concatenate(sep, fields.get(i).second, fields.get(i).first)).unsafeAppend(") AS VARCHAR)");
                sep = SQLFragment.unsafe("', '");
            }

            sb.append(getSqlDialect().concatenate(castFields));
        }

        @Override
        public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
        {
            super.declareJoins(parentAlias, map);

            if (_includeVialComments)
                specimenCommentJoin(parentAlias, map);

            if (_ptidCommentTable != null)
                participantCommentJoin(parentAlias, map);

            if (_ptidVisitCommentTable != null)
                participantVisitCommentJoin(parentAlias, map);
        }

        private void specimenCommentJoin(String parentAlias, Map<String, SQLFragment> map)
        {
            String tableAlias = parentAlias + "$" + SPECIMEN_COMMENT_JOIN;
            if (map.containsKey(tableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();
            joinSql.unsafeAppend(" LEFT OUTER JOIN ").append(SpecimenSchema.get().getTableInfoSpecimenComment().getFromSQL(tableAlias));
            joinSql.unsafeAppend(" ON ");
            joinSql.appendIdentifier(parentAlias).unsafeAppend(".GlobalUniqueId = ").appendIdentifier(tableAlias).unsafeAppend(".GlobalUniqueId AND ");
            joinSql.appendIdentifier(parentAlias).unsafeAppend(".Container = ").appendIdentifier(tableAlias).unsafeAppend(".Container\n");
            map.put(tableAlias, joinSql);
        }

        private void participantCommentJoin(String parentAlias, Map<String, SQLFragment> map)
        {
            String ptidTableAlias = getSqlDialect().truncate(parentAlias + "$" + PARTICIPANT_COMMENT_JOIN, 0);
            if (map.containsKey(ptidTableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();
            joinSql.unsafeAppend(" LEFT OUTER JOIN ").append(_ptidCommentTable.getFromSQL(ptidTableAlias));
            joinSql.unsafeAppend(" ON ");
            joinSql.appendIdentifier(parentAlias).unsafeAppend(".Ptid = ")
                    .append(_ptidCommentTable.getColumn(StudyService.get().getSubjectColumnName(_container)).getValueSql(ptidTableAlias))
                    .unsafeAppend("\n");
            map.put(ptidTableAlias, joinSql);
        }

        private void participantVisitCommentJoin(String parentAlias, Map<String, SQLFragment> map)
        {
            String ptidTableAlias = getSqlDialect().truncate(parentAlias + "$" + PARTICIPANTVISIT_COMMENT_JOIN, 0);
            if (map.containsKey(ptidTableAlias))
                return;

            SQLFragment joinSql = new SQLFragment();
            joinSql.unsafeAppend(" LEFT OUTER JOIN ").append(_ptidVisitCommentTable.getFromSQL(ptidTableAlias));
            joinSql.unsafeAppend(" ON ");
            joinSql.appendIdentifier(parentAlias).unsafeAppend(".ParticipantSequenceNum = ").appendIdentifier(ptidTableAlias).unsafeAppend(".ParticipantSequenceNum");
            map.put(ptidTableAlias, joinSql);
        }
    }

    public static class CommentDisplayColumn extends DataColumn
    {
        public CommentDisplayColumn(ColumnInfo commentColumn)
        {
            super(commentColumn);
        }

        @Override
        public Object getDisplayValue(RenderContext ctx)
        {
            Object value = getDisplayColumn().getValue(ctx);
            return Objects.requireNonNullElse(value, "");
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
        {
            Object value = getDisplayColumn().getValue(ctx);
            if (value instanceof String s)
                out.write(s);
        }
    }

    protected static final String COMMENT_FORMAT_HTML = "<i>%s:&nbsp;</i>";
    protected static final String COMMENT_FORMAT = "%s: ";
    protected static final String LINE_SEPARATOR_HTML = "<br>";
    protected static final String LINE_SEPARATOR = ", ";

    protected static String formatParticipantComments(RenderContext ctx, boolean renderHtml)
    {
        Map<String, Object> row = ctx.getRow();

        Object vialComment = row.get(SpecimenCommentColumn.VIAL_COMMENT_ALIAS);
        Object participantComment = row.get(SpecimenCommentColumn.PARTICIPANT_COMMENT_ALIAS);
        Object participantVisitComment = row.get(SpecimenCommentColumn.PARTICIPANTVISIT_COMMENT_ALIAS);

        String lineSeparator = renderHtml ? LINE_SEPARATOR_HTML : LINE_SEPARATOR;
        String commentFormat = renderHtml ? COMMENT_FORMAT_HTML : COMMENT_FORMAT;

        StringBuilder sb = new StringBuilder();

        if (vialComment instanceof String)
        {
            sb.append(String.format(commentFormat, "Vial"));
            if (renderHtml)
                sb.append(PageFlowUtil.filter(vialComment));
            else
                sb.append(vialComment);
            sb.append(lineSeparator);
        }
        String subjectNoun = StudyService.get().getSubjectNounSingular(ctx.getContainer());
        if (participantComment instanceof String)
        {
            if (!sb.isEmpty())
                sb.append(lineSeparator);
            if (renderHtml)
            {
                sb.append(String.format(commentFormat, PageFlowUtil.filter(subjectNoun)));
                sb.append(PageFlowUtil.filter(participantComment));
            }
            else
            {
                sb.append(String.format(commentFormat, subjectNoun));
                sb.append(participantComment);
            }
            sb.append(lineSeparator);
        }
        if (participantVisitComment instanceof String)
        {
            if (!sb.isEmpty())
                sb.append(lineSeparator);

            if (renderHtml)
            {
                sb.append(String.format(commentFormat, PageFlowUtil.filter(subjectNoun) + "/Visit"));
                sb.append(PageFlowUtil.filter(participantVisitComment));
            }
            else
            {
                sb.append(String.format(commentFormat, subjectNoun + "/Visit"));
                sb.append(participantVisitComment);
            }
            sb.append(lineSeparator);
        }
        return sb.toString();
    }


    // UNDONE: a lot of study tables already use addContainerColumns()

    protected ColumnInfo addFolderColumn()
    {
        // Workaround to prevent IllegalArgumentException for assay tables
        if (getColumn("Folder") == null)
        {
            var folder = new AliasedColumn(this, "Folder", _rootTable.getColumn("Container"));
            folder.setHidden(true);
            addColumn(folder);
        }
        return getColumn("Folder");
    }


    protected ColumnInfo addStudyColumn()
    {
        // Don't copy the container concepturi, we don't want a ContainerDisplayFactory
        // NOTE: we can't just setConcentURI(null) because AliasedColumn will just ask the wrapped column
        var study = new AliasedColumn(this, "Study", _rootTable.getColumn("Container"))
        {
            @Override
            public String getConceptURI()
            {
                return null;
            }
        };

//      NOTE: QFK doesn't seem to support container joins
//      study.setFk(new QueryForeignKey("study", getUserSchema().getContainer(), null, getUserSchema().getUser(), "studyproperties", "Container", "Label", false));
        study.setFk(new LookupForeignKey()
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return getUserSchema().getTable("studyproperties");
            }
        });
        study.setHidden(true);
        addColumn(study);
        return study;
    }

    // for testing/breakpoints

    @Override
    public void setContainerFilter(@NotNull ContainerFilter filter)
    {
        super.setContainerFilter(filter);
    }

    @NotNull
    @Override
    public ContainerFilter getContainerFilter()
    {
        return super.getContainerFilter();
    }
}
