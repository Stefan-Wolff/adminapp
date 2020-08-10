/* $This file is distributed under the terms of the license in LICENSE$ */

package edu.cornell.mannlib.vitro.webapp.utils.developer.listeners;

import org.apache.jena.rdf.listeners.StatementListener;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelChangedListener;
import org.apache.jena.rdf.model.Statement;

import edu.cornell.mannlib.vitro.webapp.utils.developer.Key;

/**
 * Disabling of indexing processes.
 * 
 * @author swolff
 */
public class DeveloperDisabledChangeListener extends StatementListener implements ModelChangedListener {
    
    private static boolean enabled = true;
    
    private final ModelChangedListener inner;
    

    public DeveloperDisabledChangeListener(ModelChangedListener inner, Key disablingKey) {
        this.inner = inner;
    }

    public static void setEnabled(boolean enabled) {            // yes, no synchronization (for performance reasons)
        DeveloperDisabledChangeListener.enabled = enabled;
    }
    
    private boolean isEnabled() {
        return DeveloperDisabledChangeListener.enabled;
    }

    // ----------------------------------------------------------------------
    // Delegated methods.
    // ----------------------------------------------------------------------

    @Override
    public void addedStatement(Statement stmt) {
        if (isEnabled()) {
            inner.addedStatement(stmt);
        }
    }

    @Override
    public void removedStatement(Statement stmt) {
        if (isEnabled()) {
            inner.removedStatement(stmt);
        }
    }

    @Override
    public void notifyEvent(Model model, Object event) {
        if (isEnabled()) {
            inner.notifyEvent(model, event);
        }
    }

}
