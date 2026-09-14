/*
 * Copyright (c) 2011-2026 LabKey Corporation
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
package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.QueryService;

/**
 * User: matthewb
 * Date: Jan 3, 2011
 * Time: 1:49:21 PM
 */
public class QParameter extends QExpr implements QueryService.ParameterDecl
{
    private final QNode _decl;
    private final String _name;
    private final QType _type;
    private final boolean _required;
    private final Object _defaultValue;


    QParameter(QNode decl, String name, QType type, boolean required, Object def)
    {
        super();
        setLineAndColumn(decl);
        _decl = decl;
        _name = name;
        _type = type;
        _required = required;
        _defaultValue = def;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    // These are not associated with a domain or persisted type
    @Override
    public String getURI()
    {
        return "#" + _name;
    }

    @NotNull
    @Override
    public JdbcType getJdbcType()
    {
        return _type.getJdbcType();
    }

    @Override
    public Object getDefault()
    {
        return _defaultValue;
    }

    @Override
    public boolean isRequired()
    {
        return _required;
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        int count = _decl.childList().size();
        if (count > 0)
            _decl.childList().get(0).appendSource(builder);
        if (count > 1)
        {
            _type.appendSource(builder);
        }
        if (count > 2)
        {
            builder.append(" DEFAULT ");
            _decl.childList().get(2).appendSource(builder);
        }
    }

    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        builder.append("CAST(? AS ");
        _type.appendSql(builder, query);
        builder.append(")");
        builder.add(this);
    }

    @Override
    public boolean equalsNode(QNode other)
    {
        return other instanceof QParameter &&
                _name.equals(((QParameter)other)._name);
    }

    @Override
    public boolean isConstant()
    {
        return true;
    }
}
