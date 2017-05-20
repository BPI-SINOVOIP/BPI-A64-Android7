/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.print.cts;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintAttributes.Margins;
import android.print.PrintAttributes.MediaSize;
import android.print.PrintAttributes.Resolution;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentAdapter.LayoutResultCallback;
import android.print.PrintDocumentAdapter.WriteResultCallback;
import android.print.PrintDocumentInfo;
import android.print.PrintJobInfo;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.print.cts.services.FirstPrintService;
import android.print.cts.services.PrintServiceCallbacks;
import android.print.cts.services.PrinterDiscoverySessionCallbacks;
import android.print.cts.services.SecondPrintService;
import android.print.cts.services.StubbablePrinterDiscoverySession;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;

import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

/**
 * This test verifies that the system correctly adjust the
 * page ranges to be printed depending whether the app gave
 * the requested pages, more pages, etc.
 */
public class PageRangeAdjustmentTest extends BasePrintTest {

    private static final int MAX_PREVIEW_PAGES_BATCH = 50;
    private static final int PAGE_COUNT = 60;
    private static final String FIRST_PRINTER = "First printer";

    public void testAllPagesWantedAndAllPagesWritten() throws Exception {
        if (!supportsPrinting()) {
            return;
        }

        // Create a callback for the target print service.
        PrintServiceCallbacks firstServiceCallbacks = createMockPrintServiceCallbacks(
            new Answer<PrinterDiscoverySessionCallbacks>() {
            @Override
            public PrinterDiscoverySessionCallbacks answer(InvocationOnMock invocation) {
                    return createMockFirstPrinterDiscoverySessionCallbacks();
                }
            },
            new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                PrintJob printJob = (PrintJob) invocation.getArguments()[0];
                PageRange[] pages = printJob.getInfo().getPages();
                assertTrue(pages.length == 1 && PageRange.ALL_PAGES.equals(pages[0]));
                printJob.complete();
                onPrintJobQueuedCalled();
                return null;
            }
        }, null);

        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Configure the print services.
        FirstPrintService.setCallbacks(firstServiceCallbacks);
        SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
            new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PAGE_COUNT)
                        .build();
                callback.onLayoutFinished(info, false);
                // Mark layout was called.
                onLayoutCalled();
                return null;
            }
        }, new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                PageRange[] pages = (PageRange[]) args[0];
                ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                WriteResultCallback callback = (WriteResultCallback) args[3];
                writeBlankPages(printAttributes[0], fd, 0, PAGE_COUNT - 1);
                fd.close();
                callback.onWriteFinished(pages);
                // Mark write was called.
                onWriteCalled();
                return null;
            }
        }, new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                // Mark finish was called.
                onFinishCalled();
                return null;
            }
        });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Select the first printer.
        selectPrinter(FIRST_PRINTER);

        // Wait for layout as the printer has different capabilities.
        waitForLayoutAdapterCallbackCount(2);

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the print job.
        waitForServiceOnPrintJobQueuedCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(firstServiceCallbacks);

        // We create a new session first.
        inOrder.verify(firstServiceCallbacks)
                .onCreatePrinterDiscoverySessionCallbacks();

        // Next we wait for a call with the print job.
        inOrder.verify(firstServiceCallbacks).onPrintJobQueued(
                any(PrintJob.class));
    }

    public void testSomePagesWantedAndAllPagesWritten() throws Exception {
        if (!supportsPrinting()) {
            return;
        }

        // Create a callback for the target print service.
        PrintServiceCallbacks firstServiceCallbacks = createMockPrintServiceCallbacks(
            new Answer<PrinterDiscoverySessionCallbacks>() {
            @Override
            public PrinterDiscoverySessionCallbacks answer(InvocationOnMock invocation) {
                    return createMockFirstPrinterDiscoverySessionCallbacks();
                }
            },
            new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                PrintJob printJob = (PrintJob) invocation.getArguments()[0];
                PageRange[] pages = printJob.getInfo().getPages();
                // We asked for some pages, the app wrote more, but the system
                // pruned extra pages, hence we expect to print all pages.
                assertTrue(pages.length == 1 && PageRange.ALL_PAGES.equals(pages[0]));
                printJob.complete();
                onPrintJobQueuedCalled();
                return null;
            }
        }, null);

        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Configure the print services.
        FirstPrintService.setCallbacks(firstServiceCallbacks);
        SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
            new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PAGE_COUNT)
                        .build();
                callback.onLayoutFinished(info, false);
                // Mark layout was called.
                onLayoutCalled();
                return null;
            }
        }, new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                PageRange[] pages = (PageRange[]) args[0];
                assertTrue(pages[pages.length - 1].getEnd() < PAGE_COUNT);
                ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                WriteResultCallback callback = (WriteResultCallback) args[3];
                writeBlankPages(printAttributes[0], fd, 0, PAGE_COUNT - 1);
                fd.close();
                callback.onWriteFinished(new PageRange[] {PageRange.ALL_PAGES});
                // Mark write was called.
                onWriteCalled();
                return null;
            }
        }, new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                // Mark finish was called.
                onFinishCalled();
                return null;
            }
        });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Open the print options.
        openPrintOptions();

        // Select the first printer.
        selectPrinter(FIRST_PRINTER);

        // Wait for layout as the printer has different capabilities.
        waitForLayoutAdapterCallbackCount(2);

        // Select only the second page.
        selectPages("2");

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the print job.
        waitForServiceOnPrintJobQueuedCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(firstServiceCallbacks);

        // We create a new session first.
        inOrder.verify(firstServiceCallbacks)
                .onCreatePrinterDiscoverySessionCallbacks();

        // Next we wait for a call with the print job.
        inOrder.verify(firstServiceCallbacks).onPrintJobQueued(
                any(PrintJob.class));
    }

    public void testSomePagesWantedAndSomeMorePagesWritten() throws Exception {
        final int REQUESTED_PAGE = 55;

        if (!supportsPrinting()) {
            return;
        }

        // Create a callback for the target print service.
        PrintServiceCallbacks firstServiceCallbacks = createMockPrintServiceCallbacks(
            new Answer<PrinterDiscoverySessionCallbacks>() {
            @Override
            public PrinterDiscoverySessionCallbacks answer(InvocationOnMock invocation) {
                    return createMockFirstPrinterDiscoverySessionCallbacks();
                }
            },
            new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                PrintJob printJob = (PrintJob) invocation.getArguments()[0];
                PrintJobInfo printJobInfo = printJob.getInfo();
                PageRange[] pages = printJobInfo.getPages();
                // We asked only for page 55 (index 54) but got 55 and 56 (indices
                // 54, 55), but the system pruned the extra page, hence we expect
                // to print all pages.
                assertTrue(pages.length == 1 && PageRange.ALL_PAGES.equals(pages[0]));
                assertSame(printJob.getDocument().getInfo().getPageCount(), 1);
                printJob.complete();
                onPrintJobQueuedCalled();
                return null;
            }
        }, null);

        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Configure the print services.
        FirstPrintService.setCallbacks(firstServiceCallbacks);
        SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
            new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PAGE_COUNT)
                        .build();
                callback.onLayoutFinished(info, false);
                // Mark layout was called.
                onLayoutCalled();
                return null;
            }
        }, new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                PageRange[] pages = (PageRange[]) args[0];
                ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                WriteResultCallback callback = (WriteResultCallback) args[3];
                // We expect a single range as it is either the pages for
                // preview or the page we selected in the UI.
                assertSame(pages.length, 1);

                // The first write request for some pages to preview.
                if (getWriteCallCount() == 0) {
                    // Write all requested pages.
                    writeBlankPages(printAttributes[0], fd, pages[0].getStart(), pages[0].getEnd());
                    callback.onWriteFinished(pages);
                } else {
                    // Otherwise write a page more that the one we selected.
                    writeBlankPages(printAttributes[0], fd, REQUESTED_PAGE - 1, REQUESTED_PAGE);
                    callback.onWriteFinished(new PageRange[] {new PageRange(REQUESTED_PAGE - 1,
                            REQUESTED_PAGE)});
                }

                fd.close();

                // Mark write was called.
                onWriteCalled();
                return null;
            }
        }, new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                // Mark finish was called.
                onFinishCalled();
                return null;
            }
        });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Open the print options.
        openPrintOptions();

        // Select the first printer.
        selectPrinter(FIRST_PRINTER);

        // Wait for layout as the printer has different capabilities.
        waitForLayoutAdapterCallbackCount(2);

        // Select a page not written for preview.
        selectPages(Integer.valueOf(REQUESTED_PAGE).toString());

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the print job.
        waitForServiceOnPrintJobQueuedCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(firstServiceCallbacks);

        // We create a new session first.
        inOrder.verify(firstServiceCallbacks)
                .onCreatePrinterDiscoverySessionCallbacks();

        // Next we wait for a call with the print job.
        inOrder.verify(firstServiceCallbacks).onPrintJobQueued(
                any(PrintJob.class));
    }

    public void testSomePagesWantedAndNotWritten() throws Exception {
        if (!supportsPrinting()) {
            return;
        }

        // Create a callback for the target print service.
        PrintServiceCallbacks firstServiceCallbacks = createMockPrintServiceCallbacks(
            new Answer<PrinterDiscoverySessionCallbacks>() {
            @Override
            public PrinterDiscoverySessionCallbacks answer(InvocationOnMock invocation) {
                    return createMockFirstPrinterDiscoverySessionCallbacks();
                }
            },
            null, null);

        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Configure the print services.
        FirstPrintService.setCallbacks(firstServiceCallbacks);
        SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
            new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PAGE_COUNT)
                        .build();
                callback.onLayoutFinished(info, false);
                // Mark layout was called.
                onLayoutCalled();
                return null;
            }
        }, new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                PageRange[] pages = (PageRange[]) args[0];
                ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                WriteResultCallback callback = (WriteResultCallback) args[3];
                assertSame(pages.length, 1);

                // We should be asked for some pages...
                assertSame(pages[0].getStart(), 0);
                assertTrue(pages[0].getEnd() == MAX_PREVIEW_PAGES_BATCH - 1);

                writeBlankPages(printAttributes[0], fd, pages[0].getStart(), pages[0].getEnd());
                fd.close();
                callback.onWriteFinished(new PageRange[]{new PageRange(1, 1)});

                // Mark write was called.
                onWriteCalled();
                return null;
            }
        }, new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                // Mark finish was called.
                onFinishCalled();
                return null;
            }
        });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Cancel printing.
        getUiDevice().pressBack(); // wakes up the device.
        getUiDevice().pressBack();

        // Wait for finish.
        waitForAdapterFinishCallbackCalled();

        // Verify the expected calls.
        InOrder inOrder = inOrder(firstServiceCallbacks);

        // We create a new session first.
        inOrder.verify(firstServiceCallbacks)
                .onCreatePrinterDiscoverySessionCallbacks();

        // We should not receive a print job callback.
        inOrder.verify(firstServiceCallbacks, never()).onPrintJobQueued(
                any(PrintJob.class));
    }

    public void testWantedPagesAlreadyWrittenForPreview() throws Exception {
        if (!supportsPrinting()) {
            return;
        }

        // Create a callback for the target print service.
        PrintServiceCallbacks firstServiceCallbacks = createMockPrintServiceCallbacks(
            new Answer<PrinterDiscoverySessionCallbacks>() {
            @Override
            public PrinterDiscoverySessionCallbacks answer(InvocationOnMock invocation) {
                return createMockFirstPrinterDiscoverySessionCallbacks();
                    }
            }, new Answer<Void>() {
            @Override
                public Void answer(InvocationOnMock invocation) {
                    PrintJob printJob = (PrintJob) invocation.getArguments()[0];
                    PrintJobInfo printJobInfo = printJob.getInfo();
                    PageRange[] pages = printJobInfo.getPages();
                    // We asked only for page 3 (index 2) but got this page when
                    // we were getting the pages for preview (indices 0 - 49),
                    // but the framework pruned extra pages, hence we should be asked
                    // to print all pages from a single page document.
                    assertTrue(pages.length == 1 && PageRange.ALL_PAGES.equals(pages[0]));
                    assertSame(printJob.getDocument().getInfo().getPageCount(), 1);
                    printJob.complete();
                    onPrintJobQueuedCalled();
                    return null;
                }
            }, null);

        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Configure the print services.
        FirstPrintService.setCallbacks(firstServiceCallbacks);
        SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
            new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PAGE_COUNT)
                        .build();
                callback.onLayoutFinished(info, false);
                // Mark layout was called.
                onLayoutCalled();
                return null;
            }
        }, new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                PageRange[] pages = (PageRange[]) args[0];
                ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                WriteResultCallback callback = (WriteResultCallback) args[3];
                // We expect a single range as it is either the pages for
                // preview or the page we selected in the UI.
                assertSame(pages.length, 1);

                // Write all requested pages.
                writeBlankPages(printAttributes[0], fd, pages[0].getStart(), pages[0].getEnd());
                callback.onWriteFinished(pages);
                fd.close();

                // Mark write was called.
                onWriteCalled();
                return null;
            }
        }, new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                // Mark finish was called.
                onFinishCalled();
                return null;
            }
        });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Open the print options.
        openPrintOptions();

        // Select the first printer.
        selectPrinter(FIRST_PRINTER);

        // Wait for layout as the printer has different capabilities.
        waitForLayoutAdapterCallbackCount(2);

        // Select a page not written for preview.
        selectPages("3");

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the print job.
        waitForServiceOnPrintJobQueuedCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(firstServiceCallbacks);

        // We create a new session first.
        inOrder.verify(firstServiceCallbacks)
                .onCreatePrinterDiscoverySessionCallbacks();

        // Next we wait for a call with the print job.
        inOrder.verify(firstServiceCallbacks).onPrintJobQueued(
                any(PrintJob.class));
    }

    private void selectPages(String pages) throws UiObjectNotFoundException {
        UiObject pagesSpinner = getUiDevice().findObject(new UiSelector().resourceId(
                "com.android.printspooler:id/range_options_spinner"));
        pagesSpinner.click();

        UiObject rangeOption = getUiDevice().findObject(new UiSelector().textContains("Range"));
        rangeOption.click();

        UiObject pagesEditText = getUiDevice().findObject(new UiSelector().resourceId(
                "com.android.printspooler:id/page_range_edittext"));
        pagesEditText.setText(pages);

        // Hide the keyboard.
        getUiDevice().pressBack();
    }

    private PrinterDiscoverySessionCallbacks createMockFirstPrinterDiscoverySessionCallbacks() {
        return createMockPrinterDiscoverySessionCallbacks(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                PrinterDiscoverySessionCallbacks mock = (PrinterDiscoverySessionCallbacks)
                        invocation.getMock();

                StubbablePrinterDiscoverySession session = mock.getSession();
                PrintService service = session.getService();

                if (session.getPrinters().isEmpty()) {
                          List<PrinterInfo> printers = new ArrayList<>();

                    // Add one printer.
                    PrinterId firstPrinterId = service.generatePrinterId("first_printer");
                    PrinterCapabilitiesInfo firstCapabilities =
                            new PrinterCapabilitiesInfo.Builder(firstPrinterId)
                        .setMinMargins(new Margins(200, 200, 200, 200))
                        .addMediaSize(MediaSize.ISO_A4, true)
                        .addMediaSize(MediaSize.ISO_A5, false)
                        .addResolution(new Resolution("300x300", "300x300", 300, 300), true)
                        .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                PrintAttributes.COLOR_MODE_COLOR)
                        .build();
                    PrinterInfo firstPrinter = new PrinterInfo.Builder(firstPrinterId,
                            FIRST_PRINTER, PrinterInfo.STATUS_IDLE)
                        .setCapabilities(firstCapabilities)
                        .build();
                    printers.add(firstPrinter);

                    session.addPrinters(printers);
                }

                return null;
            }
        }, null, null, null, null, null, null);
    }

    private PrintServiceCallbacks createSecondMockPrintServiceCallbacks() {
        return createMockPrintServiceCallbacks(null, null, null);
    }
}
