package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

import java.util.Collections;
import java.util.List;

/**
 * Service which exists to provide mostly application specific functionality around how name expressions work in
 * data classes and sample types.
 */
public interface NameExpressionOptionService
{
    String NAME_EXPRESSION_REQUIRED_MSG_PREFIX = "A Naming Pattern is required because manually specifying a name has been disabled ";
    String NAME_EXPRESSION_REQUIRED_MSG = NAME_EXPRESSION_REQUIRED_MSG_PREFIX + "for this folder.";
    String NAME_EXPRESSION_REQUIRED_MSG_WITH_SUBFOLDERS = NAME_EXPRESSION_REQUIRED_MSG_PREFIX + "for this folder or a subfolder.";

    // Must match PREFIX_SUBSTITUTION_EXPRESSION in labkey-ui-components/.../domainproperties/constants.ts
    String FOLDER_PREFIX_TOKEN = "folderPrefix";            // name expression substitution token for a configured prefix
    String FOLDER_PREFIX_EXPRESSION = "${" + FOLDER_PREFIX_TOKEN + "}";

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
    void setExpressionPrefix(Container c, User user, boolean skipPrefixIneligibleSampleTypes, @Nullable String prefix) throws Exception;

    /**
     * Returns the list of names of sample types where the ID column is not 'Name' and is thus
     * considered ineligible for setting the expression prefix. See Issue 48675.
     */
    @NotNull List<String> getPrefixIneligibleSampleTypeNames(Container c, User user);
    /**
     * Returns whether user-specified names for samples and dataclasses are permitted for this folder.
     */
    boolean allowUserSpecifiedNames(Container c);

    /**
     * Returns the persisted value of the setting "allowUserSpecificNames" for the provided container.
     * Only use this method if you're looking for the value for this container specifically, otherwise,
     * use {@link NameExpressionOptionService#allowUserSpecifiedNames(Container)} for using/operating against
     * the setting.
     */
    boolean getAllowUserSpecificNamesValue(Container c);

    /**
     * Set whether user-specified names for samples and dataclasses are allowed for this folder. If set to false
     * it is required that all samples and dataclasses in the folder have a configured name expression.
     */
    void setAllowUserSpecifiedNames(Container c, User user, boolean allowNames) throws Exception;

    /**
     * Creates a prefixed name expression for new data classes or sample types, if configured for the
     * container.
     */
    String createPrefixedExpression(Container c, String nameExpression, boolean isAliquotNameExpression);

    class NoOpService implements NameExpressionOptionService
    {
        @Override
        public String getExpressionPrefix(Container c)
        {
            return null;
        }

        @Override
        public void setExpressionPrefix(Container c, User user, boolean skipPrefixIneligibleSampleTypes, String prefix) throws Exception
        {
        }

        @Override
        public @NotNull List<String> getPrefixIneligibleSampleTypeNames(Container c, User user)
        {
            return Collections.emptyList();
        }

        @Override
        public boolean allowUserSpecifiedNames(Container c)
        {
            return true;
        }

        @Override
        public boolean getAllowUserSpecificNamesValue(Container c)
        {
            return true;
        }

        @Override
        public void setAllowUserSpecifiedNames(Container c, User user, boolean allowNames) throws Exception
        {
        }

        @Override
        public String createPrefixedExpression(Container c, String nameExpression, boolean isAliquotNameExpression)
        {
            return nameExpression;
        }
    }
}
