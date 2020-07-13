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

package com.android.hierarchyviewerlib.ui;

import com.android.annotations.NonNull;
import com.android.hierarchyviewerlib.models.ThemeModel;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import java.util.List;


public class DumpThemeDisplay {
    private static final int DEFAULT_HEIGHT = 600; // px
    private static final int NUM_COLUMNS = 2;

    private static Shell sShell;
    private static ThemeModel sModel;
    private static Text sSearchText;
    private static Table sTable;

    public static void show(Shell parentShell, ThemeModel model) {
        if (sShell == null) {
            buildContents();
        } else {
            sSearchText.setText("");
            sTable.removeAll();
        }

        sModel = model;
        addTableItems("", sModel.getData());

        // configure size and placement
        sShell.setLocation(parentShell.getBounds().x, parentShell.getBounds().y);
        for (int i = 0; i < NUM_COLUMNS; ++i) {
            sTable.getColumn(i).pack();
        }
        sTable.setLayoutData(GridDataFactory.swtDefaults().hint(
                sTable.computeSize(SWT.DEFAULT, SWT.DEFAULT).x, DEFAULT_HEIGHT).create());
        sShell.pack();
        sShell.open();
    }

    private static void addTableItem(String name, String value) {
        TableItem row = new TableItem(sTable, SWT.NONE);
        row.setText(0, name);
        row.setText(1, value);
    }

    private static String sanitize(@NonNull String text) {
        return text.toLowerCase().trim();
    }

    private static void addTableItems(String searchText,
            List<ThemeModel.ThemeModelData> list) {
        for (ThemeModel.ThemeModelData data : list) {
            searchText = sanitize(searchText);

            if ("".equals(searchText)) {
                addTableItem(data.getName(), data.getValue());
            } else {
                if (sanitize(data.getName()).contains(searchText)
                        || sanitize(data.getValue()).contains(searchText)) {
                    addTableItem(data.getName(), data.getValue());
                }
            }
        }
    }

    private static void buildContents() {
        sShell = new Shell(Display.getDefault(), SWT.CLOSE | SWT.TITLE);
        sShell.setText("Dump Theme");
        sShell.addShellListener(sShellListener);
        sShell.setLayout(new GridLayout());

        sSearchText = new Text(sShell, SWT.SINGLE | SWT.BORDER);
        sSearchText.setMessage("Enter text to search list");
        sSearchText.addModifyListener(sModifyListener);

        sTable = new Table(sShell, SWT.BORDER | SWT.FULL_SELECTION);
        sTable.setHeaderVisible(true);
        sTable.setLinesVisible(true);

        String[] headers = { "Resource Name", "Resource Value" };
        for (int i = 0; i < headers.length; ++i) {
            TableColumn column = new TableColumn(sTable, SWT.NONE);
            column.setText(headers[i]);
        }
    }

    private static ModifyListener sModifyListener = new ModifyListener() {
        @Override
        public void modifyText(ModifyEvent modifyEvent) {
            String searchText = sanitize(sSearchText.getText());
            sTable.removeAll();
            addTableItems(searchText, sModel.getData());
        }
    };

    private static ShellAdapter sShellListener = new ShellAdapter() {
        @Override
        public void shellClosed(ShellEvent e) {
            e.doit = false;
            sShell.setVisible(false);
        }
    };
}
