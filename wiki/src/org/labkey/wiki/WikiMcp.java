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
package org.labkey.wiki;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.mcp.McpException;
import org.labkey.api.mcp.McpService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.writer.ContainerUser;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * POC: exposes "assistant memory" over MCP, backed by ordinary wikis. Each memory is stored as a
 * wiki whose name is {@code memory:<slug>} with {@link WikiRendererType#MARKDOWN} content. The
 * content is a small YAML-style frontmatter block (name/type/description) followed by the body —
 * the same shape as Claude Code's local file-based memory, so the two stores share one format.
 *
 * <p>The {@code memory:} storage prefix is an implementation detail: tools accept and return bare
 * slugs and never expose the prefix to the caller.</p>
 *
 * <p>This POC deliberately uses a single shared namespace per container — visibility is whatever
 * wiki read/write access the container grants. The private-by-default / per-user {@code OwnedBy}
 * model from the design is deferred.</p>
 */
public class WikiMcp implements McpService.McpImpl
{
    private static final String PREFIX = "memory:";

    private static WikiService wikiService()
    {
        WikiService svc = WikiService.get();
        if (null == svc)
            throw new McpException("The wiki module is not available, so assistant memory cannot be used.");
        return svc;
    }

    @Tool(description =
        "List the assistant memories saved in the current container. " +
        "Assistant memories are durable notes about this LabKey folder — its structure, data " +
        "semantics, project context, and user preferences — that persist across assistant sessions " +
        "and are shared with anyone who has read access to the folder. " +
        "Call this early in a session, after setContainer, to recover what past sessions learned " +
        "about this folder instead of rediscovering it. " +
        "Returns a JSON array of objects with `name` (the memory's slug), `description` (a one-line " +
        "summary), and `type`. Use getAssistantMemory to read the full body of any listed entry. " +
        "Returns an empty array if no memories have been saved here yet.")
    @RequiresPermission(ReadPermission.class)
    String listAssistantMemory(ToolContext toolContext)
    {
        ContainerUser cu = getContext(toolContext);
        WikiService svc = wikiService();

        JSONArray array = new JSONArray();
        for (String wikiName : svc.getNames(cu.getContainer()))
        {
            if (!wikiName.startsWith(PREFIX))
                continue;

            String slug = wikiName.substring(PREFIX.length());
            String content = svc.getContent(cu.getContainer(), wikiName);
            Map<String, String> front = parseFrontmatter(content);

            JSONObject obj = new JSONObject();
            obj.put("name", slug);
            obj.put("description", front.getOrDefault("description", ""));
            obj.put("type", front.getOrDefault("type", ""));
            array.put(obj);
        }

        return array.toString();
    }

    @Tool(description =
        "Read the full content of one assistant memory in the current container, by its slug. " +
        "Returns the memory as Markdown (frontmatter with name/type/description, followed by the body, " +
        "including any **Why:** / **How to apply:** notes). " +
        "Use listAssistantMemory first to discover available slugs. " +
        "Throws if no memory with that slug exists in this container.")
    @RequiresPermission(ReadPermission.class)
    String getAssistantMemory(
        ToolContext toolContext,
        @ToolParam(description =
            "The memory's slug, e.g. \"container-layout\" or \"sample-naming-convention\". " +
            "This is the bare name as returned by listAssistantMemory — do not add any prefix.")
        String name)
    {
        ContainerUser cu = getContext(toolContext);
        String content = wikiService().getContent(cu.getContainer(), PREFIX + name);
        if (null == content)
            throw new NotFoundException("No assistant memory named \"" + name + "\" exists in this container. Use listAssistantMemory to see the available slugs.");
        return content;
    }

    @Tool(description =
        "Create or update an assistant memory in the current container. If a memory with the given " +
        "slug already exists it is overwritten (a new wiki version is kept); otherwise a new one is " +
        "created. Use this to record durable, reusable facts about this folder that a future session " +
        "would benefit from — its container/folder layout, the meaning of tables or columns, project " +
        "goals and constraints, or how the user likes you to work. " +
        "IMPORTANT: these memories are visible to everyone with read access to this folder. Record " +
        "STRUCTURE and SEMANTICS (what a column means, where things live), NOT concrete data values — " +
        "do not copy PHI/PII or specific record contents into the body. " +
        "Keep one fact per memory. Reuse an existing slug (see listAssistantMemory) to revise rather " +
        "than duplicate.")
    @RequiresPermission(InsertPermission.class)
    String saveAssistantMemory(
        ToolContext toolContext,
        @ToolParam(description =
            "Short kebab-case slug identifying this memory, e.g. \"container-layout\". Reuse an " +
            "existing slug to update that memory. Do not add any prefix — the server manages storage " +
            "naming.")
        String name,
        @ToolParam(description =
            "One-line summary of the memory, shown in listAssistantMemory so a future session can " +
            "judge relevance without reading the full body. e.g. \"How study folders are nested under " +
            "the Registry project\".")
        String description,
        @ToolParam(description =
            "The kind of memory: one of `reference`, `project`, `feedback`, or `user` " +
            "(mirrors the local assistant-memory taxonomy). Free text; not enforced.")
        String type,
        @ToolParam(description =
            "The full memory content as Markdown. State the single fact plainly. For `feedback` and " +
            "`project` types, follow it with **Why:** and **How to apply:** lines. Describe structure " +
            "and semantics only — never paste real data values, PHI, or PII.")
        String body)
    {
        ContainerUser cu = getContext(toolContext);
        WikiService svc = wikiService();
        String wikiName = PREFIX + name;
        String content = composeContent(name, type, description, body);

        // Upsert: WikiService splits create vs. update, so route on existence.
        if (null != svc.getContent(cu.getContainer(), wikiName))
        {
            svc.updateContent(cu.getContainer(), cu.getUser(), wikiName, content, null);
            return "Updated assistant memory \"" + name + "\".";
        }
        else
        {
            svc.insertWiki(cu.getUser(), cu.getContainer(), wikiName, content, WikiRendererType.MARKDOWN, description);
            return "Created assistant memory \"" + name + "\".";
        }
    }

    @Tool(description =
        "Delete an assistant memory from the current container by its slug. Use when a memory is wrong " +
        "or obsolete. This removes the memory for everyone with access to the folder, so prefer " +
        "updating (saveAssistantMemory with the same slug) when the fact is merely stale rather than " +
        "wrong. Throws if no memory with that slug exists.")
    @RequiresPermission(DeletePermission.class)
    String deleteAssistantMemory(
        ToolContext toolContext,
        @ToolParam(description =
            "The slug of the memory to delete, as returned by listAssistantMemory. Do not add any prefix.")
        String name)
    {
        ContainerUser cu = getContext(toolContext);
        WikiService svc = wikiService();
        String wikiName = PREFIX + name;

        if (null == svc.getContent(cu.getContainer(), wikiName))
            throw new NotFoundException("No assistant memory named \"" + name + "\" exists in this container.");

        try
        {
            svc.deleteWiki(cu.getContainer(), cu.getUser(), wikiName, false);
        }
        catch (SQLException e)
        {
            throw new McpException("Failed to delete assistant memory \"" + name + "\": " + e.getMessage());
        }

        return "Deleted assistant memory \"" + name + "\".";
    }

    /** Assemble the stored wiki content: frontmatter (name/type/description) followed by the body. */
    private static String composeContent(String name, String type, String description, String body)
    {
        return "---\n" +
            "name: " + StringUtils.trimToEmpty(name) + "\n" +
            "type: " + StringUtils.trimToEmpty(type) + "\n" +
            "description: " + StringUtils.trimToEmpty(description) + "\n" +
            "---\n\n" +
            StringUtils.trimToEmpty(body) + "\n";
    }

    /** Parse the leading {@code ---}-delimited frontmatter block into a key/value map. Best effort. */
    private static Map<String, String> parseFrontmatter(String content)
    {
        Map<String, String> map = new HashMap<>();
        if (null == content)
            return map;

        List<String> lines = List.of(content.split("\n", -1));
        if (lines.isEmpty() || !lines.getFirst().strip().equals("---"))
            return map;

        for (int i = 1; i < lines.size(); i++)
        {
            String line = lines.get(i);
            if (line.strip().equals("---"))
                break;
            int colon = line.indexOf(':');
            if (colon > 0)
            {
                String key = line.substring(0, colon).strip();
                String value = line.substring(colon + 1).strip();
                if (isNotBlank(key))
                    map.put(key, value);
            }
        }

        return map;
    }
}