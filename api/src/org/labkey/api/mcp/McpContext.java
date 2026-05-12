package org.labkey.api.mcp;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.QuietCloser;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.writer.ContainerUser;
import org.springframework.ai.chat.model.ToolContext;

import java.util.HashMap;
import java.util.Map;

/**
 *  TODO MCP tool calling supports passing along a ToolContext. And most all
 *  interesting tools probably need a User and Container. This is not all hooked-up
 *  yet. This is an area for further investigation.
 */
public class McpContext implements ContainerUser
{
    final User user;
    final Container container;
    final Map<String, Object> attributes = new HashMap<>();

    public McpContext(ContainerUser ctx)
    {
        this(ctx.getContainer(), ctx.getUser());
    }

    public McpContext(Container container, User user)
    {
        if (!container.hasPermission(user, ReadPermission.class))
            throw new UnauthorizedException();
        this.container = container;
        this.user = user;
    }

    public ToolContext getToolContext()
    {
        Map<String, Object> map = new HashMap<>(attributes);
        map.put("container", getContainer());
        map.put("user", getUser());
        return new ToolContext(map);
    }

    public McpContext put(String key, Object value)
    {
        if ("container".equals(key) || "user".equals(key))
            throw new IllegalArgumentException("Reserved key: " + key);
        attributes.put(key, value);
        return this;
    }

    public Object get(String key)
    {
        return attributes.get(key);
    }

    @Override
    public Container getContainer()
    {
        return container;
    }

    @Override
    public User getUser()
    {
        return user;
    }

    //
    // I'd like to get away from using ThreadLocal, but I haven't
    // researched if there are other ways to pass context around to Tools registered by McpService
    //

    private static final ThreadLocal<McpContext> contexts = new ThreadLocal();

    public static @NotNull McpContext get()
    {
        var ret = contexts.get();
        if (null == ret)
            throw new IllegalStateException("McpContext is not set");
        return ret;
    }

    public static QuietCloser withContext(ContainerUser ctx)
    {
        return with(new McpContext(ctx));
    }

    public static QuietCloser withContext(Container container, User user)
    {
        return with(new McpContext(container, user));
    }

    private static QuietCloser with(McpContext ctx)
    {
        final McpContext prev = contexts.get();
        contexts.set(ctx);
        return () -> contexts.set(prev);
    }
}
