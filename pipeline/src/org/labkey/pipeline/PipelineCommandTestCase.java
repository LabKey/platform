/*
 * Copyright (c) 2012 LabKey Corporation
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

import junit.framework.Assert;
import org.junit.Test;
import org.labkey.api.pipeline.cmd.BooleanToSwitch;
import org.labkey.api.pipeline.cmd.RequiredSwitch;
import org.labkey.api.pipeline.cmd.UnixCompactSwitchFormat;
import org.labkey.api.pipeline.cmd.UnixNewSwitchFormat;
import org.labkey.api.pipeline.cmd.UnixSwitchFormat;
import org.labkey.api.pipeline.cmd.ValueToMultiCommandArgs;
import org.labkey.api.pipeline.cmd.ValueToSwitch;
import org.labkey.api.pipeline.cmd.ValueWithSwitch;

/**
 * User: cnathe
 * Date: Mar 20, 2012
 */
public class PipelineCommandTestCase extends Assert
{
        @Test
        public void testPipelineIndividualCmds() throws Exception
        {
            // test the different PipelineCommands used in the ms2Context.xml
            // with different switch formats

            ValueWithSwitch test1 = new ValueWithSwitch();
            test1.setSwitchFormat(new UnixSwitchFormat());
            test1.setSwitchName("a");
            String[] args1 = test1.toArgs("Test1");
            assertEquals("Unexpected length for ValueWithSwitch args", 2, args1.length);
            assertEquals("Unexpected arg for ValueWithSwitch", "-a", args1[0]);
            assertEquals("Unexpected arg for ValueWithSwitch", "Test1", args1[1]);
            args1 = test1.toArgs(null);
            assertEquals("Unexpected length for ValueWithSwitch args", 0, args1.length);

            BooleanToSwitch test2 = new BooleanToSwitch();
            test2.setSwitchFormat(new UnixSwitchFormat());
            test2.setSwitchName("b");
            String[] args2 = test2.toArgs("yes");
            assertEquals("Unexpected length for BooleanToSwitch args", 1, args2.length);
            assertEquals("Unexpected arg for ValueWithSwitch", "-b", args2[0]);
            args2 = test2.toArgs("no");
            assertEquals("Unexpected length for BooleanToSwitch args", 0, args2.length);
            args2 = test2.toArgs("somethingNotYesOrNo");
            assertEquals("Unexpected length for BooleanToSwitch args", 0, args2.length);
            test2.setDefault("yes");
            args2 = test2.toArgs(null);
            assertEquals("Unexpected length for BooleanToSwitch args", 1, args2.length);

            RequiredSwitch test3 = new RequiredSwitch();
            test3.setSwitchFormat(new UnixNewSwitchFormat());
            test3.setSwitchName("c");
            String[] args3 = test3.toArgsInner(null, null);
            assertEquals("Unexpected length for RequiredSwitch args", 1, args3.length);
            assertEquals("Unexpected arg for RequiredSwitch", "--c", args3[0]);
            test3.setValue("Test3");
            args3 = test3.toArgsInner(null, null);
            assertEquals("Unexpected length for RequiredSwitch args", 1, args3.length);
            assertEquals("Unexpected arg for RequiredSwitch", "--c=Test3", args3[0]);

            ValueToSwitch test4 = new ValueToSwitch();
            test4.setSwitchFormat(new UnixCompactSwitchFormat());
            test4.setSwitchName("d");
            String[] args4 = test4.toArgs("anything");
            assertEquals("Unexpected length for ValueToSwitch args", 1, args4.length);
            assertEquals("Unexpected arg for ValueToSwitch", "-d", args4[0]);
            args4 = test4.toArgs(null);
            assertEquals("Unexpected length for ValueToSwitch args", 0, args4.length);

            ValueToMultiCommandArgs test5 = new ValueToMultiCommandArgs();
            test5.setDelimiter(" ");
            String[] args5 = test5.toArgs("Test5 -100");
            assertEquals("Unexpected length for ValueToMultiCommandArgs args", 2, args5.length);
            assertEquals("Unexpected arg for ValueToMultiCommandArgs", "Test5", args5[0]);
            assertEquals("Unexpected arg for ValueToMultiCommandArgs", "-100", args5[1]);
            test5.setDelimiter("-");
            args5 = test5.toArgs("Test5 -100");
            assertEquals("Unexpected length for ValueToMultiCommandArgs args", 2, args5.length);
            assertEquals("Unexpected arg for ValueToMultiCommandArgs", "Test5 ", args5[0]);
            assertEquals("Unexpected arg for ValueToMultiCommandArgs", "100", args5[1]);
            args5 = test5.toArgs(null);
            assertEquals("Unexpected length for ValueToMultiCommandArgs args", 0, args5.length);
        }

        @Test
        public void testPipelineCombinedCmds() throws Exception
        {
            // TODO
            //ListToCommandArgs
            //CommandTaskFactorySettings settings = new CommandTaskFactorySettings("UnitTestCommand");
            //settings.setSwitchFormat(new UnixSwitchFormat());
        }
}
