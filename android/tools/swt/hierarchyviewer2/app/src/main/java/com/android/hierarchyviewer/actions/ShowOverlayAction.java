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

package com.android.hierarchyviewer.actions;

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewer.HierarchyViewerApplication;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.actions.ImageAction;
import com.android.hierarchyviewerlib.models.PixelPerfectModel;
import com.android.hierarchyviewerlib.models.PixelPerfectModel.IImageChangeListener;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

public class ShowOverlayAction extends Action implements ImageAction, IImageChangeListener {

    private static ShowOverlayAction sAction;

    private Image mImage;

    private ShowOverlayAction() {
        super("Show In &Loupe", Action.AS_CHECK_BOX);
        setAccelerator(SWT.MOD1 + 'L');
        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        mImage = imageLoader.loadImage("show-overlay.png", Display.getDefault()); //$NON-NLS-1$
        setImageDescriptor(ImageDescriptor.createFromImage(mImage));
        setToolTipText("Show the overlay in the loupe view");
        setEnabled(PixelPerfectModel.getModel().getOverlayImage() != null);
        PixelPerfectModel.getModel().addImageChangeListener(this);
    }

    public static ShowOverlayAction getAction() {
        if (sAction == null) {
            sAction = new ShowOverlayAction();
        }
        return sAction;
    }

    @Override
    public void run() {
        HierarchyViewerApplication.getMainWindow().showOverlayInLoupe(sAction.isChecked());
    }

    @Override
    public Image getImage() {
        return mImage;
    }

    @Override
    public void crosshairMoved() {
        // pass
    }

    @Override
    public void treeChanged() {
        // pass
    }

    @Override
    public void imageChanged() {
        // pass
    }

    @Override
    public void imageLoaded() {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                Image overlayImage = PixelPerfectModel.getModel().getOverlayImage();
                setEnabled(overlayImage != null);
            }
        });
    }

    @Override
    public void overlayChanged() {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                setEnabled(PixelPerfectModel.getModel().getOverlayImage() != null);
            }
        });
    }

    @Override
    public void overlayTransparencyChanged() {
        // pass
    }

    @Override
    public void selectionChanged() {
        // pass
    }

    @Override
    public void zoomChanged() {
        // pass
    }
}
