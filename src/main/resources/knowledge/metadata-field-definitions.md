# Metadata Field Definitions

Definitions of the descriptive metadata fields used for digital assets.

## angleFacing
angleFacing describes which side of the product faces the camera: FRONT, BACK, LEFT, RIGHT or DETAIL.
FRONT means the front of the garment is visible, BACK the back, LEFT and RIGHT are side views, and DETAIL is a
close-up of a specific part such as a collar or a button. angleFacing is not the camera position; see angle.
Because angleFacing drives which image is shown first on product pages, AI proposals always need human approval.

## angle
angle is the vertical camera position: EYE_LEVEL, HIGH, LOW or OVERHEAD (flat-lay shots are OVERHEAD).

## shotType
shotType describes the photographic set-up: STUDIO (packshot on a plain background), ON_MODEL (worn by a model),
FLAT_LAY (laid flat, shot from above), LIFESTYLE (in a real environment), DETAIL (close-up) and
GHOST_MANNEQUIN (invisible mannequin).

## color and dominantColor
color is the main colour of the product as sold and should match the product information system.
dominantColor is the visually dominant colour of the entire image, including background, as measured by image
analysis. A white packshot of a navy jacket has color NAVY and dominantColor NAVY or WHITE depending on framing.

## backgroundType
backgroundType classifies the background: WHITE, GREY, COLORED, TRANSPARENT, OUTDOOR or INTERIOR.

## productType and category
productType is the specific type of the main product (SHIRT, T_SHIRT, TROUSERS, JEANS, JACKET, KNITWEAR, DRESS,
TOP, SKIRT, SNEAKERS, BOOTS, BAG, ACCESSORY, CUSHION). category is the coarse merchandising category (TOPS,
BOTTOMS, DRESSES, OUTERWEAR, KNITWEAR, FOOTWEAR, ACCESSORIES, HOME).

## keywords and tags
keywords are lowercase search terms that customers or editors would type, e.g. shirt, oxford, blue, menswear,
cotton. tags are normalised, hyphenated machine tags such as light-blue or front-view. Neither should contain
generic words like image, photo, new or sale.

## taxonomyPath
taxonomyPath is the canonical path of an existing taxonomy node, for example Fashion > Men > Clothing > Shirts.
Only paths that exist in the taxonomy are valid.

## intendedForABTest and experimentIdentifier
intendedForABTest must be populated (true) only when the asset is a variant in an A/B test that has been
registered by the experimentation team; otherwise leave it empty. When intendedForABTest is true,
experimentIdentifier is mandatory and must follow the pattern EXP-YYYY-NNN, for example EXP-2026-014.
AI never decides whether an asset participates in an A/B test.

## modelPresent, modelCount and genderPresentation
modelPresent states whether a human model is visible; modelCount gives the number of models.
genderPresentation describes the styling target of the presentation and is never inferred automatically.

## version and generation
version is the revision number of a digital asset; generation identifies the digital-twin pipeline generation.
