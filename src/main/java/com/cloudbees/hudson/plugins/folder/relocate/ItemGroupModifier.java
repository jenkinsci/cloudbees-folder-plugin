/*
 * The MIT License
 *
 * Copyright 2013 CloudBees.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.hudson.plugins.folder.relocate;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import java.io.IOException;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;

public interface ItemGroupModifier<G extends ItemGroup<I>, I extends TopLevelItem> extends ExtensionPoint {

    /**
     * The type of group that this modifier works on.
     */
    Class<G> getTargetClass();

    /**
     * Returns  if the target can take the item.
     *
     
     
     
     *
     */
    <II extends I> boolean canAdd(G target, II item);

    /**
     * Adds an item to the target.
     *
     
     
     
     *
     *         new instance, depending on the target container.
     */
    <II extends I> II add(G target, II item) throws IOException;

    /**
     * Removes an item from the target.
     *
     
     
     */
    void remove(G target, I item) throws IOException;

    /**
     * A factory for creating  instances.
     */
    public static final class Factory {
        /**
         * Do not instantiate
         */
        private Factory() {
            throw new IllegalAccessError("Utility class");
        }

        /**
         * Returns the most appropriate  for the supplied type of .
         *
         
         *
         *          if no injector is available.
         */
        public static <G extends ItemGroup<I>, I extends TopLevelItem> ItemGroupModifier<G, I> get(
                Class<G> targetClass) {
            ItemGroupModifier<G, I> best = null;
            for (ItemGroupModifier i : ExtensionList.lookup(ItemGroupModifier.class)) {
                if (i.getTargetClass().isAssignableFrom(targetClass)) {
                    if (best == null) {
                        best = i;
                    } else {
                        if (best.getTargetClass().isAssignableFrom(i.getTargetClass())) {
                            // closer fit
                            best = i;
                        }
                    }
                }
            }
            return best;
        }
    }

    final class StandardModifier implements ItemGroupModifier<DirectlyModifiableTopLevelItemGroup,TopLevelItem> {

         public Class<DirectlyModifiableTopLevelItemGroup> getTargetClass() {
            return DirectlyModifiableTopLevelItemGroup.class;
        }

         public <II extends TopLevelItem> boolean canAdd(DirectlyModifiableTopLevelItemGroup target, II item) {
            return target.canAdd(item);
        }

         public <II extends TopLevelItem> II add(DirectlyModifiableTopLevelItemGroup target, II item) throws IOException {
            II _item = target.add(item, item.getName());
            _item.onLoad(target, item.getName());
            return _item;
        }

         public void remove(DirectlyModifiableTopLevelItemGroup target, TopLevelItem item) throws IOException {
            target.remove(item);
        }

    }

}
