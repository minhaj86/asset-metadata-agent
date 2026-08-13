-- Reference taxonomy (normally owned by a taxonomy management system).
-- Paths are canonical and are what the taxonomyPath metadata field stores.

INSERT INTO taxonomy_nodes (id, parent_id, name, path, depth, synonyms, description) VALUES
('fashion', NULL, 'Fashion', 'Fashion', 0, '{apparel,clothing,fashion}', 'All fashion products'),

('fashion.men', 'fashion', 'Men', 'Fashion > Men', 1, '{men,mens,menswear,male}', 'Menswear'),
('fashion.men.clothing', 'fashion.men', 'Clothing', 'Fashion > Men > Clothing', 2, '{apparel,garments}', 'Men''s clothing'),
('fashion.men.clothing.shirts', 'fashion.men.clothing', 'Shirts', 'Fashion > Men > Clothing > Shirts', 3, '{shirt,oxford,button-down,flannel,dress shirt}', 'Woven shirts with collar and buttons'),
('fashion.men.clothing.t-shirts', 'fashion.men.clothing', 'T-shirts', 'Fashion > Men > Clothing > T-shirts', 3, '{t-shirt,tshirt,tee,crew-neck,polo}', 'Jersey tops and polos'),
('fashion.men.clothing.trousers', 'fashion.men.clothing', 'Trousers', 'Fashion > Men > Clothing > Trousers', 3, '{trousers,pants,chinos,slacks}', 'Trousers and chinos'),
('fashion.men.clothing.jeans', 'fashion.men.clothing', 'Jeans', 'Fashion > Men > Clothing > Jeans', 3, '{jeans,denim trousers}', 'Denim jeans'),
('fashion.men.clothing.jackets', 'fashion.men.clothing', 'Jackets', 'Fashion > Men > Clothing > Jackets', 3, '{jacket,coat,blazer,outerwear,denim jacket}', 'Jackets, coats and blazers'),
('fashion.men.clothing.knitwear', 'fashion.men.clothing', 'Knitwear', 'Fashion > Men > Clothing > Knitwear', 3, '{sweater,jumper,cardigan,knit}', 'Knitted garments'),
('fashion.men.shoes', 'fashion.men', 'Shoes', 'Fashion > Men > Shoes', 2, '{footwear,shoes}', 'Men''s footwear'),
('fashion.men.shoes.sneakers', 'fashion.men.shoes', 'Sneakers', 'Fashion > Men > Shoes > Sneakers', 3, '{sneaker,trainers}', 'Sneakers'),
('fashion.men.shoes.boots', 'fashion.men.shoes', 'Boots', 'Fashion > Men > Shoes > Boots', 3, '{boot,boots}', 'Boots'),
('fashion.men.accessories', 'fashion.men', 'Accessories', 'Fashion > Men > Accessories', 2, '{accessories}', 'Men''s accessories'),
('fashion.men.accessories.belts', 'fashion.men.accessories', 'Belts', 'Fashion > Men > Accessories > Belts', 3, '{belt}', 'Belts'),
('fashion.men.accessories.hats', 'fashion.men.accessories', 'Hats & Caps', 'Fashion > Men > Accessories > Hats & Caps', 3, '{hat,cap,beanie}', 'Headwear'),

