package org.labkey.test.tests.devtools;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.GetCommand;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.Base;
import org.labkey.test.categories.DRT;
import org.labkey.test.categories.Daily;
import org.labkey.test.categories.Git;
import org.labkey.test.categories.Hosting;
import org.labkey.test.categories.Smoke;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.fail;

/**
 * We expect {@code response.sendError(statusCode, message)} to send the provided message in the response, but this
 * wasn't happening by default on embedded Tomcat distributions. This simple test verifies the fix. See Issue 51110.
 */
@Category({Base.class, DRT.class, Daily.class, Git.class, Hosting.class, Smoke.class})
public class SendErrorTest extends BaseWebDriverTest
{
    @Override
    protected String getProjectName()
    {
        return null;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return List.of("DeveloperTools");
    }

    @Test
    public void testSendError() throws IOException
    {
        SendErrorCommand sendErrorCommand = new SendErrorCommand();
        final CommandResponse response;
        try
        {
            response = sendErrorCommand.execute(WebTestHelper.getRemoteApiConnection(), "/");
            fail("Expected execute() to throw a CommandException but succeeded with: " + response.getText());
        }
        catch (CommandException e)
        {
            checker().verifyThat("Response status code", e.getStatusCode(), equalTo(400));
            checker().verifyThat("Response text", e.getResponseText(), containsString("This is a very bad request!"));
        }
    }

    private static class SendErrorCommand extends GetCommand<CommandResponse>
    {
        protected SendErrorCommand()
        {
            super("test", "sendError");
        }
    }
}
