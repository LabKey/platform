/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.study.specimen.report;

import jxl.Workbook;
import jxl.WorkbookSettings;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.study.model.VisitImpl;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletOutputStream;

import jxl.write.*;
import jxl.write.Number;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
/*
 * User: brittp
 * Date: May 20, 2008
 * Time: 9:34:36 AM
 */

public class SpecimenReportExcelWriter
{
    private SpecimenVisitReportParameters _parameters;
    private WritableCellFormat _headerFormat;

    public SpecimenReportExcelWriter(SpecimenVisitReportParameters parameters)
    {
        _parameters = parameters;
    }

    public void write(HttpServletResponse response)
    {
        WritableWorkbook workbook = null;
        ServletOutputStream ostream = null;
        try
        {
            WritableFont boldFont = new WritableFont(WritableFont.ARIAL, 10, WritableFont.BOLD);
            _headerFormat = new WritableCellFormat(boldFont);
            _headerFormat.setWrap(true);
            _headerFormat.setVerticalAlignment(jxl.format.VerticalAlignment.TOP);
            ostream = ExcelWriter.getOutputStream(response, _parameters.getLabel(), ExcelWriter.ExcelDocumentType.xls);

            WorkbookSettings settings = new WorkbookSettings();
            settings.setArrayGrowSize(300000);
            workbook = Workbook.createWorkbook(ostream, settings);

            for (SpecimenVisitReport report : _parameters.getReports())
                writeReport(workbook, report);
        }
        catch (WriteException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            if (workbook != null)
            {
                try
                {
                    workbook.write();
                    workbook.close();

                    // Flush the outpustream
                    ostream.flush();
                    // Finally, close the outputstream
                    ostream.close();
                }
                catch (WriteException e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
                catch (IOException e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
            }
        }
    }

    private void writeReport(WritableWorkbook workbook, SpecimenVisitReport report) throws WriteException
    {
        String sheetName = ExcelWriter.cleanSheetName(report.getTitle());
        for (int i = 2; workbook.getSheet(sheetName) != null; i++)
            sheetName = sheetName.substring(0, sheetName.length() - 1) + i;

        WritableSheet sheet = workbook.createSheet(sheetName, workbook.getNumberOfSheets());

        List<VisitImpl> visits = report.getVisits();

        // Merge cells at top of sheet and write the headers
        // One or more embedded tabs split a header into equally spaced columns
        sheet.mergeCells(0, 0, 10, 0);
        WritableCell cell = new Label(0, 0, _parameters.getLabel() + ": " + report.getTitle());
        sheet.addCell(cell);

        for (int i = 0; i < visits.size(); i++)
            sheet.addCell(new Label(i + report.getLabelDepth(), 1, visits.get(i).getDisplayString(), _headerFormat));

        boolean numericData = report.isNumericData();
        Collection<SpecimenVisitReport.Row> rows = (Collection<SpecimenVisitReport.Row>) report.getRows();
        if (rows.isEmpty())
            sheet.addCell(new Label(report.getLabelDepth() + 1, 2, "No data to show"));
        else
        {
            int rowIndex = 2;
            for (SpecimenVisitReport.Row rowData : rows)
            {
                int maxRowHeight = rowData.getMaxExcelRowHeight(visits);
                int columnIndex = 0;

                for (SpecimenReportTitle titleElement : rowData.getTitleHierarchy())
                    sheet.addCell(new Label(columnIndex++, rowIndex, titleElement.getDisplayValue()));

                for (VisitImpl visit : visits)
                {
                    String[] valueSet = rowData.getCellExcelText(visit);
                    if (valueSet != null && valueSet.length > 0)
                    {
                        for (int i = 0; i < valueSet.length; i++)
                        {
                            String value = valueSet[i];
                            sheet.addCell(numericData ?
                                    new Number(columnIndex, rowIndex + i, Double.parseDouble(value)) :
                                    new Label(columnIndex, rowIndex + i, value));
                        }
                    }
                    columnIndex++;
                }
                rowIndex += maxRowHeight;
            }
        }
        sheet.getSettings().setVerticalFreeze(2);
        sheet.getSettings().setHorizontalFreeze(report.getLabelDepth());
    }
}