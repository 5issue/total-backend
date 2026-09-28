package com.kurly.product.infrastructure.scheduler;

import com.kurly.product.application.ProductAutocompleteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductAutocompleteIndexScheduler {

    private final ProductAutocompleteService productAutocompleteService;

    @Scheduled(initialDelay = 0, fixedDelayString = "${product.autocomplete.rebuild-delay-ms:60000}")
    public void rebuildIndex() {
        try {
            productAutocompleteService.rebuildIndex();
        } catch (RuntimeException e) {
            log.error("자동완성 인덱스 재구축 실패", e);
        }
    }
}
