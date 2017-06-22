/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

package org.labkey.core.admin;

import org.labkey.api.action.ActionType;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.ActionsHelper;
import org.labkey.api.data.TSVWriter;

import java.util.Arrays;
import java.util.Map;

/*
* User: adam
* Date: Oct 21, 2009
* Time: 8:24:21 PM
*/
public class ActionsTsvWriter extends TSVWriter
{
    @Override
    protected void writeColumnHeaders()
    {
        writeLine(Arrays.asList("module", "controller", "action", "type", "invocations", "cumulative", "average", "max"));
    }

    @Override
    protected void writeBody()
    {
        try
        {
            Map<String, Map<String, Map<String, SpringActionController.ActionStats>>> modules = ActionsHelper.getActionStatistics();

            for (Map.Entry<String, Map<String, Map<String, SpringActionController.ActionStats>>> module : modules.entrySet())
            {
                String moduleName = module.getKey();
                Map<String, Map<String, SpringActionController.ActionStats>> controllers = module.getValue();

                for (Map.Entry<String, Map<String, SpringActionController.ActionStats>> controller : controllers.entrySet())
                {
                    String controllerName = controller.getKey();
                    Map<String, SpringActionController.ActionStats> actions = controller.getValue();

                    for (Map.Entry<String, SpringActionController.ActionStats> action : actions.entrySet())
                    {
                        _pw.print(moduleName);
                        _pw.print('\t');
                        _pw.print(controllerName);
                        _pw.print('\t');
                        _pw.print(action.getKey());
                        _pw.print('\t');

                        SpringActionController.ActionStats stats = action.getValue();
                        Class<? extends ActionType> type = stats.getActionType();
                        if (null != type)
                            _pw.print(type.getSimpleName());
                        _pw.print('\t');
                        _pw.print(stats.getCount());
                        _pw.print('\t');
                        _pw.print(stats.getElapsedTime());
                        _pw.print('\t');
                        _pw.print(0 == stats.getCount() ? 0 : stats.getElapsedTime() / stats.getCount());
                        _pw.print('\t');
                        _pw.println(stats.getMaxTime());
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
