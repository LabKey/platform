/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.pipeline;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.cmd.BooleanToSwitch;
import org.labkey.api.pipeline.cmd.ListToCommandArgs;
import org.labkey.api.pipeline.cmd.RequiredSwitch;
import org.labkey.api.pipeline.cmd.SubstitutionWithSwitch;
import org.labkey.api.pipeline.cmd.TaskToCommandArgs;
import org.labkey.api.pipeline.cmd.UnixCompactSwitchFormat;
import org.labkey.api.pipeline.cmd.UnixNewSwitchFormat;
import org.labkey.api.pipeline.cmd.UnixSwitchFormat;
import org.labkey.api.pipeline.cmd.ValueInLine;
import org.labkey.api.pipeline.cmd.ValueToMultiCommandArgs;
import org.labkey.api.pipeline.cmd.ValueToSwitch;
import org.labkey.api.pipeline.cmd.ValueWithSwitch;
import org.labkey.pipeline.analysis.CommandTaskImpl;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * User: cnathe
 * Date: Mar 20, 2012
 */
public class PipelineCommandTestCase extends Assert
{
    private Mockery _context;

    @Before
    public void setUp()
    {
        _context = new Mockery();
        _context.setImposteriser(ClassImposteriser.INSTANCE);
    }

    @Test
    public void testSubstitution() throws Exception
    {
        SubstitutionWithSwitch sws = new SubstitutionWithSwitch();
        UnixSwitchFormat switchFormat = new UnixNewSwitchFormat();
        switchFormat.setSeparator(" ");
        sws.setSwitchFormat(switchFormat);
        sws.setSwitchName("filter");
        sws.setRegex("([0-9]+)\\-([0-9]+)");
        sws.setSubstitution("mzWindow [${1},${2}]");
        List<String> args = sws.toArgs("0-2000");
        assertEquals("Wrong number of args", 2, args.size());
        assertEquals("Wrong argument value", "--filter", args.get(0));
        assertEquals("Wrong argument value", "mzWindow [0,2000]", args.get(1));
    }

    @Test
    public void testNullSubstitution() throws Exception
    {
        SubstitutionWithSwitch sws = new SubstitutionWithSwitch();
        UnixSwitchFormat switchFormat = new UnixNewSwitchFormat();
        switchFormat.setSeparator(" ");
        sws.setSwitchFormat(switchFormat);
        sws.setSwitchName("filter");
        sws.setRegex("([0-9]+)\\-([0-9]+)");
        sws.setSubstitution("mzWindow [${1},${2}]");
        List<String> args = sws.toArgs(null);
        assertEquals("Wrong number of args", 0, args.size());
    }

    @Test
    public void testPipelineIndividualCmds() throws Exception
    {
        // test the different PipelineCommands used in the ms2Context.xml
        // with different switch formats

        ValueWithSwitch test1 = new ValueWithSwitch();
        test1.setSwitchFormat(new UnixSwitchFormat());
        test1.setSwitchName("a");
        List<String> args1 = test1.toArgs("Test1");
        assertEquals("Unexpected length for ValueWithSwitch args", 2, args1.size());
        assertEquals("Unexpected arg for ValueWithSwitch", "-a", args1.get(0));
        assertEquals("Unexpected arg for ValueWithSwitch", "Test1", args1.get(1));
        args1 = test1.toArgs(null);
        assertEquals("Unexpected length for ValueWithSwitch args", 0, args1.size());

        test1.setParameter("test1");
        test1.setDefault("default1");
        assertEquals("Expected default value", "default1", test1.getValue(Collections.singletonMap("test1", null)));
        args1 = test1.toArgs(test1.getValue(Collections.singletonMap("test1", null)));
        assertEquals("Unexpected length for ValueWithSwitch args", 2, args1.size());
        assertEquals("Unexpected arg for ValueWithSwitch", "-a", args1.get(0));
        assertEquals("Unexpected arg for ValueWithSwitch", "default1", args1.get(1));


        BooleanToSwitch test2 = new BooleanToSwitch();
        test2.setSwitchFormat(new UnixSwitchFormat());
        test2.setSwitchName("b");
        List<String> args2 = test2.toArgs("yes");
        assertEquals("Unexpected length for BooleanToSwitch args", 1, args2.size());
        assertEquals("Unexpected arg for ValueWithSwitch", "-b", args2.get(0));
        args2 = test2.toArgs("no");
        assertEquals("Unexpected length for BooleanToSwitch args", 0, args2.size());
        args2 = test2.toArgs("somethingNotYesOrNo");
        assertEquals("Unexpected length for BooleanToSwitch args", 0, args2.size());

        test2.setParameter("test2");
        test2.setDefault("yes");
        assertEquals("Expected default value", "yes", test2.getValue(Collections.singletonMap("test2", null)));
        args2 = test2.toArgs(test2.getValue(Collections.singletonMap("test2", null)));
        assertEquals("Unexpected length for BooleanToSwitch args", 1, args2.size());

        RequiredSwitch test3 = new RequiredSwitch();
        test3.setSwitchFormat(new UnixNewSwitchFormat());
        test3.setSwitchName("c");
        List<String> args3 = test3.toArgsInner(null, null);
        assertEquals("Unexpected length for RequiredSwitch args", 1, args3.size());
        assertEquals("Unexpected arg for RequiredSwitch", "--c", args3.get(0));
        test3.setValue("Test3");
        args3 = test3.toArgsInner(null, null);
        assertEquals("Unexpected length for RequiredSwitch args", 1, args3.size());
        assertEquals("Unexpected arg for RequiredSwitch", "--c=Test3", args3.get(0));

        ValueToSwitch test4 = new ValueToSwitch();
        test4.setSwitchFormat(new UnixCompactSwitchFormat());
        test4.setSwitchName("d");
        List<String> args4 = test4.toArgs("anything");
        assertEquals("Unexpected length for ValueToSwitch args", 1, args4.size());
        assertEquals("Unexpected arg for ValueToSwitch", "-d", args4.get(0));
        args4 = test4.toArgs(null);
        assertEquals("Unexpected length for ValueToSwitch args", 0, args4.size());

        ValueToMultiCommandArgs test5 = new ValueToMultiCommandArgs();
        test5.setDelimiter(" ");
        List<String> args5 = test5.toArgs("Test5 -100");
        assertEquals("Unexpected length for ValueToMultiCommandArgs args", 2, args5.size());
        assertEquals("Unexpected arg for ValueToMultiCommandArgs", "Test5", args5.get(0));
        assertEquals("Unexpected arg for ValueToMultiCommandArgs", "-100", args5.get(1));
        test5.setDelimiter("-");
        args5 = test5.toArgs("Test5 -100");
        assertEquals("Unexpected length for ValueToMultiCommandArgs args", 2, args5.size());
        assertEquals("Unexpected arg for ValueToMultiCommandArgs", "Test5 ", args5.get(0));
        assertEquals("Unexpected arg for ValueToMultiCommandArgs", "100", args5.get(1));
        args5 = test5.toArgs(null);
        assertEquals("Unexpected length for ValueToMultiCommandArgs args", 0, args5.size());
    }

