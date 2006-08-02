/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Lesser Public License (LGPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.spark.ui;

import org.jivesoftware.MainWindowListener;
import org.jivesoftware.Spark;
import org.jivesoftware.resource.SparkRes;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket;
import org.jivesoftware.smack.packet.StreamError;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.SharedGroupManager;
import org.jivesoftware.smackx.packet.LastActivity;
import org.jivesoftware.spark.ChatManager;
import org.jivesoftware.spark.SparkManager;
import org.jivesoftware.spark.Workspace;
import org.jivesoftware.spark.component.InputDialog;
import org.jivesoftware.spark.component.MessageDialog;
import org.jivesoftware.spark.component.RolloverButton;
import org.jivesoftware.spark.component.VerticalFlowLayout;
import org.jivesoftware.spark.component.WrappedLabel;
import org.jivesoftware.spark.plugin.ContextMenuListener;
import org.jivesoftware.spark.plugin.Plugin;
import org.jivesoftware.spark.ui.rooms.ChatRoomImpl;
import org.jivesoftware.spark.ui.status.StatusBar;
import org.jivesoftware.spark.util.ModelUtil;
import org.jivesoftware.spark.util.ResourceUtils;
import org.jivesoftware.spark.util.SwingWorker;
import org.jivesoftware.spark.util.log.Log;
import org.jivesoftware.sparkimpl.profile.VCardManager;
import org.jivesoftware.sparkimpl.settings.local.LocalPreferences;
import org.jivesoftware.sparkimpl.settings.local.SettingsManager;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class ContactList extends JPanel implements ActionListener, ContactGroupListener, Plugin, RosterListener, ConnectionListener {
    private JPanel mainPanel = new JPanel();
    private JScrollPane treeScroller;
    private final List<ContactGroup> groupList = new ArrayList<ContactGroup>();
    private final RolloverButton addingGroupButton;

    private ContactItem activeItem;
    private ContactGroup activeGroup;
    private ContactGroup unfiledGroup = new ContactGroup("Unfiled");


    // Create Menus
    private JMenuItem addContactMenu;
    private JMenuItem addContactGroupMenu;
    private JMenuItem removeContactFromGroupMenu;
    private JMenuItem chatMenu;
    private JMenuItem renameMenu;

    private ContactGroup offlineGroup;
    final JCheckBoxMenuItem showHideMenu = new JCheckBoxMenuItem();

    private List sharedGroups = new ArrayList();

    private final List contextListeners = new ArrayList();

    private List initialPresences = new ArrayList();
    private final Timer presenceTimer = new Timer();
    private final List dndListeners = new ArrayList();
    private final List contactListListeners = new ArrayList();
    private Properties props;
    private File propertiesFile;

    private LocalPreferences localPreferences;


    public final static String RETRY_PANEL = "RETRY_PANEL";


    private RetryPanel retryPanel;
    private RetryPanel.ReconnectListener reconnectListener;

    private Workspace workspace;


    public ContactList() {
        // Load Local Preferences
        localPreferences = SettingsManager.getLocalPreferences();

        offlineGroup = new ContactGroup("Offline Group");

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        addContactMenu = new JMenuItem("Add Contact", SparkRes.getImageIcon(SparkRes.USER1_ADD_16x16));
        addContactGroupMenu = new JMenuItem("Add Contact Group", SparkRes.getImageIcon(SparkRes.SMALL_ADD_IMAGE));

        removeContactFromGroupMenu = new JMenuItem("Remove from Group", SparkRes.getImageIcon(SparkRes.SMALL_DELETE));
        chatMenu = new JMenuItem("Start a Chat", SparkRes.getImageIcon(SparkRes.SMALL_MESSAGE_IMAGE));
        renameMenu = new JMenuItem("Rename", SparkRes.getImageIcon(SparkRes.DESKTOP_IMAGE));

        addContactMenu.addActionListener(this);
        removeContactFromGroupMenu.addActionListener(this);
        chatMenu.addActionListener(this);
        renameMenu.addActionListener(this);


        setLayout(new BorderLayout());

        addingGroupButton = new RolloverButton(SparkRes.getImageIcon(SparkRes.ADD_CONTACT_IMAGE));

        RolloverButton groupChatButton = new RolloverButton(SparkRes.getImageIcon(SparkRes.JOIN_GROUPCHAT_IMAGE));
        toolbar.add(addingGroupButton);
        toolbar.add(groupChatButton);

        addingGroupButton.addActionListener(this);

        mainPanel.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
        mainPanel.setBackground((Color)UIManager.get("List.background"));
        treeScroller = new JScrollPane(mainPanel);
        treeScroller.setBorder(BorderFactory.createEmptyBorder());
        treeScroller.getVerticalScrollBar().setBlockIncrement(50);
        treeScroller.getVerticalScrollBar().setUnitIncrement(20);

        retryPanel = new RetryPanel();

        workspace = SparkManager.getWorkspace();

        workspace.getCardPanel().add(RETRY_PANEL, retryPanel);


        add(treeScroller, BorderLayout.CENTER);

        // Load Properties file
        props = new Properties();
        // Save to properties file.
        propertiesFile = new File(new File(Spark.getUserHome(), "Spark"), "groups.properties");
        try {
            props.load(new FileInputStream(propertiesFile));
        }
        catch (IOException e) {
            // File does not exist.
        }

        // Add ActionListener(s) to menus
        addContactGroup(unfiledGroup);
        addContactGroup(offlineGroup);

        showHideMenu.setSelected(false);

        // Add KeyMappings
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control N"), "searchContacts");
        getActionMap().put("searchContacts", new AbstractAction("searchContacts") {
            public void actionPerformed(ActionEvent evt) {
                searchContacts("");
            }
        });

        // Save state on shutdown.
        SparkManager.getMainWindow().addMainWindowListener(new MainWindowListener() {
            public void shutdown() {
                saveState();
            }

            public void mainWindowActivated() {

            }

            public void mainWindowDeactivated() {

            }
        });

        SparkManager.getConnection().addConnectionListener(this);

        // Get command panel and add View Online/Offline, Add Contact
        StatusBar statusBar = SparkManager.getWorkspace().getStatusBar();
        JPanel commandPanel = statusBar.getCommandPanel();


        final RolloverButton addContactButton = new RolloverButton(SparkRes.getImageIcon(SparkRes.USER1_ADD_16x16));
        commandPanel.add(addContactButton);
        addContactButton.setToolTipText("Add a contact");
        addContactButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new RosterDialog().showRosterDialog();
            }
        });


    }

    /**
     * Updates the users presence.
     *
     * @param presence the user to update.
     */
    private void updateUserPresence(Presence presence) {
        if (presence == null) {
            return;
        }

        Roster roster = SparkManager.getConnection().getRoster();

        final String bareJID = StringUtils.parseBareAddress(presence.getFrom());

        RosterEntry entry = roster.getEntry(bareJID);
        boolean isPending = entry != null && (entry.getType() == RosterPacket.ItemType.NONE || entry.getType() == RosterPacket.ItemType.FROM)
            && RosterPacket.ItemStatus.SUBSCRIPTION_PENDING == entry.getStatus();

        // If online, check to see if they are in the offline group.
        // If so, remove from offline group and add to all groups they
        // belong to.
        if (presence.getType() == Presence.Type.AVAILABLE && offlineGroup.getContactItemByJID(bareJID) != null) {
            changeOfflineToOnline(bareJID, entry, presence);
        }
        else if (presence.getFrom().indexOf("workgroup.") != -1) {
            changeOfflineToOnline(bareJID, entry, presence);
        }

        // If online, but not in offline group, update presence.
        else if (presence.getType() == Presence.Type.AVAILABLE) {
            final Iterator groupIterator = groupList.iterator();
            while (groupIterator.hasNext()) {
                ContactGroup group = (ContactGroup)groupIterator.next();
                ContactItem item = group.getContactItemByJID(bareJID);
                if (item != null) {
                    item.setPresence(presence);
                    group.fireContactGroupUpdated();
                }
            }
        }

        // If not available, move to offline group.
        else if (presence.getType() == Presence.Type.UNAVAILABLE && !isPending) {
            final Iterator groupIterator = new ArrayList(groupList).iterator();
            while (groupIterator.hasNext()) {
                ContactGroup group = (ContactGroup)groupIterator.next();
                ContactItem item = group.getContactItemByJID(bareJID);
                if (item != null) {
                    item.setPresence(null);

                    // Check for ContactItemHandler.

                    group.removeContactItem(item);
                    checkGroup(group);

                    if (offlineGroup.getContactItemByJID(item.getFullJID()) == null) {
                        offlineGroup.addContactItem(item);
                        offlineGroup.fireContactGroupUpdated();
                    }

                    group.fireContactGroupUpdated();
                }
            }
        }

    }

    private void moveToOfflineGroup(String bareJID) {
        final Iterator groupIterator = new ArrayList(groupList).iterator();
        while (groupIterator.hasNext()) {
            ContactGroup group = (ContactGroup)groupIterator.next();
            ContactItem item = group.getContactItemByJID(bareJID);
            if (item != null) {
                item.setPresence(null);

                // Check for ContactItemHandler.
                group.removeContactItem(item);
                checkGroup(group);

                if (offlineGroup.getContactItemByJID(item.getFullJID()) == null) {
                    offlineGroup.addContactItem(item);
                    offlineGroup.fireContactGroupUpdated();
                }
            }
        }
    }

    private void changeOfflineToOnline(String bareJID, RosterEntry entry, Presence presence) {
        // Move out of offline group. Add to all groups.
        ContactItem offlineItem = offlineGroup.getContactItemByJID(bareJID);
        if (offlineItem == null) {
            return;
        }

        offlineGroup.removeContactItem(offlineItem);

        // Add To all groups it belongs to.
        Iterator groups = entry.getGroups();
        boolean isFiled = groups.hasNext();

        while (groups.hasNext()) {
            RosterGroup rosterGroup = (RosterGroup)groups.next();
            ContactGroup contactGroup = getContactGroup(rosterGroup.getName());
            if (contactGroup != null) {
                String name = entry.getName();
                if (name == null) {
                    name = entry.getUser();
                }
                if (contactGroup.getContactItemByJID(entry.getUser()) == null) {
                    ContactItem contactItem = new ContactItem(name, entry.getUser());
                    contactItem.setPresence(presence);
                    contactItem.setAvailable(true);

                    contactGroup.addContactItem(contactItem);

                    toggleGroupVisibility(contactGroup.getGroupName(), true);

                    contactGroup.fireContactGroupUpdated();
                }
                else if (contactGroup.getContactItemByJID(entry.getUser()) != null) {
                    // If the user is in their, but without a nice presence.
                    ContactItem contactItem = contactGroup.getContactItemByJID(entry.getUser());
                    contactItem.setPresence(presence);
                    contactItem.setAvailable(true);
                    toggleGroupVisibility(contactGroup.getGroupName(), true);

                    contactGroup.fireContactGroupUpdated();
                }
            }
        }

        if (!isFiled) {
            String name = entry.getName();
            if (name == null) {
                name = entry.getUser();
            }
            ContactItem contactItem = new ContactItem(name, entry.getUser());
            unfiledGroup.addContactItem(contactItem);
            contactItem.setPresence(presence);
            contactItem.setAvailable(true);

            unfiledGroup.setVisible(true);
            unfiledGroup.fireContactGroupUpdated();
        }
    }


    private void buildContactList() {
        XMPPConnection con = SparkManager.getConnection();
        final Roster roster = con.getRoster();


        roster.addRosterListener(this);

        final Iterator rosterGroups = roster.getGroups();
        while (rosterGroups.hasNext()) {
            RosterGroup group = (RosterGroup)rosterGroups.next();
            ContactGroup contactGroup = addContactGroup(group.getName());

            Iterator entries = group.getEntries();
            while (entries.hasNext()) {
                RosterEntry entry = (RosterEntry)entries.next();
                String name = entry.getName();
                if (name == null) {
                    name = entry.getUser();
                }

                ContactItem contactItem = new ContactItem(name, entry.getUser());
                contactItem.setPresence(null);
                if ((entry.getType() == RosterPacket.ItemType.NONE || entry.getType() == RosterPacket.ItemType.FROM)
                    && RosterPacket.ItemStatus.SUBSCRIPTION_PENDING == entry.getStatus()) {
                    // Add to contact group.
                    contactGroup.addContactItem(contactItem);
                    contactGroup.setVisible(true);
                }
                else {
                    if (offlineGroup.getContactItemByJID(entry.getUser()) == null) {
                        offlineGroup.addContactItem(contactItem);
                    }
                }
            }

        }

        // Add Unfiled Group
        // addContactGroup(unfiledGroup);
        final Iterator unfiledEntries = roster.getUnfiledEntries();
        while (unfiledEntries.hasNext()) {
            RosterEntry entry = (RosterEntry)unfiledEntries.next();
            String name = entry.getName();
            if (name == null) {
                name = entry.getUser();
            }

            ContactItem contactItem = new ContactItem(name, entry.getUser());
            offlineGroup.addContactItem(contactItem);
        }
        unfiledGroup.setVisible(false);
    }

    /**
     * Called when NEW entries are added.
     *
     * @param addresses the addressss added.
     */
    public void entriesAdded(final Collection addresses) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Roster roster = SparkManager.getConnection().getRoster();

                Iterator jids = addresses.iterator();
                while (jids.hasNext()) {
                    String jid = (String)jids.next();
                    RosterEntry entry = roster.getEntry(jid);
                    addUser(entry);
                }
            }
        });
    }

    private void addUser(RosterEntry entry) {
        String name = entry.getName();
        if (!ModelUtil.hasLength(name)) {
            name = entry.getUser();
        }

        String nickname = entry.getName();
        if (!ModelUtil.hasLength(nickname)) {
            nickname = entry.getUser();
        }

        ContactItem newContactItem = new ContactItem(nickname, entry.getUser());

        // Update users icon
        Presence presence = SparkManager.getConnection().getRoster().getPresence(entry.getUser());
        newContactItem.setPresence(presence);


        if (entry != null && (entry.getType() == RosterPacket.ItemType.NONE || entry.getType() == RosterPacket.ItemType.FROM)) {
            // Ignore, since the new user is pending to be added.
            final Iterator groups = entry.getGroups();
            while (groups.hasNext()) {
                final RosterGroup group = (RosterGroup)groups.next();
                ContactGroup contactGroup = getContactGroup(group.getName());
                if (contactGroup == null) {
                    contactGroup = addContactGroup(group.getName());
                }
                contactGroup.addContactItem(newContactItem);
            }
            return;
        }
        else {
            offlineGroup.addContactItem(newContactItem);
        }


        if (presence != null) {
            updateUserPresence(presence);
        }
    }

    private ContactItem addNewContactItem(String groupName, String nickname, String jid) {
        // Create User Node
        ContactGroup contactGroup = getContactGroup(groupName);
        ContactItem contactItem = new ContactItem(nickname, jid);
        contactGroup.addContactItem(contactItem);
        contactGroup.setVisible(true);

        validateTree();
        return contactItem;
    }


    /**
     * Handle when the Roster changes based on subscription notices.
     *
     * @param addresses
     */
    public void entriesUpdated(final Collection addresses) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Roster roster = SparkManager.getConnection().getRoster();

                Iterator jids = addresses.iterator();
                while (jids.hasNext()) {
                    String jid = (String)jids.next();
                    RosterEntry rosterEntry = roster.getEntry(jid);
                    if (rosterEntry != null) {
                        // Check for new Roster Groups and add them if they do not exist.
                        Iterator rosterGroups = rosterEntry.getGroups();

                        boolean isUnfiled = true;
                        while (rosterGroups.hasNext()) {
                            isUnfiled = false;
                            RosterGroup group = (RosterGroup)rosterGroups.next();

                            // Handle if this is a new Entry in a new Group.
                            if (getContactGroup(group.getName()) == null) {
                                // Create group.
                                ContactGroup contactGroup = addContactGroup(group.getName());
                                contactGroup.setVisible(false);
                                contactGroup = getContactGroup(group.getName());
                                String name = rosterEntry.getName();
                                if (name == null) {
                                    name = rosterEntry.getUser();
                                }

                                ContactItem contactItem = new ContactItem(name, rosterEntry.getUser());
                                contactGroup.addContactItem(contactItem);
                                Presence presence = roster.getPresence(jid);
                                contactItem.setPresence(presence);
                                if (presence != null) {
                                    contactGroup.setVisible(true);
                                }
                            }
                            else {
                                ContactGroup contactGroup = getContactGroup(group.getName());
                                ContactItem item = contactGroup.getContactItemByJID(jid);

                                // Check to see if this entry is new to a pre-existing group.
                                if (item == null) {
                                    String name = rosterEntry.getName();
                                    if (name == null) {
                                        name = rosterEntry.getUser();
                                    }
                                    item = new ContactItem(name, rosterEntry.getUser());
                                    Presence presence = roster.getPresence(jid);
                                    item.setPresence(presence);
                                    if (presence != null) {
                                        contactGroup.addContactItem(item);
                                        contactGroup.fireContactGroupUpdated();
                                    }
                                }

                                // If not, just update their presence.
                                else {
                                    Presence presence = roster.getPresence(jid);
                                    item.setPresence(presence);
                                    updateUserPresence(presence);
                                    contactGroup.fireContactGroupUpdated();
                                }
                            }
                        }

                        // Now check to see if groups have been modified or removed. This is used
                        // to check if Contact Groups have been renamed or removed.
                        Set groupSet = new HashSet();
                        jids = addresses.iterator();
                        while (jids.hasNext()) {
                            jid = (String)jids.next();
                            rosterEntry = roster.getEntry(jid);
                            Iterator groups = rosterEntry.getGroups();
                            while (groups.hasNext()) {
                                RosterGroup g = (RosterGroup)groups.next();
                                groupSet.add(g.getName());
                            }

                            for (ContactGroup group : new ArrayList<ContactGroup>(getContactGroups())) {
                                if (group.getContactItemByJID(jid) != null && group != unfiledGroup && group != offlineGroup) {
                                    if (!groupSet.contains(group.getGroupName())) {
                                        removeContactGroup(group);
                                    }
                                }

                            }
                        }


                        if (!isUnfiled) {
                            return;
                        }

                        ContactItem unfiledItem = unfiledGroup.getContactItemByJID(jid);
                        if (unfiledItem != null) {

                        }
                        else {
                            ContactItem offlineItem = offlineGroup.getContactItemByJID(jid);
                            if (offlineItem != null) {
                                if ((rosterEntry.getType() == RosterPacket.ItemType.NONE || rosterEntry.getType() == RosterPacket.ItemType.FROM)
                                    && RosterPacket.ItemStatus.SUBSCRIPTION_PENDING == rosterEntry.getStatus()) {
                                    // Remove from offlineItem and add to unfiledItem.
                                    offlineGroup.removeContactItem(offlineItem);
                                    unfiledGroup.addContactItem(offlineItem);
                                    unfiledGroup.fireContactGroupUpdated();
                                    unfiledGroup.setVisible(true);
                                }
                            }
                        }
                    }
                }
            }
        });

    }

    /**
     * Called when users are removed from the roster.
     *
     * @param addresses the addresses removed from the roster.
     */
    public void entriesDeleted(final Collection addresses) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Iterator jids = addresses.iterator();
                while (jids.hasNext()) {
                    String jid = (String)jids.next();
                    removeContactItem(jid);
                }
            }
        });

    }

    public void presenceChanged(final String user) {

    }

    /**
     * Retrieve the ContactItem by it's jid.
     *
     * @param jid the JID of the user.
     * @return the "first" contact item found.
     */
    public ContactItem getContactItemByJID(String jid) {
        Iterator contactGroups = getContactGroups().iterator();
        while (contactGroups.hasNext()) {
            ContactGroup contactGroup = (ContactGroup)contactGroups.next();
            ContactItem item = contactGroup.getContactItemByJID(StringUtils.parseBareAddress(jid));
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    /**
     * Retrieve the ContactItem by their nickname.
     *
     * @param nickname the users nickname in the contact list.
     * @return the "first" contact item found.
     */
    public ContactItem getContactItemByNickname(String nickname) {
        Iterator contactGroups = getContactGroups().iterator();
        while (contactGroups.hasNext()) {
            ContactGroup contactGroup = (ContactGroup)contactGroups.next();
            ContactItem item = contactGroup.getContactItemByNickname(nickname);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    private void addContactGroup(ContactGroup group) {
        groupList.add(group);

        try {
            mainPanel.add(group, groupList.size() - 1);

        }
        catch (Exception e) {
            System.out.println("Unable to add Contact Group" + group.getGroupName());
            Log.error(e);
        }

        group.addContactGroupListener(this);


        fireContactGroupAdded(group);

        // Check state

        String prop = props.getProperty(group.getGroupName());
        if (prop != null) {
            boolean isCollapsed = Boolean.valueOf(prop).booleanValue();
            group.setCollapsed(isCollapsed);
        }
    }


    private ContactGroup addContactGroup(String groupName) {
        StringTokenizer tkn = new StringTokenizer(groupName, "::");

        ContactGroup rootGroup = null;
        ContactGroup lastGroup = null;
        StringBuffer buf = new StringBuffer();

        boolean groupAdded = false;
        while (tkn.hasMoreTokens()) {
            String group = tkn.nextToken();
            buf.append(group);
            if (tkn.hasMoreTokens()) {
                buf.append("::");
            }

            String name = buf.toString();
            if (name.endsWith("::")) {
                name = name.substring(0, name.length() - 2);
            }

            ContactGroup newContactGroup = getContactGroup(name);


            if (newContactGroup == null) {
                newContactGroup = new ContactGroup(group);

                String realGroupName = buf.toString();
                if (realGroupName.endsWith("::")) {
                    realGroupName = realGroupName.substring(0, realGroupName.length() - 2);
                }

                newContactGroup.setGroupName(realGroupName);
            }
            else {
                if (newContactGroup != offlineGroup && newContactGroup != unfiledGroup) {
                    rootGroup = newContactGroup;
                    continue;
                }
            }


            if (lastGroup != null) {
                lastGroup.addContactGroup(newContactGroup);
                groupList.add(newContactGroup);
            }
            else if (rootGroup != null) {
                rootGroup.addContactGroup(newContactGroup);
                groupList.add(newContactGroup);
            }
            else {
                rootGroup = newContactGroup;
            }

            lastGroup = newContactGroup;


            newContactGroup.addContactGroupListener(this);

            if (sharedGroups != null) {
                boolean isSharedGroup = sharedGroups.contains(newContactGroup.getGroupName());
                newContactGroup.setSharedGroup(isSharedGroup);
            }

            fireContactGroupAdded(newContactGroup);

            // Check state

            String prop = props.getProperty(newContactGroup.getGroupName());
            if (prop != null) {
                boolean isCollapsed = Boolean.valueOf(prop).booleanValue();
                newContactGroup.setCollapsed(isCollapsed);
            }


            groupAdded = true;
        }

        if (!groupAdded) {
            return getContactGroup(groupName);
        }


        final List tempList = new ArrayList();
        final Component[] comps = mainPanel.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component c = comps[i];
            if (comps[i] instanceof ContactGroup && c != offlineGroup) {
                tempList.add(c);
            }
        }
        tempList.add(rootGroup);


        groupList.add(rootGroup);

        Collections.sort(tempList, groupComparator);

        int loc = tempList.indexOf(rootGroup);


        try {
            if (rootGroup == offlineGroup) {
                mainPanel.add(rootGroup, tempList.size() - 1);
            }
            else {
                mainPanel.add(rootGroup, loc);
            }
        }
        catch (Exception e) {
            System.out.println("Unable to add Contact Group" + rootGroup.getGroupName());
            Log.error(e);
        }

        return getContactGroup(groupName);

    }

    private void removeContactGroup(ContactGroup contactGroup) {
        contactGroup.removeContactGroupListener(this);
        groupList.remove(contactGroup);
        mainPanel.remove(contactGroup);

        ContactGroup parent = getParentGroup(contactGroup.getGroupName());
        if (parent != null) {
            parent.removeContactGroup(contactGroup);
        }

        treeScroller.validate();
        mainPanel.invalidate();
        mainPanel.repaint();

        fireContactGroupRemoved(contactGroup);
    }


    public ContactGroup getContactGroup(String groupName) {
        ContactGroup grp = null;

        for (ContactGroup contactGroup : groupList) {
            if (contactGroup.getGroupName().equals(groupName)) {
                grp = contactGroup;
                break;
            }
            else {
                grp = getSubContactGroup(contactGroup, groupName);
                if (grp != null) {
                    break;
                }
            }
        }

        return grp;
    }

    public ContactGroup getParentGroup(String groupName) {
        // Check if there is even a parent group
        if (!groupName.contains("::")) {
            return null;
        }

        final ContactGroup group = getContactGroup(groupName);
        if (group == null) {
            return null;
        }

        // Otherwise, find parent
        int index = groupName.lastIndexOf("::");
        String parentGroupName = groupName.substring(0, index);
        return getContactGroup(parentGroupName);
    }

    private ContactGroup getSubContactGroup(ContactGroup group, String groupName) {
        final Iterator contactGroups = group.getContactGroups().iterator();
        ContactGroup grp = null;

        while (contactGroups.hasNext()) {
            ContactGroup contactGroup = (ContactGroup)contactGroups.next();
            if (contactGroup.getGroupName().equals(groupName)) {
                grp = contactGroup;
                break;
            }
            else if (contactGroup.getContactGroups().size() > 0) {
                grp = getSubContactGroup(contactGroup, groupName);
                if (grp != null) {
                    break;
                }
            }

        }
        return grp;
    }

    public void toggleGroupVisibility(String groupName, boolean visible) {
        StringTokenizer tkn = new StringTokenizer(groupName, "::");
        while (tkn.hasMoreTokens()) {
            String group = tkn.nextToken();
            ContactGroup contactGroup = getContactGroup(group);
            if (contactGroup != null) {
                contactGroup.setVisible(visible);
            }
        }

        ContactGroup group = getContactGroup(groupName);
        if (group != null) {
            group.setVisible(true);
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == addingGroupButton) {
            new RosterDialog().showRosterDialog();
        }
        else if (e.getSource() == chatMenu) {
            if (activeItem != null) {
                activateChat(activeItem.getContactJID(), activeItem.getNickname());
            }
        }
        else if (e.getSource() == addContactMenu) {
            RosterDialog rosterDialog = new RosterDialog();
            if (activeGroup != null) {
                rosterDialog.setDefaultGroup(activeGroup);
            }
            rosterDialog.showRosterDialog();
        }
        else if (e.getSource() == removeContactFromGroupMenu) {
            if (activeItem != null) {
                removeContactFromGroup(activeItem);
            }
        }
        else if (e.getSource() == renameMenu) {
            if (activeItem == null) {
                return;
            }

            String oldNickname = activeItem.getNickname();
            String newNickname = JOptionPane.showInputDialog(this, "Rename to:", oldNickname);
            if (ModelUtil.hasLength(newNickname)) {
                String address = activeItem.getFullJID();
                ContactGroup contactGroup = getContactGroup(activeItem.getGroupName());
                ContactItem contactItem = contactGroup.getContactItemByNickname(activeItem.getNickname());
                contactItem.setNickname(newNickname);

                final Roster roster = SparkManager.getConnection().getRoster();
                RosterEntry entry = roster.getEntry(address);
                entry.setName(newNickname);


                final Iterator contactGroups = groupList.iterator();
                String user = StringUtils.parseBareAddress(address);
                while (contactGroups.hasNext()) {
                    ContactGroup cg = (ContactGroup)contactGroups.next();
                    ContactItem ci = cg.getContactItemByJID(user);
                    if (ci != null) {
                        ci.setNickname(newNickname);
                    }
                }

            }
        }
    }


    /**
     * Removes a contact item from the group.
     *
     * @param item the ContactItem to remove.
     */
    private void removeContactFromGroup(ContactItem item) {
        String groupName = item.getGroupName();
        ContactGroup contactGroup = getContactGroup(groupName);
        Roster roster = SparkManager.getConnection().getRoster();
        RosterEntry entry = roster.getEntry(item.getFullJID());
        if (entry != null && contactGroup != offlineGroup) {
            try {
                RosterGroup rosterGroup = roster.getGroup(groupName);
                if (rosterGroup != null) {
                    RosterEntry rosterEntry = rosterGroup.getEntry(entry.getUser());
                    if (rosterEntry != null) {
                        rosterGroup.removeEntry(rosterEntry);
                    }
                }
                contactGroup.removeContactItem(contactGroup.getContactItemByJID(item.getFullJID()));
                checkGroup(contactGroup);
            }
            catch (Exception e) {
                Log.error("Error removing user from contact list.", e);
            }
        }
    }

    private void removeContactFromRoster(ContactItem item) {
        Roster roster = SparkManager.getConnection().getRoster();
        RosterEntry entry = roster.getEntry(item.getFullJID());
        if (entry != null) {
            try {
                roster.removeEntry(entry);
            }
            catch (XMPPException e) {
                Log.warning("Unable to remove roster entry.", e);
            }
        }
    }

    private void removeContactItem(String jid) {
        Iterator groups = new ArrayList(getContactGroups()).iterator();
        while (groups.hasNext()) {
            ContactGroup group = (ContactGroup)groups.next();
            ContactItem item = group.getContactItemByJID(jid);
            if (item != null) {
                group.removeContactItem(item);
                checkGroup(group);
            }
        }
    }

    /**
     * Activate a chat room with the selected user.
     */
    private void activateChat(final String userJID, final String nickname) {
        if (!ModelUtil.hasLength(userJID)) {
            return;
        }

        SwingWorker worker = new SwingWorker() {
            final ChatManager chatManager = SparkManager.getChatManager();
            ChatRoom chatRoom;

            public Object construct() {
                try {
                    Thread.sleep(10);
                }
                catch (InterruptedException e) {
                    Log.error("Error in activate chat.", e);
                }

                ChatContainer chatRooms = chatManager.getChatContainer();

                try {
                    chatRoom = chatRooms.getChatRoom(userJID);
                }
                catch (ChatRoomNotFoundException e) {

                }
                return chatRoom;
            }

            public void finished() {
                if (chatRoom == null) {
                    chatRoom = new ChatRoomImpl(userJID, nickname, nickname);
                    chatManager.getChatContainer().addChatRoom(chatRoom);
                }
                chatManager.getChatContainer().activateChatRoom(chatRoom);
            }
        };

        worker.start();

    }


    public void contactItemClicked(ContactItem item) {
        activeItem = item;

        clearSelectionList(item);
    }

    public void contactItemDoubleClicked(ContactItem item) {
        activeItem = item;

        ChatManager chatManager = SparkManager.getChatManager();
        boolean handled = chatManager.fireContactItemDoubleClicked(item);

        if (!handled) {
            activateChat(item.getContactJID(), item.getNickname());
        }

        clearSelectionList(item);
    }

    public void contactGroupPopup(MouseEvent e, final ContactGroup group) {
        // Do nothing with offline group
        if (group == offlineGroup || group == unfiledGroup) {
            return;
        }


        final JPopupMenu popup = new JPopupMenu();
        popup.add(addContactMenu);
        popup.add(addContactGroupMenu);
        popup.addSeparator();

        fireContextMenuListenerPopup(popup, group);

        JMenuItem delete = new JMenuItem("Delete");
        JMenuItem rename = new JMenuItem("Rename");
        if (!group.isSharedGroup()) {
            popup.addSeparator();
            popup.add(delete);
            popup.add(rename);
        }

        delete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int ok = JOptionPane.showConfirmDialog(group, "Are you sure you want to delete \"" + group.getGroupName() + "\"", "Delete Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (ok == JOptionPane.YES_OPTION) {
                    String groupName = group.getGroupName();
                    Roster roster = SparkManager.getConnection().getRoster();

                    RosterGroup rosterGroup = roster.getGroup(groupName);
                    if (rosterGroup != null) {
                        Iterator entries = rosterGroup.getEntries();
                        while (entries.hasNext()) {
                            RosterEntry entry = (RosterEntry)entries.next();
                            try {
                                rosterGroup.removeEntry(entry);
                            }
                            catch (XMPPException e1) {
                                Log.error("Error removing entry", e1);
                            }
                        }
                    }

                    // Remove from UI
                    removeContactGroup(group);
                    invalidate();
                    repaint();
                }

            }
        });


        rename.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String newName = JOptionPane.showInputDialog(group, "Rename Roster Group", "Rename to:", JOptionPane.QUESTION_MESSAGE);
                if (!ModelUtil.hasLength(newName)) {
                    return;
                }
                String groupName = group.getGroupName();
                Roster roster = SparkManager.getConnection().getRoster();

                RosterGroup rosterGroup = roster.getGroup(groupName);
                if (rosterGroup != null) {
                    removeContactGroup(group);
                    rosterGroup.setName(newName);
                }

            }
        });

        // popup.add(inviteFirstAcceptor);

        popup.show(group, e.getX(), e.getY());
        activeGroup = group;
    }

    /**
     * Shows popup for right-clicking of ContactItem.
     *
     * @param e    the MouseEvent
     * @param item the ContactItem
     */
    public void showPopup(MouseEvent e, final ContactItem item) {
        if (item.getFullJID() == null) {
            return;
        }

        activeItem = item;

        final JPopupMenu popup = new JPopupMenu();

        // Add Start Chat Menu
        popup.add(chatMenu);

        // Add Send File Action
        Action sendAction = new AbstractAction() {
            public void actionPerformed(ActionEvent actionEvent) {
                SparkManager.getTransferManager().sendFileTo(item);
            }
        };

        sendAction.putValue(Action.SMALL_ICON, SparkRes.getImageIcon(SparkRes.DOCUMENT_16x16));
        sendAction.putValue(Action.NAME, "Send a File");

        if (item.getPresence() != null) {
            popup.add(sendAction);
        }

        popup.addSeparator();


        String groupName = item.getGroupName();
        ContactGroup contactGroup = getContactGroup(groupName);

        // Only show "Remove Contact From Group" if the user belongs to more than one group.
        if (!contactGroup.isSharedGroup() && !contactGroup.isOfflineGroup() && contactGroup != unfiledGroup) {
            Roster roster = SparkManager.getConnection().getRoster();
            RosterEntry entry = roster.getEntry(item.getFullJID());
            if (entry != null) {
                int groupCount = 0;
                Iterator groups = entry.getGroups();
                while (groups.hasNext()) {
                    groups.next();
                    groupCount++;
                }

                if (groupCount > 1) {
                    popup.add(removeContactFromGroupMenu);
                }

            }
        }

        // Define remove entry action
        Action removeAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                removeContactFromRoster(item);
            }
        };

        removeAction.putValue(Action.NAME, "Remove from Roster");
        removeAction.putValue(Action.SMALL_ICON, SparkRes.getImageIcon(SparkRes.SMALL_CIRCLE_DELETE));

        // Check if user is in shared group.
        boolean isInSharedGroup = false;
        Iterator contactGroups = new ArrayList(getContactGroups()).iterator();
        while (contactGroups.hasNext()) {
            ContactGroup cGroup = (ContactGroup)contactGroups.next();
            if (cGroup.isSharedGroup()) {
                ContactItem it = cGroup.getContactItemByJID(item.getFullJID());
                if (it != null) {
                    isInSharedGroup = true;
                }
            }
        }


        if (!contactGroup.isSharedGroup() && !isInSharedGroup) {
            popup.add(removeAction);
        }

        popup.add(renameMenu);


        Action viewProfile = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                VCardManager vcardSupport = SparkManager.getVCardManager();
                String jid = item.getFullJID();
                vcardSupport.viewProfile(jid, SparkManager.getWorkspace());
            }
        };
        viewProfile.putValue(Action.SMALL_ICON, SparkRes.getImageIcon(SparkRes.SMALL_PROFILE_IMAGE));
        viewProfile.putValue(Action.NAME, "View Profile");

        popup.add(viewProfile);


        popup.addSeparator();

        Action viewPresenceAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                JEditorPane pane = new JEditorPane();
                StringBuffer buf = new StringBuffer();
                Collection col = item.getPresenceHistory();
                Iterator iter = col.iterator();
                while (iter.hasNext()) {
                    String history = (String)iter.next();
                    buf.append(history + "\n");
                }
                pane.setText(buf.toString());
                MessageDialog.showComponent("Presence History", "History of user activity while online.",
                    SparkRes.getImageIcon(SparkRes.INFORMATION_IMAGE), new JScrollPane(pane),
                    getGUI(), 400, 400, false);

            }
        };

        /*
        viewPresenceAction.putValue(Action.NAME, "View Presence History");
        viewPresenceAction.putValue(Action.SMALL_ICON, LaRes.getImageIcon(LaRes.VIEW_IMAGE));
        popup.add(viewPresenceAction);
        */

        Action lastActivityAction = new AbstractAction() {
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    LastActivity activity = LastActivity.getLastActivity(SparkManager.getConnection(), item.getFullJID());
                    long idleTime = (activity.getIdleTime() * 1000);
                    String time = ModelUtil.getTimeFromLong(idleTime);
                    JOptionPane.showMessageDialog(getGUI(), "Idle for " + time, "Last Activity", JOptionPane.INFORMATION_MESSAGE);
                }
                catch (XMPPException e1) {
                }

            }
        };

        lastActivityAction.putValue(Action.NAME, "View Last Activity");
        lastActivityAction.putValue(Action.SMALL_ICON, SparkRes.getImageIcon(SparkRes.SMALL_USER1_STOPWATCH));

        if (contactGroup == offlineGroup) {
            popup.add(lastActivityAction);
        }

        Action subscribeAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                String jid = item.getFullJID();
                Presence response = new Presence(Presence.Type.SUBSCRIBE);
                response.setTo(jid);

                SparkManager.getConnection().sendPacket(response);
            }
        };

        subscribeAction.putValue(Action.SMALL_ICON, SparkRes.getImageIcon(SparkRes.SMALL_USER1_INFORMATION));
        subscribeAction.putValue(Action.NAME, "Subscribe To");

        Roster roster = SparkManager.getConnection().getRoster();
        RosterEntry entry = roster.getEntry(item.getFullJID());
        if (entry != null && entry.getType() == RosterPacket.ItemType.FROM) {
            popup.add(subscribeAction);
        }

        // Fire Context Menu Listener
        fireContextMenuListenerPopup(popup, item);

        ContactGroup group = getContactGroup(item.getGroupName());
        popup.show(group.getList(), e.getX(), e.getY());
    }

    public void showPopup(MouseEvent e, final Collection items) {
        ContactGroup group = null;
        Iterator contactItems = items.iterator();
        while (contactItems.hasNext()) {
            ContactItem item = (ContactItem)contactItems.next();
            group = getContactGroup(item.getGroupName());
            break;
        }


        final JPopupMenu popup = new JPopupMenu();
        final JMenuItem sendMessagesMenu = new JMenuItem("Send a Message...", SparkRes.getImageIcon(SparkRes.SMALL_MESSAGE_IMAGE));


        fireContextMenuListenerPopup(popup, items);

        popup.add(sendMessagesMenu);

        sendMessagesMenu.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendMessages(items);
            }
        });

        popup.show(group.getList(), e.getX(), e.getY());
    }

    private void clearSelectionList(ContactItem item) {
        // Check for null. In certain cases the event triggering the model might
        // not find the selected object.
        if (item == null) {
            return;
        }
        final ContactGroup owner = getContactGroup(item.getGroupName());
        Iterator groups = new ArrayList(groupList).iterator();
        while (groups.hasNext()) {
            ContactGroup contactGroup = (ContactGroup)groups.next();
            if (owner != contactGroup) {
                contactGroup.clearSelection();
            }
        }
    }


    private void sendMessages(Collection items) {
        InputDialog dialog = new InputDialog();
        final String messageText = dialog.getInput("Broadcast Message", "Enter message to broadcast to selected users.", SparkRes.getImageIcon(SparkRes.BLANK_IMAGE), SparkManager.getMainWindow());
        if (ModelUtil.hasLength(messageText)) {
            Iterator contacts = items.iterator();
            while (contacts.hasNext()) {
                ContactItem item = (ContactItem)contacts.next();
                final ContactGroup contactGroup = getContactGroup(item.getGroupName());
                contactGroup.clearSelection();
                if (item.isAvailable()) {
                    Message mess = new Message();
                    mess.setTo(item.getFullJID());
                    mess.setBody(messageText);

                    SparkManager.getConnection().sendPacket(mess);
                }
            }
        }

    }

    // For plugin use only

    public void initialize() {
        setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        // Add Contact List
        addContactListToWorkspace();

        // Hide top toolbar
        SparkManager.getMainWindow().getTopToolBar().setVisible(false);

        SwingWorker worker = new SwingWorker() {
            public Object construct() {

                // Retrieve shared group list.
                try {
                    sharedGroups = SharedGroupManager.getSharedGroups(SparkManager.getConnection());
                }
                catch (XMPPException e) {
                    Log.error("Unable to contact shared group info.", e);
                }

                return "ok";
            }

            public void finished() {
                // Build the initial contact list.
                buildContactList();

                boolean show = localPreferences.isEmptyGroupsShown();

                // Hide all groups initially
                showEmptyGroups(show);

                // Add a subscription listener.
                addSubscriptionListener();

                // Load all plugins
                SparkManager.getWorkspace().loadPlugins();
            }
        };

        worker.start();


    }

    public void addSubscriptionListener() {
        // Add subscription listener
        PacketFilter packetFilter = new PacketTypeFilter(Presence.class);
        PacketListener subscribeListener = new PacketListener() {
            public void processPacket(Packet packet) {
                final Presence presence = (Presence)packet;
                if (presence != null && presence.getType() == Presence.Type.SUBSCRIBE) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            subscriptionRequest(presence.getFrom());
                        }
                    });
                }
                else if (presence != null && presence.getType() == Presence.Type.UNSUBSCRIBE) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            Roster roster = SparkManager.getConnection().getRoster();
                            RosterEntry entry = roster.getEntry(presence.getFrom());
                            if (entry != null) {
                                try {
                                    removeContactItem(presence.getFrom());
                                    roster.removeEntry(entry);
                                }
                                catch (XMPPException e) {
                                    Presence unsub = new Presence(Presence.Type.UNSUBSCRIBED);
                                    unsub.setTo(presence.getFrom());
                                    SparkManager.getConnection().sendPacket(unsub);
                                    Log.error(e);
                                }
                            }
                        }
                    });


                }
                else if (presence != null && presence.getType() == Presence.Type.SUBSCRIBED) {
                    // Find Contact in Contact List
                    String jid = StringUtils.parseBareAddress(presence.getFrom());
                    ContactItem item = getContactItemByJID(jid);

                    // If item is not in the Contact List, add them.
                    if (item == null) {
                        final Roster roster = SparkManager.getConnection().getRoster();
                        RosterEntry entry = roster.getEntry(jid);
                        if (entry != null) {
                            String nickname = entry.getName();
                            if (nickname == null) {
                                nickname = entry.getUser();
                            }
                            item = new ContactItem(nickname, jid);
                            offlineGroup.addContactItem(item);
                            offlineGroup.fireContactGroupUpdated();
                        }
                    }
                }
                else if (presence != null && presence.getType() == Presence.Type.UNSUBSCRIBED) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            Roster roster = SparkManager.getConnection().getRoster();
                            RosterEntry entry = roster.getEntry(presence.getFrom());
                            if (entry != null) {
                                try {
                                    removeContactItem(presence.getFrom());
                                    roster.removeEntry(entry);
                                }
                                catch (XMPPException e) {
                                    Log.error(e);
                                }
                            }
                            String jid = StringUtils.parseBareAddress(presence.getFrom());
                            removeContactItem(jid);
                        }
                    });
                }
                else {
                    initialPresences.add(presence);

                    int numberOfMillisecondsInTheFuture = 500;

                    presenceTimer.schedule(new TimerTask() {
                        public void run() {
                            SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    final Iterator users = new ArrayList(initialPresences).iterator();
                                    while (users.hasNext()) {
                                        Presence userToUpdate = (Presence)users.next();
                                        initialPresences.remove(userToUpdate);
                                        updateUserPresence(userToUpdate);

                                    }
                                }
                            });
                        }
                    }, numberOfMillisecondsInTheFuture);
                }
            }
        };

        SparkManager.getConnection().addPacketListener(subscribeListener, packetFilter);
    }


    public void shutdown() {
        saveState();
    }

    public boolean canShutDown() {
        return true;
    }

    private void addContactListToWorkspace() {
        Workspace workspace = SparkManager.getWorkspace();
        workspace.getWorkspacePane().addTab("Contacts", SparkRes.getImageIcon(SparkRes.SMALL_ALL_CHATS_IMAGE), this); //NOTRANS

        // Add To Contacts Menu
        final JMenu contactsMenu = SparkManager.getMainWindow().getMenuByName("Contacts");
        JMenuItem addContactsMenu = new JMenuItem("", SparkRes.getImageIcon(SparkRes.USER1_ADD_16x16));
        ResourceUtils.resButton(addContactsMenu, "&Add Contact");
        ResourceUtils.resButton(addContactGroupMenu, "Add Contact &Group");

        contactsMenu.add(addContactsMenu);
        contactsMenu.add(addContactGroupMenu);
        addContactsMenu.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new RosterDialog().showRosterDialog();
            }
        });

        addContactGroupMenu.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                String groupName = JOptionPane.showInputDialog(getGUI(), "Name of Group:", "Add New Group", JOptionPane.QUESTION_MESSAGE);
                if (ModelUtil.hasLength(groupName)) {
                    ContactGroup contactGroup = getContactGroup(groupName);
                    if (contactGroup == null) {
                        contactGroup = addContactGroup(groupName);
                    }
                }
            }
        });

        // Add Toggle Contacts Menu
        ResourceUtils.resButton(showHideMenu, "&Show Empty Groups");
        contactsMenu.add(showHideMenu);

        showHideMenu.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showEmptyGroups(showHideMenu.isSelected());
            }
        });

        // Initialize vcard support
        SparkManager.getVCardManager();
    }

    private void showEmptyGroups(boolean show) {
        Iterator contactGroups = getContactGroups().iterator();
        while (contactGroups.hasNext()) {
            ContactGroup group = (ContactGroup)contactGroups.next();
            if (show) {
                group.setVisible(true);
            }
            else {
                // Never hide offline group.
                if (group != offlineGroup) {
                    group.setVisible(group.hasAvailableContacts());
                }
            }
        }

        localPreferences.setEmptyGroupsShown(show);
        showHideMenu.setSelected(show);
    }

    /**
     * Sorts ContactGroups
     */
    final Comparator groupComparator = new Comparator() {
        public int compare(Object contactGroupOne, Object contactGroup2) {
            final ContactGroup group1 = (ContactGroup)contactGroupOne;
            final ContactGroup group2 = (ContactGroup)contactGroup2;
            return group1.getGroupName().toLowerCase().compareTo(group2.getGroupName().toLowerCase());

        }
    };

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public List<ContactGroup> getContactGroups() {
        return groupList;
    }

    private void subscriptionRequest(final String jid) {
        final Roster roster = SparkManager.getConnection().getRoster();
        RosterEntry entry = roster.getEntry(jid);
        if (entry != null && entry.getType() == RosterPacket.ItemType.TO) {
            Presence response = new Presence(Presence.Type.SUBSCRIBED);
            response.setTo(jid);

            SparkManager.getConnection().sendPacket(response);
            return;
        }


        String message = jid + " would like to see your online presence and add you to their roster. Do you accept?";

        final JFrame dialog = new JFrame("Subscription Request");
        dialog.setIconImage(SparkManager.getMainWindow().getIconImage());
        JPanel layoutPanel = new JPanel();
        layoutPanel.setLayout(new GridBagLayout());

        WrappedLabel messageLabel = new WrappedLabel();
        messageLabel.setText(message);
        layoutPanel.add(messageLabel, new GridBagConstraints(0, 0, 5, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));

        JButton acceptButton = new JButton("Accept");
        JButton viewInfoButton = new JButton("Profile");
        JButton denyButton = new JButton("Deny");
        layoutPanel.add(acceptButton, new GridBagConstraints(2, 1, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));
        layoutPanel.add(viewInfoButton, new GridBagConstraints(3, 1, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));
        layoutPanel.add(denyButton, new GridBagConstraints(4, 1, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));

        acceptButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();

                Presence response = new Presence(Presence.Type.SUBSCRIBED);
                response.setTo(jid);

                SparkManager.getConnection().sendPacket(response);

                int ok = JOptionPane.showConfirmDialog(getGUI(), "Would you like to add the user to your roster?", "Add To Roster", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (ok == JOptionPane.OK_OPTION) {
                    RosterDialog rosterDialog = new RosterDialog();
                    rosterDialog.setDefaultJID(jid);
                    rosterDialog.showRosterDialog();
                }
            }
        });

        denyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Send subscribed
                Presence response = new Presence(Presence.Type.UNSUBSCRIBE);
                response.setTo(jid);
                SparkManager.getConnection().sendPacket(response);
                dialog.dispose();
            }
        });

        viewInfoButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                SparkManager.getVCardManager().viewProfile(jid, getGUI());
            }
        });

        // show dialog
        dialog.getContentPane().add(layoutPanel);
        dialog.pack();
        dialog.setSize(450, 125);
        dialog.setLocationRelativeTo(SparkManager.getMainWindow());
        SparkManager.getChatManager().getChatContainer().blinkFrameIfNecessary(dialog);

    }

    private void searchContacts(String contact) {
        final JTextField contactField = new JTextField();

        final Map contactMap = new HashMap();
        final Set contacts = new HashSet();

        Iterator groups = getContactGroups().iterator();
        while (groups.hasNext()) {
            ContactGroup group = (ContactGroup)groups.next();
            Iterator contactItems = group.getContactItems().iterator();
            while (contactItems.hasNext()) {
                ContactItem item = (ContactItem)contactItems.next();
                contacts.add(item.getNickname());
                contactMap.put(item.getNickname(), item);
            }
        }

        //ListDataIntelliHints hints = new ListDataIntelliHints(contactField, new ArrayList(contacts));
        //   hints.setCaseSensitive(false);

        final JDialog frame = new JDialog(SparkManager.getMainWindow(), "Find Contacts", false);

        JPanel layoutPanel = new JPanel();
        layoutPanel.setLayout(new GridBagLayout());
        frame.getContentPane().setLayout(new BorderLayout());
        JLabel enterLabel = new JLabel("Contact To Find?");
        enterLabel.setFont(new Font("Verdana", Font.BOLD, 10));
        layoutPanel.add(enterLabel, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 0, 5), 0, 0));
        layoutPanel.add(contactField, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 200, 0));
        layoutPanel.setBorder(BorderFactory.createBevelBorder(0));
        frame.getContentPane().add(layoutPanel);

        frame.pack();
        frame.setLocationRelativeTo(this);
        frame.setVisible(true);

        frame.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent keyEvent) {
                if (keyEvent.getKeyChar() == KeyEvent.VK_ESCAPE) {
                    frame.dispose();
                }
            }
        });

        contactField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent keyEvent) {
                if (keyEvent.getKeyChar() == KeyEvent.VK_ENTER) {
                    if (ModelUtil.hasLength(contactField.getText())) {
                        ContactItem item = (ContactItem)contactMap.get(contactField.getText());
                        if (item != null) {
                            activateChat(item.getFullJID(), item.getNickname());
                            frame.dispose();
                        }
                    }

                }
                else if (keyEvent.getKeyChar() == KeyEvent.VK_ESCAPE) {
                    frame.dispose();
                }
            }
        });
        contactField.setText(contact);
    }

    public void addContextMenuListener(ContextMenuListener listener) {
        contextListeners.add(listener);
    }

    public void removeContextMenuListener(ContextMenuListener listener) {
        contextListeners.remove(listener);
    }

    public void fireContextMenuListenerPopup(JPopupMenu popup, Object object) {
        Iterator listeners = new ArrayList(contextListeners).iterator();
        while (listeners.hasNext()) {
            ContextMenuListener listener = (ContextMenuListener)listeners.next();
            listener.poppingUp(object, popup);
        }
    }

    public JComponent getGUI() {
        return this;
    }

    public ContactGroup getActiveGroup() {
        return activeGroup;
    }

    public Collection getSelectedUsers() {
        final List list = new ArrayList();

        Iterator contactGroups = getContactGroups().iterator();
        while (contactGroups.hasNext()) {
            ContactGroup group = (ContactGroup)contactGroups.next();
            List items = group.getSelectedContacts();
            Iterator itemIterator = items.iterator();
            while (itemIterator.hasNext()) {
                ContactItem item = (ContactItem)itemIterator.next();
                list.add(item);
            }
        }
        return list;
    }

    private void checkGroup(ContactGroup group) {
        if (!group.hasAvailableContacts() && group != offlineGroup && group != unfiledGroup && !showHideMenu.isSelected()) {
            group.setVisible(false);
        }
    }

    public void addFileDropListener(FileDropListener listener) {
        dndListeners.add(listener);
    }

    public void removeFileDropListener(FileDropListener listener) {
        dndListeners.remove(listener);
    }

    public void fireFilesDropped(Collection files, ContactItem item) {
        final Iterator listeners = new ArrayList(dndListeners).iterator();
        while (listeners.hasNext()) {
            ((FileDropListener)listeners.next()).filesDropped(files, item);
        }
    }

    public void contactItemAdded(ContactItem item) {
        fireContactItemAdded(item);
    }

    public void contactItemRemoved(ContactItem item) {
        fireContactItemRemoved(item);
    }

    /*
        Adding ContactListListener support.
    */

    public void addContactListListener(ContactListListener listener) {
        contactListListeners.add(listener);
    }

    public void removeContactListListener(ContactListListener listener) {
        contactListListeners.remove(listener);
    }

    public void fireContactItemAdded(ContactItem item) {
        final Iterator listeners = new ArrayList(contactListListeners).iterator();
        while (listeners.hasNext()) {
            ((ContactListListener)listeners.next()).contactItemAdded(item);
        }
    }

    public void fireContactItemRemoved(ContactItem item) {
        final Iterator listeners = new ArrayList(contactListListeners).iterator();
        while (listeners.hasNext()) {
            ((ContactListListener)listeners.next()).contactItemRemoved(item);
        }
    }

    public void fireContactGroupAdded(ContactGroup group) {
        final Iterator listeners = new ArrayList(contactListListeners).iterator();
        while (listeners.hasNext()) {
            ((ContactListListener)listeners.next()).contactGroupAdded(group);
        }
    }

    public void fireContactGroupRemoved(ContactGroup group) {
        final Iterator listeners = new ArrayList(contactListListeners).iterator();
        while (listeners.hasNext()) {
            ((ContactListListener)listeners.next()).contactGroupRemoved(group);
        }
    }

    public void uninstall() {
        // Do nothing.
    }

    private void saveState() {
        if (props == null) {
            return;
        }
        final Iterator contactGroups = getContactGroups().iterator();
        while (contactGroups.hasNext()) {
            ContactGroup group = (ContactGroup)contactGroups.next();
            props.put(group.getGroupName(), Boolean.toString(group.isCollapsed()));
        }

        try {
            props.store(new FileOutputStream(propertiesFile), "Tracks the state of groups.");
        }
        catch (IOException e) {
            Log.error("Unable to save group properties.", e);
        }

    }


    public void connectionClosed() {
        // No reason to reconnect.
    }

    private void reconnect(final String message, final boolean conflict) {
        // Show MainWindow
        SparkManager.getMainWindow().setVisible(true);

        // Flash That Window.
        SparkManager.getAlertManager().flashWindowStopOnFocus(SparkManager.getMainWindow());

        if (reconnectListener == null) {
            reconnectListener = new RetryPanel.ReconnectListener() {
                public void reconnected() {
                    clientReconnected();
                }

                public void cancelled() {
                    removeAllUsers();
                }

            };

            retryPanel.addReconnectionListener(reconnectListener);
        }

        workspace.changeCardLayout(RETRY_PANEL);

        retryPanel.setDisconnectReason(message);

        if (false) {
            retryPanel.startTimer();
        }
        else {
            retryPanel.showConflict();
        }


    }

    private void removeAllUsers() {
        // Show reconnect panel
        workspace.changeCardLayout(RETRY_PANEL);

        // Behind the scenes, move everyone to the offline group.
        Iterator contactGroups = new ArrayList(getContactGroups()).iterator();
        while (contactGroups.hasNext()) {
            ContactGroup contactGroup = (ContactGroup)contactGroups.next();
            Iterator contactItems = new ArrayList(contactGroup.getContactItems()).iterator();
            while (contactItems.hasNext()) {
                ContactItem item = (ContactItem)contactItems.next();
                contactGroup.removeContactItem(item);
            }
        }

    }

    public void clientReconnected() {
        XMPPConnection con = SparkManager.getConnection();
        if (con.isConnected()) {
            // Send Available status
            final Presence presence = SparkManager.getWorkspace().getStatusBar().getPresence();
            SparkManager.getSessionManager().changePresence(presence);
            final Roster roster = con.getRoster();

            final Iterator rosterEntries = roster.getEntries();
            while (rosterEntries.hasNext()) {
                RosterEntry entry = (RosterEntry)rosterEntries.next();
                updateUserPresence(roster.getPresence(entry.getUser()));
            }
        }

        workspace.changeCardLayout(Workspace.WORKSPACE_PANE);
    }

    public void connectionClosedOnError(final Exception ex) {
        String errorMessage = "";

        boolean conflictError = false;
        if (ex instanceof XMPPException) {
            XMPPException xmppEx = (XMPPException)ex;
            StreamError error = xmppEx.getStreamError();
            String reason = error.getCode();
            if ("conflict".equals(reason)) {
                errorMessage = "A user with the same account has logged in from another location.";
                conflictError = true;
            }
            else {
                errorMessage = "You have lost your connection to the server.\n\nReason: " + reason;
            }
        }

        reconnect(errorMessage, conflictError);
    }


}

