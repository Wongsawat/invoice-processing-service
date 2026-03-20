package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * JPA Entity for invoice parties (seller and buyer)
 */
@Entity
@Table(name = "invoice_parties", indexes = {
    @Index(name = "idx_party_invoice", columnList = "invoice_id"),
    @Index(name = "idx_party_type", columnList = "party_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoicePartyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private ProcessedInvoiceEntity invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 10)
    private PartyType partyType;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(name = "tax_id_scheme", length = 20)
    private String taxIdScheme;

    @Column(name = "street_address", length = 500)
    private String streetAddress;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "email", length = 200)
    private String email;

    public enum PartyType {
        SELLER, BUYER
    }
}
