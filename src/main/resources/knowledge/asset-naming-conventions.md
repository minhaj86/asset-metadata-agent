# Asset Naming Conventions

Rules for asset identifiers and filenames in the enterprise DAM.

## External asset identifiers
Every asset has an external asset id assigned by the DAM, e.g. DA-100001 in this demo. The id never changes and
is the key for synchronisation between systems. Binaries are stored only in the DAM; other systems reference
them by id or URL.

## Filename pattern
Filenames are lowercase, hyphen-separated and describe the content in this order:
colour - product - view, optionally followed by a qualifier. Examples: blue-shirt-front.jpg,
red-dress-model.jpg, navy-chinos-back.jpg, light-blue-oxford-shirt-detail.jpg.
Campaign assets use campaign-season-purpose, e.g. summer-campaign-hero.jpg.

## Using filenames as evidence
Filenames are helpful hints but not authoritative. A view word in the filename (front, back, left, right,
detail) supports angleFacing only when image analysis agrees. Colour words in filenames must be checked against
the product information system before they are used for color.

## Versions
Re-shoots of the same product and view receive a new asset id; near-duplicates should be reviewed and the weaker
asset archived.
