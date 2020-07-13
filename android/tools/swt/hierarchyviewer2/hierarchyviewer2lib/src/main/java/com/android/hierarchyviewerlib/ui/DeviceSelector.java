/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.device.IHvDevice;
import com.android.hierarchyviewerlib.models.DeviceSelectionModel;
import com.android.hierarchyviewerlib.models.DeviceSelectionModel.IWindowChangeListener;
import com.android.hierarchyviewerlib.models.Window;

import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

public class DeviceSelector extends Composite implements IWindowChangeListener, SelectionListener {
    private TreeViewer mTreeViewer;

    private Tree mTree;

    private DeviceSelectionModel mModel;

    private Font mBoldFont;

    private Image mDeviceImage;

    private Image mEmulatorImage;

    private final static int ICON_WIDTH = 16;

    private boolean mDoTreeViewStuff;

    private boolean mDoPixelPerfectStuff;

    private class ContentProvider implements ITreeContentProvider, ILabelProvider, IFontProvider {
        @Override
        public Object[] getChildren(Object parentElement) {
            if (parentElement instanceof IHvDevice && mDoTreeViewStuff) {
                Window[] list = mModel.getWindows((IHvDevice) parentElement);
                if (list != null) {
                    return list;
                }
            }
            return new Object[0];
        }

        @Override
        public Object getParent(Object element) {
            if (element instanceof Window) {
                return ((Window) element).getDevice();
            }
            return null;
        }

        @Override
        public boolean hasChildren(Object element) {
            if (element instanceof IHvDevice && mDoTreeViewStuff) {
                Window[] list = mModel.getWindows((IHvDevice) element);
                if (list != null) {
                    return list.length != 0;
                }
            }
            return false;
        }

        @Override
        public Object[] getElements(Object inputElement) {
            if (inputElement instanceof DeviceSelectionModel) {
                return mModel.getDevices();
            }
            return new Object[0];
        }

        @Override
        public void dispose() {
            // pass
        }

        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            // pass
        }

        @Override
        public Image getImage(Object element) {
            if (element instanceof IHvDevice) {
                if (((IHvDevice) element).getDevice().isEmulator()) {
                    return mEmulatorImage;
                }
                return mDeviceImage;
            }
            return null;
        }

        @Override
        public String getText(Object element) {
            if (element instanceof IHvDevice) {
                return ((IHvDevice) element).getDevice().getName();
            } else if (element instanceof Window) {
                return ((Window) element).getTitle();
            }
            return null;
        }

        @Override
        public Font getFont(Object element) {
            if (element instanceof Window) {
                int focusedWindow = mModel.getFocusedWindow(((Window) element).getHvDevice());
                if (focusedWindow == ((Window) element).getHashCode()) {
                    return mBoldFont;
                }
            }
            return null;
        }

        @Override
        public void addListener(ILabelProviderListener listener) {
            // pass
        }

        @Override
        public boolean isLabelProperty(Object element, String property) {
            // pass
            return false;
        }

