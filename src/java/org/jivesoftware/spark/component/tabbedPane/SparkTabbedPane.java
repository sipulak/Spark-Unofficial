/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Lesser Public License (LGPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.spark.component.tabbedPane;

import org.jivesoftware.Spark;
import org.jivesoftware.resource.SparkRes;
import org.jivesoftware.spark.util.ModelUtil;

import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Jive Software imlementation of a TabbedPane.
 *
 * @author Derek DeMoro
 */
public class SparkTabbedPane extends JPanel implements MouseListener {
    private JPanel tabs;
    private JPanel mainPanel;

    private Window parentWindow;

    private boolean closeButtonEnabled;
    private Icon closeInactiveButtonIcon;
    private Icon closeActiveButtonIcon;

    private boolean popupAllowed;

    private boolean activeButtonBold;

    private Map<Component, JFrame> framesMap = new HashMap<Component, JFrame>();


    private int tabPlacement = JTabbedPane.TOP;

    private Color backgroundColor;
    private Color borderColor;

    /**
     * Listeners
     */
    private List<SparkTabbedPaneListener> listeners = new ArrayList<SparkTabbedPaneListener>();

    public SparkTabbedPane() {
        createUI();
    }

    public SparkTabbedPane(int placement) {
        this.tabPlacement = placement;

        createUI();
    }

    private void createUI() {
        setLayout(new BorderLayout());

        tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)) {
            public Dimension getPreferredSize() {
                if (getParent() == null)
                    return getPreferredSize();
                // calculate the preferred size based on the flow of components
                FlowLayout flow = (FlowLayout)getLayout();
                int w = getParent().getWidth();
                int h = flow.getVgap();
                int x = flow.getHgap();
                int rowH = 0;
                Dimension d;
                Component[] comps = getComponents();
                for (int i = 0; i < comps.length; i++) {
                    if (comps[i].isVisible()) {
                        d = comps[i].getPreferredSize();
                        if (x + d.width > w && x > flow.getHgap()) {
                            x = flow.getHgap();
                            h += rowH;
                            rowH = 0;
                            h += flow.getVgap();
                        }
                        rowH = Math.max(d.height, rowH);
                        x += d.width + flow.getHgap();
                    }
                }
                h += rowH;
                return new Dimension(w, h);
            }
        };


        final JPanel topPanel = new JPanel();
        topPanel.setLayout(new GridBagLayout());
        topPanel.setOpaque(false);

        // Add Tabs panel to top of panel.
        if (tabPlacement == JTabbedPane.TOP) {
            topPanel.add(tabs, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0));
            add(topPanel, BorderLayout.NORTH);
        }
        else {
            topPanel.add(tabs, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 2, 0), 0, 0));
            add(topPanel, BorderLayout.SOUTH);
        }

        // Create mainPanel
        mainPanel = new JPanel(new CardLayout());
        mainPanel.setBackground(Color.WHITE);
        add(mainPanel, BorderLayout.CENTER);

        //  mainPanel.setBorder(BorderFactory.createLineBorder(Color.lightGray));

        // Initialize close button
        closeInactiveButtonIcon = SparkRes.getImageIcon(SparkRes.CLOSE_WHITE_X_IMAGE);
        closeActiveButtonIcon = SparkRes.getImageIcon(SparkRes.CLOSE_DARK_X_IMAGE);

        setOpaque(false);
        tabs.setOpaque(false);
    }

    public SparkTab addTab(String text, Icon icon, final Component component, String tooltip) {
        SparkTab tab = addTab(text, icon, component);
        tab.setToolTipText(tooltip);
        return tab;
    }


    public SparkTab addTab(String text, Icon icon, final Component component) {
        final SparkTab tab = new SparkTab(icon, text);
        if (getBackgroundColor() != null) {
            tab.setBackgroundColor(backgroundColor);
        }

        if (getBorderColor() != null) {
            tab.setBorderColor(borderColor);
        }

        tab.setTabPlacement(tabPlacement);
        //tabs.add(tab, new GridBagConstraints(tabs.getComponentCount(), 1, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 0, 0), 0, 0));

        tabs.add(tab);
        // Add Component to main panel
        mainPanel.add(Integer.toString(component.hashCode()), component);
        tab.addMouseListener(this);

        // Add Close Button
        if (isCloseButtonEnabled()) {
            final JLabel closeButton = new JLabel(closeInactiveButtonIcon);
            tab.addComponent(closeButton);
            closeButton.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent mouseEvent) {
                    if (Spark.isWindows())
                        closeButton.setIcon(closeActiveButtonIcon);
                }

                public void mouseExited(MouseEvent mouseEvent) {
                    if (Spark.isWindows())
                        closeButton.setIcon(closeInactiveButtonIcon);
                }

                public void mouseClicked(MouseEvent mouseEvent) {
                    close(tab, component);
                }
            });

        }


        if (getSelectedIndex() == -1) {
            setSelectedTab(tab);
        }


        fireTabAdded(tab, component, getIndex(tab));


        return tab;
    }

    public int getSelectedIndex() {
        Component[] comps = tabs.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            SparkTab tab = (SparkTab)c;
            if (tab.isSelected()) {
                return i;
            }
        }

        return -1;
    }

    public void setSelectedIndex(int index) {
        Component[] comps = tabs.getComponents();
        if (index <= comps.length) {
            SparkTab tab = (SparkTab)comps[index];
            setSelectedTab(tab);
        }
    }

    public int getTabCount() {
        return tabs.getComponents().length;
    }

    public int indexOfComponent(Component comp) {
        Component[] comps = mainPanel.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            if (c == comp) {
                return i;
            }
        }

        return -1;
    }

    public Component getComponentAt(int index) {
        Component[] comps = mainPanel.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            if (i == index) {
                return c;
            }
        }

        return null;
    }

    public void removeTabAt(int index) {
        SparkTab tab = getTabAt(index);
        Component comp = getComponentAt(index);
        close(tab, comp);
    }


    public SparkTab getTabAt(int index) {
        Component[] comps = tabs.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            if (i == index) {
                return (SparkTab)c;
            }
        }

        return null;
    }

    public Component getComponentInTab(SparkTab tab) {
        Component[] comps = tabs.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            if (c == tab) {
                return getComponentAt(i);
            }
        }

        return null;
    }

    public void removeComponent(Component comp) {
        int index = indexOfComponent(comp);
        if (index != -1) {
            removeTabAt(index);
        }
    }

    public int getIndex(SparkTab tab) {
        Component[] comps = tabs.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            if (c instanceof SparkTab && c == tab) {
                return i;
            }
        }

        return -1;
    }

    public void close(SparkTab tab, Component comp) {
        int index = getIndex(tab);

        // Close Tab
        mainPanel.remove(comp);
        tabs.remove(tab);


        tabs.invalidate();
        tabs.validate();
        tabs.repaint();

        mainPanel.invalidate();
        mainPanel.validate();
        mainPanel.repaint();

        fireTabRemoved(tab, comp, index);
        Component[] comps = tabs.getComponents();
        if (comps.length == 0) {
            allTabsClosed();
        }
        else {
            findSelectedTab(index);
        }
    }

    public int indexOfTab(String title) {
        Component[] comps = tabs.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            if (c instanceof SparkTab) {
                SparkTab tab = (SparkTab)c;
                if (tab.getTitleLabel().getText().equals(title)) {
                    return i;
                }
            }
        }

        return -1;
    }

    private void findSelectedTab(int previousIndex) {
        Component[] comps = tabs.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            if (c instanceof SparkTab && i == previousIndex) {
                setSelectedTab(((SparkTab)c));
                return;
            }
        }

        if (comps.length > 0 && comps.length == previousIndex) {
            SparkTab tab = (SparkTab)comps[previousIndex - 1];
            setSelectedTab(tab);
        }
    }

    public void mouseClicked(MouseEvent e) {

    }

    public void setSelectedTab(SparkTab tab) {
        CardLayout cl = (CardLayout)mainPanel.getLayout();
        Component comp = getComponentInTab(tab);
        cl.show(mainPanel, Integer.toString(comp.hashCode()));
        tab.setBoldWhenActive(isActiveButtonBold());

        deselectAllTabsExcept(tab);

        tab.setSelected(true);
        fireTabSelected(tab, getSelectedComponent(), getIndex(tab));
    }

    public void mousePressed(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            dispatchEvent(e);
            return;
        }
        if (e.getSource() instanceof SparkTab) {
            SparkTab tab = (SparkTab)e.getSource();
            setSelectedTab(tab);
        }

    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    private void deselectAllTabsExcept(SparkTab tab) {
        Component[] comps = tabs.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            if (c instanceof SparkTab) {
                SparkTab sparkTab = (SparkTab)c;
                if (sparkTab != tab) {
                    sparkTab.setSelected(false);
                    sparkTab.showBorder(true);
                }
                else if (sparkTab == tab) {
                    int j = i - 1;
                    if (j >= 0) {
                        SparkTab previousTab = (SparkTab)comps[j];
                        previousTab.showBorder(false);
                    }
                }
            }

        }

    }

    public Component getSelectedComponent() {
        Component[] comps = mainPanel.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            if (c.isShowing()) {
                return c;
            }
        }

        return null;
    }


    public void setParentWindow(Window window) {
        this.parentWindow = window;
    }

    public Dimension getPreferredSize() {
        final Dimension size = super.getPreferredSize();
        size.width = 0;
        return size;
    }


    public boolean isCloseButtonEnabled() {
        return closeButtonEnabled;
    }

    public void setCloseButtonEnabled(boolean closeButtonEnabled) {
        this.closeButtonEnabled = closeButtonEnabled;
    }

    public Icon getCloseInactiveButtonIcon() {
        return closeInactiveButtonIcon;
    }

    public void setCloseInactiveButtonIcon(Icon closeInactiveButtonIcon) {
        this.closeInactiveButtonIcon = closeInactiveButtonIcon;
    }

    public Icon getCloseActiveButtonIcon() {
        return closeActiveButtonIcon;
    }

    public void setCloseActiveButtonIcon(Icon closeActiveButtonIcon) {
        this.closeActiveButtonIcon = closeActiveButtonIcon;
    }


    public boolean isPopupAllowed() {
        return popupAllowed;
    }

    public void setPopupAllowed(boolean popupAllowed) {
        this.popupAllowed = popupAllowed;
    }


    public void addSparkTabbedPaneListener(SparkTabbedPaneListener listener) {
        listeners.add(listener);
    }

    public void removeSparkTabbedPaneListener(SparkTabbedPaneListener listener) {
        listeners.remove(listener);
    }

    public void fireTabAdded(SparkTab tab, Component component, int index) {
        final Iterator list = ModelUtil.reverseListIterator(listeners.listIterator());
        while (list.hasNext()) {
            ((SparkTabbedPaneListener)list.next()).tabAdded(tab, component, index);
        }
    }

    public void fireTabRemoved(SparkTab tab, Component component, int index) {
        final Iterator list = ModelUtil.reverseListIterator(listeners.listIterator());
        while (list.hasNext()) {
            ((SparkTabbedPaneListener)list.next()).tabRemoved(tab, component, index);
        }
    }

    public void fireTabSelected(SparkTab tab, Component component, int index) {
        final Iterator list = ModelUtil.reverseListIterator(listeners.listIterator());
        while (list.hasNext()) {
            ((SparkTabbedPaneListener)list.next()).tabSelected(tab, component, index);
        }
    }

    public void allTabsClosed() {
        final Iterator list = ModelUtil.reverseListIterator(listeners.listIterator());
        while (list.hasNext()) {
            ((SparkTabbedPaneListener)list.next()).allTabsRemoved();
        }
    }

    public boolean isActiveButtonBold() {
        return activeButtonBold;
    }

    public void setActiveButtonBold(boolean activeButtonBold) {
        this.activeButtonBold = activeButtonBold;
    }


    /**
     * Returns the main panel used as the UI container for the card panel.
     *
     * @return the UI Container.
     */
    public JPanel getMainPanel() {
        return mainPanel;
    }


    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public Color getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
    }
}
