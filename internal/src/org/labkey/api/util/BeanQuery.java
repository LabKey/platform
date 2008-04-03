/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.view.GroovyView;
import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.iterators.FilterIterator;
import org.codehaus.groovy.control.CompilationFailedException;
import groovy.lang.GroovyClassLoader;

import java.io.IOException;
import java.util.Iterator;
import java.util.Arrays;

/**
 * User: mbellew
 * Date: Apr 8, 2005
 * Time: 9:10:58 AM
 */
public class BeanQuery
{
    private static Predicate compilePredicate(String filter)
            throws CompilationFailedException, IOException
    {
        String predicate =
                "public class __p implements org.apache.commons.collections.Predicate { boolean evaluate(row) {return (\n" +
                        filter + "\n" +
                        ") ? true : false;}}";

        GroovyClassLoader loader = new GroovyClassLoader(GroovyView.class.getClassLoader());
        Class c = loader.parseClass(predicate);
        assert Predicate.class.isAssignableFrom(c);
        try
        {
            return (Predicate) c.newInstance();
        }
        catch (InstantiationException x)
        {
        }
        catch (IllegalAccessException x)
        {
        }
        return null;
    }


    public static class Bean
    {
        private int a;
        private int b;
        private String s;


        Bean(int a, int b, String s)
        {
            this.a = a;
            this.b = b;
            this.s = s;
        }

        public int getA()
        {
            return a;
        }

        public void setA(int a)
        {
            this.a = a;
        }

        public int getB()
        {
            return b;
        }

        public void setB(int b)
        {
            this.b = b;
        }

        public String getS()
        {
            return s;
        }

        public void setS(String s)

        {
            this.s = s;
        }

        public String toString()
        {
            return "" + a + " " + b + " " + s;
        }
    }


    public static void main(String[] args) throws Exception
    {
        Bean[] beans = new Bean[]
                {
                        new Bean(1, 2, "one"),
                        new Bean(2, 4, "two"),
                        new Bean(3, 6, "three")
                };

        String filter = "row.a == 2 || row.b == 4";
        //String filter = "row.s.startsWith(\"t\")";
        Predicate p = compilePredicate(filter);

        Iterator it = new FilterIterator(Arrays.asList(beans).iterator(), p);
        while (it.hasNext())
        {
            Bean bean = (Bean) it.next();
            System.err.println(bean);
        }
    }
}
