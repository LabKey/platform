package org.labkey.api.mcp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.writer.ContainerUser;
import org.springframework.ai.chat.model.ToolContext;
import java.util.Map;

public class McpContext implements ContainerUser
{
    final User user;
    final Container container;

    public McpContext(ContainerUser ctx)
    {
        this.container = ctx.getContainer();
        this.user = ctx.getUser();
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
        return new ToolContext(Map.of("container", getContainer(), "user", getUser()));
    }

    /**
     * Reconstruct an McpContext from a Spring AI ToolContext.
     * Returns null if toolContext is null or missing required user/container entries.
     */
    public static @Nullable McpContext fromToolContext(@Nullable ToolContext toolContext)
    {
        if (null == toolContext)
            return null;
        Object containerObj = toolContext.getContext().get("container");
        Object userObj = toolContext.getContext().get("user");
        if (!(containerObj instanceof Container container) || !(userObj instanceof User user))
            return null;
        return new McpContext(container, user);
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
    // researched if there are other ways to pass context around to Tools registerd by McpService
    //

    private static final ThreadLocal<McpContext> contexts = new ThreadLocal();

    public static @NotNull McpContext get()
    {
        var ret = contexts.get();
        if (null == ret)
            throw new IllegalStateException("McpContext is not set");
        return ret;
    }

        public static AutoCloseable withContext(ContainerUser ctx)
    {
        return with(new McpContext(ctx));
    }

    public static AutoCloseable withContext(Container container, User user)
    {
        return with(new McpContext(container, user));
    }

    private static AutoCloseable with(McpContext ctx)
    {
        final McpContext prev = contexts.get();
        contexts.set(ctx);
        return () -> contexts.set(prev);
    }
}
