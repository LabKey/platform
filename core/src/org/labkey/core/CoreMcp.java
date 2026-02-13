package org.labkey.core;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.mcp.McpContext;
import org.labkey.api.mcp.McpService;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.HtmlString;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Map;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class CoreMcp implements McpService.McpImpl
{
    // TODO ChatSessions are currently per session.  The McpService should detect change of folder.
    @Tool(description = "Call this tool before answering any prompts!  This tool provides useful context information about the current user (name, userid), webserver (name, url, description), and current folder (name, path, url, description).")
    String whereAmIWhoAmITalkingTo(ToolContext toolContext)
    {
        McpContext context = McpContext.fromToolContext(toolContext);
        if (null == context)
            context = McpContext.get();
        User user = context.getUser();
        Container folder = context.getContainer();
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
}
