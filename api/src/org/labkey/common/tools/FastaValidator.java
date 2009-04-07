/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.common.tools;

import org.labkey.api.util.CaseInsensitiveHashSet;

import java.io.File;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.text.Format;
import java.text.DecimalFormat;

/**
 * User: adam
 * Date: Jan 12, 2008
 * Time: 7:43:36 PM
 */
public class FastaValidator
{
    private File _fastaFile;


    public FastaValidator(File fastaFile)
    {
        _fastaFile = fastaFile;
    }


    public List<String> validate()
    {
        Set<String> proteinNames = new CaseInsensitiveHashSet(1000);
        List<String> errors = new ArrayList<String>();
        Format lineFormat = DecimalFormat.getIntegerInstance();
        FastaLoader curLoader = new FastaLoader(_fastaFile);

        for (FastaLoader.ProteinIterator proteinIterator = curLoader.iterator(); proteinIterator.hasNext();)
        {
            Protein protein = proteinIterator.next();

            // Creating a new string here is critical for handling large FASTA files -- see issue #6441 
            //noinspection RedundantStringConstructorCall
            String lookup = new String(protein.getLookup());

            if (proteinNames.contains(lookup))
            {
                errors.add("Line " + lineFormat.format(proteinIterator.getLastHeaderLine()) + ": " + lookup + " is a duplicate protein name");

                if (errors.size() > 999)
                {
                    errors.add("Stopped validating after 1,000 errors");
                    break;
                }
            }
            else
            {
                proteinNames.add(lookup);
            }
        }

        return errors;
    }
}
