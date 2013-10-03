/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.antlr.runtime.tree.CommonTree;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.JdbcType;
import org.labkey.query.sql.antlr.SqlBaseParser;

import java.math.BigDecimal;
import java.math.BigInteger;

public class QNumber extends QExpr implements IConstant
{
	Number _value = null;
	JdbcType _sqlType = JdbcType.DOUBLE;
	

	public QNumber(String s)
	{
		if (StringUtils.containsOnly(s,"0123456789"))
			setValue(convertInteger(s));
		else
			setValue(convertDouble(s));
	}

	
    public QNumber(CommonTree n)
    {
		super(false);
		from(n);

		try
		{
			switch (getTokenType())
			{
				case SqlBaseParser.NUM_DOUBLE:
				case SqlBaseParser.NUM_FLOAT:
					setValue(convertDouble(getTokenText()));
					break;
				case SqlBaseParser.NUM_LONG:
				case SqlBaseParser.NUM_INT:
					setValue(convertInteger(getTokenText()));
					break;
				default:
					throw new IllegalArgumentException(getTokenText());
			}
		}
		catch (NumberFormatException x)
		{
			//
		}
    }

		
	public QNumber(Number value)
    {
		setValue(value);
	}


	private void setValue(Number value)
	{
		_value = value;
		if (value instanceof Double || value instanceof Float)
			_sqlType = JdbcType.DOUBLE;
        else if (value instanceof Integer || value instanceof Long)
			_sqlType = JdbcType.INTEGER;
		else if (value instanceof BigInteger || value instanceof BigDecimal)
			_sqlType = JdbcType.DECIMAL;
        setTokenText(value.toString());
    }


    public Number getValue()
    {
		return _value;
    }

    public void appendSql(SqlBuilder builder, Query query)
    {
        builder.append(getValueString());
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append(getValueString());
    }

    public JdbcType getSqlType()
    {
        return _sqlType;
    }

    public String getValueString()
    {
        return _value == null ? getTokenText() : _value.toString();
    }


	Number convertInteger(String s)
	{
		int base = 10;
		if (s.startsWith("0x"))
		{
			base = 16;
			s = s.substring(2);
		}
		try
		{
            if (s.endsWith("l") || s.endsWith("L"))
                s = s.substring(0,s.length()-1);
			return Long.parseLong(s, base);
		}
		catch (NumberFormatException x)
		{
			return new BigInteger(s, base);
		}
	}
	

	Number convertDouble(String s)
	{
		try
		{
			return Double.parseDouble(s);
		}
		catch (NumberFormatException x)
		{
			return new BigDecimal(s);
		}
	}

    @Override
     public boolean equalsNode(QNode other)
    {
        return other instanceof QNumber && getValue().equals(((QNumber) other).getValue());
    }

    @Override
    public boolean isConstant()
    {
        return true;
    }
}
