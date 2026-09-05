package com.czlr.orangemarketbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.ProductStatus;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.entity.dto.AddCartItemRequest;
import com.czlr.orangemarketbackend.entity.dto.CartDTO;
import com.czlr.orangemarketbackend.entity.dto.CartItemDTO;
import com.czlr.orangemarketbackend.entity.dto.CartMergeItemRequest;
import com.czlr.orangemarketbackend.entity.dto.CartMergeRequest;
import com.czlr.orangemarketbackend.entity.dto.ProductDTO;
import com.czlr.orangemarketbackend.entity.dto.UpdateCartItemRequest;
import com.czlr.orangemarketbackend.entity.po.CartItem;
import com.czlr.orangemarketbackend.entity.po.Product;
import com.czlr.orangemarketbackend.mapper.CartItemMapper;
import com.czlr.orangemarketbackend.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CartService extends ServiceImpl<CartItemMapper, CartItem> {

    private static final int SELECTED = 1;
    private static final int NOT_SELECTED = 0;
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2);

    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;

    public CartService(ProductMapper productMapper, ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.objectMapper = objectMapper;
    }

    public CartDTO getCart(Long userId) {
        List<CartItem> cartItems = baseMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByAsc(CartItem::getCreatedAt)
                .orderByAsc(CartItem::getId));
        if (cartItems.isEmpty()) {
            return emptyCart();
        }

        List<Long> productIds = cartItems.stream()
                .map(CartItem::getProductId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Product> products = productIds.isEmpty()
                ? Collections.emptyMap()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, product -> product));

        List<CartItemDTO> items = new ArrayList<>(cartItems.size());
        int selectedCount = 0;
        BigDecimal subtotal = ZERO_AMOUNT;
        BigDecimal shippingFee = ZERO_AMOUNT;

        for (CartItem cartItem : cartItems) {
            Product product = products.get(cartItem.getProductId());
            CartItemDTO item = toCartItemDTO(cartItem, product);
            items.add(item);
            if (isSelected(cartItem)) {
                selectedCount++;
                if (product != null) {
                    subtotal = subtotal.add(lineAmount(product, cartItem.getQuantity()));
                    shippingFee = shippingFee.add(amount(product.getShippingFee()));
                }
            }
        }

        subtotal = amount(subtotal);
        shippingFee = amount(shippingFee);
        return new CartDTO(items, selectedCount, subtotal, shippingFee, amount(subtotal.add(shippingFee)));
    }

    @Transactional
    public CartItemDTO addItem(Long userId, AddCartItemRequest request) {
        Long productId = parseId(request == null ? null : request.getProductId(), "productId");
        int quantity = requireQuantity(request == null ? null : request.getQuantity());
        Product product = getAvailableProduct(productId);
        ensureStock(product, quantity);

        CartItem cartItem = findCartItem(userId, productId);
        if (cartItem == null) {
            cartItem = new CartItem();
            cartItem.setUserId(userId);
            cartItem.setProductId(productId);
            cartItem.setQuantity(quantity);
            cartItem.setSelected(SELECTED);
            save(cartItem);
        } else {
            int mergedQuantity = addQuantities(cartItem.getQuantity(), quantity);
            ensureStock(product, mergedQuantity);
            cartItem.setQuantity(mergedQuantity);
            updateById(cartItem);
        }
        return toCartItemDTO(cartItem, product);
    }

    @Transactional
    public CartItemDTO updateItem(Long userId, Long cartItemId, UpdateCartItemRequest request) {
        if (request == null || (request.getQuantity() == null && request.getSelected() == null)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "至少提供 quantity 或 selected 字段");
        }

        CartItem cartItem = getOwnedCartItem(userId, cartItemId);
        if (request.getQuantity() != null) {
            int quantity = requireQuantity(request.getQuantity());
            Product product = getAvailableProduct(cartItem.getProductId());
            ensureStock(product, quantity);
            cartItem.setQuantity(quantity);
        }
        if (request.getSelected() != null) {
            cartItem.setSelected(toSelectedValue(request.getSelected()));
        }
        updateById(cartItem);
        return toCartItemDTO(cartItem, productMapper.selectById(cartItem.getProductId()));
    }

    @Transactional
    public void deleteItem(Long userId, Long cartItemId) {
        int deleted = baseMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, cartItemId)
                .eq(CartItem::getUserId, userId));
        if (deleted == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "购物车商品不存在");
        }
    }

    @Transactional
    public void updateSelection(Long userId, Boolean selected) {
        if (selected == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "selected 字段不能为空");
        }
        update(new LambdaUpdateWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .set(CartItem::getSelected, toSelectedValue(selected)));
    }

    @Transactional
    public void deleteSelectedItems(Long userId) {
        baseMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSelected, SELECTED));
    }

    @Transactional
    public CartDTO merge(Long userId, CartMergeRequest request) {
        if (request == null || request.getItems() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "items 字段不能为空");
        }

        Map<Long, MergeItem> mergedItems = new LinkedHashMap<>();
        for (CartMergeItemRequest item : request.getItems()) {
            if (item == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "购物车商品不能为空");
            }
            Long productId = parseId(item.getProductId(), "productId");
            int quantity = requireQuantity(item.getQuantity());
            boolean selected = item.getSelected() == null || item.getSelected();
            MergeItem previous = mergedItems.get(productId);
            int mergedQuantity = previous == null
                    ? quantity
                    : addQuantities(previous.quantity(), quantity);
            mergedItems.put(productId, new MergeItem(mergedQuantity, selected));
        }

        for (Map.Entry<Long, MergeItem> entry : mergedItems.entrySet()) {
            Long productId = entry.getKey();
            MergeItem mergeItem = entry.getValue();
            Product product = getAvailableProduct(productId);
            ensureStock(product, mergeItem.quantity());

            CartItem cartItem = findCartItem(userId, productId);
            if (cartItem == null) {
                cartItem = new CartItem();
                cartItem.setUserId(userId);
                cartItem.setProductId(productId);
                cartItem.setQuantity(mergeItem.quantity());
                cartItem.setSelected(toSelectedValue(mergeItem.selected()));
                save(cartItem);
            } else {
                int totalQuantity = addQuantities(cartItem.getQuantity(), mergeItem.quantity());
                ensureStock(product, totalQuantity);
                cartItem.setQuantity(totalQuantity);
                cartItem.setSelected(toSelectedValue(mergeItem.selected()));
                updateById(cartItem);
            }
        }
        return getCart(userId);
    }

    private CartDTO emptyCart() {
        return new CartDTO(List.of(), 0, ZERO_AMOUNT, ZERO_AMOUNT, ZERO_AMOUNT);
    }

    private CartItem findCartItem(Long userId, Long productId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, productId));
    }

    private CartItem getOwnedCartItem(Long userId, Long cartItemId) {
        CartItem cartItem = baseMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, cartItemId)
                .eq(CartItem::getUserId, userId));
        if (cartItem == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "购物车商品不存在");
        }
        return cartItem;
    }

    private Product getAvailableProduct(Long productId) {
        Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getId, productId)
                .eq(Product::getStatus, ProductStatus.ON_SALE));
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }
        return product;
    }

    private void ensureStock(Product product, int quantity) {
        if (product.getStock() == null || product.getStock() < quantity) {
            throw new BusinessException(ResultCode.CONFLICT, "商品库存不足");
        }
    }

    private int requireQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "quantity 必须大于等于 1");
        }
        return quantity;
    }

    private int addQuantities(Integer currentQuantity, int additionalQuantity) {
        long total = (long) (currentQuantity == null ? 0 : currentQuantity) + additionalQuantity;
        if (total < 1 || total > Integer.MAX_VALUE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "quantity 数值过大");
        }
        return (int) total;
    }

    private Long parseId(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, parameterName + " 不能为空");
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, parameterName + " 参数错误");
        }
    }

    private CartItemDTO toCartItemDTO(CartItem cartItem, Product product) {
        ProductDTO productDTO = product == null ? null : toProductDTO(product);
        BigDecimal effectivePrice = product == null ? null : amount(product.getSalePrice());
        BigDecimal shippingFee = product == null ? null : amount(product.getShippingFee());
        return new CartItemDTO(
                toString(cartItem.getId()),
                toString(cartItem.getProductId()),
                cartItem.getQuantity(),
                isSelected(cartItem),
                productDTO,
                effectivePrice,
                shippingFee);
    }

    private ProductDTO toProductDTO(Product product) {
        return new ProductDTO(
                toString(product.getId()),
                product.getName(),
                product.getSubtitle(),
                toString(product.getCategoryId()),
                parseStringList(product.getImagesJson(), product.getCoverImage()),
                product.getVideoUrl(),
                amount(product.getSalePrice()),
                amount(product.getOriginalPrice()),
                product.getStock(),
                product.getSalesCount(),
                product.getRatingAvg() == null ? null : product.getRatingAvg().doubleValue(),
                product.getReviewCount(),
                product.getDescription(),
                amount(product.getShippingFee()),
                parseStringList(product.getTagsJson(), null));
    }

    private List<String> parseStringList(String json, String fallback) {
        if (json != null && !json.isBlank()) {
            try {
                List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() { });
                if (values != null && !values.isEmpty()) {
                    return values;
                }
            } catch (RuntimeException e) {
                throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "商品数据格式错误");
            }
        }
        return fallback == null ? List.of() : List.of(fallback);
    }

    private BigDecimal lineAmount(Product product, Integer quantity) {
        if (product.getSalePrice() == null || quantity == null) {
            return ZERO_AMOUNT;
        }
        return amount(product.getSalePrice().multiply(BigDecimal.valueOf(quantity)));
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO_AMOUNT : value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isSelected(CartItem cartItem) {
        return Integer.valueOf(SELECTED).equals(cartItem.getSelected());
    }

    private int toSelectedValue(boolean selected) {
        return selected ? SELECTED : NOT_SELECTED;
    }

    private String toString(Long value) {
        return value == null ? null : value.toString();
    }

    private record MergeItem(int quantity, boolean selected) {
    }
}
