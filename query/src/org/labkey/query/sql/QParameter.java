package org.labkey.query.sql;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Jan 3, 2011
 * Time: 1:49:21 PM
 */
public class QParameter extends QNode
{
    QNode _decl;
    final String _name;
    final ParameterType _type;
    final boolean _required;
    final Object _defaultValue;

    QParameter(QNode decl, String name, ParameterType type, boolean required, Object def)
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
    public void appendSource(SourceBuilder builder)
    {
        int count = _decl.childList().size();
        if (count > 0)
            _decl.childList().get(0).appendSource(builder);
        if (count > 1)
        {
            builder.append(" ");
            _decl.childList().get(1).appendSource(builder);
        }
        if (count > 2)
        {
            builder.append(" DEFAULT ");
            _decl.childList().get(2).appendSource(builder);
        }
    }
}
