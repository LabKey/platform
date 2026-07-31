/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.api.action;

/**
 * A marker interface for providing URLs. Any interface
 * that extends UrlProvider and is implemented by a controller can be obtained using urlProvider() in
 * {@link org.labkey.api.util.PageFlowUtil}.
 *
 * <p>If your implementation exposes pages worth navigating to from an LLM client, consider also having it
 * implement {@code org.labkey.api.mcp.McpService.McpImpl} and expose an {@code org.labkey.api.mcp.NavigablePage}
 * enum of its argument-free, GET/HTML-rendering pages -- see {@code StudyController.StudyUrlsImpl} for an example.
 * Prefer building the {@code ActionURL} directly from the action class over adding a single-use method here, unless
 * something else in the codebase already needs that method. Only include actions that render an HTML page on a GET
 * request -- check the actual base class, not just the name, since some {@code *ApiAction} subclasses still render
 * real HTML on GET while {@code MutatingApiAction}/{@code ReadOnlyApiAction}/{@code FormHandlerAction}/
 * {@code ConfirmAction} do not. See the MCP Development Guide in {@code org.labkey.api.mcp.McpService} for the
 * full guidance.</p>
 */
public interface UrlProvider
{
}
