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
package org.labkey.api.reports;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.reports.ExternalScriptEngineDefinition.EngineKind;
import org.labkey.api.reports.ExternalScriptEngineDefinition.Type;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Script engine providers keyed by language and kind, since LOCAL and REMOTE exist for several languages. */
public final class ScriptEngineProviderRegistry
{
    private static final ScriptEngineProviderRegistry INSTANCE = new ScriptEngineProviderRegistry();

    private record Key(Type type, EngineKind kind) {}

    private final Map<Key, ScriptEngineProvider> _providers = new ConcurrentHashMap<>();

    private ScriptEngineProviderRegistry()
    {
    }

    public static ScriptEngineProviderRegistry get()
    {
        return INSTANCE;
    }

    public void register(@NotNull Type type, @NotNull EngineKind kind, @NotNull ScriptEngineProvider provider)
    {
        if (_providers.putIfAbsent(new Key(type, kind), provider) != null)
            throw new IllegalStateException("A script engine provider is already registered for " + type + " " + kind);
    }

    public @Nullable ScriptEngineProvider get(@NotNull Type type, @NotNull EngineKind kind)
    {
        return _providers.get(new Key(type, kind));
    }
}
