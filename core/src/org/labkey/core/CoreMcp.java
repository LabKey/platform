package org.labkey.core;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.mcp.McpService;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.ActionURL;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class CoreMcp implements McpService.McpImpl
{
    @Tool(description = "This tool provides useful context information about the current user (name, userid), webserver " +
        "(name, url, description), and current container/folder (name, path, url, description) once the container is set via setContainer.")
    @RequiresPermission(ReadPermission.class)
    String whereAmIWhoAmITalkingTo(ToolContext context)
    {
        var cu = getContext(context);
        User user = cu.getUser();
        Container folder = cu.getContainer();
        AppProps appProps = AppProps.getInstance();
        Study study = null != StudyService.get() ? Objects.requireNonNull(StudyService.get()).getStudy(folder) : null;
        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(folder);

        JSONObject userObj = new JSONObject();
        userObj.put("userId", user.getUserId());
        userObj.put("displayName", user.getDisplayName(user));
        if (isNotBlank(user.getFirstName()))
            userObj.put("firstName", user.getFirstName());

        JSONObject folderObj = new JSONObject();
        folderObj.put("name", folder.getName());
        folderObj.put("path", folder.getPath());
        folderObj.put("startUrl", folder.getStartURL(user).getURIString());
        if (isNotBlank(folder.getDescription()))
            folderObj.put("description", folder.getDescription());
        if (null != study)
        {
            var studyDescription = study.getDescriptionHtml();
            if (!HtmlString.isBlank(studyDescription))
            {
                folderObj.put("studyDescription", new JSONObject(Map.of("contentType", "text/html", "content", studyDescription.toString())));
            }
        }

        JSONObject siteObj = new JSONObject();
        siteObj.put("name", appProps.getServerName());
        siteObj.put("baseServerUrl", appProps.getBaseServerUrl());
        siteObj.put("description", laf.getDescription());
        siteObj.put("homePageUrl", appProps.getHomePageUrl());

        return new JSONObject(Map.of(
            "user", userObj,
            "currentFolder", folderObj,
            "site", siteObj
        )).toString();
    }

    @Tool(description = "List the hierarchical path for every container in the server where the user has read permissions.")
    @RequiresNoPermission
    String listContainers(ToolContext toolContext)
    {
        return ContainerManager.getAllChildren(ContainerManager.getRoot(), getUser(toolContext), ReadPermission.class)
            .stream()
            .map(container -> StringUtils.stripStart(container.getPath(), "/")) // No leading slash since typing that brings up custom shortcuts
            .collect(LabKeyCollectors.toJSONArray())
            .toString();
    }

    @Tool(description = "Every tool in this MCP requires a container path, e.g. MyProject/MyFolder. A container is also called a folder or project. " +
        "Please prompt the user for a container path and use this tool to save the path for this MCP session. The user can also change the container " +
        "during the session using this tool. The user must have read permissions in the container, in other words, the path must be on the list that " +
        "the listContainers tool returns. Don't suggest a leading slash on the path because typing a slash in some LLM clients triggers custom shortcuts. " +
        "Alternately, the user may provide a LabKey server URL like http://localhost:8080/StudyVerifyProject/My%20Study/project-begin.view. " +
        "The container path is encoded in the URL and can be accepted as a valid parameter.")
    @RequiresNoPermission // Because we don't have a container yet, but the tool will verify read permission before setting the container
    String setContainer(ToolContext context, @ToolParam(description = "Container path, e.g. MyProject/MyFolder") String containerPath)
    {
        final String message;

        Container container = ContainerManager.getForPath(containerPath);

        if (null == container)
        {
            try
            {
                var url = new ActionURL(containerPath);
                container = ContainerManager.getForURL(url);
            }
            catch (IllegalArgumentException x)
            {
            }
        }

        // Must exist and user must have read permission to set a container. Note: Send the same message in either
        // case to prevent information exposure.
        if (container == null || !container.hasPermission(getUser(context), ReadPermission.class))
        {
            message = "That's not a valid container path. Try using listContainers to see the valid options.";
        }
        else
        {
            McpService.get().saveSessionContainer(context, container);
            message = "Container has been set to " + container.getPath();
        }

        return message;
    }

    @McpResource(
        uri = "resource://org/labkey/core/FileBasedModules.md",
        mimeType = "application/markdown",
        name = "File-Based Module Development Guide",
        description = "Provide documentation for developing LabKey file-based modules")
    public ReadResourceResult getFileBasedModuleDevelopmentGuide() throws IOException
    {
        incrementResourceRequestCount("File-Based Modules");
        String markdown = IOUtils.resourceToString("org/labkey/core/FileBasedModules.md", null, CoreModule.class.getClassLoader());
        return new ReadResourceResult(List.of(
            new McpSchema.TextResourceContents(
                "resource://org/labkey/core/FileBasedModules.md",
                "application/markdown",
                markdown
            )
        ));
    }

    @McpResource(
            uri = "resource://org/labkey/core/DataAnalysis_Python.md",
            mimeType = "application/markdown",
            name = "Python Data Analysis Development Guide",
            description = "Provide documentation for developers using Python to analyze LabKey data")
    public ReadResourceResult getPythonDataAnalysisGuide() throws IOException
    {
        incrementResourceRequestCount("Python Data Analysis");
        String markdown = IOUtils.resourceToString("org/labkey/core/DataAnalysis_Python.md", null, CoreModule.class.getClassLoader());
        return new ReadResourceResult(List.of(
                new McpSchema.TextResourceContents(
                        "resource://org/labkey/core/DataAnalysis_Python.md",
                        "application/markdown",
                        markdown
                )
        ));
    }

    @McpResource(
            uri = "resource://org/labkey/core/DataAnalysis_R.md",
            mimeType = "application/markdown",
            name = "R Data Analysis Development Guide",
            description = "Provide documentation for developers using R to analyze LabKey data")
    public ReadResourceResult getRDataAnalysisGuide() throws IOException
    {
        incrementResourceRequestCount("R Data Analysis");
        String markdown = IOUtils.resourceToString("org/labkey/core/DataAnalysis_R.md", null, CoreModule.class.getClassLoader());
        return new ReadResourceResult(List.of(
                new McpSchema.TextResourceContents(
                        "resource://org/labkey/core/DataAnalysis_R.md",
                        "application/markdown",
                        markdown
                )
        ));
    }

}
