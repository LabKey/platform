/*
 * Copyright (c) 2005-2007 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.util;

import org.apache.log4j.Logger;

import java.util.Map;

/**
 * User: migra
 * Date: Dec 28, 2004
 * Time: 2:59:44 PM
 */
public class BooleanExpression implements Expression
{
    static Logger _log = Logger.getLogger(BooleanExpression.class);

    private Expression expr = null;
    private boolean val = false;

    public BooleanExpression(boolean val)
    {
        this.val = val;
    }

    public BooleanExpression(Expression expr)
    {
        this.expr = expr;
    }

    public void set(boolean val)
    {
        this.val = val;
        expr = null;
    }

    public void set(Expression expr)
    {
        this.expr = expr;
    }


    public Object eval(Map context)
    {
        return get(context);
    }


    public boolean get(Map context)
    {
        if (null == expr)
            return val;

        Object o = expr.eval(context);
        if (!(o instanceof Boolean))
        {
            _log.warn("BooleanExpression '" + expr + "'.eval() returned " + o.getClass() + " : " + o.toString());
            return false;
        }
        if (null == o)
            return false;

        Boolean b = (Boolean) o;
        return b.booleanValue();
    }


    public String getStringExpr()
    {
        return expr==null ? null : expr.toString();
    }

    public Boolean getBooleanExpr()
    {
        return expr==null ? val : null;
    }
}
