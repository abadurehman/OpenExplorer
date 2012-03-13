/*
 * Copyright (C) 2006 The Android Open Source Project
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

package org.brandroid.utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import org.brandroid.openmanager.R;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import android.view.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * This class is used to instantiate menu XML files into Menu objects.
 * <p>
 * For performance reasons, menu inflation relies heavily on pre-processing of
 * XML files that is done at build time. Therefore, it is not currently possible
 * to use MenuInflater with an XmlPullParser over a plain XML file at runtime;
 * it only works with an XmlPullParser returned from a compiled resource (R.
 * <em>something</em> file.)
 */
public class MenuInflater {
    private static final String LOG_TAG = "MenuInflater";

    /** Menu tag name in XML. */
    private static final String XML_MENU = "menu";
    
    /** Group tag name in XML. */
    private static final String XML_GROUP = "group";
    
    /** Item tag name in XML. */
    private static final String XML_ITEM = "item";

    private static final int NO_ID = 0;
    
    private static final Class<?>[] ACTION_VIEW_CONSTRUCTOR_SIGNATURE = new Class[] {Context.class};

    private static final Class<?>[] ACTION_PROVIDER_CONSTRUCTOR_SIGNATURE = ACTION_VIEW_CONSTRUCTOR_SIGNATURE;

    private final Object[] mActionViewConstructorArguments;

    private final Object[] mActionProviderConstructorArguments;

    private Context mContext;

    /**
     * Constructs a menu inflater.
     * 
     * @see Activity#getMenuInflater()
     */
    public MenuInflater(Context context) {
        mContext = context;
        mActionViewConstructorArguments = new Object[] {context};
        mActionProviderConstructorArguments = mActionViewConstructorArguments;
    }

    /**
     * Inflate a menu hierarchy from the specified XML resource. Throws
     * {@link InflateException} if there is an error.
     * 
     * @param menuRes Resource ID for an XML layout resource to load (e.g.,
     *            <code>R.menu.main_activity</code>)
     * @param menu The Menu to inflate into. The items and submenus will be
     *            added to this Menu.
     */
    public void inflate(int menuRes, Menu menu) {
        XmlResourceParser parser = null;
        try {
            parser = mContext.getResources().getLayout(menuRes);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            
            parseMenu(parser, attrs, menu);
        } catch (XmlPullParserException e) {
            throw new InflateException("Error inflating menu XML", e);
        } catch (IOException e) {
            throw new InflateException("Error inflating menu XML", e);
        } finally {
            if (parser != null) parser.close();
        }
    }

    /**
     * Called internally to fill the given menu. If a sub menu is seen, it will
     * call this recursively.
     */
    private void parseMenu(XmlPullParser parser, AttributeSet attrs, Menu menu)
            throws XmlPullParserException, IOException {
        MenuState menuState = new MenuState(menu);

        int eventType = parser.getEventType();
        String tagName;
        boolean lookingForEndOfUnknownTag = false;
        String unknownTagName = null;

        // This loop will skip to the menu start tag
        do {
            if (eventType == XmlPullParser.START_TAG) {
                tagName = parser.getName();
                if (tagName.equals(XML_MENU)) {
                    // Go to next tag
                    eventType = parser.next();
                    break;
                }
                
                throw new RuntimeException("Expecting menu, got " + tagName);
            }
            eventType = parser.next();
        } while (eventType != XmlPullParser.END_DOCUMENT);
        
        boolean reachedEndOfMenu = false;
        while (!reachedEndOfMenu) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    if (lookingForEndOfUnknownTag) {
                        break;
                    }
                    
                    tagName = parser.getName();
                    if (tagName.equals(XML_GROUP)) {
                        menuState.readGroup(attrs);
                    } else if (tagName.equals(XML_ITEM)) {
                        menuState.readItem(attrs);
                    } else if (tagName.equals(XML_MENU)) {
                        // A menu start tag denotes a submenu for an item
                        SubMenu subMenu = menuState.addSubMenuItem();

                        // Parse the submenu into returned SubMenu
                        parseMenu(parser, attrs, subMenu);
                    } else {
                        lookingForEndOfUnknownTag = true;
                        unknownTagName = tagName;
                    }
                    break;
                    
                case XmlPullParser.END_TAG:
                    tagName = parser.getName();
                    if (lookingForEndOfUnknownTag && tagName.equals(unknownTagName)) {
                        lookingForEndOfUnknownTag = false;
                        unknownTagName = null;
                    } else if (tagName.equals(XML_GROUP)) {
                        menuState.resetGroup();
                    } else if (tagName.equals(XML_ITEM)) {
                        // Add the item if it hasn't been added (if the item was
                        // a submenu, it would have been added already)
                        if (!menuState.hasAddedItem()) {
                            if (menuState.itemActionProvider != null &&
                                    menuState.itemActionProvider.hasSubMenu()) {
                                menuState.addSubMenuItem();
                            } else {
                                menuState.addItem();
                            }
                        }
                    } else if (tagName.equals(XML_MENU)) {
                        reachedEndOfMenu = true;
                    }
                    break;
                    
                case XmlPullParser.END_DOCUMENT:
                    throw new RuntimeException("Unexpected end of document");
            }
            