    @Test
    public void testPipelineCombinedCmds() throws Exception
    {
        // test stringing together a set of command parameters to view the resulting command args

        RequiredSwitch test1 = new RequiredSwitch();
        test1.setSwitchFormat(new UnixSwitchFormat());
        test1.setSwitchName("a");
        test1.setValue("100");

        BooleanToSwitch test2 = new BooleanToSwitch();
        test2.setSwitchFormat(new UnixSwitchFormat());
        test2.setParameter("test, boolean to switch");
        test2.setSwitchName("b");

        ValueWithSwitch test3 = new ValueWithSwitch();
        test3.setSwitchFormat(new UnixSwitchFormat());
        test3.setParameter("test, value with switch");
        test3.setSwitchName("c");

        ValueToSwitch test4 = new ValueToSwitch();
        test4.setSwitchFormat(new UnixCompactSwitchFormat());
        test4.setParameter("test, value to switch with multi args");
        test4.setSwitchName("d");

        ValueToMultiCommandArgs test5 = new ValueToMultiCommandArgs();
        test5.setParameter("test, value to switch with multi args");
        test5.setDelimiter(" ");

        ValueInLine test6 = new ValueInLine();
        test6.setParameter("test, inline value with default");
        test6.setDefault("e");

        ListToCommandArgs commandList = new ListToCommandArgs();
        commandList.addConverter(test1);
        commandList.addConverter(test2);
        commandList.addConverter(test3);
        commandList.addConverter(test4);
        commandList.addConverter(test5);
        commandList.addConverter(test6);

        // expected param args to be : -a 100 -b -c testing -d testing2 100 -999
        final PipelineJob j = _context.mock(PipelineJob.class);
        _context.checking(new Expectations() {{
            allowing(j).getParameters();
            Map<String, String> params = new HashMap<>();
            params.put("test, boolean to switch", "yes");
            params.put("test, value with switch", "testing");
            params.put("test, value to switch with multi args", "testing2 100 -999");
            params.put("test, inline value with default", "f");
            will(returnValue(params));
        }});
        
        List<String> args = commandList.toArgs(new CommandTaskImpl(j, new CommandTaskImpl.Factory()), new HashSet<TaskToCommandArgs>());
        assertEquals("Unexpected length for args", 10, args.size());
        assertEquals("Unexpected arg for RequiredSwitch", "-a", args.get(0));
        assertEquals("Unexpected arg for RequiredSwitch", "100", args.get(1));
        assertEquals("Unexpected arg for BooleanToSwitch", "-b", args.get(2));
        assertEquals("Unexpected arg for ValueWithSwitch", "-c", args.get(3));
        assertEquals("Unexpected arg for ValueWithSwitch", "testing", args.get(4));
        assertEquals("Unexpected arg for ValueToSwitch", "-d", args.get(5));
        assertEquals("Unexpected arg for ValueToMultiCommandArgs", "testing2", args.get(6));
        assertEquals("Unexpected arg for ValueToMultiCommandArgs", "100", args.get(7));
        assertEquals("Unexpected arg for ValueToMultiCommandArgs", "-999", args.get(8));
        assertEquals("Unexpected arg for ValueInLine", "f", args.get(9));

        // expected param args to be : -a 100
        final PipelineJob j2 = _context.mock(PipelineJob.class, "job2");
        _context.checking(new Expectations() {{
            allowing(j2).getParameters();
            Map<String, String> params = new HashMap<>();
            params.put("test, boolean to switch", "no");
            params.put("test, value with switch", null);
            params.put("test, value to switch with multi args", "");
            params.put("test, inline value with default", null);
            will(returnValue(params));
        }});
        List<String> args2 = commandList.toArgs(new CommandTaskImpl(j2, new CommandTaskImpl.Factory()), new HashSet<TaskToCommandArgs>());
        assertEquals("Unexpected length for args2", 3, args2.size());
        assertEquals("Unexpected arg for RequiredSwitch", "-a", args2.get(0));
        assertEquals("Unexpected arg for RequiredSwitch", "100", args2.get(1));
        assertEquals("Unexpected arg for ValueInLine", "e", args2.get(2));
    }

}
