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
 * track which Python packages each script imports; see
 * {@link org.labkey.api.reports.report.ScriptPackageUsageTracker}.
 */
public class PythonScriptEngine extends ExternalScriptEngine
{
    private static final String PACKAGES_FILE = "labkeyPythonPackages.txt";

    // Python appended to a user script to capture the modules it imports. The sidecar file's absolute path is
    // embedded (see getPackageCaptureEpilog) rather than derived from os.getcwd(), so that a script that changes the
    // working directory still writes the sidecar where recordPackageUsage() looks for it.
    // try/except means a capture failure can never break the script run.
    // Note: we are tracking module names as written in the source, not installable distribution names - 'yaml', not 'PyYAML'.
    private static final String PACKAGE_CAPTURE_EPILOG = """
            try:
                import ast as _lk_ast, sys as _lk_sys

                def _lk_pkg(_lk_dotted):
                    # Report the shortest prefix that is a real module
                    _lk_parts = _lk_dotted.split('.')
                    for _lk_i in range(1, len(_lk_parts)):
                        _lk_prefix = '.'.join(_lk_parts[:_lk_i])
                        if getattr(_lk_sys.modules.get(_lk_prefix), '__file__', None):
                            return _lk_prefix
                    # Nothing loaded under this name - an optional dependency this server lacks, or an import in a
                    # branch that never ran - so record it as written.
                    return _lk_dotted if _lk_parts[0] in _lk_sys.modules else _lk_parts[0]

                with open(globals().get('__file__') or _lk_sys.argv[0], 'rb') as _lk_src:
                    _lk_tree = _lk_ast.parse(_lk_src.read())
                _lk_names = set()
                for _lk_node in _lk_ast.walk(_lk_tree):
                    if isinstance(_lk_node, _lk_ast.Import):
                        _lk_names.update(_lk_pkg(_lk_a.name) for _lk_a in _lk_node.names)
                    elif isinstance(_lk_node, _lk_ast.ImportFrom) and not _lk_node.level and _lk_node.module:
                        _lk_names.update(_lk_pkg(_lk_node.module + '.' + _lk_a.name) for _lk_a in _lk_node.names)
                _lk_std = set(getattr(_lk_sys, 'stdlib_module_names', ('ast', 'sys')))
                _lk_names = sorted(n for n in _lk_names if n[:1] != '_' and n.split('.')[0] not in _lk_std)
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