('fashion.women', 'fashion', 'Women', 'Fashion > Women', 1, '{women,womens,womenswear,ladies,female}', 'Womenswear'),
('fashion.women.clothing', 'fashion.women', 'Clothing', 'Fashion > Women > Clothing', 2, '{apparel,garments}', 'Women''s clothing'),
('fashion.women.clothing.dresses', 'fashion.women.clothing', 'Dresses', 'Fashion > Women > Clothing > Dresses', 3, '{dress,gown,maxi,midi,wrap dress}', 'Dresses'),
('fashion.women.clothing.tops', 'fashion.women.clothing', 'Tops & Blouses', 'Fashion > Women > Clothing > Tops & Blouses', 3, '{top,blouse,shirt,tee}', 'Tops, blouses and shirts'),
('fashion.women.clothing.skirts', 'fashion.women.clothing', 'Skirts', 'Fashion > Women > Clothing > Skirts', 3, '{skirt}', 'Skirts'),
('fashion.women.clothing.trousers', 'fashion.women.clothing', 'Trousers', 'Fashion > Women > Clothing > Trousers', 3, '{trousers,pants,culottes}', 'Trousers'),
('fashion.women.clothing.jackets', 'fashion.women.clothing', 'Jackets & Coats', 'Fashion > Women > Clothing > Jackets & Coats', 3, '{jacket,coat,blazer,outerwear}', 'Jackets and coats'),
('fashion.women.clothing.knitwear', 'fashion.women.clothing', 'Knitwear', 'Fashion > Women > Clothing > Knitwear', 3, '{sweater,jumper,cardigan,knit}', 'Knitted garments'),
('fashion.women.shoes', 'fashion.women', 'Shoes', 'Fashion > Women > Shoes', 2, '{footwear,shoes}', 'Women''s footwear'),
('fashion.women.shoes.heels', 'fashion.women.shoes', 'Heels', 'Fashion > Women > Shoes > Heels', 3, '{heel,pumps}', 'Heels'),
('fashion.women.shoes.sneakers', 'fashion.women.shoes', 'Sneakers', 'Fashion > Women > Shoes > Sneakers', 3, '{sneaker,trainers}', 'Sneakers'),
('fashion.women.accessories', 'fashion.women', 'Accessories', 'Fashion > Women > Accessories', 2, '{accessories}', 'Women''s accessories'),
('fashion.women.accessories.bags', 'fashion.women.accessories', 'Bags', 'Fashion > Women > Accessories > Bags', 3, '{bag,handbag,tote}', 'Bags'),
('fashion.women.accessories.jewellery', 'fashion.women.accessories', 'Jewellery', 'Fashion > Women > Accessories > Jewellery', 3, '{jewelry,necklace,earrings,bracelet}', 'Jewellery'),

('fashion.kids', 'fashion', 'Kids', 'Fashion > Kids', 1, '{kids,children,kidswear,baby}', 'Kidswear'),
('fashion.kids.clothing', 'fashion.kids', 'Clothing', 'Fashion > Kids > Clothing', 2, '{apparel}', 'Kids'' clothing'),
('fashion.kids.clothing.tops', 'fashion.kids.clothing', 'Tops', 'Fashion > Kids > Clothing > Tops', 3, '{top,t-shirt,shirt}', 'Kids'' tops'),
('fashion.kids.clothing.bottoms', 'fashion.kids.clothing', 'Bottoms', 'Fashion > Kids > Clothing > Bottoms', 3, '{trousers,shorts,jeans}', 'Kids'' bottoms'),

('home', NULL, 'Home', 'Home', 0, '{home,interior}', 'Home and interior'),
('home.textiles', 'home', 'Textiles', 'Home > Textiles', 1, '{textiles}', 'Home textiles'),
('home.textiles.cushions', 'home.textiles', 'Cushions', 'Home > Textiles > Cushions', 2, '{cushion,pillow}', 'Cushions'),
('home.textiles.bedding', 'home.textiles', 'Bedding', 'Home > Textiles > Bedding', 2, '{duvet,sheets,bedding}', 'Bedding'),
('home.decoration', 'home', 'Decoration', 'Home > Decoration', 1, '{decor,vase,candle}', 'Decoration'),

('marketing', NULL, 'Marketing', 'Marketing', 0, '{marketing}', 'Marketing content'),
('marketing.campaigns', 'marketing', 'Campaigns', 'Marketing > Campaigns', 1, '{campaign,campaigns}', 'Campaign content'),
('marketing.campaigns.seasonal', 'marketing.campaigns', 'Seasonal Campaigns', 'Marketing > Campaigns > Seasonal Campaigns', 2, '{seasonal,summer,winter,spring,autumn,hero}', 'Seasonal campaign content'),
('marketing.campaigns.lookbooks', 'marketing.campaigns', 'Lookbooks', 'Marketing > Campaigns > Lookbooks', 2, '{lookbook,editorial}', 'Lookbooks and editorials');
