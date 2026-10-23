package eu.amadeos.apps.contacts.ui.main;

import eu.amadeos.apps.contacts.domain.Source;

/** One line of the list on the left: what it shows, its name and how many contacts it has. */
public record SourceItem(Source source, String label, int count) {
}
