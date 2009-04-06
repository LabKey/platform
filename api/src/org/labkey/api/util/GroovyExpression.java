/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import org.apache.log4j.Logger;
import org.codehaus.groovy.control.CompilationFailedException;

import java.io.IOException;
import java.util.Map;

/**
 * User: migra
 * Date: Dec 28, 2004
 * Time: 11:32:56 AM
 */
public class GroovyExpression implements Expression
{
    private static GroovyClassLoader loader = new GroovyClassLoader(GroovyExpression.class.getClassLoader());
    private static Logger _log = Logger.getLogger(GroovyExpression.class);
    private Class groovyClass;
    private String expr;

    public GroovyExpression(String expr) throws IOException, CompilationFailedException
    {
        this.expr = expr;
        groovyClass = loader.parseClass(expr);
    }

    public Object eval(Map context)
    {
        try
        {
            Script instance = (Script) groovyClass.newInstance();
            instance.setBinding(new Binding(context));
            return instance.run();
        }
        catch (Exception x)
        {
            _log.error("Exception evaluating " + expr, x);
            return "Exception evaluating script: " + x.getMessage();
        }
    }

    public String toString()
    {
        return expr;
    }
}
