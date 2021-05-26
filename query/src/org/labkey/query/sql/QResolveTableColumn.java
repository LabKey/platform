package org.labkey.query.sql;

import org.antlr.runtime.tree.CommonTree;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class QResolveTableColumn extends QIfDefined
{
    FieldKey _fieldKey = null;
    QueryRelation.RelationColumn _column = null;
    CaseInsensitiveHashMap<Object> _namedParameters = new CaseInsensitiveHashMap<>();

    public QResolveTableColumn(CommonTree node)
    {
        super(node);

        // look for named parameters (annotations on the exprlist)
        SupportsAnnotations exprList = (SupportsAnnotations)node.getChild(1);
        if (null != exprList.getAnnotations())
            this._namedParameters.putAll(convertAnnotations(exprList.getAnnotations()));
    }

    @Override
    protected boolean isValidChild(QNode n)
    {
        return n instanceof QDot || n instanceof QExprList;
    }

    /**
     * Helper for finding a column in a particular Table
     */
    private QueryRelation.RelationColumn resolveColumn(QueryRelation table, @Nullable String name, @Nullable String concept, @Nullable String conceptURI, QNode location)
    {
        if (isBlank(name) && isBlank(concept) && isBlank(conceptURI))
            return null;

        List<QueryRelation.RelationColumn> list = null;
        if (isNotBlank(name))
        {
            QueryRelation.RelationColumn col = table.getColumn(name);
            if (null == col)
                return null;
            list = List.of(col);
        }

        if (null == list)
            list = new ArrayList<>(table.getAllColumns().values());

        if (isNotBlank(concept))
            list = list.stream().filter(col -> (concept.equals(col.getPrincipalConceptCode()))).collect(Collectors.toList());

        if (isNotBlank(conceptURI))
            list = list.stream().filter(col -> (conceptURI.equals(col.getConceptURI()))).collect(Collectors.toList());

        if (list.isEmpty())
            return null;

        if (list.size() > 1)
        {
            table.reportWarning("findColumn() specification is ambiguous: " + StringUtils.defaultIfBlank(concept, conceptURI), location);
            return null;
        }

        /* have to call getColumn() so table knows column is in use */
        return table.getColumn(list.get(0).getFieldKey().getName());
    }


    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        throw new IllegalStateException();
    }

    @Override
    public boolean isConstant()
    {
        return false;
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        getFirstChild().appendSource(builder);
        builder.append("(");
        builder.pushPrefix("");

        for (QNode n : getLastChild().children())
        {
            QExpr child = (QExpr)n;
            child.appendSource(builder);
            builder.nextPrefix(",");
        }
        builder.popPrefix();
        builder.append(")");
    }

    @Override
    public FieldKey getFieldKey()
    {
        assert null != select;

        if (null != _fieldKey || !isDefined)
            return _fieldKey;

        QDot dot = (QDot) getFirstChild();
        assert dot.getFieldKey().size() == 2;
        FieldKey tableName = dot.getFieldKey().getTable();
        QueryRelation table = select.getTable(tableName);
        if (null == table)
        {
            select.parseError("Could not resolve table: " + tableName.toDisplayString(), this);
            isDefined = false;
            return null;
        }

        // get columnName
        String columnName = null;
        if (_namedParameters.get("name") instanceof String)
            columnName = (String)_namedParameters.get("name");

        String concept = null;
        if (_namedParameters.get("concept") instanceof String)
            concept = (String)_namedParameters.get("concept");

        String conceptURI = null;
        if (_namedParameters.get("concepturi") instanceof String)
            conceptURI = (String)_namedParameters.get("concepturi");

        if (isBlank(columnName) && isBlank(concept) && isBlank(conceptURI))
        {
            isDefined = false;
            return null;
        }

        QueryRelation.RelationColumn c = resolveColumn(table, columnName, concept, conceptURI, dot);
        if (null == c)
        {
            isDefined = false;
            return null;
        }
        _fieldKey = tableName.append(c.getFieldKey().getName());
        _column = c;
        return _fieldKey;
    }

    @Override
    public @NotNull JdbcType getJdbcType()
    {
        return null!=_column ? JdbcType.VARCHAR : _column.getJdbcType();
    }
}