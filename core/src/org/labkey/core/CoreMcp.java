package org.labkey.core;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.mcp.McpService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.HtmlString;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class CoreMcp implements McpService.McpImpl
{
    @Tool(description = "This tool provides useful context information about the current user (name, userid), webserver " +
        "(name, url, description), and current container/folder (name, path, url, description) once the container is set via setContainer.")
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
    String listContainers(ToolContext toolContext)
    {
        return ContainerManager.getAllChildren(ContainerManager.getRoot(), getUser(toolContext), ReadPermission.class)
            .stream()
            .map(container -> StringUtils.stripStart(container.getPath(), "/")) // No leading slash since typing that brings up custom shortcuts
            .collect(LabKeyCollectors.toJSONArray())
            .toString();
    }

    @Tool(description = "Every tool in this MCP requires a container path, e.g. MyProject/MyFolder. A container is also called a folder or project. " +
        "Please prompt the user for a container path and use this tool to save the path for this session. Don't suggest a leading slash on the path " +
        "because typing a slash in some LLM clients triggers custom shortcuts.")
    String setContainer(ToolContext context, @ToolParam(description = "Container path, e.g. MyProject/MyFolder") String containerPath)
    {
        final String message;

        if (containerPath == null)
        {
            message = "Container path was null. Please enter a valid container path. Try using listContainers to see them.";
        }
        else
        {
            Container container = ContainerManager.getForPath(containerPath);

            if (container == null)
            {
                message = "That's not a valid container path. Try using listContainers to see them.";
            }
            else
            {
                McpService.get().saveSessionContainer(context, container);
                message = "Container has been set";
            }
        }

        return message;
    }
}
