package com.example.dam.metadata.domain.proposal;

import com.example.dam.common.domain.ResourceNotFoundException;
import java.util.UUID;

public class ProposalNotFoundException extends ResourceNotFoundException {

    public ProposalNotFoundException(UUID id) {
        super("PROPOSAL_NOT_FOUND", "Metadata proposal not found: " + id);
    }
}
