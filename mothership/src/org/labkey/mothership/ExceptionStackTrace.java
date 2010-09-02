/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.mothership;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.HashHelpers;

import java.io.StringReader;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Apr 20, 2006
 */
public class ExceptionStackTrace
{
    private String _container;
    private int _exceptionStackTraceId;
    private String _stackTrace;
    private String _stackTraceHash;
    private Integer _assignedTo;
    private Integer _bugNumber;
    private String _comments;

    public String getStackTrace()
    {
        return _stackTrace;
    }

    public void setStackTrace(String stackTrace)
    {
        _stackTrace = stackTrace;
    }

    public int getExceptionStackTraceId()
    {
        return _exceptionStackTraceId;
    }

    public void setExceptionStackTraceId(int exceptionStackTraceId)
    {
        _exceptionStackTraceId = exceptionStackTraceId;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public String getStackTraceHash()
    {
        return _stackTraceHash;
    }

    public void setStackTraceHash(String stackTraceHash)
    {
        _stackTraceHash = stackTraceHash;
    }

    public void hashStackTrace()
    {
        String[] ignoreLineNumberList = { "at java.", "at org.apache.", "at javax.", "at sun." };
        if (_stackTrace != null)
        {
            BufferedReader reader = new BufferedReader(new StringReader(_stackTrace));
            StringBuilder sb = new StringBuilder();
            try
            {
                String line = reader.readLine();
                // Strip off the message part of the exception for hashing
                if (line != null)
                {
                    int index = line.indexOf(":");
                    if (index != -1)
                    {
                        sb.append(line.substring(0, index));
                    }
                    else
                    {
                        sb.append(line);
                    }
                }
                while ((line = reader.readLine()) != null)
                {
                    // Don't include lines that vary based on reflection
                    // Don't include lines that depend on Groovy view numbers
                    if (!line.trim().startsWith("at sun.reflect.")
                            && !(line.trim().startsWith("..."))
                            && !(line.trim().startsWith("at script") && line.contains("run(script") && line.contains(".groovy:"))
                            && !line.trim().startsWith("Detail:")    // Postgres stack traces can include a second details line
                            && !line.trim().startsWith("Detalhe:"))  // which is oddly sometimes prefixed by "Detalhe:" instead of "Detail:"
                    {
                        // Don't include line numbers that depend on non-labkey version install
                        if (line.trim().startsWith("Caused by:") && line.indexOf(":", line.indexOf(":") + 1) != -1)
                        {
                            line = line.substring(0, line.indexOf(":", line.indexOf(":") + 1));
                        }
                        else
                        {
                            for (String ignoreLineNumber : ignoreLineNumberList)
                            {
                                if (line.trim().startsWith(ignoreLineNumber) && line.contains("("))
                                {
                                    line = line.substring(0, line.lastIndexOf("("));
                                    break;
                                }
                            }
                        }

                        sb.append(line);
                        sb.append("\n");
                    }
                }
            }
            catch (IOException e)
            {
                // Shouldn't happen - this is an in-memory source
                throw new RuntimeException(e);
            }
            _stackTraceHash = HashHelpers.hash(sb.toString());
        }
    }

    public Integer getAssignedTo()
    {
        return _assignedTo;
    }

    public void setAssignedTo(Integer assignedTo)
    {
        _assignedTo = assignedTo;
    }

    public Integer getBugNumber()
    {
        return _bugNumber;
    }

    public void setBugNumber(Integer bugNumber)
    {
        _bugNumber = bugNumber;
    }

    public String getComments()
    {
        return _comments;
    }

    public void setComments(String comments)
    {
        _comments = comments;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testReflectionHashCombining()
        {
            ExceptionStackTrace stackTrace1 = new ExceptionStackTrace();
            stackTrace1.setStackTrace("java.lang.NullPointerException\n" +
                    "\tat org.labkey.api.view.ViewController.requiresPermission(ViewController.java:231)\n" +
                    "\tat Experiment.ExperimentController.showRun(ExperimentController.java:788)\n" +
                    "\tat Experiment.ExperimentController.showRunGraphDetail(ExperimentController.java:380)\n" +
                    "\tat sun.reflect.GeneratedMethodAccessor709.invoke(Unknown Source)\n" +
                    "\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)\n" +
                    "\tat java.lang.reflect.Method.invoke(Method.java:585)");

            ExceptionStackTrace stackTrace2 = new ExceptionStackTrace();
            stackTrace2.setStackTrace("java.lang.NullPointerException\n" +
                    "\tat org.labkey.api.view.ViewController.requiresPermission(ViewController.java:231)\n" +
                    "\tat Experiment.ExperimentController.showRun(ExperimentController.java:788)\n" +
                    "\tat Experiment.ExperimentController.showRunGraphDetail(ExperimentController.java:380)\n" +
                    "\tat sun.reflect.GeneratedMethodAccessor105.invoke(Unknown Source)\n" +
                    "\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)\n" +
                    "\tat java.lang.reflect.Method.invoke(Method.java:585)");

            stackTrace1.hashStackTrace();
            stackTrace2.hashStackTrace();
            assertEquals(stackTrace1.getStackTraceHash(), stackTrace2.getStackTraceHash());
        }

        @Test
        public void testMessageHashCombining()
        {
            ExceptionStackTrace stackTrace1 = new ExceptionStackTrace();
            stackTrace1.setStackTrace("org.apache.commons.beanutils.ConversionException: For input string: \"null\"\n" +
                    "\tat org.apache.commons.beanutils.converters.IntegerConverter.convert(IntegerConverter.java:118)\n" +
                    "\tat org.apache.commons.beanutils.ConvertUtilsBean.convert(ConvertUtilsBean.java:428)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtilsBean.setProperty(BeanUtilsBean.java:1004)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtilsBean.populate(BeanUtilsBean.java:811)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtils.populate(BeanUtils.java:298)");

            ExceptionStackTrace stackTrace2 = new ExceptionStackTrace();
            stackTrace2.setStackTrace("org.apache.commons.beanutils.ConversionException: For input string: \"something\"\n" +
                    "\tat org.apache.commons.beanutils.converters.IntegerConverter.convert(IntegerConverter.java:118)\n" +
                    "\tat org.apache.commons.beanutils.ConvertUtilsBean.convert(ConvertUtilsBean.java:428)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtilsBean.setProperty(BeanUtilsBean.java:1004)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtilsBean.populate(BeanUtilsBean.java:811)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtils.populate(BeanUtils.java:298)");

            ExceptionStackTrace stackTrace3 = new ExceptionStackTrace();
            stackTrace3.setStackTrace("org.apache.commons.beanutils.ConversionException2: For input string: \"something\"\n" +
                    "\tat org.apache.commons.beanutils.converters.IntegerConverter.convert(IntegerConverter.java:118)\n" +
                    "\tat org.apache.commons.beanutils.ConvertUtilsBean.convert(ConvertUtilsBean.java:428)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtilsBean.setProperty(BeanUtilsBean.java:1004)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtilsBean.populate(BeanUtilsBean.java:811)\n" +
                    "\tat org.apache.commons.beanutils.BeanUtils.populate(BeanUtils.java:298)");

            stackTrace1.hashStackTrace();
            stackTrace2.hashStackTrace();
            stackTrace3.hashStackTrace();
            assertEquals(stackTrace1.getStackTraceHash(), stackTrace2.getStackTraceHash());
            assertFalse(stackTrace1.getStackTraceHash().equals(stackTrace3.getStackTraceHash()));
        }

        @Test
        public void testGroovyHashCombining()
        {
            ExceptionStackTrace stackTrace1 = new ExceptionStackTrace();
            stackTrace1.setStackTrace("java.lang.NoSuchMethodError: java.util.AbstractMap.get(Ljava/lang/Object;)Lorg/labkey/experiment/CustomPropertyRenderer;\n" +
                    "\tat gjdk.org.labkey.experiment.ExperimentController$CustomPropertiesView$1_GroovyReflector.invoke(Unknown Source)\n" +
                    "\tat org.codehaus.groovy.runtime.ScriptBytecodeAdapter.invokeMethodN(ScriptBytecodeAdapter.java:187)\n" +
                    "\tat script1184078621037.run(script1184078621037.groovy:27)\n" +
                    "\tat org.labkey.api.view.GroovyView.renderView(GroovyView.java:476)\n" +
                    "\tat org.labkey.api.view.HttpView.render(HttpView.java:113)\n");

            ExceptionStackTrace stackTrace2 = new ExceptionStackTrace();
            stackTrace2.setStackTrace("java.lang.NoSuchMethodError: get\n" +
                    "\tat gjdk.org.labkey.experiment.ExperimentController$CustomPropertiesView$1_GroovyReflector.invoke(Unknown Source)\n" +
                    "\tat org.codehaus.groovy.runtime.ScriptBytecodeAdapter.invokeMethodN(ScriptBytecodeAdapter.java:187)\n" +
                    "\tat script1184310712861.run(script1184310712861.groovy:27)\n" +
                    "\tat org.labkey.api.view.GroovyView.renderView(GroovyView.java:476)\n" +
                    "\tat org.labkey.api.view.HttpView.render(HttpView.java:113)");

            stackTrace1.hashStackTrace();
            stackTrace2.hashStackTrace();
            assertEquals(stackTrace1.getStackTraceHash(), stackTrace2.getStackTraceHash());
        }

        @Test
        public void testVersionHashCombining()
        {
            ExceptionStackTrace stackTrace1 = new ExceptionStackTrace();
            stackTrace1.setStackTrace("org.jfree.data.general.SeriesException: X-value already exists.\n" +
                    "\tat org.labkey.ms2.MS2Controller.showCombinedElutionGraph(MS2Controller.java:2065)\n" +
                    "\tat java.lang.reflect.Method.invoke(Method.java:597)\n" +
                    "\tat javax.servlet.http.HttpServlet.service(HttpServlet.java:802)\n" +
                    "\tat org.apache.beehive.netui.pageflow.FlowController.invokeActionMethod(FlowController.java:815)\n" +
                    "\tat org.apache.beehive.netui.pageflow.FlowController.execute(FlowController.java:308)\n" +
                    "\tCaused by: Exception: 24\n" +
                    "\t... 61 more");

            ExceptionStackTrace stackTrace2 = new ExceptionStackTrace();
            stackTrace2.setStackTrace("org.jfree.data.general.SeriesException: X-value already exists.\n" +
                    "\tat org.labkey.ms2.MS2Controller.showCombinedElutionGraph(MS2Controller.java:2065)\n" +
                    "\tat java.lang.reflect.Method.invoke(Method.java:601)\n" +
                    "\tat javax.servlet.http.HttpServlet.service(HttpServlet.java:805)\n" +
                    "\tat org.apache.beehive.netui.pageflow.FlowController.invokeActionMethod(FlowController.java:815)\n" +
                    "\tat org.apache.beehive.netui.pageflow.FlowController.execute(FlowController.java:32)\n" +
                    "\tCaused by: Exception: 23\n" +
                    "\t... 62 more");

            stackTrace1.hashStackTrace();
            stackTrace2.hashStackTrace();
            assertEquals(stackTrace1.getStackTraceHash(), stackTrace2.getStackTraceHash());
        }
    }
}
