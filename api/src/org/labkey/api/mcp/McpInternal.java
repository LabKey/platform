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
package org.labkey.api.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an MCP tool as internal-only. Internal tools are available to in-process callers (e.g.,
 * {@link AbstractAgentAction}-backed agents that supply tool-specific context via {@link McpContext})
 * but are filtered out of the externally exposed MCP server: they do not appear in {@code listTools}
 * and direct {@code callTool} requests for them are rejected.
 * <p>
 * Use this for tools that require request-scoped context the LLM cannot reasonably supply on its own.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpInternal
{
    /** Optional reason surfaced in startup logs. */
    String value() default "";
}
