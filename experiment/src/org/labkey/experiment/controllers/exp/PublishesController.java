package org.labkey.experiment.controllers.exp;

import org.labkey.api.action.SpringActionController;
import org.labkey.experiment.publish.SampleTypePublishConfirmAction;
import org.labkey.experiment.publish.SampleTypePublishStartAction;

public class PublishesController extends SpringActionController //TODO Rosaline: Ideally we change this name?
{
    private static final ActionResolver _resolver = new DefaultActionResolver(PublishesController.class,
            SampleTypePublishStartAction.class,
            SampleTypePublishConfirmAction.class
    );

    public PublishesController()
    {
        setActionResolver(_resolver);
    }
}
