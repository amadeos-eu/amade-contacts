package eu.amadeos.apps.contacts.domain;

import java.util.Arrays;
import java.util.Objects;

/** A contact's picture as stored in the vCard, usually a JPEG. */
public record Photo(byte[] data, String mediaType) {
    public Photo {
        Objects.requireNonNull(data, "data");
        data = data.clone();
        mediaType = mediaType == null || mediaType.isBlank() ? "image/jpeg" : mediaType;
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Photo p && Arrays.equals(data, p.data) && mediaType.equals(p.mediaType);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data) * 31 + mediaType.hashCode();
    }

    @Override
    public String toString() {
        return "Photo[" + mediaType + ", " + data.length + " bytes]";
    }
}
