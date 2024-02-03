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

package com.cloudbees.hudson.plugins.folder.properties;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.CredentialsStoreAction;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import hudson.security.Permission;
import hudson.util.CopyOnWriteMap;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import jenkins.model.Jenkins;
import net.jcip.annotations.GuardedBy;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.security.core.Authentication;

/**
 * A store of credentials that can be used as a Stapler object.
 */
@Extension(optional = true)
public class FolderCredentialsProvider extends CredentialsProvider {

    /**
     * The valid scopes for this store.
     */
    private static final Set<CredentialsScope> SCOPES =
            Collections.singleton(CredentialsScope.GLOBAL);

    @GuardedBy("self")
    private static final WeakHashMap<AbstractFolder<?>,FolderCredentialsProperty> emptyProperties =
            new WeakHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<CredentialsScope> getScopes(ModelObject object) {
        if (object instanceof AbstractFolder) {
            return SCOPES;
        }
        return super.getScopes(object);
    }

    @NonNull
    @Override
    public <C extends Credentials> List<C> getCredentialsInItemGroup(@NonNull Class<C> type, @Nullable ItemGroup itemGroup,
                                                          @Nullable Authentication authentication,
                                                          @NonNull List<DomainRequirement> domainRequirements) {
        List<C> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        if (ACL.SYSTEM2.equals(authentication)) {
            while (itemGroup != null) {
                if (itemGroup instanceof AbstractFolder) {
                    final AbstractFolder<?> folder = AbstractFolder.class.cast(itemGroup);
                    FolderCredentialsProperty property = folder.getProperties().get(FolderCredentialsProperty.class);
                    if (property != null) {
                        for (C c : DomainCredentials.getCredentials(
                                property.getDomainCredentialsMap(),
                                type,
                                domainRequirements,
                                CredentialsMatchers.always())) {
                            if (!(c instanceof IdCredentials) || ids.add(((IdCredentials) c).getId())) {
                                // if IdCredentials, only add if we haven't added already
                                // if not IdCredentials, always add
                                result.add(c);
                            }
                        }
                    }
                }
                if (itemGroup instanceof Item) {
                    itemGroup = Item.class.cast(itemGroup).getParent();
                } else {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <C extends Credentials> List<C> getCredentialsInItem(@NonNull Class<C> type, @NonNull Item item,
                                                          @Nullable Authentication authentication,
                                                          @NonNull List<DomainRequirement> domainRequirements) {
        if (item instanceof AbstractFolder) {
            // credentials defined in the folder should be available in the context of the folder
            return getCredentialsInItemGroup(type, (ItemGroup) item, authentication, domainRequirements);
        }
        return super.getCredentialsInItem(type, item, authentication, domainRequirements);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <C extends IdCredentials> ListBoxModel getCredentialIdsInItemGroup(@NonNull Class<C> type,
                                                                   @Nullable ItemGroup itemGroup,
                                                                   @Nullable Authentication authentication,
                                                                   @NonNull List<DomainRequirement> domainRequirements,
                                                                   @NonNull CredentialsMatcher matcher) {
        ListBoxModel result = new ListBoxModel();
        Set<String> ids = new HashSet<>();
        if (ACL.SYSTEM2.equals(authentication)) {
            while (itemGroup != null) {
                if (itemGroup instanceof AbstractFolder) {
                    final AbstractFolder<?> folder = AbstractFolder.class.cast(itemGroup);
                    FolderCredentialsProperty property = folder.getProperties().get(FolderCredentialsProperty.class);
                    if (property != null) {
                        for (C c : DomainCredentials.getCredentials(
                                property.getDomainCredentialsMap(),
                                type,
                                domainRequirements,
                                matcher)) {
                            if (ids.add(c.getId())) {
                                result.add(CredentialsNameProvider.name(c), c.getId());
                            }
                        }
                    }
                }
                if (itemGroup instanceof Item) {
                    itemGroup = ((Item)itemGroup).getParent();
                } else {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <C extends IdCredentials> ListBoxModel getCredentialIdsInItem(@NonNull Class<C> type, @NonNull Item item,
                                                                   @Nullable Authentication authentication,
                                                                   @NonNull List<DomainRequirement> domainRequirements,
                                                                   @NonNull CredentialsMatcher matcher) {
        if (item instanceof AbstractFolder) {
            // credentials defined in the folder should be available in the context of the folder
            return getCredentialIdsInItemGroup(type, (ItemGroup) item, authentication, domainRequirements, matcher);
        }
        return getCredentialIdsInItemGroup(type, item.getParent(), authentication, domainRequirements, matcher);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CredentialsStore getStore(@CheckForNull ModelObject object) {
        if (object instanceof AbstractFolder) {
            final AbstractFolder<?> folder = AbstractFolder.class.cast(object);
            FolderCredentialsProperty property = folder.getProperties().get(FolderCredentialsProperty.class);
            if (property != null) {
                return property.getStore();
            }
            synchronized (emptyProperties) {
                property = emptyProperties.get(folder);
                if (property == null) {
                    property = new FolderCredentialsProperty(folder);
                    emptyProperties.put(folder, property);
                }
            }
            return property.getStore();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconClassName() {
        return "icon-folder-store";
    }

    /**
     * Our property.
     */
    public static class FolderCredentialsProperty extends AbstractFolderProperty<AbstractFolder<?>> {

        /**
         * Old store of credentials
         *
         * @deprecated
         */
        @Deprecated
        private transient List<Credentials> credentials;

        /**
         * Our credentials.
         *
         * @since 3.10
         */
        private Map<Domain, List<Credentials>> domainCredentialsMap =
                new CopyOnWriteMap.Hash<>();

        /**
         * Our store.
         */
        private transient StoreImpl store = new StoreImpl();

        /*package*/ FolderCredentialsProperty(AbstractFolder<?> owner) {
            setOwner(owner);
            domainCredentialsMap = DomainCredentials.migrateListToMap(null, null);
        }

        /**
         * Backwards compatibility.
         *
         * @param credentials the credentials.
         * @deprecated
         */
        @Deprecated
        public FolderCredentialsProperty(List<Credentials> credentials) {
            domainCredentialsMap = DomainCredentials.migrateListToMap(domainCredentialsMap, credentials);
        }

        /**
         * Constructor for stapler.
         *
         * @param domainCredentials the credentials.
         * @since 1.5
         */
        @DataBoundConstructor
        public FolderCredentialsProperty(DomainCredentials[] domainCredentials) {
            domainCredentialsMap = DomainCredentials.asMap(Arrays.asList(domainCredentials));
        }

        /**
         * Resolve old data store into new data store.
         *
         * @since 1.5
         */
        @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "Only unprotected during deserialization")
        @SuppressWarnings("deprecation")
        private Object readResolve() throws ObjectStreamException {
            if (domainCredentialsMap == null) {
                domainCredentialsMap = DomainCredentials.migrateListToMap(domainCredentialsMap, credentials);
                credentials = null;
            }
            return this;
        }

        public <C extends Credentials> List<C> getCredentials(Class<C> type) {
            List<C> result = new ArrayList<>();
            for (Credentials credential : getCredentials()) {
                if (type.isInstance(credential)) {
                    result.add(type.cast(credential));
                }
            }
            return result;
        }

        /**
         * Gets all the folder's credentials.
         *
         * @return all the folder's credentials.
         */
        @SuppressWarnings("unused") // used by stapler
        public List<Credentials> getCredentials() {
            return getDomainCredentialsMap().get(Domain.global());
        }

        /**
         * Returns the {@link com.cloudbees.plugins.credentials.domains.DomainCredentials}
         *
         * @return the {@link com.cloudbees.plugins.credentials.domains.DomainCredentials}
         * @since 3.10
         */
        @SuppressWarnings("unused") // used by stapler
        public List<DomainCredentials> getDomainCredentials() {
            return DomainCredentials.asList(getDomainCredentialsMap());
        }

        /**
         * The Map of domain credentials.
         *
         * @return The Map of domain credentials.
         * @since 3.10
         */
        @SuppressWarnings("deprecation")
        @NonNull
        public synchronized Map<Domain, List<Credentials>> getDomainCredentialsMap() {
            return domainCredentialsMap = DomainCredentials.migrateListToMap(domainCredentialsMap, credentials);
        }

        /**
         * Sets the map of domain credentials.
         *
         * @param domainCredentialsMap the map of domain credentials.
         * @since 3.10
         */
        public synchronized void setDomainCredentialsMap(Map<Domain, List<Credentials>> domainCredentialsMap) {
            this.domainCredentialsMap = DomainCredentials.toCopyOnWriteMap(domainCredentialsMap);
        }

        /**
         * Returns the {@link StoreImpl}.
         * @return the {@link StoreImpl}.
         */
        @NonNull
        public synchronized StoreImpl getStore() {
            if (store == null) {
                store = new StoreImpl();
            }
            return store;
        }

        /**
         * Short-cut method for checking {@link CredentialsStore#hasPermission(hudson.security.Permission)}
         *
         * @param p the permission to check.
         */
        private void checkPermission(Permission p) {
            if (!store.hasPermission(p)) {
                throw new AccessDeniedException3(Jenkins.getAuthentication2(), p);
            }
        }

        /**
         * Short-cut method that redundantly checks the specified permission (to catch any typos) and then escalates
         * authentication in order to save the {@link CredentialsStore}.
         *
         * @param p the permissions of the operation being performed.
         * @throws IOException if something goes wrong.
         */
        private void checkedSave(Permission p) throws IOException {
            checkPermission(p);
            try (ACLContext oldContext = ACL.as2(ACL.SYSTEM2)) {
                FolderCredentialsProperty property =
                        owner.getProperties().get(FolderCredentialsProperty.class);
                if (property == null) {
                    synchronized (emptyProperties) {
                        owner.getProperties().add(this);
                        emptyProperties.remove(owner);
                    }
                }
                // we assume it is ourselves
                owner.save();
            }
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        private synchronized boolean addDomain(@NonNull Domain domain, List<Credentials> credentials)
                throws IOException {
            checkPermission(CredentialsProvider.MANAGE_DOMAINS);
            Map<Domain, List<Credentials>> domainCredentialsMap = getDomainCredentialsMap();
            if (domainCredentialsMap.containsKey(domain)) {
                List<Credentials> list = domainCredentialsMap.get(domain);
                boolean modified = false;
                for (Credentials c : credentials) {
                    if (list.contains(c)) {
                        continue;
                    }
                    list.add(c);
                    modified = true;
                }
                if (modified) {
                    checkedSave(CredentialsProvider.MANAGE_DOMAINS);
                }
                return modified;
            } else {
                domainCredentialsMap.put(domain, new ArrayList<>(credentials));
                checkedSave(CredentialsProvider.MANAGE_DOMAINS);
                return true;
            }
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        private synchronized boolean removeDomain(@NonNull Domain domain) throws IOException {
            checkPermission(CredentialsProvider.MANAGE_DOMAINS);
            Map<Domain, List<Credentials>> domainCredentialsMap = getDomainCredentialsMap();
            if (domainCredentialsMap.containsKey(domain)) {
                domainCredentialsMap.remove(domain);
                checkedSave(CredentialsProvider.MANAGE_DOMAINS);
                return true;
            }
            return false;
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        private synchronized boolean updateDomain(@NonNull Domain current, @NonNull Domain replacement)
                throws IOException {
            checkPermission(CredentialsProvider.MANAGE_DOMAINS);
            Map<Domain, List<Credentials>> domainCredentialsMap = getDomainCredentialsMap();
            if (domainCredentialsMap.containsKey(current)) {
                domainCredentialsMap.put(replacement, domainCredentialsMap.remove(current));
                checkedSave(CredentialsProvider.MANAGE_DOMAINS);
                return true;
            }
            return false;
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        private synchronized boolean addCredentials(@NonNull Domain domain, @NonNull Credentials credentials)
                throws IOException {
            checkPermission(CredentialsProvider.CREATE);
            Map<Domain, List<Credentials>> domainCredentialsMap = getDomainCredentialsMap();
            if (domainCredentialsMap.containsKey(domain)) {
                List<Credentials> list = domainCredentialsMap.get(domain);
                if (list.contains(credentials)) {
                    return false;
                }
                list.add(credentials);
                checkedSave(CredentialsProvider.CREATE);
                return true;
            }
            return false;
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        @NonNull
        private synchronized List<Credentials> getCredentials(@NonNull Domain domain) {
            if (store.hasPermission(CredentialsProvider.VIEW)) {
                List<Credentials> list = getDomainCredentialsMap().get(domain);
                if (list == null || list.isEmpty()) {
                    return Collections.emptyList();
                }
                return Collections.unmodifiableList(new ArrayList<>(list));
            }
            return Collections.emptyList();
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        private synchronized boolean removeCredentials(@NonNull Domain domain, @NonNull Credentials credentials)
                throws IOException {
            checkPermission(CredentialsProvider.DELETE);
            Map<Domain, List<Credentials>> domainCredentialsMap = getDomainCredentialsMap();
            if (domainCredentialsMap.containsKey(domain)) {
                List<Credentials> list = domainCredentialsMap.get(domain);
                if (!list.contains(credentials)) {
                    return false;
                }
                list.remove(credentials);
                checkedSave(CredentialsProvider.DELETE);
                return true;
            }
            return false;
        }

        /**
         * Implementation for {@link StoreImpl} to delegate to while keeping the lock synchronization simple.
         */
        private synchronized boolean updateCredentials(@NonNull Domain domain, @NonNull Credentials current,
                                                       @NonNull Credentials replacement) throws IOException {
            checkPermission(CredentialsProvider.UPDATE);
            Map<Domain, List<Credentials>> domainCredentialsMap = getDomainCredentialsMap();
            if (domainCredentialsMap.containsKey(domain)) {
                if (current instanceof IdCredentials || replacement instanceof IdCredentials) {
                    if (!current.equals(replacement)) {
                        throw new IllegalArgumentException("Credentials' IDs do not match, will not update.");
                    }
                }
                List<Credentials> list = domainCredentialsMap.get(domain);
                int index = list.indexOf(current);
                if (index == -1) {
                    return false;
                }
                list.set(index, replacement);
                checkedSave(CredentialsProvider.UPDATE);
                return true;
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public AbstractFolderProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws FormException {
            return this;
        }

        /**
         * Our {@link CredentialsStoreAction}.
         * @since 5.11
         */
        @Restricted(NoExternalUse.class)
        public class CredentialsStoreActionImpl extends CredentialsStoreAction {

            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public StoreImpl getStore() {
                return FolderCredentialsProperty.this.getStore();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getIconFileName() {
                return isVisible()
                        ? "/plugin/cloudbees-folder/images/svgs/folder-store.svg"
                        : null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getIconClassName() {
                return isVisible()
                        ? "icon-folder-store"
                        : null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.folder();
            }
        }

        /**
         * Our constructor
         */
        @Extension(optional = true)
        public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.FolderCredentialsProvider_DisplayName();
            }

            /**
             * Gets all the {@link com.cloudbees.plugins.credentials.domains.DomainSpecification} descriptors.
             *
             * @return all the {@link com.cloudbees.plugins.credentials.domains.DomainSpecification} descriptors.
             * @since 3.10
             */
            @SuppressWarnings("unused") // used by stapler
            public DescriptorExtensionList<DomainSpecification, Descriptor<DomainSpecification>>
            getSpecificationDescriptors() {
                return Jenkins.get().getDescriptorList(DomainSpecification.class);
            }
        }

        /**
         * Our actual {@link CredentialsStore}.
         */
        private class StoreImpl extends CredentialsStore {

            /**
             * Our action.
             */
            private final CredentialsStoreAction storeAction = new CredentialsStoreActionImpl();

            /**
             * {@inheritDoc}
             */
            @Override
            public ModelObject getContext() {
                return owner;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean hasPermission2(@NonNull Authentication a, @NonNull Permission permission) {
                return owner.getACL().hasPermission2(a, permission);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public CredentialsStoreAction getStoreAction() {
                return storeAction;
            }

            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public List<Domain> getDomains() {
                return Collections.unmodifiableList(new ArrayList<>(
                        getDomainCredentialsMap().keySet()
                ));
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean addDomain(@NonNull Domain domain, List<Credentials> credentials) throws IOException {
                return FolderCredentialsProperty.this.addDomain(domain, credentials);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean removeDomain(@NonNull Domain domain) throws IOException {
                return FolderCredentialsProperty.this.removeDomain(domain);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean updateDomain(@NonNull Domain current, @NonNull Domain replacement) throws IOException {
                return FolderCredentialsProperty.this.updateDomain(current, replacement);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean addCredentials(@NonNull Domain domain, @NonNull Credentials credentials) throws IOException {
                return FolderCredentialsProperty.this.addCredentials(domain, credentials);
            }

            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public List<Credentials> getCredentials(@NonNull Domain domain) {
                return FolderCredentialsProperty.this.getCredentials(domain);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean removeCredentials(@NonNull Domain domain, @NonNull Credentials credentials)
                    throws IOException {
                return FolderCredentialsProperty.this.removeCredentials(domain, credentials);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean updateCredentials(@NonNull Domain domain, @NonNull Credentials current,
                                             @NonNull Credentials replacement) throws IOException {
                return FolderCredentialsProperty.this.updateCredentials(domain, current, replacement);
            }
        }
    }
}
