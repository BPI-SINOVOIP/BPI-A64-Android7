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

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.models.EvaluateContrastModel;
import com.android.hierarchyviewerlib.models.ViewNode;
import com.android.hierarchyviewerlib.models.EvaluateContrastModel.ContrastResult;
import com.android.hierarchyviewerlib.models.ViewNode.Property;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import java.lang.Math;
import java.lang.Override;
import java.lang.StringBuilder;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class EvaluateContrastDisplay {
    private static final int DEFAULT_HEIGHT = 600; // px
    private static final int MARGIN = 30; // px
    private static final int PALLETE_IMAGE_SIZE = 16; // px
    private static final int IMAGE_WIDTH = 800; // px
    private static final int RESULTS_PANEL_WIDTH = 300; // px
    private static final int MAX_NUM_CHARACTERS = 35;

    private static final String ABBREVIATE_SUFFIX = "...\"";

    private static Shell sShell;
    private static Canvas sCanvas;
    private static Composite sResultsPanel;
    private static Tree sResultsTree;

    private static Image sImage;
    private static Point sImageOffset;
    private static ScrollBar sImageScrollBar;
    private static int sImageWidth;
    private static int sImageHeight;

    private static Image sYellowImage;
    private static Image sRedImage;
    private static Image sGreenImage;

    private static ViewNode sSelectedNode;

    private static org.eclipse.swt.graphics.Color sBorderColorPass;
    private static org.eclipse.swt.graphics.Color sBorderColorFail;
    private static org.eclipse.swt.graphics.Color sBorderColorIndeterminate;
    private static org.eclipse.swt.graphics.Color sBorderColorCurrentlySelected;

    private static HashMap<ViewNode, Rectangle> sRectangleForViewNode;
    private static HashMap<ViewNode, org.eclipse.swt.graphics.Color> sBorderColorForViewNode;
    private static HashMap<ViewNode, EvaluateContrastModel> sViewNodeForModel;
    private static HashMap<Integer, Image> sImageForColor;
    private static HashMap<TreeItem, ViewNode> sViewNodeForTreeItem;

    private static double sScaleFactor;

    static {
        sImageForColor = new HashMap<Integer, Image>();
        sViewNodeForTreeItem = new HashMap<TreeItem, ViewNode>();

        ImageLoader loader = ImageLoader.getLoader(EvaluateContrastDisplay.class);
        sYellowImage = loader.loadImage("yellow.png", Display.getDefault());
        sRedImage = loader.loadImage("red.png", Display.getDefault());
        sGreenImage = loader.loadImage("green.png", Display.getDefault());

        sRectangleForViewNode = new HashMap<ViewNode, Rectangle>();
        sBorderColorForViewNode = new HashMap<ViewNode, org.eclipse.swt.graphics.Color>();
        sViewNodeForModel = new HashMap<ViewNode, EvaluateContrastModel>();
    }

    private static org.eclipse.swt.graphics.Color getBorderColorPass() {
        if (sBorderColorPass == null) {
            sBorderColorPass = /** green */
                    new org.eclipse.swt.graphics.Color(Display.getDefault(), new RGB(0, 255, 0));
        }
        return sBorderColorPass;
    }

    private static org.eclipse.swt.graphics.Color getBorderColorFail() {
        if (sBorderColorFail == null) {
            sBorderColorFail = /** red */
                    new org.eclipse.swt.graphics.Color(Display.getDefault(), new RGB(255, 0, 0));
        }
        return sBorderColorFail;
    }

    private static org.eclipse.swt.graphics.Color getBorderColorIndeterminate() {
        if (sBorderColorIndeterminate == null) {
            sBorderColorIndeterminate = /** yellow */
                    new org.eclipse.swt.graphics.Color(Display.getDefault(), new RGB(255, 255, 0));
        }
        return sBorderColorIndeterminate;
    }

    private static org.eclipse.swt.graphics.Color getBorderColorCurrentlySelected() {
        if (sBorderColorCurrentlySelected == null) {
            sBorderColorCurrentlySelected = /** blue */
                    new org.eclipse.swt.graphics.Color(Display.getDefault(), new RGB(0, 0, 255));
        }
        return sBorderColorCurrentlySelected;
    }

    private static void clear(boolean shellIsNull) {
        sRectangleForViewNode.clear();
        sBorderColorForViewNode.clear();
        sViewNodeForModel.clear();

        if (!shellIsNull) {
            sImage.dispose();
            for (Image image : sImageForColor.values()) {
                image.dispose();
            }

            sImageForColor.clear();
            sViewNodeForTreeItem.clear();
            for (Control item : sShell.getChildren()) {
                item.dispose();
            }
        }

        if (sBorderColorPass != null) {
            sBorderColorPass.dispose();
            sBorderColorPass = null;
        }
        if (sBorderColorFail != null) {
            sBorderColorFail.dispose();
            sBorderColorFail = null;
        }
        if (sBorderColorIndeterminate != null) {
            sBorderColorIndeterminate.dispose();
            sBorderColorIndeterminate = null;
        }
        if (sBorderColorCurrentlySelected != null) {
            sBorderColorCurrentlySelected.dispose();
            sBorderColorCurrentlySelected = null;
        }
    }

    private static Image scaleImage(Image image, int width, int height) {
        Image scaled = new Image(Display.getDefault(), width, height);
        GC gc = new GC(scaled);
        gc.setInterpolation(SWT.HIGH);
        gc.setAntialias(SWT.ON);
        gc.drawImage(image, 0, 0, image.getBounds().width, image.getBounds().height, 0, 0,
                width, height);
        image.dispose();
        gc.dispose();
        return scaled;
    }

    public static void show(Shell parentShell, ViewNode rootNode, Image image) {
        clear(sShell == null);

        sScaleFactor = Math.min(IMAGE_WIDTH / (double) image.getBounds().width, 1.0);
        sImage = scaleImage(image, IMAGE_WIDTH,
                (int) Math.round(image.getBounds().height * sScaleFactor));
        sImageWidth = sImage.getBounds().width;
        sImageHeight = sImage.getBounds().height;

        if (sShell == null) {
            sShell = new Shell(Display.getDefault(), SWT.CLOSE | SWT.TITLE);
            sShell.setText("Evaluate Contrast");
            sShell.addShellListener(sShellListener);
            sShell.setLayout(new GridLayout(2, false));
        }
        buildContents(sShell);
        processEvaluatableChildViews(rootNode);

        sShell.setLocation(parentShell.getBounds().x, parentShell.getBounds().y);
        sShell.setSize(IMAGE_WIDTH + RESULTS_PANEL_WIDTH + MARGIN, DEFAULT_HEIGHT + (MARGIN * 2));
        sImageScrollBar.setMaximum(sImage.getBounds().height);
        sImageScrollBar.setThumb(DEFAULT_HEIGHT);
        sShell.open();
        sShell.layout();
    }

    private static void buildContents(Composite shell) {
        buildResultsPanel();
        buildImagePanel(shell);
    }

    private static void buildResultsPanel() {
        sResultsPanel = new Composite(sShell, SWT.NONE);
        sResultsPanel.setLayout(new FillLayout());
        GridData gridData = new GridData(SWT.BEGINNING, SWT.BEGINNING, false, true);
        sResultsPanel.setLayoutData(gridData);

        ScrolledComposite scrolledComposite = new ScrolledComposite(sResultsPanel, SWT.VERTICAL);
        sResultsTree = new Tree(scrolledComposite, SWT.NONE);
        sResultsTree.setLinesVisible(true);
        scrolledComposite.setContent(sResultsTree);
        sResultsTree.setSize(RESULTS_PANEL_WIDTH, DEFAULT_HEIGHT);

        sResultsTree.addListener(SWT.PaintItem, new Listener() {
            @Override
            public void handleEvent(Event event) {
                TreeItem item = (TreeItem) event.item;
                Image image = (Image) item.getData();
                if (image != null) {
                    int x = event.x + event.width;
                    int itemHeight = sResultsTree.getItemHeight();
                    int imageHeight = image.getBounds().height;
                    int y = event.y + (itemHeight - imageHeight) / 2;
                    event.gc.drawImage(image, x, y);
                }
            }
        });

        Listener listener = new Listener() {
            @Override
            public void handleEvent(Event e) {
                TreeItem treeItem = (TreeItem) e.item;
                if (treeItem.getItemCount() == 0) {
                    do {
                        treeItem = treeItem.getParentItem();
                    } while (treeItem.getParentItem() != null);
                }

                ViewNode node = sViewNodeForTreeItem.get(treeItem);
                if (sSelectedNode != node) {
                    sSelectedNode = sViewNodeForTreeItem.get(treeItem);
                    sCanvas.redraw();
                }
            }
        };
        sResultsTree.addListener(SWT.Selection, listener);
        sResultsTree.addListener(SWT.DefaultSelection, listener);
    }

    private static void buildImagePanel(Composite parent) {
        sImageOffset = new Point(0, 0);
        sCanvas = new Canvas(parent, SWT.V_SCROLL | SWT.NO_BACKGROUND | SWT.NO_REDRAW_RESIZE);
        sCanvas.addPaintListener(new PaintListener() {

            @Override
            public void paintControl(PaintEvent e) {
                GC gc = e.gc;
                gc.drawImage(sImage, sImageOffset.x, sImageOffset.y);

                for (ViewNode viewNode : sRectangleForViewNode.keySet()) {
                    Rectangle rectangle = sRectangleForViewNode.get(viewNode);
                    if (sSelectedNode == viewNode) {
                        e.gc.setForeground(getBorderColorCurrentlySelected());
                    } else {
                        e.gc.setForeground(sBorderColorForViewNode.get(viewNode));
                    }
                    e.gc.drawRectangle(Math.max(0, sImageOffset.x + rectangle.x - 1),
                            sImageOffset.y + rectangle.y - 1,
                            rectangle.width - 1,
                            rectangle.height - 1);
                }

                Rectangle rect = sImage.getBounds();
                Rectangle client = sCanvas.getClientArea();
                int marginWidth = client.width - rect.width;
                if (marginWidth > 0) {
                    gc.fillRectangle(rect.width, 0, marginWidth, client.height);
                }
                int marginHeight = client.height - rect.height;
                if (marginHeight > 0) {
                    gc.fillRectangle(0, rect.height, client.width, marginHeight);
                }
            }
        });

        sImageScrollBar = sCanvas.getVerticalBar();
        sImageScrollBar.addListener(SWT.Selection, new Listener() {
            @Override
            public void handleEvent(Event e) {
                int offset = sImageScrollBar.getSelection();
                Rectangle imageBounds = sImage.getBounds();
                sImageOffset.y = -offset;

                int y = -offset - sImageOffset.y;
                sCanvas.scroll(0, y, 0, 0, imageBounds.width, imageBounds.height, false);
                sCanvas.redraw();
            }
        });

        GridData gridData = new GridData(SWT.BEGINNING, SWT.BEGINNING, true, true);
        gridData.widthHint = IMAGE_WIDTH + MARGIN;
        gridData.heightHint = DEFAULT_HEIGHT;
        sCanvas.setLayoutData(gridData);
    }

    private static void processEvaluatableChildViews(ViewNode root){
        List<ViewNode> children = getEvaluatableChildViews(root);

        for (final ViewNode child : children) {
            calculateRectangleForViewNode(child);
            EvaluateContrastModel evaluateContrastModel = evaluateContrastForView(child);
            if (evaluateContrastModel != null) {
                calculateBorderColorForViewNode(child, evaluateContrastModel.getContrastResult());
                buildTreeItem(evaluateContrastModel, child);
                sViewNodeForModel.put(child, evaluateContrastModel);
            } else {
                sRectangleForViewNode.remove(child);
            }
        }
    }

    private static void buildTreeItem(EvaluateContrastModel model, final ViewNode child) {
        int dotIndex = child.name.lastIndexOf('.');
        String shortName = (dotIndex == -1) ? child.name : child.name.substring(dotIndex + 1);
        String text = shortName + ": \"" + child.namedProperties.get("text:mText").value + "\"";

        TreeItem item = new TreeItem(sResultsTree, SWT.NONE);
        item.setText(transformText(text, MAX_NUM_CHARACTERS));
        item.setImage(getResultImage(model.getContrastResult()));
        sViewNodeForTreeItem.put(item, child);
        buildTreeItemsForModel(model, item);
    }

    private static Image buildImageForColor(int color) {
        Image image = sImageForColor.get(color);

        if (image == null) {
            image = new Image(Display.getDefault(), PALLETE_IMAGE_SIZE, PALLETE_IMAGE_SIZE);
            GC gc = new GC(image);

            org.eclipse.swt.graphics.Color swtColor = awtColortoSwtColor(new java.awt.Color(color));
            gc.setBackground(swtColor);
            swtColor.dispose();
            gc.fillRectangle(0, 0, PALLETE_IMAGE_SIZE, PALLETE_IMAGE_SIZE);

            swtColor = awtColortoSwtColor(java.awt.Color.BLACK);
            gc.setForeground(swtColor);
            swtColor.dispose();
            gc.drawRectangle(0, 0, PALLETE_IMAGE_SIZE - 1, PALLETE_IMAGE_SIZE - 1);
            gc.dispose();

            sImageForColor.put(color, image);
        }

        return image;
    }

    public static org.eclipse.swt.graphics.Color awtColortoSwtColor(java.awt.Color color) {
        return new org.eclipse.swt.graphics.Color(Display.getDefault(),
                color.getRed(), color.getGreen(), color.getBlue());
    }

    private static void buildTreeItemsForModel(EvaluateContrastModel model, TreeItem parent) {
        TreeItem item = new TreeItem(parent, SWT.NONE);
        item.setText("Text color: " + model.getTextColorHex());
        item.setData(buildImageForColor(model.getTextColor()));

        item = new TreeItem(parent, SWT.NONE);
        item.setText("Background color: " + model.getBackgroundColorHex());
        item.setData(buildImageForColor(model.getBackgroundColor()));

        new TreeItem(parent, SWT.NONE).setText("Text size: " + model.getTextSize());

        new TreeItem(parent, SWT.NONE).setText("Contrast ratio: " + String.format(
                EvaluateContrastModel.CONTRAST_RATIO_FORMAT, model.getContrastRatio()));

        if (!model.isIndeterminate()) {
            new TreeItem(parent, SWT.NONE).setText("Test: " + model.getContrastResult().name());
        } else {
            item = new TreeItem(parent, SWT.NONE);
            item.setText("Normal Text Test: " + model.getContrastResultForNormalText().name());
            item.setImage(getResultImage(model.getContrastResultForNormalText()));
            item = new TreeItem(parent, SWT.NONE);
            item.setText("Large Text Test: " + model.getContrastResultForLargeText().name());
            item.setImage(getResultImage(model.getContrastResultForLargeText()));
        }
    }

    private static List<ViewNode> getEvaluatableChildViews(ViewNode root) {
        List<ViewNode> children = new ArrayList<ViewNode>();

        children.add(root);
        for (int i = 0; i < children.size(); ++i) {
            ViewNode node = children.get(i);
            List<ViewNode> temp = node.children;
            for (ViewNode child: temp) {
                if (!children.contains(child)) {
                    children.add(child);
                }
            }
        }

        List<ViewNode> evalutableChildren = new ArrayList<ViewNode>();
        for (final ViewNode child : children) {
            if (child.namedProperties.get("text:mText") != null) {
                evalutableChildren.add(child);
            }
        }

        return evalutableChildren;
    }

    private static void calculateBorderColorForViewNode(ViewNode node, ContrastResult result) {
        org.eclipse.swt.graphics.Color borderColor;

        switch (result) {
            case PASS:
                borderColor = getBorderColorPass();
                break;
            case FAIL:
                borderColor = getBorderColorFail();
                break;
            case INDETERMINATE:
            default:
                borderColor = getBorderColorIndeterminate();
        }

        sBorderColorForViewNode.put(node, borderColor);
    }

    private static Image getResultImage(ContrastResult result) {
        switch (result) {
            case PASS:
                return sGreenImage;
            case FAIL:
                return sRedImage;
            default:
                return sYellowImage;
        }
    }

    private static String transformText(String text, int maxNumCharacters) {
        if (text.length() == maxNumCharacters) {
            return text;
        } else if (text.length() < maxNumCharacters) {
            char[] filler = new char[maxNumCharacters - text.length()];
            Arrays.fill(filler,' ');
            return text + new String(filler);
        }

        StringBuilder abbreviatedText = new StringBuilder();
        abbreviatedText.append(text.substring(0, maxNumCharacters - ABBREVIATE_SUFFIX.length()));
        abbreviatedText.append(ABBREVIATE_SUFFIX);
        return abbreviatedText.toString();
    }

    private static void calculateRectangleForViewNode(ViewNode viewNode) {
          int leftShift = 0;
          int topShift = 0;
          int nodeLeft = (int) Math.round(viewNode.left * sScaleFactor);
          int nodeTop = (int) Math.round(viewNode.top * sScaleFactor);
          int nodeWidth = (int) Math.round(viewNode.width * sScaleFactor);
          int nodeHeight = (int) Math.round(viewNode.height * sScaleFactor);
          ViewNode current = viewNode;

          while (current.parent != null) {
              leftShift += (int) Math.round(
                      sScaleFactor * (current.parent.left - current.parent.scrollX));
              topShift += (int) Math.round(
                      sScaleFactor * (current.parent.top - current.parent.scrollY));
              current = current.parent;
          }

          sRectangleForViewNode.put(viewNode, new Rectangle(leftShift + nodeLeft,
                  topShift + nodeTop, nodeWidth, nodeHeight));
    }

    private static EvaluateContrastModel evaluateContrastForView(ViewNode node) {
        Map<String, Property> namedProperties = node.namedProperties;
        Property textColorProperty = namedProperties.get("text:mCurTextColor");
        Integer textColor = textColorProperty == null ? null :
                Integer.valueOf(textColorProperty.value);
        Property textSizeProperty = namedProperties.get("text:getScaledTextSize()");
        Double textSize = textSizeProperty == null ? null : Double.valueOf(textSizeProperty.value);
        Rectangle rectangle = sRectangleForViewNode.get(node);
        Property boldProperty = namedProperties.get("text:getTypefaceStyle()");
        boolean isBold = boldProperty != null && boldProperty.value.equals("BOLD");

        // TODO: also remove views that are covered by other views
        if (rectangle.x < 0 || rectangle.x > sImageWidth ||
                rectangle.y < 0 || rectangle.y > sImageHeight ||
                rectangle.width == 0 || rectangle.height == 0) {
            // not viewable in screenshot, therefore can't parse background color
            return null;
        }

        int x = Math.max(0, rectangle.x);
        int y = Math.max(0, rectangle.y);
        int width = Math.min(sImageWidth, rectangle.x + rectangle.width);
        int height = Math.min(sImageHeight, rectangle.y + rectangle.height);

        return new EvaluateContrastModel(
                sImage, textColor, textSize, x, y, width, height, isBold);
    }

    private static ShellAdapter sShellListener = new ShellAdapter() {
        @Override
        public void shellClosed(ShellEvent e) {
            e.doit = false;
            sShell.setVisible(false);
            clear(sShell == null);
        }
    };
}
