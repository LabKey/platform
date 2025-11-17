package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.data.xml.ColumnType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class QueryLineage extends AbstractQueryRelation
{
    final QuerySelect sourceSelect;
    final boolean ancestors;

    QueryLineage(Query query, QuerySelect source, String alias, boolean ancestors)
    {
        super(query, query.getSchema(), Objects.toString(alias, "explineage_" + query.incrementAliasCounter()));
        this.sourceSelect = source;
        this.ancestors = ancestors;
    }

//    @Override
//    public String getAlias()
//    {
//        return sourceSelect.getAlias();
//    }

    @Override
    public void declareFields()
    {
        sourceSelect.declareFields();
    }

    @Override
    public void resolveFields()
    {
        sourceSelect.resolveFields();
    }

    @Override
    public TableInfo getTableInfo()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, RelationColumn> getAllColumns()
    {
        return Map.of(
                "Depth",      Objects.requireNonNull(getColumn("depth")),
                "FromObject", Objects.requireNonNull(getColumn("fromobject")),
                "ToObject",   Objects.requireNonNull(getColumn("toobject")));
    }

    @Override
    public @Nullable AbstractQueryRelation.RelationColumn getFirstColumn()
    {
        return getColumn("fromobject");
    }

    @Override
    public @Nullable AbstractQueryRelation.RelationColumn getColumn(@NotNull String name)
    {
        return switch (name.toLowerCase())
        {
            case "depth", "fromobject", "toobject" -> new LineageColumn(name.toLowerCase());
            default -> null;
        };
    }

    @Override
    public int getSelectedColumnCount()
    {
        return 3;
    }

    @Override
    public @Nullable AbstractQueryRelation.RelationColumn getLookupColumn(@NotNull RelationColumn parent, @NotNull String name)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable AbstractQueryRelation.RelationColumn getLookupColumn(@NotNull RelationColumn parent, ColumnType.@NotNull Fk fk, @NotNull String name)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLFragment getSql()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLFragment getFromSql()
    {
        SqlDialect d = _query.getSchema().getDbSchema().getSqlDialect();

        ExpLineageOptions options = new ExpLineageOptions(ancestors, !ancestors, 1000);
        options.setForLookup(true);             // remove intermediate edges
        options.setUseObjectIds(true);          // expObject() returns objectid not lsid

        SQLFragment sql = new SQLFragment();
        sql.appendComment("<QueryLineage>", d);
        // CONSIDER: use CTE for sourceSelect.getSql()
        SQLFragment lineageSql = ExperimentService.get().generateExperimentTreeSQL(sourceSelect.getSql(), options);
        sql.append("(\n").append(lineageSql).append("\n) AS ").appendIdentifier(getAlias());
        sql.appendComment("</QueryLineage>", d);
        return sql;
    }

    @Override
    public String getQueryText()
    {
        return (ancestors?"EXPANCESTORSOF":"EXPDESCENDANTSOF") + "(" + sourceSelect.getQueryText() + ")";
    }

    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {
        sourceSelect.setContainerFilter(containerFilter);
    }

    @Override
    public Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    {
        return Set.of();
    }


    private class LineageColumn extends RelationColumn
    {
        final FieldKey _fieldKey;
        final String _alias;
        final JdbcType _jdbcType;

        LineageColumn(String name)
        {
            String alias;
            FieldKey fk;
            JdbcType jdbcType = JdbcType.BIGINT;
            switch (name.toLowerCase())
            {
                case "depth":
                    alias = "depth";
                    fk = new FieldKey(null, "Depth");
                    jdbcType = JdbcType.INTEGER;
                    break;
                case "fromobject":
                    alias = "self";
                    fk = new FieldKey(null, "FromObject");
                    break;
                case "toobject":
                    alias = "objectid";
                    fk = new FieldKey(null, "ToObject");
                    break;
                default:
                    throw new IllegalArgumentException("Unknown column name: " + name);
            }
            _fieldKey = fk;
            _alias = alias;
            _jdbcType = jdbcType;
        }

        @Override
        SQLFragment getInternalSql()
        {
            return new SQLFragment().appendDottedIdentifiers(QueryLineage.this.getAlias(), getAlias());
        }

        @Override
        void copyColumnAttributesTo(@NotNull BaseColumnInfo to)
        {
            to.setJdbcType(_jdbcType);
        }

        @Override
        public FieldKey getFieldKey()
        {
            return _fieldKey;
        }

        @Override
        String getAlias()
        {
            return _alias;
        }

        @Override
        AbstractQueryRelation getTable()
        {
            return QueryLineage.this;
        }

        @Override
        boolean isHidden()
        {
            return false;
        }

        @Override
        String getPrincipalConceptCode()
        {
            return "";
        }

        @Override
        String getConceptURI()
        {
            return "";
        }

        @Override
        public @NotNull JdbcType getJdbcType()
        {
            return _jdbcType;
        }

        @Override
        public Collection<RelationColumn> gatherInvolvedSelectColumns(Collection<RelationColumn> collect)
        {
            return List.of();
        }
    }
}
