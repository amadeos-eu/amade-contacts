package eu.amadeos.apps.contacts.domain;

import java.util.Objects;

/** A phone number, e-mail address or website with what it is for. */
public record Entry(Kind kind, String value) {
    public Entry {
        Objects.requireNonNull(kind, "kind");
        value = value == null ? "" : value.strip();
    }

    public boolean isBlank() {
        return value.isEmpty();
    }
}