            eventType = parser.next();
        }
    }
    
    private static class InflatedOnMenuItemClickListener
            implements MenuItem.OnMenuItemClickListener {
        private static final Class<?>[] PARAM_TYPES = new Class[] { MenuItem.class };
        
        private Context mContext;
        private Method mMethod;
        
        public InflatedOnMenuItemClickListener(Context context, String methodName) {
            mContext = context;
            Class<?> c = context.getClass();
            try {
                mMethod = c.getMethod(methodName, PARAM_TYPES);
            } catch (Exception e) {
                InflateException ex = new InflateException(
                        "Couldn't resolve menu item onClick handler " + methodName +
                        " in class " + c.getName());
                ex.initCause(e);
                throw ex;
            }
        }
        
        public boolean onMenuItemClick(MenuItem item) {
            try {
                if (mMethod.getReturnType() == Boolean.TYPE) {
                    return (Boolean) mMethod.invoke(mContext, item);
                } else {
                    mMethod.invoke(mContext, item);
                    return true;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    /**
     * State for the current menu.
     * <p>
     * Groups can not be nested unless there is another menu (which will have
     * its state class).
     */
    private class MenuState {
        private Menu menu;

        /*
         * Group state is set on items as they are added, allowing an item to
         * override its group state. (As opposed to set on items at the group end tag.)
         */
        private int groupId;
        private int groupCategory;
        private int groupOrder;
        private int groupCheckable;
        private boolean groupVisible;
        private boolean groupEnabled;

        private boolean itemAdded;
        private int itemId;
        private int itemCategoryOrder;
        private CharSequence itemTitle;
        private CharSequence itemTitleCondensed;
        private int itemIconResId;
        private char itemAlphabeticShortcut;
        private char itemNumericShortcut;
        /**
         * Sync to attrs.xml enum:
         * - 0: none
         * - 1: all
         * - 2: exclusive
         */
        private int itemCheckable;
        private boolean itemChecked;
        private boolean itemVisible;
        private boolean itemEnabled;
        
        /**
         * Sync to attrs.xml enum, values in MenuItem:
         * - 0: never
         * - 1: ifRoom
         * - 2: always
         * - -1: Safe sentinel for "no value".
         */
        private int itemShowAsAction;

        private int itemActionViewLayout;
        private String itemActionViewClassName;
        private String itemActionProviderClassName;

        private String itemListenerMethodName;
        
        private ActionProvider itemActionProvider;

        private static final int defaultGroupId = NO_ID;
        private static final int defaultItemId = NO_ID;
        private static final int defaultItemCategory = 0;
        private static final int defaultItemOrder = 0;
        private static final int defaultItemCheckable = 0;
        private static final boolean defaultItemChecked = false;
        private static final boolean defaultItemVisible = true;
        private static final boolean defaultItemEnabled = true;
        
        public MenuState(final Menu menu) {
            this.menu = menu;
            
            resetGroup();
        }
        
        public void resetGroup() {
            groupId = defaultGroupId;
            groupCategory = defaultItemCategory;
            groupOrder = defaultItemOrder;
            groupCheckable = defaultItemCheckable;
            groupVisible = defaultItemVisible;
            groupEnabled = defaultItemEnabled;
        }

        /**
         * Called when the parser is pointing to a group tag.
         */
        public void readGroup(AttributeSet attrs) {
            /*
            TypedArray a = mContext.obtainStyledAttributes(attrs,
                    new int[]{android.R.attr.id,android.R.attr.menuCategory,android.R.attr.orderInCategory,
            		android.R.attr.checkableBehavior,android.R.attr.visible,android.R.attr.enabled});
            
            groupId = a.getResourceId(android.R.attr.id, defaultGroupId);
            groupCategory = a.getInt(android.R.attr.menuCategory, defaultItemCategory);
            groupOrder = a.getInt(android.R.attr.orderInCategory, defaultItemOrder);
            groupCheckable = a.getInt(android.R.attr.checkableBehavior, defaultItemCheckable);
            groupVisible = a.getBoolean(android.R.attr.visible, defaultItemVisible);
            groupEnabled = a.getBoolean(android.R.attr.enabled, defaultItemEnabled);

            a.recycle();
            */
        	groupId = attrs.getAttributeIntValue("android", "groupId", defaultGroupId);
        	groupCategory = attrs.getAttributeIntValue("android", "menuCategory", defaultItemCategory);
        	groupVisible = defaultItemVisible;
        	groupEnabled = defaultItemEnabled;
        }
        
        /**
         * Called when the parser is pointing to an item tag.
         */
        public void readItem(AttributeSet attrs) {
        	itemId = attrs.getAttributeResourceValue("android", "id", defaultItemId);
        	final int category = attrs.getAttributeIntValue("android", "menuCategory", defaultItemCategory);
        	final int order = attrs.getAttributeIntValue("android", "orderInCategory", groupOrder);
        	//itemTitle = mContext.getText(attrs.getAttributeResourceValue("android", "title", 0));
        	itemTitleCondensed = attrs.getAttributeValue("android", "titleCondensed");
        	//itemIconResId = attrs.getAttributeResourceValue("android", "icon", 0);
        	
            
        	TypedArray a = mContext.obtainStyledAttributes(attrs,
                    new int[]{
            		android.R.attr.id,android.R.attr.menuCategory,android.R.attr.orderInCategory,
            		android.R.attr.title,android.R.attr.titleCondensed,android.R.attr.icon,
            		android.R.attr.alphabeticShortcut,android.R.attr.numericShortcut,android.R.attr.checkable,
            		android.R.attr.checked,android.R.attr.visible,android.R.attr.enabled,
            		android.R.attr.showAsAction,android.R.attr.onClick,android.R.attr.actionLayout,
            		android.R.attr.actionViewClass,android.R.attr.actionProviderClass
            		});
        	
            // Inherit attributes from the group as default value
            itemId = a.getResourceId(findAttribute(attrs, "id"), defaultItemId);
            //final int category = a.getInt(android.R.attr.menuCategory, groupCategory);
            //final int order = a.getInt(android.R.attr.orderInCategory, groupOrder);
            //itemCategoryOrder = (category & 0x0000ffff) | (order & 0x0000ffff);
            itemTitle = a.getText(findAttribute(attrs, "title"));
            //itemTitleCondensed = a.getText(android.R.attr.titleCondensed);
            itemIconResId = a.getResourceId(findAttribute(attrs, "icon"), 0);
            //itemAlphabeticShortcut = getShortcut(a.getString(android.R.attr.alphabeticShortcut));
            //itemNumericShortcut = getShortcut(a.getString(android.R.attr.numericShortcut));
        	itemCheckable = attrs.getAttributeBooleanValue("android", "checkable", false) ? 1 : 0;
            /*if (a.hasValue(android.R.attr.checkable)) {
                // Item has attribute checkable, use it
                itemCheckable = a.getBoolean(android.R.attr.checkable, false) ? 1 : 0;
            } else {
                // Item does not have attribute, use the group's (group can have one more state
                // for checkable that represents the exclusive checkable)
                itemCheckable = groupCheckable;
            }*/
        	itemChecked = attrs.getAttributeBooleanValue("android", "checked", false);
        	itemVisible = attrs.getAttributeBooleanValue("android", "visible", true);
        	itemEnabled = attrs.getAttributeBooleanValue("android", "enabled", true);
        	itemShowAsAction = attrs.getAttributeIntValue("android", "showAsAction", -1);
        	/*
            //itemChecked = a.getBoolean(android.R.attr.checked, defaultItemChecked);
            itemVisible = a.getBoolean(android.R.attr.visible, groupVisible);
            itemEnabled = a.getBoolean(android.R.attr.enabled, groupEnabled);
            itemShowAsAction = a.getInt(android.R.attr.showAsAction, -1);
            itemListenerMethodName = a.getString(android.R.attr.onClick);
            itemActionViewLayout = a.getResourceId(android.R.attr.actionLayout, 0);
            itemActionViewClassName = a.getString(android.R.attr.actionViewClass);
            itemActionProviderClassName = a.getString(android.R.attr.actionProviderClass);
        	 */
            final boolean hasActionProvider = itemActionProviderClassName != null;
            if (hasActionProvider && itemActionViewLayout == 0 && itemActionViewClassName == null) {
                itemActionProvider = newInstance(itemActionProviderClassName,
                            ACTION_PROVIDER_CONSTRUCTOR_SIGNATURE,
                            mActionProviderConstructorArguments);
            } else {
                if (hasActionProvider) {
                    Log.w(LOG_TAG, "Ignoring attribute 'actionProviderClass'."
                            + " Action view already specified.");
                }
                itemActionProvider = null;
            }

            a.recycle();

            itemAdded = false;
        }
        
        private int findAttribute(AttributeSet attrs, String name)
        {
        	for(int i=0;i<attrs.getAttributeCount();i++)
        		if(attrs.getAttributeName(i).equalsIgnoreCase(name))
        			return i;
        		//else Logger.LogVerbose(attrs.getAttributeName(i) + "=" + attrs.getAttributeValue(i));
        	return 0;
        }

        private char getShortcut(String shortcutString) {
            if (shortcutString == null) {
                return 0;
            } else {
                return shortcutString.charAt(0);
            }
        }
        
        private void setItem(MenuItem item) {
            item.setChecked(itemChecked)
                .setVisible(itemVisible)
                .setEnabled(itemEnabled)
                .setCheckable(itemCheckable >= 1)
                .setTitleCondensed(itemTitleCondensed)
                .setIcon(itemIconResId)
                .setAlphabeticShortcut(itemAlphabeticShortcut)
                .setNumericShortcut(itemNumericShortcut);
            
            if (itemShowAsAction >= 0) {
                item.setShowAsAction(itemShowAsAction);
            }
            
            if (itemListenerMethodName != null) {
                if (mContext.isRestricted()) {
                    throw new IllegalStateException("The android:onClick attribute cannot "
                            + "be used within a restricted context");
                }
                item.setOnMenuItemClickListener(
                        new InflatedOnMenuItemClickListener(mContext, itemListenerMethodName));
            }

            if (item instanceof MenuItemImpl) {
                MenuItemImpl impl = (MenuItemImpl) item;
                if (itemCheckable >= 2) {
                    impl.setExclusiveCheckable(true);
                }
            }

            boolean actionViewSpecified = false;
            if (itemActionViewClassName != null) {
                View actionView = (View) newInstance(itemActionViewClassName,
                        ACTION_VIEW_CONSTRUCTOR_SIGNATURE, mActionViewConstructorArguments);
                item.setActionView(actionView);
                actionViewSpecified = true;
            }
            if (itemActionViewLayout > 0) {
                if (!actionViewSpecified) {
                    item.setActionView(itemActionViewLayout);
                    actionViewSpecified = true;
                } else {
                    Log.w(LOG_TAG, "Ignoring attribute 'itemActionViewLayout'."
                            + " Action view already specified.");
                }
            }
            if (itemActionProvider != null) {
                item.setActionProvider(itemActionProvider);
            }
        }

        public void addItem() {
            itemAdded = true;
            setItem(menu.add(groupId, itemId, itemCategoryOrder, itemTitle));
        }
        
        public SubMenu addSubMenuItem() {
            itemAdded = true;
            SubMenu subMenu = menu.addSubMenu(groupId, itemId, itemCategoryOrder, itemTitle);
            setItem(subMenu.getItem());
            return subMenu;
        }
        
        public boolean hasAddedItem() {
            return itemAdded;
        }

        @SuppressWarnings("unchecked")
        private <T> T newInstance(String className, Class<?>[] constructorSignature,
                Object[] arguments) {
            try {
                Class<?> clazz = mContext.getClassLoader().loadClass(className);
                Constructor<?> constructor = clazz.getConstructor(constructorSignature);
                return (T) constructor.newInstance(arguments);
            } catch (Exception e) {
                Log.w(LOG_TAG, "Cannot instantiate class: " + className, e);
            }
            return null;
        }
    }
}
