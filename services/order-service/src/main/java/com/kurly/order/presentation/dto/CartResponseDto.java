package com.kurly.order.presentation.dto;

import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.DeliveryType;
import com.kurly.order.domain.common.StorageType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CartResponseDto(Address selectedAddress, List<Group> groups, AmountSummary amountSummary) {
    public static CartResponseDto from(Cart cart, Address address, Map<Long, Product> products) {
        Map<GroupKey, List<Item>> grouped = new LinkedHashMap<>();
        cart.getItems().forEach(cartItem -> {
            Product product = products.get(cartItem.getProductId());
            if (product == null) return;
            GroupKey key = new GroupKey(product.deliveryType(), product.storageType(), product.sellerId(),
                    product.sellerName(), product.deliveryFee() == null ? 0L : product.deliveryFee());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Item(cartItem.getId(), product.productId(),
                    product.skuId(), product.title(), product.thumbnailUrl(), product.unitPrice(), cartItem.getQuantity(),
                    product.maxQuantity(), product.available()));
        });
        List<Group> groups = grouped.entrySet().stream().map(entry -> {
            long amount = entry.getValue().stream().mapToLong(item -> item.unitPrice() * item.quantity()).sum();
            long fee = entry.getKey().deliveryFee();
            Seller seller = entry.getKey().sellerId() == null ? null : new Seller(entry.getKey().sellerId(), entry.getKey().sellerName());
            return new Group(entry.getKey().deliveryType(), entry.getKey().storageType(), seller, entry.getValue(), amount, fee);
        }).toList();
        long itemAmount = groups.stream().mapToLong(Group::groupItemAmount).sum();
        long deliveryFee = groups.stream().mapToLong(Group::groupDeliveryFee).sum();
        return new CartResponseDto(address, groups, new AmountSummary(itemAmount, 0L, deliveryFee, itemAmount + deliveryFee));
    }

    public record Address(Long addressId, String addressName, String recipientName, String recipientPhone,
                          String zipCode, String address, String detailAddress) {}
    public record Product(Long productId, Long skuId, String title, String thumbnailUrl, Long unitPrice,
                          Integer maxQuantity, boolean available, DeliveryType deliveryType, StorageType storageType,
                          Long sellerId, String sellerName, Long deliveryFee) {}
    public record Group(DeliveryType deliveryType, StorageType temperatureType, Seller seller, List<Item> items,
                        Long groupItemAmount, Long groupDeliveryFee) {}
    public record Seller(Long sellerId, String sellerName) {}
    public record Item(Long cartItemId, Long productId, Long skuId, String title, String thumbnailUrl,
                       Long unitPrice, Integer quantity, Integer maxQuantity, boolean available) {}
    public record AmountSummary(Long totalItemAmount, Long discountAmount, Long deliveryFee, Long paymentAmount) {}
    private record GroupKey(DeliveryType deliveryType, StorageType storageType, Long sellerId, String sellerName,
                            Long deliveryFee) {}
}
