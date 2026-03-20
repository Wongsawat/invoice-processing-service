package com.wpanther.invoice.processing.infrastructure.adapter.out.persistence;

import com.wpanther.invoice.processing.domain.model.Address;
import com.wpanther.invoice.processing.domain.model.InvoiceId;
import com.wpanther.invoice.processing.domain.model.LineItem;
import com.wpanther.invoice.processing.domain.model.Money;
import com.wpanther.invoice.processing.domain.model.Party;
import com.wpanther.invoice.processing.domain.model.ProcessedInvoice;
import com.wpanther.invoice.processing.domain.model.TaxIdentifier;
import com.wpanther.invoice.processing.infrastructure.adapter.out.persistence.InvoicePartyEntity.PartyType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Mapper between domain model and JPA entities
 */
@Component
public class ProcessedInvoiceMapper {

    /**
     * Convert domain model to JPA entity
     */
    public ProcessedInvoiceEntity toEntity(ProcessedInvoice domain) {
        ProcessedInvoiceEntity entity = ProcessedInvoiceEntity.builder()
            .id(domain.getId().value())
            .sourceInvoiceId(domain.getSourceInvoiceId())
            .invoiceNumber(domain.getInvoiceNumber())
            .issueDate(domain.getIssueDate())
            .dueDate(domain.getDueDate())
            .currency(domain.getCurrency())
            .subtotal(domain.getSubtotal().amount())
            .totalTax(domain.getTotalTax().amount())
            .total(domain.getTotal().amount())
            .originalXml(domain.getOriginalXml())
            .status(domain.getStatus())
            .errorMessage(domain.getErrorMessage())
            .createdAt(domain.getCreatedAt())
            .completedAt(domain.getCompletedAt())
            .parties(new HashSet<>())
            .lineItems(new ArrayList<>())
            .build();

        // Map seller
        InvoicePartyEntity seller = toPartyEntity(domain.getSeller(), PartyType.SELLER);
        entity.addParty(seller);

        // Map buyer
        InvoicePartyEntity buyer = toPartyEntity(domain.getBuyer(), PartyType.BUYER);
        entity.addParty(buyer);

        // Map line items
        int lineNumber = 1;
        for (LineItem item : domain.getItems()) {
            InvoiceLineItemEntity lineItemEntity = toLineItemEntity(item, lineNumber++);
            entity.addLineItem(lineItemEntity);
        }

        return entity;
    }

    /**
     * Convert JPA entity to domain model
     */
    public ProcessedInvoice toDomain(ProcessedInvoiceEntity entity) {
        // Find seller and buyer
        Party seller = null;
        Party buyer = null;

        for (InvoicePartyEntity partyEntity : entity.getParties()) {
            Party party = toPartyDomain(partyEntity);
            if (partyEntity.getPartyType() == PartyType.SELLER) {
                seller = party;
            } else if (partyEntity.getPartyType() == PartyType.BUYER) {
                buyer = party;
            }
        }

        // Convert line items
        List<LineItem> items = new ArrayList<>();
        for (InvoiceLineItemEntity itemEntity : entity.getLineItems()) {
            items.add(toLineItemDomain(itemEntity, entity.getCurrency()));
        }

        if (seller == null) {
            throw new IllegalStateException("No SELLER party found for invoice " + entity.getId());
        }
        if (buyer == null) {
            throw new IllegalStateException("No BUYER party found for invoice " + entity.getId());
        }

        // Build domain object
        return ProcessedInvoice.builder()
            .id(InvoiceId.from(entity.getId().toString()))
            .sourceInvoiceId(entity.getSourceInvoiceId())
            .invoiceNumber(entity.getInvoiceNumber())
            .issueDate(entity.getIssueDate())
            .dueDate(entity.getDueDate())
            .seller(seller)
            .buyer(buyer)
            .items(items)
            .currency(entity.getCurrency())
            .originalXml(entity.getOriginalXml())
            .status(entity.getStatus())
            .createdAt(entity.getCreatedAt())
            .completedAt(entity.getCompletedAt())
            .errorMessage(entity.getErrorMessage())
            .build();
    }

    private InvoicePartyEntity toPartyEntity(Party domain, PartyType partyType) {
        return InvoicePartyEntity.builder()
            .partyType(partyType)
            .name(domain.name())
            .taxId(domain.taxIdentifier() != null ? domain.taxIdentifier().value() : null)
            .taxIdScheme(domain.taxIdentifier() != null ? domain.taxIdentifier().scheme() : null)
            .streetAddress(domain.address() != null ? domain.address().streetAddress() : null)
            .city(domain.address() != null ? domain.address().city() : null)
            .postalCode(domain.address() != null ? domain.address().postalCode() : null)
            .country(domain.address() != null ? domain.address().country() : null)
            .email(domain.email())
            .build();
    }

    private Party toPartyDomain(InvoicePartyEntity entity) {
        TaxIdentifier taxId = entity.getTaxId() != null
            ? TaxIdentifier.of(entity.getTaxId(), entity.getTaxIdScheme())
            : null;

        Address address = Address.of(
            entity.getStreetAddress(),
            entity.getCity(),
            entity.getPostalCode(),
            entity.getCountry()
        );

        return Party.of(entity.getName(), taxId, address, entity.getEmail());
    }

    private InvoiceLineItemEntity toLineItemEntity(LineItem domain, int lineNumber) {
        return InvoiceLineItemEntity.builder()
            .lineNumber(lineNumber)
            .description(domain.description())
            .quantity(domain.quantity())
            .unitPrice(domain.unitPrice().amount())
            .taxRate(domain.taxRate())
            .lineTotal(domain.getLineTotal().amount())
            .taxAmount(domain.getTaxAmount().amount())
            .build();
    }

    private LineItem toLineItemDomain(InvoiceLineItemEntity entity, String currency) {
        Money unitPrice = Money.of(entity.getUnitPrice(), currency);
        return new LineItem(
            entity.getDescription(),
            entity.getQuantity(),
            unitPrice,
            entity.getTaxRate()
        );
    }
}
