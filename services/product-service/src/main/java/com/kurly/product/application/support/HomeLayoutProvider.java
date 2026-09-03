package com.kurly.product.application.support;

import com.kurly.product.presentation.dto.HomeResponse.QuickMenuItemDto;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HomeLayoutProvider {

    public List<QuickMenuItemDto> getQuickMenus() {
        return List.of(
                new  QuickMenuItemDto("신상품", "/images/quickmenu/new.png", "/products?sort=LATEST"),
                new QuickMenuItemDto("베스트", "/images/quickmenu/best.png", "/products?sort=BEST"),
                new QuickMenuItemDto("알뜰쇼핑", "/images/quickmenu/sale.png", "/products?sort=SALE"),
                new QuickMenuItemDto("특가/혜택", "/images/quickmenu/deal.png", "/products?sort=DEAL")
        );
    }

}
