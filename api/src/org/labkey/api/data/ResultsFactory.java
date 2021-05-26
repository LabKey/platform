package org.labkey.api.data;

import java.io.IOException;
import java.sql.SQLException;

public interface ResultsFactory
{
    Results get() throws IOException, SQLException;
}
