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

import javax.script.ScriptContext;

/**
 * Script engine for locally-executed Python scripts (Python assay transform scripts configured as an
 * external ".py" engine). Behaves like the base {@link ExternalScriptEngine} except that it prepends a capture prolog to
 * track which Python modules each script loads; see
 * {@link org.labkey.api.reports.report.ScriptPackageUsageTracker}.
 */
public class PythonScriptEngine extends ExternalScriptEngine
{
    private static final String PACKAGES_FILE = "labkeyPythonPackages.txt";

    // Python prepended to a user script that registers an atexit hook to capture the loaded modules (top-level names,
    // excluding the standard library). atexit runs on interpreter shutdown, including after an uncaught exception, so
    // we also capture usage for scripts that fail. try/except means a capture failure can never break
    // the script run, and registering the hook inside the try keeps a capture error out of the script's own traceback.
    private static final String PACKAGE_CAPTURE_PROLOG = """
            # --- LabKey Python package usage capture ---
            try:
                import atexit as _lk_atexit, os as _lk_os, sys as _lk_sys
                _lk_packages_file = _lk_os.path.join(_lk_os.getcwd(), '%s')
                def _lk_write_packages():
                    try:
                        _lk_stdlib = set(getattr(_lk_sys, 'stdlib_module_names', ()))
                        _lk_mods = sorted({m.split('.')[0] for m in list(_lk_sys.modules)} - _lk_stdlib)
                        with open(_lk_packages_file, 'w') as _lk_f:
                            _lk_f.write('\\n'.join(m for m in _lk_mods if m and not m.startswith('_')))
                    except Exception:
                        pass
                _lk_atexit.register(_lk_write_packages)
            except Exception:
                pass
            """.formatted(PACKAGES_FILE);

    public PythonScriptEngine(ExternalScriptEngineDefinition def)
    {
        super(def);
    }

    @Override
    protected @Nullable String getPackageCaptureProlog(ScriptContext context)
    {
        return PACKAGE_CAPTURE_PROLOG;
    }

    @Override
    protected void recordPackageUsage(ScriptContext context)
    {
        readPackageSidecar(context, PACKAGES_FILE, "python");
    }
}
