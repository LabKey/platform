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

package org.labkey.query.sql;

import org.antlr.runtime.CommonToken;
import org.antlr.runtime.tree.CommonTree;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.QueryParseException;
import org.labkey.query.sql.antlr.SqlBaseParser;

import java.math.BigDecimal;
import java.math.BigInteger;

public class QNumber extends QExpr implements IConstant
{
	Number _value = null;
	JdbcType _sqlType = JdbcType.DOUBLE;


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
			// QueryParseException (not IllegalArgumentException) yields a located parse error; null cause skips mothership logging
			throw new QueryParseException("Invalid numeric literal: " + getTokenText(), null, getLine(), getColumn());
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


    @Override
    public Number getValue()
    {
		return _value;
    }

    @Override
    public void appendSql(SqlBuilder builder, Query query)
    {
        builder.append(new SQLFragment().setSqlUnsafe(getValueString()));
    }

    @Override
    public void appendSource(SourceBuilder builder)
    {
        builder.append(getValueString());
    }

    @NotNull
	@Override
	public JdbcType getJdbcType()
    {
        return _sqlType;
    }

    @Override
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
		boolean floatish = false;
		if (s.endsWith("f") || s.endsWith("F") || s.endsWith("d") || s.endsWith("D"))
		{
			s = s.substring(0,s.length()-1);
			floatish = true;
		}
		if (StringUtils.containsAny(s, "eE"))
			floatish = true;
		if (floatish)
		{
			// try double first fall-back to decimal
			try
			{
				return Double.parseDouble(s);
			}
			catch (NumberFormatException x)
			{
				return new BigDecimal(s);
			}
		}
		else
		{
			// try decimal first fall-back to double
			try
			{
				return new BigDecimal(s);
			}
			catch (NumberFormatException x)
			{
				return Double.parseDouble(s);
			}
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


    public static class TestCase extends Assert
    {
        private static CommonTree token(int type, String text)
        {
            return new CommonTree(new CommonToken(type, text));
        }

        @Test
        public void testValidIntegerToken()
        {
            QNumber n = new QNumber(token(SqlBaseParser.NUM_INT, "42"));
            assertEquals(42L, n.getValue());
            assertEquals("42", n.getValueString());
            assertEquals(JdbcType.INTEGER, n.getJdbcType());
        }

        @Test
        public void testValidLongToken()
        {
            QNumber n = new QNumber(token(SqlBaseParser.NUM_LONG, "9999999999"));
            assertEquals(9999999999L, n.getValue());
        }

        @Test
        public void testValidDoubleToken()
        {
            QNumber n = new QNumber(token(SqlBaseParser.NUM_DOUBLE, "1.5"));
            assertEquals(new BigDecimal("1.5"), n.getValue());
            assertEquals(JdbcType.DECIMAL, n.getJdbcType());
        }

        @Test
        public void testValidFloatScientificToken()
        {
            // 'e' in the token text triggers the floatish branch -> Double.parseDouble
            QNumber n = new QNumber(token(SqlBaseParser.NUM_FLOAT, "1.5e2"));
            assertEquals(150.0, n.getValue().doubleValue(), 0.0);
        }

        /**
         * An unparseable numeric token must surface as a parse-time QueryParseException so SqlParser
         * routes it into _parseErrors as a user-facing error rather than silently emitting the raw lexeme.
         */
        @Test
        public void testInvalidIntegerStrictThrows()
        {
            try
            {
                new QNumber(token(SqlBaseParser.NUM_INT, "1.2.3"));
                fail("Expected QueryParseException for unparseable NUM_INT token");
            }
            catch (QueryParseException expected)
            {
                assertTrue("error message should include the bad token: " + expected.getMessage(),
                        expected.getMessage().contains("1.2.3"));
            }
        }

        @Test
        public void testInvalidDoubleStrictThrows()
        {
            try
            {
                new QNumber(token(SqlBaseParser.NUM_DOUBLE, "1.2.3.4"));
                fail("Expected QueryParseException for unparseable NUM_DOUBLE token");
            }
            catch (QueryParseException expected)
            {
                assertTrue(expected.getMessage().contains("1.2.3.4"));
            }
        }
    }
}
