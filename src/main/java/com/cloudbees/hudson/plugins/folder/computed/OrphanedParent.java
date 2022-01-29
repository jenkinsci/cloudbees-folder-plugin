package com.cloudbees.hudson.plugins.folder.computed;

import hudson.model.Item;
import jenkins.model.CauseOfInterruption;

/**
 * Indicates that an execution was aborted because of the orphaning of one of its parents.
 *
 * @author Réda Housni Alaoui
 */
public class OrphanedParent extends CauseOfInterruption {

	private final String orphanedParentFullName;

	public OrphanedParent(Item orphanedParent) {
		this.orphanedParentFullName = orphanedParent.getFullName();
	}

	@Override
	public String getShortDescription() {
		return Messages.OrphanedParent_CauseOfInterruption_ShortDescription(orphanedParentFullName);
	}
}
