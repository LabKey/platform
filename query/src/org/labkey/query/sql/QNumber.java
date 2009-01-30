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

package org.labkey.query.sql;

import java.sql.Types;
import java.math.BigInteger;
import java.math.BigDecimal;

public class QNumber extends QExpr implements IConstant
{
	Number _value = null;
	int _sqlType = Types.DOUBLE;
	
    public QNumber(Node n)
    {
		super(false);
		from(n);

		try
		{
			switch (getTokenType())
			{
				case SqlTokenTypes.NUM_DOUBLE:
				case SqlTokenTypes.NUM_FLOAT:
					setValue(convertDouble(getTokenText()));
					break;
				case SqlTokenTypes.NUM_LONG:
				case SqlTokenTypes.NUM_INT:
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
			_sqlType = Types.DOUBLE;
        else if (value instanceof Integer || value instanceof Long)
			_sqlType = Types.INTEGER;
		else if (value instanceof BigInteger || value instanceof BigDecimal)
			_sqlType = Types.DECIMAL;
        setTokenText(value.toString());
    }


    public Number getValue()
    {
		return _value;
    }

    public void appendSql(SqlBuilder builder)
    {
        builder.append(getValueString());
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.append(getValueString());
    }

    public int getSqlType()
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
}
