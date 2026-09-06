package com.czlr.orangemarketbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.OrderStatus;
import com.czlr.orangemarketbackend.common.enums.ProductStatus;
import com.czlr.orangemarketbackend.common.enums.ValueEnumUtils;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.entity.dto.CancelOrderRequest;
import com.czlr.orangemarketbackend.entity.dto.CartOrderCreateRequest;
import com.czlr.orangemarketbackend.entity.dto.CartOrderPreviewRequest;
import com.czlr.orangemarketbackend.entity.dto.DirectOrderCreateRequest;
import com.czlr.orangemarketbackend.entity.dto.OrderCreateResultDTO;
import com.czlr.orangemarketbackend.entity.dto.OrderAddressDTO;
import com.czlr.orangemarketbackend.entity.dto.OrderDTO;
import com.czlr.orangemarketbackend.entity.dto.OrderItemDTO;
import com.czlr.orangemarketbackend.entity.dto.OrderPageDTO;
import com.czlr.orangemarketbackend.entity.dto.OrderPreviewDTO;
import com.czlr.orangemarketbackend.entity.po.CartItem;
import com.czlr.orangemarketbackend.entity.po.Order;
import com.czlr.orangemarketbackend.entity.po.OrderItem;
import com.czlr.orangemarketbackend.entity.po.Product;
import com.czlr.orangemarketbackend.entity.po.UserAddress;
import com.czlr.orangemarketbackend.mapper.CartItemMapper;
import com.czlr.orangemarketbackend.mapper.OrderItemMapper;
import com.czlr.orangemarketbackend.mapper.OrderMapper;
import com.czlr.orangemarketbackend.mapper.ProductMapper;
import com.czlr.orangemarketbackend.mapper.UserAddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService  extends ServiceImpl<OrderMapper, Order> {

    private static final int PAYMENT_EXPIRE_MINUTES = 30;
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2);
    private static final DateTimeFormatter ORDER_NO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ProductMapper productMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserAddressMapper userAddressMapper;
    private final CartItemMapper cartItemMapper;

    public OrderService(
            ProductMapper productMapper,
            OrderItemMapper orderItemMapper,
            UserAddressMapper userAddressMapper,
            CartItemMapper cartItemMapper) {
        this.productMapper = productMapper;
        this.orderItemMapper = orderItemMapper;
        this.userAddressMapper = userAddressMapper;
        this.cartItemMapper = cartItemMapper;
    }

    public OrderPreviewDTO previewCart(Long userId, CartOrderPreviewRequest request) {
        validateCartRequest(request);
        List<Long> cartItemIds = parseIds(request.getCartItemIds(), "cartItemIds");
        UserAddress address = getOwnedAddress(userId, parseId(request.getAddressId(), "addressId"));
        List<CartItem> cartItems = getOwnedCartItems(userId, cartItemIds);
        List<OrderLine> lines = buildOrderLines(cartItems);
        return new OrderPreviewDTO(
                lines.stream().map(this::toOrderItemDTO).toList(),
                toOrderAddress(address),
                calculateSubtotal(lines),
                calculateShippingAmount(lines),
                calculateTotal(lines),
                PAYMENT_EXPIRE_MINUTES);
    }

    @Transactional
    public OrderCreateResultDTO createFromCart(Long userId, CartOrderCreateRequest request) {
        validateCartRequest(request);
        List<Long> cartItemIds = parseIds(request.getCartItemIds(), "cartItemIds");
        UserAddress address = getOwnedAddress(userId, parseId(request.getAddressId(), "addressId"));
        List<OrderLine> lines = buildOrderLines(getOwnedCartItems(userId, cartItemIds));
        decreaseStock(lines);

        Order order = createOrder(userId, address, request.getBuyerRemark(), lines);
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .in(CartItem::getId, cartItemIds));
        return toCreateResult(order);
    }

    @Transactional
    public OrderCreateResultDTO createDirect(Long userId, DirectOrderCreateRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        Long productId = parseId(request.getProductId(), "productId");
        int quantity = requireQuantity(request.getQuantity());
        Product product = getAvailableProduct(productId);
        ensureStock(product, quantity);
        UserAddress address = getOwnedAddress(userId, parseId(request.getAddressId(), "addressId"));
        decreaseStock(List.of(new OrderLine(product, quantity)));

        return toCreateResult(createOrder(
                userId,
                address,
                request.getBuyerRemark(),
                List.of(new OrderLine(product, quantity))));
    }

    public OrderPageDTO getOrders(Long userId, String status, int page, int pageSize) {
        validatePage(page, pageSize);
        OrderStatus orderStatus = parseOrderStatus(status);
        LambdaQueryWrapper<Order> query = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreatedAt)
                .orderByDesc(Order::getId);
        if (orderStatus != null) {
            query.eq(Order::getStatus, orderStatus);
        }

        Page<Order> orderPage = baseMapper.selectPage(new Page<>(page, pageSize), query);
        Map<Long, List<OrderItem>> itemsByOrderId = getItemsByOrderId(orderPage.getRecords());
        List<OrderDTO> orders = orderPage.getRecords().stream()
                .map(order -> toOrderDTO(order,
                        itemsByOrderId.getOrDefault(order.getId(), Collections.emptyList())))
                .toList();
        return new OrderPageDTO(
                orders,
                Math.toIntExact(orderPage.getTotal()),
                (int) orderPage.getCurrent(),
                (int) orderPage.getSize(),
                orderPage.hasNext());
    }

    public OrderDTO getOrder(Long userId, Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "orderId 参数错误");
        }
        Order order = getOwnedOrder(userId, orderId);
        return toOrderDTO(order, getOrderItems(order.getId()));
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId, CancelOrderRequest request) {
        Order order = getOwnedOrder(userId, orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "仅待付款订单可以取消");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = baseMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT)
                .set(Order::getStatus, OrderStatus.CANCELLED)
                .set(Order::getCancelledAt, now)
                .set(Order::getCancelReason, request == null ? null : request.getReason()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "订单状态已发生变化");
        }

        for (OrderItem item : getOrderItems(order.getId())) {
            if (item.getProductId() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                continue;
            }
            if (productMapper.increaseStock(item.getProductId(), item.getQuantity()) == 0) {
                throw new BusinessException(ResultCode.CONFLICT, "库存恢复失败");
            }
        }
    }

    @Transactional
    public void receiveOrder(Long userId, Long orderId) {
        Order order = getOwnedOrder(userId, orderId);
        if (order.getStatus() != OrderStatus.PENDING_RECEIPT) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "仅待收货订单可以确认收货");
        }

        int updated = baseMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, OrderStatus.PENDING_RECEIPT)
                .set(Order::getStatus, OrderStatus.PENDING_REVIEW)
                .set(Order::getReceivedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "订单状态已发生变化");
        }
    }

    private void validateCartRequest(CartOrderPreviewRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
    }

    private void validateCartRequest(CartOrderCreateRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
    }

    private List<Long> parseIds(List<String> values, String parameterName) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, parameterName + " 不能为空");
        }
        List<Long> ids = values.stream()
                .map(value -> parseId(value, parameterName))
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, parameterName + " 不能为空");
        }
        return ids;
    }

    private UserAddress getOwnedAddress(Long userId, Long addressId) {
        UserAddress address = userAddressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, addressId)
                .eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }
        return address;
    }

    private List<CartItem> getOwnedCartItems(Long userId, List<Long> cartItemIds) {
        List<CartItem> cartItems = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .in(CartItem::getId, cartItemIds));
        Map<Long, CartItem> cartItemsById = cartItems.stream()
                .collect(java.util.stream.Collectors.toMap(
                        CartItem::getId,
                        cartItem -> cartItem,
                        (first, second) -> first,
                        LinkedHashMap::new));
        if (cartItemsById.size() != cartItemIds.size()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "购物车商品不存在");
        }
        return cartItemIds.stream().map(cartItemsById::get).toList();
    }

    private List<OrderLine> buildOrderLines(List<CartItem> cartItems) {
        List<Long> productIds = cartItems.stream()
                .map(CartItem::getProductId)
                .distinct()
                .toList();
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        Product::getId,
                        product -> product,
                        (first, second) -> first));

        List<OrderLine> lines = cartItems.stream()
                .map(cartItem -> {
                    Product product = products.get(cartItem.getProductId());
                    if (product == null || product.getStatus() != ProductStatus.ON_SALE) {
                        throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已下架");
                    }
                    int quantity = requireQuantity(cartItem.getQuantity());
                    ensureStock(product, quantity);
                    return new OrderLine(product, quantity);
                })
                .toList();
        if (lines.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "购物车商品不能为空");
        }
        return lines;
    }

    private Product getAvailableProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }
        return product;
    }

    private void ensureStock(Product product, int quantity) {
        if (product.getStock() == null || product.getStock() < quantity) {
            throw new BusinessException(ResultCode.CONFLICT, "商品库存不足");
        }
    }

    private void decreaseStock(List<OrderLine> lines) {
        for (OrderLine line : lines) {
            productMapper.decreaseStock(String.valueOf(line.product().getId()), line.quantity());
        }
    }

    private Order createOrder(
            Long userId, UserAddress address, String buyerRemark, List<OrderLine> lines) {
        BigDecimal subtotal = calculateSubtotal(lines);
        BigDecimal shippingAmount = calculateShippingAmount(lines);
        LocalDateTime now = LocalDateTime.now();

        Order order = new Order();
        order.setOrderNo(generateOrderNo(now));
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setSubtotalAmount(subtotal);
        order.setShippingAmount(shippingAmount);
        order.setTotalAmount(amount(subtotal.add(shippingAmount)));
        order.setBuyerRemark(buyerRemark);
        order.setReceiver(address.getReceiver());
        order.setReceiverPhone(address.getPhone());
        order.setProvince(address.getProvince());
        order.setCity(address.getCity());
        order.setDistrict(address.getDistrict());
        order.setDetail(address.getDetail());
        order.setPaymentExpireAt(now.plusMinutes(PAYMENT_EXPIRE_MINUTES));
        save(order);

        for (OrderLine line : lines) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(line.product().getId());
            orderItem.setProductName(line.product().getName());
            orderItem.setProductImage(line.product().getCoverImage());
            orderItem.setUnitPrice(amount(line.product().getSalePrice()));
            orderItem.setQuantity(line.quantity());
            orderItem.setLineAmount(lineAmount(line.product(), line.quantity()));
            orderItemMapper.insert(orderItem);
        }
        return order;
    }

    private OrderCreateResultDTO toCreateResult(Order order) {
        return new OrderCreateResultDTO(
                String.valueOf(order.getId()),
                order.getOrderNo(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getPaymentExpireAt());
    }

    private Map<Long, List<OrderItem>> getItemsByOrderId(List<Order> orders) {
        if (orders.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, orderIds)
                        .orderByAsc(OrderItem::getCreatedAt)
                        .orderByAsc(OrderItem::getId))
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        OrderItem::getOrderId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
    }

    private List<OrderItem> getOrderItems(Long orderId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getCreatedAt)
                .orderByAsc(OrderItem::getId));
    }

    private OrderDTO toOrderDTO(Order order, List<OrderItem> orderItems) {
        return new OrderDTO(
                String.valueOf(order.getId()),
                order.getOrderNo(),
                order.getStatus(),
                orderItems.stream().map(this::toOrderItemDTO).toList(),
                new OrderAddressDTO(
                        order.getReceiver(),
                        order.getReceiverPhone(),
                        order.getProvince(),
                        order.getCity(),
                        order.getDistrict(),
                        order.getDetail()),
                order.getSubtotalAmount(),
                order.getShippingAmount(),
                order.getTotalAmount(),
                order.getBuyerRemark(),
                order.getPaymentMethod(),
                order.getTrackingNo(),
                order.getCreatedAt(),
                order.getPaymentExpireAt(),
                order.getPaidAt(),
                order.getShippedAt(),
                order.getReceivedAt(),
                order.getCompletedAt(),
                order.getCancelledAt());
    }

    private OrderItemDTO toOrderItemDTO(OrderItem item) {
        return new OrderItemDTO(
                String.valueOf(item.getId()),
                item.getProductId() == null ? null : String.valueOf(item.getProductId()),
                item.getProductName(),
                item.getProductImage(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineAmount());
    }

    private OrderItemDTO toOrderItemDTO(OrderLine line) {
        return new OrderItemDTO(
                null,
                String.valueOf(line.product().getId()),
                line.product().getName(),
                line.product().getCoverImage(),
                amount(line.product().getSalePrice()),
                line.quantity(),
                lineAmount(line.product(), line.quantity()));
    }

    private OrderAddressDTO toOrderAddress(UserAddress address) {
        return new OrderAddressDTO(
                address.getReceiver(),
                address.getPhone(),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getDetail());
    }

    private BigDecimal calculateSubtotal(List<OrderLine> lines) {
        return amount(lines.stream()
                .map(line -> lineAmount(line.product(), line.quantity()))
                .reduce(ZERO_AMOUNT, BigDecimal::add));
    }

    private BigDecimal calculateShippingAmount(List<OrderLine> lines) {
        return amount(lines.stream()
                .map(line -> amount(line.product().getShippingFee()))
                .reduce(ZERO_AMOUNT, BigDecimal::add));
    }

    private BigDecimal calculateTotal(List<OrderLine> lines) {
        return amount(calculateSubtotal(lines).add(calculateShippingAmount(lines)));
    }

    private BigDecimal lineAmount(Product product, int quantity) {
        return amount(amount(product.getSalePrice()).multiply(BigDecimal.valueOf(quantity)));
    }

    private int requireQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "quantity 必须大于等于 1");
        }
        return quantity;
    }

    private OrderStatus parseOrderStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        OrderStatus orderStatus = ValueEnumUtils.fromValue(OrderStatus.class, status.trim());
        if (orderStatus == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单状态参数错误");
        }
        return orderStatus;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 50) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数错误");
        }
    }

    private Order getOwnedOrder(Long userId, Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "orderId 参数错误");
        }
        Order order = baseMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private String generateOrderNo(LocalDateTime time) {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 5)
                .toUpperCase(Locale.ROOT);
        return "OM" + time.format(ORDER_NO_FORMATTER) + suffix;
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

    private BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO_AMOUNT : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record OrderLine(Product product, int quantity) {
    }
}
