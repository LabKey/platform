/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.Path;
import org.labkey.api.writer.ContainerUser;

/*
* User: Dave
* Date: Dec 11, 2008
* Time: 3:42:39 PM
*/
public class ModuleReportIdentifier extends AbstractReportIdentifier
{
    protected static final String PREFIX = "module:";

    private final Module _module;
    private final Path _reportPath;

    public ModuleReportIdentifier(Module module, Path reportPath)
    {
        assert null != module && null != reportPath;
        _module = module;
        _reportPath = reportPath;
    }

    public ModuleReportIdentifier(String id) throws IllegalArgumentException
    {
        if (!id.startsWith(PREFIX))
            throw new IllegalArgumentException("Not a valid module report id");

        Path moduleAndPath = Path.parse(id.substring(PREFIX.length()));
        if (moduleAndPath.size() < 2)
            throw new IllegalArgumentException("No / character after prefix");

        _module = ModuleLoader.getInstance().getModule(moduleAndPath.get(0));
        _reportPath = moduleAndPath.subpath(1, moduleAndPath.size());
    }

    @Override
    public String toString()
    {
        if(null == _module || null == _reportPath)
            return "Invalid Identifier!";
        else
            return PREFIX + getModule().getName() + "/" + getReportPath();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ModuleReportIdentifier that = (ModuleReportIdentifier) o;

        if (!_module.getName().equals(that._module.getName())) return false;
        if (!_reportPath.equals(that._reportPath)) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _module.getName().hashCode();
        result = 31 * result + _reportPath.hashCode();
        return result;
    }

    public Module getModule()
    {
        return _module;
    }

    public Path getReportPath()
    {
        return _reportPath;
    }

    @Override
    public int getRowId()
    {
        return -1;
    }

    public Report getReport(ContainerUser cu)
    {
        if (null != getModule())
        {
            ReportService service = ReportService.get();
            ReportDescriptor d = service.getModuleReportDescriptor(
                    getModule(), getReportPath().toString("","")
            );

            if (null != d)
                return service.createReportInstance(d);
        }

        return null;
    }
}
