package org.labkey.study.dataset;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.DataStateManager;
import org.labkey.api.security.permissions.AbstractContainerScopingTest;
import org.labkey.api.view.ActionURL;
import org.labkey.study.controllers.StudyController;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

public class DataStatesTest extends AbstractContainerScopingTest
{
    @Test
    public void testManageDataStates() throws Exception
    {
        // Note: This test will log OptimisticConflictException warnings
        // We're exercising DataStateManger via study ManageQCStatesAction, but we don't need to create actual studies
        Container folderA = createContainer("Folder A");
        Container folderB = createContainer("Folder B");

        // Create a basic data state in Folder A
        DataState state1 = new DataState();
        state1.setContainer(folderA);
        state1.setLabel("State 1");
        state1.setPublicData(true);
        state1 = DataStateManager.getInstance().insertState(getAdmin(), state1);
        List<DataState> statesInA = DataStateManager.getInstance().getStates(folderA);
        assertEquals(1, statesInA.size());
        assertTrue(statesInA.contains(state1));

        // Attempt to update that data state from the wrong folder, Folder B. Admin should *not* be able to update it.
        ActionURL url = new ActionURL(StudyController.ManageQCStatesAction.class, folderB)
            .addParameter("ids", state1.getRowId())
            .addParameter("labels", "Here's my new label");
        HttpServletResponse response = post(url, getAdmin());
        List<DataState> statesInB = DataStateManager.getInstance().getStates(folderB);
        assertTrue(statesInB.isEmpty());
        statesInA = DataStateManager.getInstance().getStates(folderA);
        assertEquals(1, statesInA.size());
        assertTrue(statesInA.contains(state1));
        assertStatus(MockHttpServletResponse.SC_INTERNAL_SERVER_ERROR, response); // Error response

        // Admin should be able to update the data state in Folder A
        url.setContainer(folderA);
        post(url, getAdmin());
        statesInA = DataStateManager.getInstance().getStates(folderA);
        assertEquals(1, statesInA.size());
        DataState updatedState = statesInA.get(0);
        assertEquals("Here's my new label", updatedState.getLabel());
        assertFalse(updatedState.isPublicData());
    }
}
