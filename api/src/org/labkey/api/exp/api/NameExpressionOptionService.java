package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

import javax.annotation.Nullable;

/**
 * Service which exists to provide mostly application specific functionality around how name expressions work in
 * data classes and sample types.
 */
public interface NameExpressionOptionService
{
    NameExpressionOptionService NO_OP_IMPL = new NoOpService();

    @NotNull
    static NameExpressionOptionService get()
    {
        NameExpressionOptionService impl = ServiceRegistry.get().getService(NameExpressionOptionService.class);
        return impl != null ? impl : NO_OP_IMPL;
    }

    static void setInstance(NameExpressionOptionService impl)
    {
        ServiceRegistry.get().registerService(NameExpressionOptionService.class, impl);
    }

    /**
     * Returns the optional name expression prefix configured for this container
     */
    @Nullable
    String getExpressionPrefix(Container c);

    /**
     * Set the name expression prefix for the specified container. Setting the prefix to null
     * will clear out an existing prefix.
     */
    void setExpressionPrefix(Container c, User user, @Nullable String prefix) throws Exception;

    /**
     * Returns whether user specified names for samples and dataclasses are permitted for this folder.
     */
    boolean allowUserSpecifiedNames(Container c);

    /**
     * Set whether user specified names for samples and dataclasses are allowed for this folder. If set to false
     * it is required that all samples and dataclasses in the folder have a configured name expression.
     */
    void setAllowUserSpecifiedNames(Container c, User user, boolean allowNames) throws Exception;

    class NoOpService implements NameExpressionOptionService
    {
        @Override
        public String getExpressionPrefix(Container c)
        {
            return null;
        }

        @Override
        public void setExpressionPrefix(Container c, User user, String prefix) throws Exception
        {
        }

        @Override
        public boolean allowUserSpecifiedNames(Container c)
        {
            return true;
        }

        @Override
        public void setAllowUserSpecifiedNames(Container c, User user, boolean allowNames) throws Exception
        {
        }
    }
}
