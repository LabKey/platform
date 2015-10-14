/*
 * Copyright (c) 2014-2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.freezerpro.export;

import org.labkey.api.pipeline.PipelineJob;

/**
 * Created by klum on 10/28/2014.
 */
public class GetFreezersResponse extends FreezerProCommandResponse
{
    public static final String DATA_NODE_NAME = "Freezers";

    public GetFreezersResponse(FreezerProExport export, String text, int statusCode, PipelineJob job)
    {
        super(export, text, statusCode, DATA_NODE_NAME, job);
    }
}
