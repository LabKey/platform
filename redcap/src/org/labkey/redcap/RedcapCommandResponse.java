/*
 * Copyright (c) 2013-2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.redcap;

import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.TabLoader;

import java.io.IOException;

/**
 * User: klum
 * Date: 4/16/13
 */
public class RedcapCommandResponse
{
    String _text;
    int _statusCode;

    public RedcapCommandResponse(String text, int statusCode)
    {
        _text = text;
        _statusCode = statusCode;
    }

    public DataLoader getLoader()
    {
        try {

            TabLoader loader = new TabLoader(_text, true);
            loader.setInferTypes(false);
            loader.parseAsCSV();

            return loader;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public String getText()
    {
        return _text;
    }

    public int getStatusCode()
    {
        return _statusCode;
    }
}
