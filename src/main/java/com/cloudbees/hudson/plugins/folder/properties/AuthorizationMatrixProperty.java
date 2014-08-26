/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., Peter Hayes, Tom Huybrechts
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

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.FolderProperty;
import com.cloudbees.hudson.plugins.folder.FolderPropertyDescriptor;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Run;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.security.SidACL;
import hudson.util.FormValidation;
import hudson.util.RobustReflectionConverter;
import net.sf.json.JSONObject;
import org.acegisecurity.acls.sid.Sid;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds ACL for {@link ProjectMatrixAuthorizationStrategy}.
 */
public class AuthorizationMatrixProperty extends FolderProperty<Folder> {

    private transient SidACL acl = new AclImpl();

    /**
     * List up all permissions that are granted.
     *
     * Strings are either the granted authority or the principal, which is not
     * distinguished.
     */
    private final Map<Permission, Set<String>> grantedPermissions = new HashMap<Permission, Set<String>>();

    private Set<String> sids = new HashSet<String>();

    protected AuthorizationMatrixProperty() {
    }

    public AuthorizationMatrixProperty(Map<Permission,? extends Set<String>> grantedPermissions) {
        // do a deep copy to be safe
        for (Entry<Permission,? extends Set<String>> e : grantedPermissions.entrySet())
            this.grantedPermissions.put(e.getKey(),new HashSet<String>(e.getValue()));
    }

    public Set<String> getGroups() {
        return sids;
    }

    /**
     * Returns all SIDs configured in this matrix, minus "anonymous"
     *
     * @return Always non-null.
     */
    public List<String> getAllSIDs() {
        Set<String> r = new HashSet<String>();
        for (Set<String> set : grantedPermissions.values())
            r.addAll(set);
        r.remove("anonymous");

        String[] data = r.toArray(new String[r.size()]);
        Arrays.sort(data);
        return Arrays.asList(data);
    }

    /**
     * Returns all the (Permission,sid) pairs that are granted, in the multi-map form.
     *
     * @return
     *      read-only. never null.
     */
    public Map<Permission,Set<String>> getGrantedPermissions() {
        return Collections.unmodifiableMap(grantedPermissions);
    }

    /**
     * Adds to {@link #grantedPermissions}. Use of this method should be limited
     * during construction, as this object itself is considered immutable once
     * populated.
     */
    protected void add(Permission p, String sid) {
        Set<String> set = grantedPermissions.get(p);
        if (set == null)
            grantedPermissions.put(p, set = new HashSet<String>());
        set.add(sid);
        sids.add(sid);
    }

    @Extension
    public static class DescriptorImpl extends FolderPropertyDescriptor {
        @Override
        public FolderProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            formData = formData.getJSONObject("useProjectSecurity");
            if (formData.isNullObject())
                return null;

            AuthorizationMatrixProperty amp = new AuthorizationMatrixProperty();
            for (Map.Entry<String, Object> r : (Set<Map.Entry<String, Object>>) formData.getJSONObject("data").entrySet()) {
                String sid = r.getKey();
                if (r.getValue() instanceof JSONObject) {
                    for (Map.Entry<String, Boolean> e : (Set<Map.Entry<String, Boolean>>) ((JSONObject) r
                            .getValue()).entrySet()) {
                        if (e.getValue()) {
                            Permission p = Permission.fromId(e.getKey());
                            amp.add(p, sid);
                        }
                    }
                }
            }
            return amp;
        }

        @Override
        public boolean isApplicable(Class<? extends Folder> containerType) {
            // only applicable when ProjectMatrixAuthorizationStrategy is in charge
            try {
                return Hudson.getInstance().getAuthorizationStrategy() instanceof ProjectMatrixAuthorizationStrategy;
            } catch (NoClassDefFoundError x) { // after matrix-auth split?
                return false;
            }
        }

        @Override
        public String getDisplayName() {
            return "Authorization Matrix";
        }

        public List<PermissionGroup> getAllGroups() {
            return Arrays.asList(PermissionGroup.get(Item.class),PermissionGroup.get(Run.class));
        }

        public boolean showPermission(Permission p) {
            return p.getEnabled();
        }

        public FormValidation doCheckName(@AncestorInPath Folder folder, @QueryParameter String value) throws IOException, ServletException {
            return GlobalMatrixAuthorizationStrategy.DESCRIPTOR.doCheckName_(value, folder, AbstractProject.CONFIGURE);
        }
    }

    private final class AclImpl extends SidACL {
        @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "NP_BOOLEAN_RETURN_NULL",
                justification = "Because that is the way this SPI works")
        protected Boolean hasPermission(Sid sid, Permission p) {
            if (AuthorizationMatrixProperty.this.hasPermission(toString(sid),p))
                return true;
            return null;
        }
    }

    public SidACL getACL() {
        return acl;
    }

    /**
     * Checks if the given SID has the given permission.
     */
    public boolean hasPermission(String sid, Permission p) {
        for (; p != null; p = p.impliedBy) {
            Set<String> set = grantedPermissions.get(p);
            if (set != null && set.contains(sid))
                return true;
        }
        return false;
    }

    /**
     * Checks if the permission is explicitly given, instead of implied through {@link Permission#impliedBy}.
     */
    public boolean hasExplicitPermission(String sid, Permission p) {
        Set<String> set = grantedPermissions.get(p);
        return set != null && set.contains(sid);
    }

    /**
     * Works like {@link #add(Permission, String)} but takes both parameters
     * from a single string of the form <tt>PERMISSIONID:sid</tt>
     */
    private void add(String shortForm) {
        int idx = shortForm.indexOf(':');
        Permission p = Permission.fromId(shortForm.substring(0, idx));
        if (p==null)
            throw new IllegalArgumentException("Failed to parse '"+shortForm+"' --- no such permission");
        add(p, shortForm.substring(idx + 1));
    }

    /**
     * Persist {@link ProjectMatrixAuthorizationStrategy} as a list of IDs that
     * represent {@link ProjectMatrixAuthorizationStrategy#grantedPermissions}.
     */
    public static final class ConverterImpl implements Converter {
        public boolean canConvert(Class type) {
            return type == AuthorizationMatrixProperty.class;
        }

        public void marshal(Object source, HierarchicalStreamWriter writer,
                MarshallingContext context) {
            AuthorizationMatrixProperty amp = (AuthorizationMatrixProperty) source;

            for (Entry<Permission, Set<String>> e : amp.grantedPermissions
                    .entrySet()) {
                String p = e.getKey().getId();
                for (String sid : e.getValue()) {
                    writer.startNode("permission");
                    writer.setValue(p + ':' + sid);
                    writer.endNode();
                }
            }
        }

        public Object unmarshal(HierarchicalStreamReader reader,
                final UnmarshallingContext context) {
            AuthorizationMatrixProperty as = new AuthorizationMatrixProperty();

            while (reader.hasMoreChildren()) {
                reader.moveDown();
                try {
                    as.add(reader.getValue());
                } catch (IllegalArgumentException ex) {
                     Logger.getLogger(AuthorizationMatrixProperty.class.getName())
                           .log(Level.WARNING,"Skipping a non-existent permission",ex);
                     RobustReflectionConverter.addErrorInContext(context, ex);
                }
                reader.moveUp();
            }

            return as;
        }
    }
}
