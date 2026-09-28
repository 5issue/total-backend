package com.kurly.product.presentation.dto;

import java.util.List;

public record ProductAutocompleteResponse(
        List<String> suggestions
) {
}
