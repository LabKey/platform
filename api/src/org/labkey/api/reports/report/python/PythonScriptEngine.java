/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.reports.report.python;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.vfs.FileLike;

import javax.script.ScriptContext;

/**
 * Script engine for locally-executed Python scripts (Python assay transform scripts configured as an
 * external ".py" engine). Behaves like the base {@link ExternalScriptEngine} except that it appends a capture epilog to
 * track which Python packages each script loads; see
 * {@link org.labkey.api.reports.report.ScriptPackageUsageTracker}.
 */
public class PythonScriptEngine extends ExternalScriptEngine
{
    private static final String PACKAGES_FILE = "labkeyPythonPackages.txt";

    // Python appended to a user script to capture the packages it loaded. The sidecar file's absolute path is embedded
    // (see getPackageCaptureEpilog) rather than derived from os.getcwd(), so that a script that changes the working
    // directory still writes the sidecar where recordPackageUsage() looks for it.
    // try/except means a capture failure can never break the script run.
    private static final String PACKAGE_CAPTURE_EPILOG = """
            # --- LabKey Python package usage capture ---
            try:
                import importlib.metadata as _lk_md, sys as _lk_sys
                _lk_dists = _lk_md.packages_distributions()
                _lk_tops = {m.split('.')[0] for m in list(_lk_sys.modules)} - set(_lk_sys.stdlib_module_names)
                _lk_names = sorted({d for t in _lk_tops if t and not t.startswith('_') for d in _lk_dists.get(t, ())})
                with open('%s', 'w') as _lk_f:
                    _lk_f.write('\\n'.join(_lk_names))
            except Exception:
                pass
            """;

    public PythonScriptEngine(ExternalScriptEngineDefinition def)
    {
        super(def);
    }

    @Override
    protected @Nullable String getPackageCaptureEpilog(ScriptContext context)
    {
        return PACKAGE_CAPTURE_EPILOG.formatted(toPythonPath(getWorkingDir(context).resolveChild(PACKAGES_FILE)));
    }

    private static String toPythonPath(FileLike file)
    {
        return file.toNioPathForWrite().toFile().getAbsolutePath()
                .replace('\\', '/')
                .replace("'", "\\'");
    }

    @Override
    protected void recordPackageUsage(ScriptContext context)
    {
        readPackageSidecar(context, PACKAGES_FILE, "python");
    }
}
