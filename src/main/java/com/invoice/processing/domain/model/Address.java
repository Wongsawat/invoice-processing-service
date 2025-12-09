package com.invoice.processing.domain.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Value Object representing a physical address
 */
public record Address(
    String streetAddress,
    String city,
    String postalCode,
    String country
) implements Serializable {

    public Address {
        Objects.requireNonNull(country, "Country is required");

        if (country.isBlank()) {
            throw new IllegalArgumentException("Country cannot be blank");
        }
    }

    /**
     * Create address with all fields
     */
    public static Address of(String streetAddress, String city, String postalCode, String country) {
        return new Address(streetAddress, city, postalCode, country);
    }

    /**
     * Format address as a single line
     */
    public String toSingleLine() {
        StringBuilder sb = new StringBuilder();

        if (streetAddress != null && !streetAddress.isBlank()) {
            sb.append(streetAddress);
        }

        if (city != null && !city.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }

        if (postalCode != null && !postalCode.isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(postalCode);
        }

        if (sb.length() > 0) sb.append(", ");
        sb.append(country);

        return sb.toString();
    }
}
