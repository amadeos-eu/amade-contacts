package eu.amadeos.apps.contacts.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A postal address. Every part may be empty. */
public record PostalAddress(Kind kind, String street, String postalCode, String city, String region, String country) {
    public PostalAddress {
        Objects.requireNonNull(kind, "kind");
        street = clean(street);
        postalCode = clean(postalCode);
        city = clean(city);
        region = clean(region);
        country = clean(country);
    }

    public boolean isBlank() {
        return (street + postalCode + city + region + country).isBlank();
    }

    /**
     * The address as it goes on an envelope, one line per entry: street lines, then postal code and city
     * (with the region), then the country.
     */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (String line : street.split("\n")) {
            if (!line.isBlank()) lines.add(line.strip());
        }
        String town = String.join(" ", nonBlank(postalCode, city));
        if (!region.isEmpty()) town = town.isEmpty() ? region : town + ", " + region;
        if (!town.isEmpty()) lines.add(town);
        if (!country.isEmpty()) lines.add(country);
        return lines;
    }

    /** The address on one line, for searching and for a map. */
    public String oneLine() {
        return String.join(", ", lines());
    }

    private static List<String> nonBlank(String... parts) {
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) result.add(part);
        }
        return result;
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip().replace("\r\n", "\n");
    }
}
