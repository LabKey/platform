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
package org.labkey.study.controllers.publish;

import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractContainerScopingTest;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

import java.util.List;
import java.util.Objects;

public class PublishConfirmContainerScopingTest extends AbstractContainerScopingTest
{
    private static final String PERMISSION_ERROR = "You do not have permission to link data to the study";

    private Container _source;
    private Container _target;
    private ExpSampleType _sampleType;

    @Before
    public void setup() throws Exception
    {
        _source = createContainer("Source");
        _target = createContainer("Target");

        StudyService.get().createStudy(_target, getAdmin(), "STUDY-4 scoping target", TimepointType.DATE, false);
        _sampleType = createSampleType(_source);

    }

    @Test
    public void testConfirmRejectsTargetStudyWithoutInsertPermission() throws Exception
    {
        // Editor in the source folder only: holds InsertPermission where the action runs, but none in the target study
        User editor = createUserInRole(_source, EditorRole.class);

        assertTrue("Linking into a target study the caller cannot insert into must be rejected",
                rejectedForPermission(validate(_source, _sampleType, _target, editor)));
    }

    @Test
    public void testConfirmAllowsTargetStudyWithInsertPermission() throws Exception
    {
        // Positive control: the same caller is also granted insert (Editor) in the target study, so the guard must not
        // fire — proving it rejects only the cross-container case rather than every link
        User editor = createUserInRole(_source, EditorRole.class);
        grantRole(editor, _target, EditorRole.class);

        assertFalse("Linking into a target study the caller can insert into must be allowed",
                rejectedForPermission(validate(_source, _sampleType, _target, editor)));
    }

    /**
     * Run the publish-confirm form's validation as {@code user}, linking {@code sampleType} (in {@code source}) to the
     * study in {@code target}, and return the resulting errors. The STUDY-4 guard lives in the shared
     * {@code AbstractPublishConfirmAction.validateCommand()}, reached here through the production
     * {@link SampleTypePublishConfirmAction} so its {@code @RequiresPermission} wiring is exercised too.
     */
    private BindException validate(Container source, ExpSampleType sampleType, Container target, User user)
    {
        ActionURL url = new ActionURL("study", "sampleTypePublishConfirm", source);
        ViewContext context = ViewContext.getMockViewContext(user, source, url, false);

        SampleTypePublishConfirmAction action = new SampleTypePublishConfirmAction();
        action.setViewContext(context);

        SampleTypePublishConfirmAction.SampleTypePublishConfirmForm form = new SampleTypePublishConfirmAction.SampleTypePublishConfirmForm();
        form.setViewContext(context);
        form.setRowId(sampleType.getRowId());
        form.setTargetStudy(new String[]{ target.getId() });
        form.setReturnUrl(url.toString());

        BindException errors = new BindException(form, "form");
        action.validateCommand(form, errors);
        return errors;
    }

    private boolean rejectedForPermission(BindException errors)
    {
        return errors.getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .filter(Objects::nonNull)
                .anyMatch(message -> message.contains(PERMISSION_ERROR));
    }

    private ExpSampleType createSampleType(Container c) throws Exception
    {
        List<GWTPropertyDescriptor> props = List.of(
                new GWTPropertyDescriptor("Name", "string"),
                new GWTPropertyDescriptor("Date", PropertyType.DATE.getTypeUri()),
                new GWTPropertyDescriptor("PTID", "string")
        );

        return SampleTypeService.get().createSampleType(c, getAdmin(), "STUDY4ScopingSamples", null,
                props, List.of(), -1,-1,-1,-1,null);
    }
}
