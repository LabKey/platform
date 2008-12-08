/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.reports.report;

import org.labkey.api.module.ModuleReportLoader;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/*
* User: Dave
* Date: Dec 4, 2008
* Time: 4:04:35 PM
*/
public class ModuleRReportDescriptor extends RReportDescriptor
{
    private File _sourceFile;
    private long _lastModified;

    public ModuleRReportDescriptor(String reportKey, String reportName, String script, File sourceFile)
    {
        _sourceFile = sourceFile;
        if(_sourceFile.exists())
            _lastModified = sourceFile.lastModified();
        setReportKey(reportKey);
        setReportName(reportName);
        setProperty(Prop.script, script);
    }

    @Override
    public String getProperty(String key)
    {
        //if the key = script, check to see if the source file contents have changed
        if(key.equalsIgnoreCase(Prop.script.name()))
            ensureScriptCurrent();

        return super.getProperty(key);
    }

    @Override
    public Map<String, Object> getProperties()
    {
        ensureScriptCurrent();
        return super.getProperties();    //To change body of overridden methods use File | Settings | File Templates.
    }

    protected void ensureScriptCurrent()
    {
        if(_sourceFile.exists() && _sourceFile.lastModified() > _lastModified)
        {
            try
            {
                String script = ModuleReportLoader.getFileContents(_sourceFile);
                if(null != script)
                    setProperty(Prop.script, script);
            }
            catch(IOException e)
            {
                Logger.getLogger(ModuleRReportDescriptor.class).warn("Unable to reload report script from source file "
                        + _sourceFile.getPath(), e);
            }
        }
    }
}