        @Override
        public void removeListener(ILabelProviderListener listener) {
            // pass
        }
    }

    public DeviceSelector(Composite parent, boolean doTreeViewStuff, boolean doPixelPerfectStuff) {
        super(parent, SWT.NONE);
        this.mDoTreeViewStuff = doTreeViewStuff;
        this.mDoPixelPerfectStuff = doPixelPerfectStuff;
        setLayout(new FillLayout());
        mTreeViewer = new TreeViewer(this, SWT.SINGLE);
        mTreeViewer.setAutoExpandLevel(TreeViewer.ALL_LEVELS);

        mTree = mTreeViewer.getTree();
        mTree.setLinesVisible(true);
        mTree.addSelectionListener(this);

        addDisposeListener(mDisposeListener);

        loadResources();

        mModel = DeviceSelectionModel.getModel();
        ContentProvider contentProvider = new ContentProvider();
        mTreeViewer.setContentProvider(contentProvider);
        mTreeViewer.setLabelProvider(contentProvider);
        mModel.addWindowChangeListener(this);
        mTreeViewer.setInput(mModel);

        addControlListener(mControlListener);
    }

    public void loadResources() {
        Display display = Display.getDefault();
        Font systemFont = display.getSystemFont();
        FontData[] fontData = systemFont.getFontData();
        FontData[] newFontData = new FontData[fontData.length];
        for (int i = 0; i < fontData.length; i++) {
            newFontData[i] =
                    new FontData(fontData[i].getName(), fontData[i].getHeight(), fontData[i]
                            .getStyle()
                            | SWT.BOLD);
        }
        mBoldFont = new Font(Display.getDefault(), newFontData);

        ImageLoader loader = ImageLoader.getDdmUiLibLoader();
        mDeviceImage =
                loader.loadImage(display, "device.png", ICON_WIDTH, ICON_WIDTH, display //$NON-NLS-1$
                        .getSystemColor(SWT.COLOR_RED));

        mEmulatorImage =
                loader.loadImage(display, "emulator.png", ICON_WIDTH, ICON_WIDTH, display //$NON-NLS-1$
                        .getSystemColor(SWT.COLOR_BLUE));
    }

    private DisposeListener mDisposeListener = new DisposeListener() {
        @Override
        public void widgetDisposed(DisposeEvent e) {
            mModel.removeWindowChangeListener(DeviceSelector.this);
            mBoldFont.dispose();
        }
    };

    // If the window gets too small, hide the data, otherwise SWT throws an
    // ERROR.

    private ControlListener mControlListener = new ControlAdapter() {
        private boolean noInput = false;

        @Override
        public void controlResized(ControlEvent e) {
            if (getBounds().height <= 38) {
                mTreeViewer.setInput(null);
                noInput = true;
            } else if (noInput) {
                mTreeViewer.setInput(mModel);
                noInput = false;
            }
        }
    };

    @Override
    public boolean setFocus() {
        return mTree.setFocus();
    }

    public void setMode(boolean doTreeViewStuff, boolean doPixelPerfectStuff) {
        if (this.mDoTreeViewStuff != doTreeViewStuff
                || this.mDoPixelPerfectStuff != doPixelPerfectStuff) {
            final boolean expandAll = !this.mDoTreeViewStuff && doTreeViewStuff;
            this.mDoTreeViewStuff = doTreeViewStuff;
            this.mDoPixelPerfectStuff = doPixelPerfectStuff;
            Display.getDefault().syncExec(new Runnable() {
                @Override
                public void run() {
                    mTreeViewer.refresh();
                    if (expandAll) {
                        mTreeViewer.expandAll();
                    }
                }
            });
        }
    }

    @Override
    public void deviceConnected(final IHvDevice device) {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                mTreeViewer.refresh();
                mTreeViewer.setExpandedState(device, true);
            }
        });
    }

    @Override
    public void deviceChanged(final IHvDevice device) {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                TreeSelection selection = (TreeSelection) mTreeViewer.getSelection();
                mTreeViewer.refresh(device);
                if (selection.getFirstElement() instanceof Window
                        && ((Window) selection.getFirstElement()).getDevice() == device) {
                    mTreeViewer.setSelection(selection, true);
                }
            }
        });
    }

    @Override
    public void deviceDisconnected(final IHvDevice device) {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                mTreeViewer.refresh();
            }
        });
    }

    @Override
    public void focusChanged(final IHvDevice device) {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                TreeSelection selection = (TreeSelection) mTreeViewer.getSelection();
                mTreeViewer.refresh(device);
                if (selection.getFirstElement() instanceof Window
                        && ((Window) selection.getFirstElement()).getDevice() == device) {
                    mTreeViewer.setSelection(selection, true);
                }
            }
        });
    }

    @Override
    public void selectionChanged(IHvDevice device, Window window) {
        // pass
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent e) {
        Object selection = ((TreeItem) e.item).getData();
        if (selection instanceof IHvDevice && mDoPixelPerfectStuff) {
            HierarchyViewerDirector.getDirector().loadPixelPerfectData((IHvDevice) selection);
        } else if (selection instanceof Window && mDoTreeViewStuff) {
            HierarchyViewerDirector.getDirector().loadViewTreeData((Window) selection);
        }
    }

    @Override
    public void widgetSelected(SelectionEvent e) {
        TreeItem item = (TreeItem) e.item;
        if (item == null) return;
        Object selection = item.getData();
        if (selection instanceof IHvDevice) {
            mModel.setSelection((IHvDevice) selection, null);
        } else if (selection instanceof Window) {
            mModel.setSelection(((Window) selection).getHvDevice(), (Window) selection);
        }
    }
}
