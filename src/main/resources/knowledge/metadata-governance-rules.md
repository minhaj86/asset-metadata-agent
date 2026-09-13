# Metadata Governance Rules

Governance rules for descriptive metadata in the digital asset management (DAM) system. The machine-readable
source of truth is the metadata schema (metadata-schema/dam-metadata-schema.yaml); this document explains it.

## Mandatory fields for PRODUCT_IMAGE
Every PRODUCT_IMAGE must have these mandatory (required) fields before it can be published:
productId, productType, color, angleFacing, shotType, copyrightOwner, usageRightsEnd and altText.
Recommended fields for PRODUCT_IMAGE are title, keywords, taxonomyPath, backgroundType, material and description.
A product image without a valid productId cannot be linked to the product catalogue and stays in status DRAFT.

## Mandatory fields for MODEL_IMAGE
A MODEL_IMAGE (product worn by a model) requires productType, modelPresent, copyrightOwner, usageRightsStart,
usageRightsEnd and altText. When modelPresent is true, modelCount is mandatory as well.
Recommended: productId, modelCount, genderPresentation, angleFacing, color, keywords, taxonomyPath, title and season.

## Mandatory fields for CAMPAIGN_IMAGE
A CAMPAIGN_IMAGE requires campaign, season, market, copyrightOwner, usageRightsStart, usageRightsEnd and altText.
Recommended: title, description, keywords, taxonomyPath and intendedForABTest.

## Mandatory fields for VIDEO, DOCUMENT and DIGITAL_TWIN
VIDEO requires title, copyrightOwner, usageRightsEnd and language. DOCUMENT requires title and language.
DIGITAL_TWIN requires productId, version and generation. OTHER requires a title.

## Asset status
DRAFT means mandatory metadata is missing or invalid. IN_REVIEW means AI proposals wait for a human decision.
READY means all mandatory metadata is present and valid and nothing is pending. The status is derived
automatically after every metadata change.

## AI-generated metadata and approval
AI agents only create proposals. Each proposal carries a confidence, an explanation and evidence.
Proposals with confidence of at least 0.92 may be applied automatically, but only for fields whose schema allows
automatic updates (keywords, tags, description, color, dominantColor, backgroundType, shotType, productType,
category, modelPresent, modelCount, language). Proposals between 0.70 and 0.92 go to human review.
Proposals below 0.70 are kept as low-confidence suggestions and never applied automatically.
The schema safety rule always wins over confidence: taxonomyPath, copyrightOwner, angleFacing, campaign,
experimentIdentifier, brand and genderPresentation always require human approval.
AI may never set productId, productArticleCode, usageRightsStart, usageRightsEnd, market or intendedForABTest.

## Authoritative metadata
Values entered manually or delivered by the product information system or the DAM are authoritative.
AI must never overwrite authoritative values. AI values that were applied automatically have status VALIDATED;
once a human approves or edits them they become APPROVED. Every change is written to the audit history.
