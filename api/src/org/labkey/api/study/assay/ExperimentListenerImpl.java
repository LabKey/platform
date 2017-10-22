package org.labkey.api.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.List;

public class ExperimentListenerImpl implements ExperimentListener
{
    @Override
    public List<ValidationException> afterResultDataCreated(Container container, User user, ExpRun run, ExpProtocol protocol)
    {
        List<ValidationException> errors = new ArrayList<>();

        // copy results data to the target study if the protocol is configured to auto copy
        for (String error : AssayPublishService.get().autoCopyResults(protocol, run, user, container))
        {
            errors.add(new ValidationException(error));
        }
        return errors;
    }
}
